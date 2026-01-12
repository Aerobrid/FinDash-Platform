package com.finstream.wallet.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.finstream.wallet.model.Session;

@Repository
public interface SessionRepository extends JpaRepository<Session, UUID> {
    List<Session> findByUserId(UUID userId);
    Optional<Session> findByIdAndUserId(UUID sessionId, UUID userId);
    void deleteByUserId(UUID userId);
}
