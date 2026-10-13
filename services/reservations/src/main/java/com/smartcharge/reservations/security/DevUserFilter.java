package com.smartcharge.reservations.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * FILTRE TEMPORAIRE (développement, sans JWT).
 * Lit l'id utilisateur dans l'en-tête X-User-Id.
 * À REMPLACER par JwtAuthFilter quand l'API Auth émettra de vrais tokens.
 */
@Component
public class DevUserFilter extends OncePerRequestFilter {

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return request.getRequestURI().startsWith("/actuator");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {

        String header = request.getHeader("X-User-Id");
        if (header == null) {
            reject(response, "En-tête X-User-Id manquant");
            return;
        }

        try {
            request.setAttribute("userId", Integer.valueOf(header));
            chain.doFilter(request, response);
        } catch (NumberFormatException e) {
            reject(response, "X-User-Id doit être un nombre");
        }
    }

    private void reject(HttpServletResponse response, String message) throws IOException {
        response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write("{\"error\":\"" + message + "\"}");
    }
}