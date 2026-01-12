package com.finstream.wallet.service;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Service;

import com.finstream.wallet.model.Wallet;
import com.finstream.wallet.repository.UserRepository;
import com.finstream.wallet.repository.WalletRepository;

@Service
public class WalletService {
    private final WalletRepository walletRepository;
    private final UserRepository userRepository;

    public WalletService(WalletRepository walletRepository, UserRepository userRepository) {
        this.walletRepository = walletRepository;
        this.userRepository = userRepository;
    }

    public Optional<Wallet> getWalletByUserId(UUID userId) {
        return walletRepository.findByUserId(userId);
    }

    public boolean userIsValid(UUID userId) {
        return userRepository.findById(userId).isPresent();
    }

    public void applyTransaction(UUID senderId, UUID receiverId, BigDecimal amount) {
        // Decrease sender balance
        walletRepository.findByUserId(senderId).ifPresent(senderWallet -> {
            senderWallet.setBalance(senderWallet.getBalance().subtract(amount));
            walletRepository.save(senderWallet);
        });

        // Increase receiver balance
        walletRepository.findByUserId(receiverId).ifPresent(receiverWallet -> {
            receiverWallet.setBalance(receiverWallet.getBalance().add(amount));
            walletRepository.save(receiverWallet);
        });
    }
}
