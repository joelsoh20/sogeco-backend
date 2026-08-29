package com.sogeco.fleet.common.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.time.Instant;

/** Reponse 403 au format ProblemDetail. */
@Component
public class RestAccessDeniedHandler implements AccessDeniedHandler {

    @Override
    public void handle(HttpServletRequest request,
                       HttpServletResponse response,
                       AccessDeniedException exception) throws IOException {

        response.setStatus(HttpStatus.FORBIDDEN.value());
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        response.getWriter().write("""
                {
                  "type": "https://api.sogeco.cm/errors/access_denied",
                  "title": "ACCESS_DENIED",
                  "status": 403,
                  "detail": "Vous n'avez pas les droits necessaires pour cette operation",
                  "instance": "%s",
                  "timestamp": "%s"
                }""".formatted(request.getRequestURI(), Instant.now()));
    }
}
