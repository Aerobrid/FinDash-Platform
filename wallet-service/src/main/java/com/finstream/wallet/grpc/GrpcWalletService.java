package com.finstream.wallet.grpc;

import java.math.BigDecimal;
import java.util.UUID;

import com.finstream.common.grpc.BalanceCheckRequest;
import com.finstream.common.grpc.BalanceCheckResponse;
import com.finstream.common.grpc.BalanceRequest;
import com.finstream.common.grpc.BalanceResponse;
import com.finstream.common.grpc.UserValidationRequest;
import com.finstream.common.grpc.UserValidationResponse;
import com.finstream.common.grpc.WalletServiceGrpc;
import com.finstream.wallet.service.WalletService;

import io.grpc.stub.StreamObserver;
import net.devh.boot.grpc.server.service.GrpcService;

@GrpcService
public class GrpcWalletService extends WalletServiceGrpc.WalletServiceImplBase {

    private final WalletService walletService;

    public GrpcWalletService(WalletService walletService) {
        this.walletService = walletService;
    }

    @Override
    public void checkSufficientBalance(BalanceCheckRequest request, StreamObserver<BalanceCheckResponse> responseObserver) {
        try {
            UUID userId = UUID.fromString(request.getUserId());
            BigDecimal requiredAmount = BigDecimal.valueOf(request.getAmount());
            
            var wallet = walletService.getWalletByUserId(userId);
            
            BalanceCheckResponse.Builder responseBuilder = BalanceCheckResponse.newBuilder();
            
            if (wallet.isPresent()) {
                BigDecimal currentBalance = wallet.get().getBalance();
                boolean hasSufficient = currentBalance.compareTo(requiredAmount) >= 0;
                
                responseBuilder
                    .setHasSufficientBalance(hasSufficient)
                    .setCurrentBalance(currentBalance.doubleValue());
            } else {
                responseBuilder
                    .setHasSufficientBalance(false)
                    .setCurrentBalance(0.0);
            }
            
            responseObserver.onNext(responseBuilder.build());
            responseObserver.onCompleted();
            
        } catch (Exception e) {
            responseObserver.onError(e);
        }
    }

    @Override
    public void validateUser(UserValidationRequest request, StreamObserver<UserValidationResponse> responseObserver) {
        try {
            UUID userId = UUID.fromString(request.getUserId());
            boolean isValid = walletService.userIsValid(userId);
            
            UserValidationResponse response = UserValidationResponse.newBuilder()
                .setIsValid(isValid)
                .setFullName(isValid ? "User" : "")
                .build();
            
            responseObserver.onNext(response);
            responseObserver.onCompleted();
            
        } catch (Exception e) {
            responseObserver.onError(e);
        }
    }

    @Override
    public void getBalance(BalanceRequest request, StreamObserver<BalanceResponse> responseObserver) {
        try {
            UUID userId = UUID.fromString(request.getUserId());
            var wallet = walletService.getWalletByUserId(userId);
            
            BalanceResponse.Builder responseBuilder = BalanceResponse.newBuilder()
                .setUserId(request.getUserId())
                .setCurrency("USD");
            
            if (wallet.isPresent()) {
                responseBuilder.setBalance(wallet.get().getBalance().doubleValue());
            } else {
                responseBuilder.setBalance(0.0);
            }
            
            responseObserver.onNext(responseBuilder.build());
            responseObserver.onCompleted();
            
        } catch (Exception e) {
            responseObserver.onError(e);
        }
    }
}

