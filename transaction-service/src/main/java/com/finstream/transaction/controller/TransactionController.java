package com.finstream.transaction.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

import com.finstream.transaction.dto.TransactionRequest;
import com.finstream.transaction.model.Transaction;
import com.finstream.transaction.service.TransactionOrchestrator;

@RestController
@RequestMapping("/api/transaction")
public class TransactionController {

    private final TransactionOrchestrator orchestrator;

    public TransactionController(TransactionOrchestrator orchestrator) {
        this.orchestrator = orchestrator;
    }

    @PostMapping("/transfer")
    public ResponseEntity<Transaction> transfer(@RequestBody TransactionRequest request) {
        Transaction tx = orchestrator.process(request);
        return ResponseEntity.ok(tx);
    }

    @org.springframework.web.bind.annotation.GetMapping("/history/{userId}")
    public ResponseEntity<java.util.List<Transaction>> getHistory(@org.springframework.web.bind.annotation.PathVariable UUID userId) {
        return ResponseEntity.ok(orchestrator.getHistory(userId));
    }
}
