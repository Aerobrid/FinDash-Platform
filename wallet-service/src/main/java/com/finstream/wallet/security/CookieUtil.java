package com.finstream.wallet.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletResponse;

@Component
public class CookieUtil {

    private static final String COOKIE_NAME = "auth_token";
    private static final int COOKIE_MAX_AGE = 24 * 60 * 60; // 24hrs
    
    @Value("${server.servlet.session.cookie.secure:false}")
    private boolean isSecure;
    
    // creates secure HttpOnly cookie for JWT
    public void addAuthCookie(HttpServletResponse response, String token) {
        Cookie cookie = createBaseCookie(token, COOKIE_MAX_AGE);
        response.addCookie(cookie);
    }
    
    // removes auth cookie -> logout
    public void removeAuthCookie(HttpServletResponse response) {
        Cookie cookie = createBaseCookie(null, 0);
        response.addCookie(cookie);
    }
    
    private Cookie createBaseCookie(String value, int maxAge) {
        Cookie cookie = new Cookie(COOKIE_NAME, value != null ? value : "");
        cookie.setHttpOnly(true); // XSS protection - prevents JavaScript from accessing the cookie
        cookie.setSecure(isSecure); // Set to true in production with HTTPS
        cookie.setPath("/");
        cookie.setMaxAge(maxAge);
        cookie.setAttribute("SameSite", "Strict"); // CSRF protection - strict mode for maximum security
        return cookie;
    }
}
