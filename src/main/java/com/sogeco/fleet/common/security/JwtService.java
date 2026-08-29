package com.sogeco.fleet.common.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.*;

/**
 * Generation et validation des jetons.
 *
 * Le jeton d'acces embarque les roles ET les permissions : l'autorisation
 * ne declenche donc aucun acces base a chaque requete. La contrepartie est
 * qu'un changement de droits ne prend effet qu'au renouvellement du jeton,
 * soit 15 minutes au maximum.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class JwtService {

    public static final String CLAIM_USER_ID     = "uid";
    public static final String CLAIM_FULL_NAME   = "name";
    public static final String CLAIM_CITY_ID     = "city";
    public static final String CLAIM_ROLES       = "roles";
    public static final String CLAIM_PERMISSIONS = "perms";
    public static final String CLAIM_TYPE        = "typ";

    private static final String TYPE_ACCESS = "access";
    private static final SecureRandom RANDOM = new SecureRandom();

    private final JwtProperties properties;

    private SecretKey key() {
        byte[] secret = properties.getSecret().getBytes(StandardCharsets.UTF_8);
        if (secret.length < 32) {
            throw new IllegalStateException(
                    "sogeco.jwt.secret doit faire au moins 256 bits (32 caracteres).");
        }
        return Keys.hmacShaKeyFor(secret);
    }

    /** Jeton d'acces de courte duree. */
    public String generateAccessToken(UserPrincipal principal) {
        Instant now = Instant.now();
        Instant expiry = now.plusSeconds(properties.getAccessTokenMinutes() * 60L);

        return Jwts.builder()
                .issuer(properties.getIssuer())
                .subject(principal.getEmail())
                .claim(CLAIM_TYPE, TYPE_ACCESS)
                .claim(CLAIM_USER_ID, principal.getId())
                .claim(CLAIM_FULL_NAME, principal.getFullName())
                .claim(CLAIM_CITY_ID, principal.getCityId())
                .claim(CLAIM_ROLES, new ArrayList<>(principal.getRoleCodes()))
                .claim(CLAIM_PERMISSIONS, new ArrayList<>(principal.getPermissionCodes()))
                .issuedAt(Date.from(now))
                .expiration(Date.from(expiry))
                .signWith(key())
                .compact();
    }

    /**
     * Jeton de rafraichissement : valeur aleatoire opaque, sans structure.
     * Il n'a pas a etre lisible, seule son empreinte est stockee en base.
     */
    public String generateRefreshToken() {
        byte[] bytes = new byte[48];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    /** Reconstruit l'utilisateur depuis un jeton valide, sans acces base. */
    public Optional<UserPrincipal> parseAccessToken(String token) {
        try {
            Claims claims = Jwts.parser()
                    .verifyWith(key())
                    .requireIssuer(properties.getIssuer())
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();

            if (!TYPE_ACCESS.equals(claims.get(CLAIM_TYPE, String.class))) {
                return Optional.empty();
            }

            return Optional.of(new UserPrincipal(
                    toLong(claims.get(CLAIM_USER_ID)),
                    claims.getSubject(),
                    claims.get(CLAIM_FULL_NAME, String.class),
                    toLong(claims.get(CLAIM_CITY_ID)),
                    toSet(claims.get(CLAIM_ROLES)),
                    toSet(claims.get(CLAIM_PERMISSIONS))));

        } catch (JwtException | IllegalArgumentException e) {
            // Jeton expire, signature invalide ou format incorrect :
            // dans tous les cas la requete sera traitee comme anonyme.
            log.debug("Jeton rejete : {}", e.getMessage());
            return Optional.empty();
        }
    }

    public Instant refreshTokenExpiry() {
        return Instant.now().plusSeconds(properties.getRefreshTokenDays() * 86_400L);
    }

    public int getAccessTokenMinutes() {
        return properties.getAccessTokenMinutes();
    }

    private Long toLong(Object value) {
        if (value == null) return null;
        return value instanceof Number number ? number.longValue() : Long.valueOf(value.toString());
    }

    @SuppressWarnings("unchecked")
    private Set<String> toSet(Object value) {
        if (value instanceof Collection<?> collection) {
            return collection.stream().map(String::valueOf).collect(java.util.stream.Collectors.toSet());
        }
        return Set.of();
    }
}
