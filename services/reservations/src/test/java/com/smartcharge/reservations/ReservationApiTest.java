package com.smartcharge.reservations;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.Date;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.PlainJWT;
import com.nimbusds.jwt.SignedJWT;
import com.smartcharge.reservations.repository.ReservationRepository;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
            "spring.data.mongodb.auto-index-creation=true",
            "jwt.secret=" + ReservationApiTest.SECRET
        })
class ReservationApiTest {

    static final String SECRET = "secret-de-test-0123456789-abcdefghijklmnop";
    static final String AUTRE_SECRET = "un-autre-secret-de-test-9876543210-zyxwvutsrq";
    static final String ISSUER = "smartcharge-auth";
    static final String AUDIENCE = "smartcharge-api";

    static final String USER_1 = "11111111-1111-1111-1111-111111111111";
    static final String USER_2 = "22222222-2222-2222-2222-222222222222";

    // MongoDB jetable (même version majeure que ton MongoDB local : 7.0)
    static final GenericContainer<?> MONGO = new GenericContainer<>("mongo:7.0")
            .withExposedPorts(27017)
            .waitingFor(Wait.forLogMessage(".*[Ww]aiting for connections.*\\n", 1));

    static {
        MONGO.start();
    }

    @DynamicPropertySource
    static void mongoProperties(DynamicPropertyRegistry registry) {
        String uri = "mongodb://" + MONGO.getHost() + ":" + MONGO.getMappedPort(27017)
                + "/smartcharge_test";
        registry.add("spring.mongodb.uri", () -> uri);
        registry.add("spring.data.mongodb.uri", () -> uri);
    }

    @Value("${local.server.port}")
    int port;

    @Autowired
    ReservationRepository repository;

    @Autowired
    MongoTemplate mongoTemplate;

    final HttpClient client = HttpClient.newHttpClient();
    final String date = LocalDate.now().plusDays(7).toString();

    @BeforeEach
    void viderLaBase() {
        // Garde-fou : on ne vide jamais une autre base que celle des tests
        assertEquals("smartcharge_test", mongoTemplate.getDb().getName(),
                "Les tests ne sont pas branchés sur la base de test, arrêt avant suppression");
        repository.deleteAll();
    }

    // ---------- Outils ----------

    private Date dans(long millisecondes) {
        return new Date(System.currentTimeMillis() + millisecondes);
    }

    private JWTClaimsSet claims(String sub, String issuer, String audience, Date expiration) {
        return new JWTClaimsSet.Builder()
                .subject(sub)
                .issuer(issuer)
                .audience(audience)
                .issueTime(new Date())
                .expirationTime(expiration)
                .build();
    }

    private String token(String sub, String secret, String issuer, String audience, Date expiration)
            throws Exception {
        SignedJWT jwt = new SignedJWT(new JWSHeader(JWSAlgorithm.HS256),
                claims(sub, issuer, audience, expiration));
        jwt.sign(new MACSigner(secret.getBytes(StandardCharsets.UTF_8)));
        return jwt.serialize();
    }

    private String tokenValide(String sub) throws Exception {
        return token(sub, SECRET, ISSUER, AUDIENCE, dans(15 * 60 * 1000));
    }

    // Envoie une requête avec un en-tête Authorization brut (ou aucun si null)
    private HttpResponse<String> envoyerBrut(String methode, String chemin,
                                             String authorization, String corps) throws Exception {
        HttpRequest.Builder builder = HttpRequest
                .newBuilder(URI.create("http://localhost:" + port + chemin))
                .header("Content-Type", "application/json");
        if (authorization != null) {
            builder.header("Authorization", authorization);
        }
        builder.method(methode, corps == null
                ? HttpRequest.BodyPublishers.noBody()
                : HttpRequest.BodyPublishers.ofString(corps));
        return client.send(builder.build(), HttpResponse.BodyHandlers.ofString());
    }

    // Envoie une requête avec un token valide pour cet utilisateur
    private HttpResponse<String> envoyer(String methode, String chemin,
                                         String userId, String corps) throws Exception {
        return envoyerBrut(methode, chemin, "Bearer " + tokenValide(userId), corps);
    }

    private String json(String borneId, String jour, String heure) {
        return "{\"borneId\":\"" + borneId + "\",\"date\":\"" + jour
                + "\",\"heureDebut\":\"" + heure + "\"}";
    }

    private HttpResponse<String> reserver(String userId, String borneId, String jour, String heure)
            throws Exception {
        return envoyer("POST", "/api/reservations", userId, json(borneId, jour, heure));
    }

    private String extraireId(String corps) {
        Matcher m = Pattern.compile("\"id\":\"([a-f0-9]+)\"").matcher(corps);
        assertTrue(m.find(), "id introuvable dans : " + corps);
        return m.group(1);
    }

    // ---------- Authentification JWT ----------

    @Test
    void sansToken_renvoie401() throws Exception {
        HttpResponse<String> r = envoyerBrut("GET", "/api/reservations", null, null);
        assertEquals(401, r.statusCode());
        assertTrue(r.body().contains("Authentification requise"));
    }

    @Test
    void tokenIllisible_renvoie401() throws Exception {
        HttpResponse<String> r = envoyerBrut("GET", "/api/reservations", "Bearer n-importe-quoi", null);
        assertEquals(401, r.statusCode());
    }

    @Test
    void schemaNonBearer_renvoie401() throws Exception {
        HttpResponse<String> r = envoyerBrut("GET", "/api/reservations", "Basic dXNlcjpwYXNz", null);
        assertEquals(401, r.statusCode());
    }

    @Test
    void tokenExpire_renvoie401() throws Exception {
        String expire = token(USER_1, SECRET, ISSUER, AUDIENCE, dans(-60 * 60 * 1000));
        HttpResponse<String> r = envoyerBrut("GET", "/api/reservations", "Bearer " + expire, null);
        assertEquals(401, r.statusCode());
    }

    @Test
    void tokenSigneAvecUnAutreSecret_renvoie401() throws Exception {
        String faux = token(USER_1, AUTRE_SECRET, ISSUER, AUDIENCE, dans(15 * 60 * 1000));
        HttpResponse<String> r = envoyerBrut("GET", "/api/reservations", "Bearer " + faux, null);
        assertEquals(401, r.statusCode());
    }

    @Test
    void tokenAvecMauvaisEmetteur_renvoie401() throws Exception {
        String faux = token(USER_1, SECRET, "autre-emetteur", AUDIENCE, dans(15 * 60 * 1000));
        HttpResponse<String> r = envoyerBrut("GET", "/api/reservations", "Bearer " + faux, null);
        assertEquals(401, r.statusCode());
    }

    @Test
    void tokenAvecMauvaiseAudience_renvoie401() throws Exception {
        String faux = token(USER_1, SECRET, ISSUER, "autre-audience", dans(15 * 60 * 1000));
        HttpResponse<String> r = envoyerBrut("GET", "/api/reservations", "Bearer " + faux, null);
        assertEquals(401, r.statusCode());
    }

    @Test
    void tokenNonSigne_renvoie401() throws Exception {
        String nonSigne = new PlainJWT(claims(USER_1, ISSUER, AUDIENCE, dans(15 * 60 * 1000))).serialize();
        HttpResponse<String> r = envoyerBrut("GET", "/api/reservations", "Bearer " + nonSigne, null);
        assertEquals(401, r.statusCode());
    }

    @Test
    void actuatorHealth_accessibleSansToken() throws Exception {
        HttpResponse<String> r = envoyerBrut("GET", "/actuator/health", null, null);
        assertEquals(200, r.statusCode());
    }

    // ---------- Création et créneaux ----------

    @Test
    void creationValide_renvoie201() throws Exception {
        HttpResponse<String> r = reserver(USER_1, "borne-1", date, "14:00:00");
        assertEquals(201, r.statusCode());
        assertTrue(r.body().contains("\"statut\":\"CONFIRMEE\""));
        assertTrue(r.body().contains("\"userId\":\"" + USER_1 + "\""));
    }

    @Test
    void doublon_memeUtilisateur_memeBorne_renvoie409() throws Exception {
        assertEquals(201, reserver(USER_1, "borne-1", date, "14:00:00").statusCode());
        HttpResponse<String> r = reserver(USER_1, "borne-1", date, "14:00:00");
        assertEquals(409, r.statusCode());
        assertTrue(r.body().contains("déjà réservé"));
    }

    @Test
    void memeBorneMemeCreneau_autreUtilisateur_renvoie409() throws Exception {
        assertEquals(201, reserver(USER_1, "borne-1", date, "14:00:00").statusCode());
        assertEquals(409, reserver(USER_2, "borne-1", date, "14:00:00").statusCode());
    }

    @Test
    void memeUtilisateurMemeCreneau_autreBorne_renvoie409() throws Exception {
        assertEquals(201, reserver(USER_1, "borne-1", date, "14:00:00").statusCode());
        assertEquals(409, reserver(USER_1, "borne-2", date, "14:00:00").statusCode());
    }

    @Test
    void creneauxConsecutifs_nSeBloquentPas() throws Exception {
        assertEquals(201, reserver(USER_1, "borne-3", date, "14:30:00").statusCode());
        assertEquals(201, reserver(USER_2, "borne-3", date, "15:00:00").statusCode());
    }

    // ---------- Validation ----------

    @Test
    void datePassee_renvoie400() throws Exception {
        HttpResponse<String> r = reserver(USER_1, "borne-1", "2020-01-01", "14:00:00");
        assertEquals(400, r.statusCode());
        assertTrue(r.body().contains("futur"));
    }

    @Test
    void heureHorsCreneau_renvoie400() throws Exception {
        HttpResponse<String> r = reserver(USER_1, "borne-1", date, "14:15:00");
        assertEquals(400, r.statusCode());
        assertTrue(r.body().contains(":00 ou :30"));
    }

    @Test
    void champsManquants_renvoie400() throws Exception {
        HttpResponse<String> r = envoyer("POST", "/api/reservations", USER_1, "{\"borneId\":\"borne-1\"}");
        assertEquals(400, r.statusCode());
    }

    @Test
    void dateMalFormee_renvoie400() throws Exception {
        HttpResponse<String> r = reserver(USER_1, "borne-1", "demain", "14:00:00");
        assertEquals(400, r.statusCode());
    }

    // ---------- Isolation entre utilisateurs ----------

    @Test
    void unUtilisateurNeVoitQueSesReservations() throws Exception {
        reserver(USER_1, "borne-1", date, "14:00:00");

        HttpResponse<String> liste1 = envoyer("GET", "/api/reservations", USER_1, null);
        assertEquals(200, liste1.statusCode());
        assertTrue(liste1.body().contains("borne-1"));

        HttpResponse<String> liste2 = envoyer("GET", "/api/reservations", USER_2, null);
        assertEquals(200, liste2.statusCode());
        assertEquals("[]", liste2.body());
    }

    @Test
    void consulterLaReservationDUnAutre_renvoie404() throws Exception {
        String id = extraireId(reserver(USER_1, "borne-1", date, "14:00:00").body());

        assertEquals(200, envoyer("GET", "/api/reservations/" + id, USER_1, null).statusCode());
        assertEquals(404, envoyer("GET", "/api/reservations/" + id, USER_2, null).statusCode());
    }

    // ---------- Annulation ----------

    @Test
    void annulation_changeLeStatut() throws Exception {
        String id = extraireId(reserver(USER_1, "borne-1", date, "14:00:00").body());

        HttpResponse<String> r = envoyer("PATCH", "/api/reservations/" + id + "/annulation", USER_1, null);
        assertEquals(200, r.statusCode());
        assertTrue(r.body().contains("\"statut\":\"ANNULEE\""));
    }

    @Test
    void annulerLaReservationDUnAutre_renvoie404() throws Exception {
        String id = extraireId(reserver(USER_1, "borne-1", date, "14:00:00").body());

        HttpResponse<String> r = envoyer("PATCH", "/api/reservations/" + id + "/annulation", USER_2, null);
        assertEquals(404, r.statusCode());

        // la réservation de l'utilisateur 1 est restée confirmée
        HttpResponse<String> verif = envoyer("GET", "/api/reservations/" + id, USER_1, null);
        assertTrue(verif.body().contains("\"statut\":\"CONFIRMEE\""));
    }

    @Test
    void apresAnnulation_leCreneauEstLibere() throws Exception {
        String id = extraireId(reserver(USER_1, "borne-1", date, "14:00:00").body());
        envoyer("PATCH", "/api/reservations/" + id + "/annulation", USER_1, null);

        // Les index uniques ne s'appliquent qu'aux réservations CONFIRMEE
        assertEquals(201, reserver(USER_1, "borne-1", date, "14:00:00").statusCode());
    }
}