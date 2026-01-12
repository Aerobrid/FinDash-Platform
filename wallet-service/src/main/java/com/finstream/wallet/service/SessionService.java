package com.finstream.wallet.service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;

import com.finstream.wallet.model.Session;
import com.finstream.wallet.repository.SessionRepository;

@Service
public class SessionService {
    private final SessionRepository sessionRepository;
    private static final int SESSION_TIMEOUT_DAYS = 30; // Sessions expire after 30 days

    public SessionService(SessionRepository sessionRepository) {
        this.sessionRepository = sessionRepository;
    }

    public Session createSession(UUID userId, String deviceName, String browser, String osName, String ipAddress) {
        Session session = new Session(userId, deviceName, browser, osName, ipAddress);
        return sessionRepository.save(session);
    }

    public List<Session> getUserSessions(UUID userId) {
        // Clean up expired sessions before returning active ones
        cleanupExpiredSessions(userId);
        return sessionRepository.findByUserId(userId);
    }

    public void updateLastActive(UUID sessionId) {
        sessionRepository.findById(sessionId).ifPresent(session -> {
            session.setLastActiveAt(LocalDateTime.now());
            sessionRepository.save(session);
        });
    }

    public void deleteSession(UUID sessionId, UUID userId) {
        Optional<Session> session = sessionRepository.findByIdAndUserId(sessionId, userId);
        session.ifPresent(sessionRepository::delete);
    }

    public void deleteAllSessions(UUID userId) {
        sessionRepository.deleteByUserId(userId);
    }

    public void setCurrentSession(UUID userId, UUID currentSessionId) {
        List<Session> sessions = sessionRepository.findByUserId(userId);
        for (Session session : sessions) {
            session.setCurrent(session.getId().equals(currentSessionId));
            sessionRepository.save(session);
        }
    }

    private void cleanupExpiredSessions(UUID userId) {
        LocalDateTime expiryThreshold = LocalDateTime.now().minusDays(SESSION_TIMEOUT_DAYS);
        List<Session> sessions = sessionRepository.findByUserId(userId);
        List<Session> expiredSessions = sessions.stream()
                .filter(session -> session.getLastActiveAt().isBefore(expiryThreshold))
                .collect(Collectors.toList());
        sessionRepository.deleteAll(expiredSessions);
    }

    public void cleanupAllExpiredSessions() {
        // This can be called by a scheduled task
        LocalDateTime expiryThreshold = LocalDateTime.now().minusDays(SESSION_TIMEOUT_DAYS);
        List<Session> allSessions = sessionRepository.findAll();
        List<Session> expiredSessions = allSessions.stream()
                .filter(session -> session.getLastActiveAt().isBefore(expiryThreshold))
                .collect(Collectors.toList());
        sessionRepository.deleteAll(expiredSessions);
    }
}
