package com.smartcharge.reservations.client;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** Interroge le service bornes (GET /bornes/{id}) pour vérifier qu'une borne existe. */
@Component
public class BorneClient {

    public enum Resultat { EXISTE, INTROUVABLE, INDISPONIBLE }

    private static final Logger LOG = LoggerFactory.getLogger(BorneClient.class);

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

            HttpResponse<Void> reponse =
                    client.send(requete.build(), HttpResponse.BodyHandlers.discarding());
            int code = reponse.statusCode();

            if (code >= 200 && code < 300) {
                return Resultat.EXISTE;
            }
            if (code == 404) {
                return Resultat.INTROUVABLE;
            }
            LOG.warn("Le service bornes a répondu {} pour la borne {}", code, borneId);
            return Resultat.INDISPONIBLE;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            LOG.warn("Appel au service bornes interrompu");
            return Resultat.INDISPONIBLE;
        } catch (Exception e) {
            LOG.warn("Service bornes injoignable : {}", e.toString());
            return Resultat.INDISPONIBLE;
        }
    }
}