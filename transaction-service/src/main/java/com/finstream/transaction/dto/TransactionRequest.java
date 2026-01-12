package com.finstream.transaction.dto;

import java.math.BigDecimal;
import java.util.UUID;

public class TransactionRequest {
    private UUID senderId;
    private UUID receiverId;
    private BigDecimal amount;

    public UUID getSenderId() { return senderId; }
    public void setSenderId(UUID senderId) { this.senderId = senderId; }

    public UUID getReceiverId() { return receiverId; }
    public void setReceiverId(UUID receiverId) { this.receiverId = receiverId; }

    public BigDecimal getAmount() { return amount; }
    public void setAmount(BigDecimal amount) { this.amount = amount; }
}
