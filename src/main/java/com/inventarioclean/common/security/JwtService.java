package com.inventarioclean.common.security;

import com.inventarioclean.common.config.JwtProperties;
import com.inventarioclean.usuario.Rol;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;

/**
 * Emision y validacion de tokens JWT (HS256).
 *
 * <p>El token lleva el username, el id y el rol. El rol viaja en el token
 * para que la autorizacion no requiera un hit a la base en cada peticion;
 * el backend es la unica fuente de verdad porque es el que firma el token.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class JwtService {

    private final JwtProperties properties;

    private SecretKey key() {
        // HS256 exige al menos 32 bytes de clave. Si la clave es mas corta,
        // Keys.hmacShaKeyFor lanza IllegalArgumentException al construirla.
        byte[] bytes = properties.secret().getBytes(StandardCharsets.UTF_8);
        if (bytes.length < 32) {
            throw new IllegalStateException(
                    "app.jwt.secret debe tener al menos 32 caracteres para HS256. "
                            + "Genera una con: openssl rand -base64 48");
        }
        return Keys.hmacShaKeyFor(bytes);
    }

    /** Genera un token para el usuario indicado. */
    public String generateToken(UsuarioPrincipal principal) {
        Instant ahora = Instant.now();
        Instant expira = ahora.plusSeconds(properties.expirationMinutes() * 60);

        return Jwts.builder()
                .subject(principal.getUsername())
                .claim("uid", principal.getId())
                .claim("rol", principal.getRol().name())
                .claim("nombre", principal.getNombreCompleto())
                .issuedAt(Date.from(ahora))
                .expiration(Date.from(expira))
                .signWith(key())
                .compact();
    }

    /**
     * Extrae los claims de un token.
     *
     * @throws io.jsonwebtoken.JwtException si el token es invalido o expiro
     */
    public Claims parseToken(String token) {
        return Jwts.parser()
                .verifyWith(key())
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    public String getUsername(String token) {
        return parseToken(token).getSubject();
    }

    public Long getUsuarioId(String token) {
        return parseToken(token).get("uid", Long.class);
    }

    public Rol getRol(String token) {
        return Rol.valueOf(parseToken(token).get("rol", String.class));
    }
}

