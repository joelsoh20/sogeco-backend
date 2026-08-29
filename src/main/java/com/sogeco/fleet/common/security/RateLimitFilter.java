package com.sogeco.fleet.common.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;

/**
 * Limite le nombre de requetes API par utilisateur (par adresse IP tant
 * qu'il n'est pas authentifie) sur une fenetre fixe d'une minute.
 *
 * Place apres {@link JwtAuthenticationFilter} : le contexte de securite
 * est deja alimente quand on determine la cle du compteur.
 *
 * Fenetre fixe plutot que glissante (log de timestamps) : un seul INCR
 * Redis par requete, largement suffisant pour se proteger d'un script qui
 * boucle ou d'un abus, pas pour lisser un trafic legitime a la seconde
 * pres — inutile a l'echelle de 11 camions et une dizaine d'utilisateurs.
 *
 * Une panne de Redis ne doit pas bloquer l'application : comme le reste
 * du cache (voir PositionCacheService), on echoue en silence et on laisse
 * passer la requete plutot que de la refuser a tort.
 */
@Slf4j
@Component
public class RateLimitFilter extends OncePerRequestFilter {

    private static final String KEY_PREFIX = "ratelimit:";

    private final StringRedisTemplate redis;
    private final int requestsPerMinute;

    public RateLimitFilter(StringRedisTemplate redis,
                           @Value("${sogeco.ratelimit.requests-per-minute:120}") int requestsPerMinute) {
        this.redis = redis;
        this.requestsPerMinute = requestsPerMinute;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {

        String key = KEY_PREFIX + resolveIdentity(request) + ":" + currentWindow();

        long count;
        try {
            count = redis.opsForValue().increment(key);
            if (count == 1L) {
                redis.expire(key, Duration.ofMinutes(1));
            }
        } catch (RuntimeException e) {
            log.warn("Controle de debit impossible, requete laissee passer", e);
            chain.doFilter(request, response);
            return;
        }

        if (count > requestsPerMinute) {
            writeTooManyRequests(request, response);
            return;
        }

        chain.doFilter(request, response);
    }

    /** Identite de l'utilisateur authentifie, ou son adresse IP a defaut (connexion, jeton expire). */
    private String resolveIdentity(HttpServletRequest request) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof UserPrincipal principal) {
            return "user:" + principal.getId();
        }
        return "ip:" + request.getRemoteAddr();
    }

    /** Minute courante depuis l'epoque : fenetre fixe, remise a zero chaque minute. */
    private long currentWindow() {
        return Instant.now().getEpochSecond() / 60;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        // Webhooks telematiques : volumetrie et protection propres (secret partage),
        // un flux GPS legitime ne doit pas se faire bloquer ici.
        return path.startsWith("/api/v1/webhooks/")
                || path.startsWith("/actuator/health")
                || path.startsWith("/swagger-ui")
                || path.startsWith("/v3/api-docs")
                || path.startsWith("/ws/");
    }

    private void writeTooManyRequests(HttpServletRequest request, HttpServletResponse response) throws IOException {
        response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
        response.setHeader("Retry-After", "60");
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        response.getWriter().write("""
                {
                  "type": "https://api.sogeco.cm/errors/too-many-requests",
                  "title": "TOO_MANY_REQUESTS",
                  "status": 429,
                  "detail": "Trop de requetes, reessayez dans une minute",
                  "instance": "%s",
                  "timestamp": "%s"
                }""".formatted(request.getRequestURI(), Instant.now()));
    }
}
