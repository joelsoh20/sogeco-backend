package com.sogeco.fleet.common.config;

import org.springframework.boot.EnvironmentPostProcessor;
import org.springframework.boot.SpringApplication;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.Profiles;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Charge .env (racine du projet) dans l'environnement Spring, quand il existe.
 *
 * docker-compose lit .env pour ses propres conteneurs, mais un lancement
 * direct — mvnw spring-boot:run, java -jar, ou depuis l'IDE — ne l'importe
 * jamais. Sans ce chargeur, ORS_API_KEY, JWT_SECRET, GOOGLE_CLIENT_ID...
 * restent absents hors Docker et retombent silencieusement sur les valeurs
 * par defaut d'application.yml, sans avertissement autre que celui,
 * facilement manque, de JwtProperties au demarrage.
 *
 * Priorite la plus basse : une variable deja definie ailleurs (environnement
 * du systeme, -D, application.yml explicite) n'est jamais ecrasee par .env.
 *
 * Desactive sous le profil "test" : la suite d'integration fixe deja ses
 * propres proprietes via @DynamicPropertySource (Testcontainers), et
 * charger .env y ajouterait un risque inutile — activer par erreur un
 * appel reseau reel (ORS) pendant les tests.
 */
public class DotenvEnvironmentPostProcessor implements EnvironmentPostProcessor {

    private static final String PROPERTY_SOURCE_NAME = "dotenv";

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        if (environment.getPropertySources().contains(PROPERTY_SOURCE_NAME)) {
            return;
        }
        if (environment.acceptsProfiles(Profiles.of("test"))) {
            return;
        }

        Path envFile = Path.of(".env");
        if (!Files.isRegularFile(envFile)) {
            return;
        }

        Map<String, Object> values = parse(envFile);
        if (!values.isEmpty()) {
            environment.getPropertySources().addLast(new MapPropertySource(PROPERTY_SOURCE_NAME, values));
        }
    }

    private Map<String, Object> parse(Path envFile) {
        Map<String, Object> values = new LinkedHashMap<>();
        try {
            for (String line : Files.readAllLines(envFile, StandardCharsets.UTF_8)) {
                String trimmed = line.trim();
                if (trimmed.isEmpty() || trimmed.startsWith("#")) {
                    continue;
                }
                int separator = trimmed.indexOf('=');
                if (separator <= 0) {
                    continue;
                }
                String key = trimmed.substring(0, separator).trim();
                String value = unquote(trimmed.substring(separator + 1).trim());
                values.put(key, value);
            }
        } catch (IOException e) {
            throw new IllegalStateException("Lecture de .env impossible", e);
        }
        return values;
    }

    private String unquote(String value) {
        if (value.length() >= 2
                && ((value.startsWith("\"") && value.endsWith("\""))
                || (value.startsWith("'") && value.endsWith("'")))) {
            return value.substring(1, value.length() - 1);
        }
        return value;
    }
}
