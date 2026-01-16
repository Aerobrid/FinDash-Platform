package com.finstream.transaction.grpc;

import java.math.BigDecimal;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.finstream.common.grpc.BalanceCheckRequest;
import com.finstream.common.grpc.BalanceCheckResponse;
import com.finstream.common.grpc.WalletServiceGrpc;

import net.devh.boot.grpc.client.inject.GrpcClient;

@Service
public class WalletGrpcClient {

    private static final Logger log = LoggerFactory.getLogger(WalletGrpcClient.class);

    @GrpcClient("walletService")
    private WalletServiceGrpc.WalletServiceBlockingStub walletServiceStub;

    public boolean hasSufficientBalance(UUID userId, BigDecimal amount) {
        try {
            log.info("Checking balance for user {} with amount {} via gRPC", userId, amount);
            if (walletServiceStub == null) {
                log.error("walletServiceStub is NULL! gRPC client not injected properly");
                throw new RuntimeException("gRPC client not initialized");
            }
            
            BalanceCheckRequest request = BalanceCheckRequest.newBuilder()
                .setUserId(userId.toString())
                .setAmount(amount.doubleValue())
                .build();

            log.info("Calling checkSufficientBalance on wallet service via gRPC");
            BalanceCheckResponse response = walletServiceStub.checkSufficientBalance(request);
            log.info("gRPC response received: hasSufficientBalance={}, currentBalance={}", 
                response.getHasSufficientBalance(), response.getCurrentBalance());
            return response.getHasSufficientBalance();
            
        } catch (Exception e) {
            log.error("gRPC balance check failed for user {}: {}", userId, e.getMessage(), e);
            // If gRPC fails, reject the transaction for safety
            throw new RuntimeException("Failed to validate balance with wallet service", e);
        }
    }
}
