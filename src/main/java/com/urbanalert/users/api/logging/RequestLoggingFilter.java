package com.urbanalert.users.api.logging;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * One log entry per request with method, path, status and duration as key-value pairs, which the structured
 * (ECS) console format of the "aws" profile writes as JSON fields (spec 001-migracion-aws RF-004, plan D-006).
 * The query string is left out on purpose: it is not needed to diagnose and may carry personal data.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RequestLoggingFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(RequestLoggingFilter.class);

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        long start = System.nanoTime();
        int status = HttpServletResponse.SC_INTERNAL_SERVER_ERROR;
        try {
            chain.doFilter(request, response);
            status = response.getStatus();
        } finally {
            double durationMs = Math.round((System.nanoTime() - start) / 100_000.0) / 10.0;
            log.atInfo()
                    .addKeyValue("method", request.getMethod())
                    .addKeyValue("path", request.getRequestURI())
                    .addKeyValue("status", status)
                    .addKeyValue("duration_ms", durationMs)
                    .log("{} {} {} {}ms", request.getMethod(), request.getRequestURI(), status, durationMs);
        }
    }
}
