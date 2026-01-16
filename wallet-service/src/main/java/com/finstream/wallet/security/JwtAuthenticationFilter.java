package com.finstream.wallet.security;

import java.io.IOException;
import java.util.Arrays;

import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtUtil jwtUtil;

    public JwtAuthenticationFilter(JwtUtil jwtUtil) {
        this.jwtUtil = jwtUtil;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        
        // skip auth for public endpoints
        String path = request.getRequestURI();
        if (path.equals("/api/wallet/login") || 
            path.equals("/api/wallet/users") || 
            path.startsWith("/actuator/health") ||
            path.startsWith("/api/sessions")) {
            filterChain.doFilter(request, response);
            return;
        }

        String token = extractTokenFromCookie(request);
        
        if (token == null) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.getWriter().write("{\"error\":\"Authentication required\"}");
            return;
        }

        if (!jwtUtil.validateToken(token)) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.getWriter().write("{\"error\":\"Invalid or expired token\"}");
            return;
        }

        // valid token -> add userId to request
        String userId = jwtUtil.extractUserId(token);
        request.setAttribute("userId", userId);

        filterChain.doFilter(request, response);
    }

    private String extractTokenFromCookie(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) {
            return null;
        }
        
        return Arrays.stream(cookies)
                .filter(cookie -> "auth_token".equals(cookie.getName()))
                .map(Cookie::getValue)
                .findFirst()
                .orElse(null);
    }
}
