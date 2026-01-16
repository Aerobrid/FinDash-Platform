package com.finstream.wallet.config;

import java.math.BigDecimal;

import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import com.finstream.wallet.model.User;
import com.finstream.wallet.model.Wallet;
import com.finstream.wallet.repository.UserRepository;
import com.finstream.wallet.repository.WalletRepository;

@Configuration
public class DataInitializer {

    @Bean
    @SuppressWarnings("unused")
    CommandLineRunner seed(UserRepository userRepository, WalletRepository walletRepository, Environment env,
                          BCryptPasswordEncoder passwordEncoder) {
        return args -> {
            boolean seedDemo = Boolean.parseBoolean(env.getProperty("wallet.seed-demo", "false"));
            if (!seedDemo) {
                return;
            }

            if (userRepository.count() == 0) {
                User alice = new User();
                alice.setFullName("Alice Example");
                alice.setEmail("alice@example.com");
                alice.setPhoneNumber("+10000000001");
                alice.setPasswordHash(passwordEncoder.encode("demo"));
                alice = userRepository.save(alice);

                Wallet aliceWallet = new Wallet();
                aliceWallet.setUserId(alice.getId());
                aliceWallet.setBalance(new BigDecimal("1000.00"));
                aliceWallet.setCurrency("USD");
                walletRepository.save(aliceWallet);

                User bob = new User();
                bob.setFullName("Bob Example");
                bob.setEmail("bob@example.com");
                bob.setPhoneNumber("+10000000002");
                bob.setPasswordHash(passwordEncoder.encode("demo"));
                bob = userRepository.save(bob);

                Wallet bobWallet = new Wallet();
                bobWallet.setUserId(bob.getId());
                bobWallet.setBalance(new BigDecimal("250.00"));
                bobWallet.setCurrency("USD");
                walletRepository.save(bobWallet);
            }
        };
    }
}
