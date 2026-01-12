package com.finstream.wallet.controller;

import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.boot.actuate.health.CompositeHealth;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthComponent;
import org.springframework.boot.actuate.health.HealthEndpoint;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/wallet")
public class HealthController {

    private final HealthEndpoint healthEndpoint;

    public HealthController(HealthEndpoint healthEndpoint) {
        this.healthEndpoint = healthEndpoint;
    }

    @GetMapping("/health")
    public ResponseEntity<?> health() {
        HealthComponent component = healthEndpoint.health();

        String statusCode = component.getStatus().getCode();
        boolean up = "UP".equals(statusCode);

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("status", up ? "UP" : "DOWN");
        response.put("service", "wallet-service");

        if (component instanceof Health h) {
            response.put("details", h.getDetails());
        } else if (component instanceof CompositeHealth ch) {
            Map<String, Object> components = new LinkedHashMap<>();
            ch.getComponents().forEach((name, comp) -> {
                Map<String, Object> c = new LinkedHashMap<>();
                c.put("status", comp.getStatus().getCode());
                if (comp instanceof Health h2) {
                    c.put("details", h2.getDetails());
                }
                components.put(name, c);
            });
            response.put("components", components);
        }

        return up ? ResponseEntity.ok(response) : ResponseEntity.status(503).body(response);
    }
}
