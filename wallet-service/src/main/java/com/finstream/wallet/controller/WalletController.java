package com.finstream.wallet.controller;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.finstream.wallet.dto.LoginRequest;
import com.finstream.wallet.dto.RegisterRequest;
import com.finstream.wallet.model.User;
import com.finstream.wallet.model.Wallet;
import com.finstream.wallet.repository.UserRepository;
import com.finstream.wallet.repository.WalletRepository;
import com.finstream.wallet.security.CookieUtil;
import com.finstream.wallet.security.JwtUtil;
import com.finstream.wallet.security.RateLimitingService;
import com.finstream.wallet.service.WalletService;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/wallet")
public class WalletController {

    private final WalletService walletService;
    private final UserRepository userRepository;
    private final WalletRepository walletRepository;
    private final BCryptPasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;
    private final RateLimitingService rateLimitingService;
    private final CookieUtil cookieUtil;

    public WalletController(WalletService walletService, UserRepository userRepository, 
                           WalletRepository walletRepository, JwtUtil jwtUtil,
                           RateLimitingService rateLimitingService, CookieUtil cookieUtil) {
        this.walletService = walletService;
        this.userRepository = userRepository;
        this.walletRepository = walletRepository;
        this.jwtUtil = jwtUtil;
        this.rateLimitingService = rateLimitingService;
        this.cookieUtil = cookieUtil;
        this.passwordEncoder = new BCryptPasswordEncoder();
    }

    // get current user from JWT cookie
    @GetMapping("/me")
    public ResponseEntity<?> getCurrentUser(HttpServletRequest request) {
        String userId = (String) request.getAttribute("userId");
        if (userId == null) {
            return ResponseEntity.status(401).body(Map.of("error", "Not authenticated"));
        }
        
        Optional<User> userOpt = userRepository.findById(UUID.fromString(userId));
        if (userOpt.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        
        User user = userOpt.get();
        return ResponseEntity.ok(Map.of(
            "userId", user.getId().toString(),
            "fullName", user.getFullName(),
            "email", user.getEmail()
        ));
    }

    @GetMapping("/{userId}/balance")
    public ResponseEntity<?> getBalance(@PathVariable String userId) {
        return walletService.getWalletByUserId(UUID.fromString(userId))
                .<ResponseEntity<?>>map(w -> {
                    java.util.Map<String, Object> payload = new java.util.LinkedHashMap<>();
                    payload.put("userId", userId);
                    payload.put("balance", w.getBalance());
                    payload.put("currency", w.getCurrency());
                    return ResponseEntity.ok(payload);
                })
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @GetMapping("/users")
    public List<Map<String, Object>> listUsers() {
        return userRepository.findAll().stream()
                .map(u -> {
                    java.util.Map<String, Object> m = new java.util.LinkedHashMap<>();
                    m.put("id", u.getId().toString());
                    m.put("fullName", u.getFullName());
                    m.put("email", u.getEmail());
                    return m;
                })
                .collect(Collectors.toList());
    }

    @PostMapping("/users")
    public ResponseEntity<?> createUser(@Valid @RequestBody RegisterRequest request, HttpServletResponse response) {
        try {
            if (userRepository.findByEmail(request.getEmail()).isPresent()) {
                return ResponseEntity.badRequest().body(Map.of("error", "Email already in use"));
            }

            User user = new User();
            user.setFullName(request.getFullName());
            user.setEmail(request.getEmail());
            user.setPasswordHash(passwordEncoder.encode(request.getPassword()));
            User savedUser = userRepository.save(user);

            // create wallet -> $1000 initial balance
            Wallet wallet = new Wallet();
            wallet.setUserId(savedUser.getId());
            wallet.setBalance(new BigDecimal("1000.00"));
            wallet.setCurrency("USD");
            walletRepository.save(wallet);

            String token = jwtUtil.generateToken(savedUser.getId(), savedUser.getEmail());
            cookieUtil.addAuthCookie(response, token);

            return ResponseEntity.ok(Map.of("success", true));

        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(@Valid @RequestBody LoginRequest request, HttpServletRequest httpRequest, HttpServletResponse httpResponse) {
        String clientIp = httpRequest.getRemoteAddr();
        
        if (!rateLimitingService.tryConsume("login_" + clientIp)) {
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                    .body(Map.of("error", "Too many login attempts. Please try again later."));
        }

        try {
            Optional<User> userOpt = userRepository.findByEmail(request.getEmail());
            if (userOpt.isEmpty()) {
                return ResponseEntity.status(401).body(Map.of("error", "Invalid credentials"));
            }

            User user = userOpt.get();
            if (!passwordEncoder.matches(request.getPassword(), user.getPasswordHash())) {
                return ResponseEntity.status(401).body(Map.of("error", "Invalid credentials"));
            }

            String token = jwtUtil.generateToken(user.getId(), user.getEmail());
            cookieUtil.addAuthCookie(httpResponse, token);

            return ResponseEntity.ok(Map.of("success", true));

        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of("error", "Login failed"));
        }
    }

    @PutMapping("/users/{userId}")
    public ResponseEntity<?> updateUser(@PathVariable String userId, @RequestBody Map<String, String> request) {
        try {
            UUID id = UUID.fromString(userId);
            Optional<User> existing = userRepository.findById(id);
            if (existing.isEmpty()) {
                return ResponseEntity.notFound().build();
            }

            String fullName = request.get("fullName");
            String email = request.get("email");

            if (fullName == null || email == null) {
                return ResponseEntity.badRequest().body(Map.of("error", "Missing required fields"));
            }

            // check email uniqueness (exclude current user)
            Optional<User> emailOwner = userRepository.findByEmail(email);
            if (emailOwner.isPresent() && !emailOwner.get().getId().equals(id)) {
                return ResponseEntity.badRequest().body(Map.of("error", "Email already in use"));
            }

            User user = existing.get();
            user.setFullName(fullName);
            user.setEmail(email);
            User saved = userRepository.save(user);

            return ResponseEntity.ok(Map.of(
                    "id", saved.getId().toString(),
                    "fullName", saved.getFullName(),
                    "email", saved.getEmail()
            ));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", "Invalid user id"));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/logout")
    public ResponseEntity<?> logout(HttpServletResponse response) {
        cookieUtil.removeAuthCookie(response);
        return ResponseEntity.ok(Map.of("message", "Logged out successfully"));
    }
}
