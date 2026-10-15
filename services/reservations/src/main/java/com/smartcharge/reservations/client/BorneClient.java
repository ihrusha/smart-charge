package com.smartcharge.reservations.client;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Interroge le service bornes (GET /bornes/{id}) pour vérifier qu'une borne
 * existe et qu'elle n'est pas hors service.
 */
@Component
public class BorneClient {

    public enum Resultat {
        /** La borne existe et accepte des réservations. */
        EXISTE,
        /** La borne existe mais son statut est HORS_SERVICE. */
        HORS_SERVICE,
        /** Aucune borne ne porte cet identifiant. */
        INTROUVABLE,
        /** Le service bornes n'a pas pu répondre (panne, délai dépassé). */
        INDISPONIBLE
    }

    private static final Logger LOG = LoggerFactory.getLogger(BorneClient.class);
    private static final String STATUT_HORS_SERVICE = "HORS_SERVICE";

    /** Repère "statut": "..." dans la réponse, espaces éventuels compris. */
    private static final Pattern CHAMP_STATUT =
            Pattern.compile("\"statut\"\\s*:\\s*\"([^\"]*)\"");

    private final String baseUrl;
    private final Duration delai;
    private final HttpClient client;

    public BorneClient(@Value("${bornes.url}") String baseUrl,
                       @Value("${bornes.timeout-ms:3000}") long timeoutMs) {
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        this.delai = Duration.ofMillis(timeoutMs);
        this.client = HttpClient.newBuilder().connectTimeout(delai).build();
    }

    public Resultat verifier(String borneId, String authorization) {
        try {
            HttpRequest.Builder requete = HttpRequest
                    .newBuilder(URI.create(baseUrl + "/bornes/" + borneId))
                    .timeout(delai)
                    .GET();
            if (authorization != null) {
                requete.header("Authorization", authorization);
            }

            // On lit le corps (avant on le jetait) : c'est lui qui porte le statut.
            HttpResponse<String> reponse = client.send(
                    requete.build(),
                    HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            int code = reponse.statusCode();

            if (code == 404) {
                return Resultat.INTROUVABLE;
            }
            if (code < 200 || code >= 300) {
                LOG.warn("Le service bornes a répondu {} pour la borne {}", code, borneId);
                return Resultat.INDISPONIBLE;
            }

            return estHorsService(reponse.body()) ? Resultat.HORS_SERVICE : Resultat.EXISTE;

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            LOG.warn("Appel au service bornes interrompu");
            return Resultat.INDISPONIBLE;
        } catch (Exception e) {
            LOG.warn("Service bornes injoignable : {}", e.toString());
            return Resultat.INDISPONIBLE;
        }
    }

    /**
     * Tant que le service bornes ne renvoie pas le champ "statut",
     * la borne est considérée comme disponible : on ne bloque personne
     * à cause d'un champ manquant.
     */
    private boolean estHorsService(String corps) {
        if (corps == null || corps.isBlank()) {
            return false;
        }
        Matcher m = CHAMP_STATUT.matcher(corps);
        return m.find() && STATUT_HORS_SERVICE.equalsIgnoreCase(m.group(1).trim());
    }
}