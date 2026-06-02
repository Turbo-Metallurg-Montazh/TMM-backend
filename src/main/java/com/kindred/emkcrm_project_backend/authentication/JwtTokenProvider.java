package com.kindred.emkcrm_project_backend.authentication;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;

import javax.crypto.SecretKey;
import java.util.Date;


@Component
public class JwtTokenProvider {

    @Value("${security.jwt.token.secret-key:secret}")
    private String secretKey;

    @Value("${security.jwt.token.expire-length:900000}")
    private long validityInMilliseconds;

    private static SecretKey key;


    @PostConstruct
    protected void init() {
        if (secretKey.length() < 32) {
            throw new IllegalArgumentException("The secret key must be at least 32 characters long");
        }
        key = Keys.hmacShaKeyFor(secretKey.getBytes());
    }

    public String generateAccessToken(String username) {
        return Jwts.builder()
                .subject(username)
                .claim("token_type", "access")
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + validityInMilliseconds))
                .signWith(key)
                .compact();
    }

    public String generateToken(String username) {
        return generateAccessToken(username);
    }

    public String getUsernameFromJWT(String token) {
        return Jwts.parser().verifyWith(key).build().parseSignedClaims(token).getPayload().getSubject();
    }

    public boolean validateToken(String token) {

        try {
            Jwts.parser().verifyWith(key).build().parseSignedClaims(token);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    public long getValidityInSeconds() {
        return validityInMilliseconds / 1000;
    }
}
