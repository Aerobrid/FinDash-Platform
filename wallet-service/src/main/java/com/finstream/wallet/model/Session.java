package com.finstream.wallet.model;

import java.time.LocalDateTime;
import java.util.UUID;
import jakarta.persistence.*;

@Entity
@Table(name = "sessions")
public class Session {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private UUID userId;

    @Column(nullable = false)
    private String deviceName;

    @Column(nullable = false)
    private String browser;

    @Column(nullable = false)
    private String osName;

    @Column(nullable = false)
    private String ipAddress;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    @Column(nullable = false)
    private LocalDateTime lastActiveAt;

    @Column(nullable = false)
    private boolean isCurrent;

    public Session() {
        this.createdAt = LocalDateTime.now();
        this.lastActiveAt = LocalDateTime.now();
        this.isCurrent = false;
    }

    public Session(UUID userId, String deviceName, String browser, String osName, String ipAddress) {
        this();
        this.userId = userId;
        this.deviceName = deviceName;
        this.browser = browser;
        this.osName = osName;
        this.ipAddress = ipAddress;
    }

    // Getters and Setters
    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public UUID getUserId() {
        return userId;
    }

    public void setUserId(UUID userId) {
        this.userId = userId;
    }

    public String getDeviceName() {
        return deviceName;
    }

    public void setDeviceName(String deviceName) {
        this.deviceName = deviceName;
    }

    public String getBrowser() {
        return browser;
    }

    public void setBrowser(String browser) {
        this.browser = browser;
    }

    public String getOsName() {
        return osName;
    }

    public void setOsName(String osName) {
        this.osName = osName;
    }

    public String getIpAddress() {
        return ipAddress;
    }

    public void setIpAddress(String ipAddress) {
        this.ipAddress = ipAddress;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getLastActiveAt() {
        return lastActiveAt;
    }

    public void setLastActiveAt(LocalDateTime lastActiveAt) {
        this.lastActiveAt = lastActiveAt;
    }

    public boolean isCurrent() {
        return isCurrent;
    }

    public void setCurrent(boolean current) {
        isCurrent = current;
    }
}
