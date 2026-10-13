package com.smartcharge.reservations.security;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSVerifier;
import com.nimbusds.jose.crypto.MACVerifier;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Vérifie le JWT émis par le service auth (HS256, émetteur smartcharge-auth,
 * audience smartcharge-api) et expose l'id utilisateur (claim "sub") dans
 * l'attribut de requête "userId".
 */
@Component
public class JwtAuthFilter extends OncePerRequestFilter {

    private static final String ISSUER = "smartcharge-auth";
    private static final String AUDIENCE = "smartcharge-api";
    private static final Pattern BEARER =
            Pattern.compile("^Bearer\\s+(.+)$", Pattern.CASE_INSENSITIVE);

    private final JWSVerifier verifier;

    public JwtAuthFilter(@Value("${jwt.secret}") String secret) throws JOSEException {
        byte[] cle = secret.getBytes(StandardCharsets.UTF_8);
        if (cle.length < 32) {
            throw new IllegalStateException(
                    "jwt.secret doit faire au moins 32 octets pour HS256 (valeur actuelle : "
                            + cle.length + ")");
        }
        this.verifier = new MACVerifier(cle);
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return request.getRequestURI().startsWith("/actuator");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {

        String header = request.getHeader("Authorization");
        Matcher m = header == null ? null : BEARER.matcher(header);
        if (m == null || !m.matches()) {
            rejeter(response, "Authentification requise");
            return;
        }

        String userId = extraireUserId(m.group(1).trim());
        if (userId == null) {
            rejeter(response, "Token invalide ou expiré");
            return;
        }

        request.setAttribute("userId", userId);
        chain.doFilter(request, response);
    }

    // Renvoie l'id utilisateur si le token est valide, sinon null.
    private String extraireUserId(String token) {
        try {
            SignedJWT jwt = SignedJWT.parse(token);

            if (!JWSAlgorithm.HS256.equals(jwt.getHeader().getAlgorithm())) {
                return null;
            }
            if (!jwt.verify(verifier)) {
                return null;
            }

            JWTClaimsSet claims = jwt.getJWTClaimsSet();
            if (!ISSUER.equals(claims.getIssuer())) {
                return null;
            }
            if (claims.getAudience() == null || !claims.getAudience().contains(AUDIENCE)) {
                return null;
            }
            Date expiration = claims.getExpirationTime();
            if (expiration == null || expiration.before(new Date())) {
                return null;
            }
            String sub = claims.getSubject();
            return (sub == null || sub.isBlank()) ? null : sub;
        } catch (Exception e) {
            return null;
        }
    }

    private void rejeter(HttpServletResponse response, String message) throws IOException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setHeader("WWW-Authenticate", "Bearer");
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write("{\"error\":\"" + message + "\"}");
    }
}