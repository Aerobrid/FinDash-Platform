package com.finstream.transaction.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.finstream.transaction.dto.TransactionRequest;
import com.finstream.transaction.grpc.WalletGrpcClient;
import com.finstream.transaction.kafka.TransactionEvent;
import com.finstream.transaction.model.Transaction;
import com.finstream.transaction.model.TransactionStatus;
import com.finstream.transaction.repository.TransactionRepository;

@Service
public class TransactionOrchestrator {

    private static final Logger log = LoggerFactory.getLogger(TransactionOrchestrator.class);

    private final TransactionRepository transactionRepository;
    private final KafkaTemplate<String, TransactionEvent> kafkaTemplate;
    private final WalletGrpcClient walletGrpcClient;

    public TransactionOrchestrator(TransactionRepository transactionRepository,
                                   KafkaTemplate<String, TransactionEvent> kafkaTemplate,
                                   WalletGrpcClient walletGrpcClient) {
        this.transactionRepository = transactionRepository;
        this.kafkaTemplate = kafkaTemplate;
        this.walletGrpcClient = walletGrpcClient;
        log.info("TransactionOrchestrator initialized with WalletGrpcClient: {}", walletGrpcClient != null);
    }

    @Transactional
    public Transaction process(TransactionRequest request) {
        log.info("Processing transaction: sender={}, receiver={}, amount={}", 
            request.getSenderId(), request.getReceiverId(), request.getAmount());
            
        // Validate inputs
        if (request.getSenderId() == null || request.getReceiverId() == null || request.getAmount() == null) {
            throw new IllegalArgumentException("Sender ID, Receiver ID, and Amount are required");
        }

        if (request.getSenderId().equals(request.getReceiverId())) {
            throw new IllegalArgumentException("Cannot transfer to yourself");
        }

        if (request.getAmount().signum() <= 0) {
            throw new IllegalArgumentException("Amount must be positive");
        }

        log.info("Starting gRPC balance validation for sender: {}", request.getSenderId());
        // gRPC validation: Check if sender has sufficient balance
        boolean hasSufficientBalance = walletGrpcClient.hasSufficientBalance(
            request.getSenderId(), 
            request.getAmount()
        );
        log.info("gRPC balance check result: {}", hasSufficientBalance);
        
        if (!hasSufficientBalance) {
            log.warn("Insufficient balance for sender: {}", request.getSenderId());
            Transaction failedTx = persist(request, TransactionStatus.FAILED);
            throw new IllegalArgumentException("Insufficient balance");
        }
        
        log.info("Balance validated successfully, persisting transaction");
        Transaction tx = persist(request, TransactionStatus.COMPLETED);
        kafkaTemplate.send("transactions", new TransactionEvent(request.getSenderId(), request.getReceiverId(), request.getAmount()));
        log.info("Transaction completed and event published: {}", tx.getId());
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
