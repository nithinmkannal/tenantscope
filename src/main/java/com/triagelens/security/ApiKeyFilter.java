package com.triagelens.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/** Protects /api/** with a static API key supplied in the X-API-Key header. */
@Component
public class ApiKeyFilter extends OncePerRequestFilter {

    private final List<byte[]> keys;

    public ApiKeyFilter(@Value("${triagelens.security.api-keys}") String configured) {
        this.keys = Arrays.stream(configured.split(","))
                .map(String::trim).filter(s -> !s.isEmpty())
                .map(s -> s.getBytes(StandardCharsets.UTF_8)).toList();
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !request.getRequestURI().startsWith("/api/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain chain)
            throws ServletException, IOException {
        String provided = req.getHeader("X-API-Key");
        if (provided != null && matches(provided.getBytes(StandardCharsets.UTF_8))) {
            chain.doFilter(req, res);
            return;
        }
        res.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        res.setContentType(MediaType.APPLICATION_JSON_VALUE);
        res.getWriter().write("{\"error\":\"UNAUTHORIZED\",\"message\":\"Missing or invalid X-API-Key\"}");
    }

    private boolean matches(byte[] candidate) {
        boolean ok = false;
        for (byte[] k : keys) {
            ok |= MessageDigest.isEqual(k, candidate);
        }
        return ok;
    }
}
