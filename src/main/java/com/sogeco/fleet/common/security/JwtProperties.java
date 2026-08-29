package com.sogeco.fleet.common.security;

import jakarta.annotation.PostConstruct;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;

/**
 * Parametres des jetons, injectes depuis sogeco.jwt.*
 *
 * La validation est faite au demarrage : une configuration incomplete
 * doit empecher l'application de se lancer, pas produire une erreur
 * a la premiere connexion d'un utilisateur.
 */
@Slf4j
@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "sogeco.jwt")
public class JwtProperties {

    private static final int MIN_SECRET_BYTES = 32;   // 256 bits, exigence HS256
    private static final String DEV_SECRET_MARKER = "changez-ce-secret";

    /** Secret HMAC. Jamais dans le depot en production. */
    private String secret;

    /** Duree de vie du jeton d'acces, en minutes. */
    private int accessTokenMinutes = 15;

    /** Duree de vie du jeton de rafraichissement, en jours. */
    private int refreshTokenDays = 7;

    private String issuer = "sogeco-fleet-manager";

    @PostConstruct
    void validate() {
        if (secret == null || secret.isBlank()) {
            throw new IllegalStateException("""

                    ============================================================
                    Configuration manquante : sogeco.jwt.secret

                    Ajouter dans application.yml, sous la cle sogeco :

                      jwt:
                        secret: ${JWT_SECRET:...}
                        access-token-minutes: 15
                        refresh-token-days: 7
                        issuer: sogeco-fleet-manager
                    ============================================================
                    """);
        }

        int length = secret.getBytes(StandardCharsets.UTF_8).length;
        if (length < MIN_SECRET_BYTES) {
            throw new IllegalStateException(
                    "sogeco.jwt.secret fait %d octets ; l'algorithme HS256 en exige au moins %d."
                            .formatted(length, MIN_SECRET_BYTES));
        }

        if (secret.contains(DEV_SECRET_MARKER)) {
            log.warn("""

                    ATTENTION : le secret JWT est celui livre par defaut.
                    Acceptable en developpement, a remplacer imperativement
                    par la variable d'environnement JWT_SECRET en production.
                    """);
        }

        if (accessTokenMinutes <= 0 || refreshTokenDays <= 0) {
            throw new IllegalStateException("Les durees de validite des jetons doivent etre positives.");
        }
    }
}
