package com.finstream.wallet.kafka;

import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import com.finstream.wallet.dto.TransactionEvent;
import com.finstream.wallet.service.WalletService;

@Component
public class TransactionEventConsumer {

    private final WalletService walletService;

    public TransactionEventConsumer(WalletService walletService) {
        this.walletService = walletService;
    }

    @KafkaListener(topics = "transactions", groupId = "wallet-group")
    public void handleTransaction(TransactionEvent event) {
        walletService.applyTransaction(event.getSenderId(), event.getReceiverId(), event.getAmount());
    }
}
