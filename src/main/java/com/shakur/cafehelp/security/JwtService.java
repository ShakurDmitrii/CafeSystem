package com.shakur.cafehelp.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

@Service
public class JwtService {

    private final String jwtSecret;
    private final long jwtExpirationSeconds;

    public JwtService(
            @Value("${security.jwt.secret:VGhpc0lzQUNhZmVoZWxwU2VjcmV0S2V5VGhhdE11c3RCZUxvbmdFbm91Z2hGb3JIQzI1Ng==}") String jwtSecret,
            @Value("${security.jwt.expiration-seconds:28800}") long jwtExpirationSeconds
    ) {
        this.jwtSecret = jwtSecret;
        this.jwtExpirationSeconds = jwtExpirationSeconds;
    }

    public String generateToken(String username, int accountId, int personId, String role) {
        Map<String, Object> claims = new HashMap<>();
        claims.put("accountId", accountId);
        claims.put("personId", personId);
        claims.put("role", role);

        Date now = new Date();
        Date exp = new Date(now.getTime() + jwtExpirationSeconds * 1000);

        return Jwts.builder()
                .claims(claims)
                .subject(username)
                .issuedAt(now)
                .expiration(exp)
                .signWith(getSigningKey())
                .compact();
    }

    public Claims extractAllClaims(String token) {
        return Jwts.parser()
                .verifyWith(getSigningKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    public String extractUsername(String token) {
        return extractAllClaims(token).getSubject();
    }

    public boolean isTokenValid(String token) {
        Date expiration = extractAllClaims(token).getExpiration();
        return expiration != null && expiration.after(new Date());
    }

    public long getJwtExpirationSeconds() {
        return jwtExpirationSeconds;
    }

    private SecretKey getSigningKey() {
        byte[] keyBytes = Decoders.BASE64.decode(jwtSecret);
        return Keys.hmacShaKeyFor(keyBytes);
    }
}

