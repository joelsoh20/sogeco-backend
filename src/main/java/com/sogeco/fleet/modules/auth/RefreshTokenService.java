package com.sogeco.fleet.modules.auth;

import com.sogeco.fleet.common.exception.BusinessException;
import com.sogeco.fleet.common.security.JwtService;
import com.sogeco.fleet.modules.user.User;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.Base64;

/**
 * Cycle de vie des jetons de rafraichissement, avec rotation.
 *
 * A chaque usage l'ancien jeton est revoque et un nouveau est emis. Si un
 * jeton deja revoque est presente, c'est le signe d'un vol : toutes les
 * sessions de l'utilisateur sont alors invalidees.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RefreshTokenService {

    private final RefreshTokenRepository repository;
    private final JwtService jwtService;

    @Transactional
    public String issue(User user, String ipAddress) {
        String rawToken = jwtService.generateRefreshToken();

        repository.save(RefreshToken.builder()
                .user(user)
                .tokenHash(hash(rawToken))
                .expiresAt(jwtService.refreshTokenExpiry())
                .createdIp(ipAddress)
                .build());

        return rawToken;
    }

    /**
     * Valide un jeton et le consomme. Retourne l'utilisateur associe.
     */
    @Transactional
    public User consume(String rawToken) {
        RefreshToken stored = repository.findByTokenHash(hash(rawToken))
                .orElseThrow(() -> new BusinessException("RG-1.1",
                        "Jeton de rafraichissement inconnu", HttpStatus.UNAUTHORIZED));

        if (stored.getRevokedAt() != null) {
            // Rejeu d'un jeton deja consomme : on coupe toutes les sessions.
            log.warn("Rejeu d'un jeton revoque pour l'utilisateur {}", stored.getUser().getEmail());
            repository.revokeAllForUser(stored.getUser().getId(), Instant.now());
            throw new BusinessException("RG-1.1",
                    "Session invalidee, veuillez vous reconnecter", HttpStatus.UNAUTHORIZED);
        }

        if (stored.getExpiresAt().isBefore(Instant.now())) {
            throw new BusinessException("RG-1.1",
                    "Session expiree, veuillez vous reconnecter", HttpStatus.UNAUTHORIZED);
        }

        stored.revoke();
        return stored.getUser();
    }

    @Transactional
    public void revokeAll(Long userId) {
        repository.revokeAllForUser(userId, Instant.now());
    }

    @Transactional
    public int purgeExpired() {
        return repository.purgeExpired(Instant.now());
    }

    /**
     * SHA-256 suffit ici : le jeton est une valeur aleatoire de 384 bits,
     * pas un mot de passe choisi par un humain. Une fonction lente comme
     * BCrypt penaliserait chaque rafraichissement sans gain reel.
     */
    private String hash(String rawToken) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest(rawToken.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(bytes);
        } catch (Exception e) {
            throw new IllegalStateException("Hachage du jeton impossible", e);
        }
    }
}
