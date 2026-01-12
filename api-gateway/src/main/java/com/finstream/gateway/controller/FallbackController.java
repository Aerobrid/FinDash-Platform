package com.finstream.gateway.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/fallback")
public class FallbackController {

    @GetMapping("/wallet")
    public ResponseEntity<?> walletFallback() {
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(Map.of(
                        "error", "Wallet service is temporarily unavailable",
                        "message", "Please try again later"
                ));
    }

    @GetMapping("/transaction")
    public ResponseEntity<?> transactionFallback() {
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(Map.of(
                        "error", "Transaction service is temporarily unavailable",
                        "message", "Please try again later"
                ));
    }
}
