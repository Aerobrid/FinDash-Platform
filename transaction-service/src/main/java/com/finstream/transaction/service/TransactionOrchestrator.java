package com.finstream.transaction.service;

import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import com.finstream.transaction.dto.TransactionRequest;
import com.finstream.transaction.kafka.TransactionEvent;
import com.finstream.transaction.model.Transaction;
import com.finstream.transaction.model.TransactionStatus;
import com.finstream.transaction.repository.TransactionRepository;

@Service
public class TransactionOrchestrator {

    private final TransactionRepository transactionRepository;
    private final KafkaTemplate<String, TransactionEvent> kafkaTemplate;

    public TransactionOrchestrator(TransactionRepository transactionRepository,
                                   KafkaTemplate<String, TransactionEvent> kafkaTemplate) {
        this.transactionRepository = transactionRepository;
        this.kafkaTemplate = kafkaTemplate;
    }

    public Transaction process(TransactionRequest request) {
        // For now, assume validation is OK; we'll wire gRPC once proto build is stable.
        Transaction tx = persist(request, TransactionStatus.COMPLETED);
        kafkaTemplate.send("transactions", new TransactionEvent(request.getSenderId(), request.getReceiverId(), request.getAmount()));
        return tx;
    }

    private Transaction persist(TransactionRequest request, TransactionStatus status) {
        Transaction tx = new Transaction();
        tx.setSenderId(request.getSenderId());
        tx.setReceiverId(request.getReceiverId());
        tx.setAmount(request.getAmount());
        tx.setStatus(status);
        return transactionRepository.save(tx);
    }

    public java.util.List<com.finstream.transaction.model.Transaction> getHistory(java.util.UUID userId) {
        return transactionRepository.findBySenderIdOrReceiverId(userId, userId);
    }
}
