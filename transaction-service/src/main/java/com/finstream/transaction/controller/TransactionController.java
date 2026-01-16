package com.finstream.transaction.controller;

import java.util.Map;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.finstream.transaction.dto.TransactionRequest;
import com.finstream.transaction.model.Transaction;
import com.finstream.transaction.service.TransactionOrchestrator;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/transaction")
public class TransactionController {

    private static final Logger log = LoggerFactory.getLogger(TransactionController.class);

    private final TransactionOrchestrator orchestrator;

    public TransactionController(TransactionOrchestrator orchestrator) {
        this.orchestrator = orchestrator;
    }

    @PostMapping("/transfer")
    public ResponseEntity<?> transfer(@Valid @RequestBody TransactionRequest request) {
        log.info("Received transfer request: sender={}, receiver={}, amount={}", 
            request.getSenderId(), request.getReceiverId(), request.getAmount());
        try {
            // Validate sender and receiver are different
            if (request.getSenderId().equals(request.getReceiverId())) {
                log.warn("Rejecting: sender equals receiver");
                return ResponseEntity.badRequest()
                    .body(Map.of("error", "Sender and receiver must be different"));
            }

            log.info("Calling orchestrator.process()");
            Transaction tx = orchestrator.process(request);
            log.info("Transaction completed: {}", tx.getId());
            return ResponseEntity.ok(tx);
        } catch (IllegalArgumentException e) {
            log.warn("Bad request: {}", e.getMessage());
            return ResponseEntity.badRequest()
                .body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            log.error("Processing failed", e);
            return ResponseEntity.status(500)
                .body(Map.of("error", "Transaction processing failed: " + e.getMessage()));
        }
    }

    @org.springframework.web.bind.annotation.GetMapping("/history/{userId}")
    public ResponseEntity<java.util.List<Transaction>> getHistory(@org.springframework.web.bind.annotation.PathVariable UUID userId) {
        return ResponseEntity.ok(orchestrator.getHistory(userId));
    }
}
