package com.conciliacion.bancaria.config;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

@Service
public class JwtService {

    @Value("${jwt.secret}")
    private String secret;

    @Value("${jwt.expiration-ms}")
    private long expirationMs;

    private SecretKey getKey() {
        return Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }

    public String generarToken(String email, String rol, Long empresaId, String permisos) {
        return Jwts.builder()
                .subject(email)
                .claim("rol", rol)
                .claim("empresaId", empresaId)
                .claim("permisos", permisos)
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + expirationMs))
                .signWith(getKey())
                .compact();
    }

    public String extraerEmail(String token) {
        return parsear(token).getSubject();
    }

    public String extraerRol(String token) {
        return parsear(token).get("rol", String.class);
    }

    public Long extraerEmpresaId(String token) {
        Object val = parsear(token).get("empresaId");
        if (val == null) return null;
        return ((Number) val).longValue();
    }

    public String extraerPermisos(String token) {
        return parsear(token).get("permisos", String.class);
    }

    public boolean esValido(String token) {
        try {
            parsear(token);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private Claims parsear(String token) {
        return Jwts.parser()
                .verifyWith(getKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }
}