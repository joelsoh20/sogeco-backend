package com.sogeco.fleet.common.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.time.Instant;

/**
 * Reponse 401 au format ProblemDetail, cohérente avec le reste de l'API.
 * Sans cela, Spring Security renverrait une page HTML de connexion.
 */
@Component
public class RestAuthenticationEntryPoint implements AuthenticationEntryPoint {

    @Override
    public void commence(HttpServletRequest request,
                         HttpServletResponse response,
                         AuthenticationException exception) throws IOException {

        response.setStatus(HttpStatus.UNAUTHORIZED.value());
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        response.getWriter().write("""
                {
                  "type": "https://api.sogeco.cm/errors/unauthorized",
                  "title": "UNAUTHORIZED",
                  "status": 401,
                  "detail": "Authentification requise",
                  "instance": "%s",
                  "timestamp": "%s"
                }""".formatted(request.getRequestURI(), Instant.now()));
    }
}
