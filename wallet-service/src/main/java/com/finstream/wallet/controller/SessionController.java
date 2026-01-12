package com.finstream.wallet.controller;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.finstream.wallet.model.Session;
import com.finstream.wallet.service.SessionService;

@RestController
@RequestMapping("/api/sessions")
public class SessionController {
    private final SessionService sessionService;

    public SessionController(SessionService sessionService) {
        this.sessionService = sessionService;
    }

    @PostMapping("/{userId}")
    public ResponseEntity<?> createSession(@PathVariable String userId, @RequestBody Map<String, String> request) {
        try {
            UUID userUuid = UUID.fromString(userId);
            String deviceName = request.get("deviceName");
            String browser = request.get("browser");
            String osName = request.get("osName");
            String ipAddress = request.get("ipAddress");

            if (deviceName == null || browser == null || osName == null || ipAddress == null) {
                return ResponseEntity.badRequest().body(Map.of("error", "Missing required fields"));
            }

            Session session = sessionService.createSession(userUuid, deviceName, browser, osName, ipAddress);
            sessionService.setCurrentSession(userUuid, session.getId());

            return ResponseEntity.ok(Map.of(
                    "id", session.getId().toString(),
                    "deviceName", session.getDeviceName(),
                    "browser", session.getBrowser(),
                    "osName", session.getOsName(),
                    "ipAddress", session.getIpAddress(),
                    "createdAt", session.getCreatedAt(),
                    "lastActiveAt", session.getLastActiveAt(),
                    "isCurrent", session.isCurrent()
            ));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", "Invalid user id"));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/{userId}")
    public ResponseEntity<?> getUserSessions(@PathVariable String userId) {
        try {
            UUID userUuid = UUID.fromString(userId);
            List<Session> sessions = sessionService.getUserSessions(userUuid);

            List<Map<String, Object>> response = sessions.stream()
                    .map(session -> {
                        Map<String, Object> map = new java.util.LinkedHashMap<>();
                        map.put("id", session.getId().toString());
                        map.put("deviceName", session.getDeviceName());
                        map.put("browser", session.getBrowser());
                        map.put("osName", session.getOsName());
                        map.put("ipAddress", session.getIpAddress());
                        map.put("createdAt", session.getCreatedAt());
                        map.put("lastActiveAt", session.getLastActiveAt());
                        map.put("isCurrent", session.isCurrent());
                        return map;
                    })
                    .collect(Collectors.toList());

            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", "Invalid user id"));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @DeleteMapping("/{userId}/{sessionId}")
    public ResponseEntity<?> deleteSession(@PathVariable String userId, @PathVariable String sessionId) {
        try {
            UUID userUuid = UUID.fromString(userId);
            UUID sessionUuid = UUID.fromString(sessionId);
            sessionService.deleteSession(sessionUuid, userUuid);
            return ResponseEntity.ok(Map.of("message", "Session deleted successfully"));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", "Invalid user or session id"));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/{userId}/logout-all")
    public ResponseEntity<?> logoutAllSessions(@PathVariable String userId) {
        try {
            UUID userUuid = UUID.fromString(userId);
            sessionService.deleteAllSessions(userUuid);
            return ResponseEntity.ok(Map.of("message", "All sessions logged out successfully"));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", "Invalid user id"));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @PutMapping("/{userId}/{sessionId}/active")
    public ResponseEntity<?> updateLastActive(@PathVariable String userId, @PathVariable String sessionId) {
        try {
            UUID sessionUuid = UUID.fromString(sessionId);
            sessionService.updateLastActive(sessionUuid);
            return ResponseEntity.ok(Map.of("message", "Session updated"));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", "Invalid session id"));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }
}
