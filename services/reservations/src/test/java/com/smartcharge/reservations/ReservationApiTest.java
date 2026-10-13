package com.smartcharge.reservations;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.LocalDate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.springframework.data.mongodb.core.MongoTemplate;

import com.smartcharge.reservations.repository.ReservationRepository;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "spring.data.mongodb.auto-index-creation=true")
class ReservationApiTest {

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
        // On renseigne les deux noms de propriété, car Spring Boot 4 en a déplacé certaines
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

    private HttpResponse<String> envoyer(String methode, String chemin, Integer userId, String corps)
            throws Exception {
        HttpRequest.Builder builder = HttpRequest
                .newBuilder(URI.create("http://localhost:" + port + chemin))
                .header("Content-Type", "application/json");
        if (userId != null) {
            builder.header("X-User-Id", String.valueOf(userId));
        }
        builder.method(methode, corps == null
                ? HttpRequest.BodyPublishers.noBody()
                : HttpRequest.BodyPublishers.ofString(corps));
        return client.send(builder.build(), HttpResponse.BodyHandlers.ofString());
    }

    private String json(String borneId, String jour, String heure) {
        return "{\"borneId\":\"" + borneId + "\",\"date\":\"" + jour
                + "\",\"heureDebut\":\"" + heure + "\"}";
    }

    private HttpResponse<String> reserver(int userId, String borneId, String jour, String heure)
            throws Exception {
        return envoyer("POST", "/api/reservations", userId, json(borneId, jour, heure));
    }

    private String extraireId(String corps) {
        Matcher m = Pattern.compile("\"id\":\"([a-f0-9]+)\"").matcher(corps);
        assertTrue(m.find(), "id introuvable dans : " + corps);
        return m.group(1);
    }

    // ---------- Authentification de développement ----------

    @Test
    void sansEnteteUserId_renvoie400() throws Exception {
        HttpResponse<String> r = envoyer("GET", "/api/reservations", null, null);
        assertEquals(400, r.statusCode());
        assertTrue(r.body().contains("X-User-Id"));
    }

    @Test
    void userIdNonNumerique_renvoie400() throws Exception {
        HttpRequest req = HttpRequest
                .newBuilder(URI.create("http://localhost:" + port + "/api/reservations"))
                .header("X-User-Id", "abc")
                .GET()
                .build();
        HttpResponse<String> r = client.send(req, HttpResponse.BodyHandlers.ofString());
        assertEquals(400, r.statusCode());
    }

    // ---------- Création et créneaux ----------

    @Test
    void creationValide_renvoie201() throws Exception {
        HttpResponse<String> r = reserver(1, "borne-1", date, "14:00:00");
        assertEquals(201, r.statusCode());
        assertTrue(r.body().contains("\"statut\":\"CONFIRMEE\""));
        assertTrue(r.body().contains("\"userId\":\"1\""));
    }

    @Test
    void doublon_memeUtilisateur_memeBorne_renvoie409() throws Exception {
        assertEquals(201, reserver(1, "borne-1", date, "14:00:00").statusCode());
        HttpResponse<String> r = reserver(1, "borne-1", date, "14:00:00");
        assertEquals(409, r.statusCode());
        assertTrue(r.body().contains("déjà réservé"));
    }

    @Test
    void memeBorneMemeCreneau_autreUtilisateur_renvoie409() throws Exception {
        assertEquals(201, reserver(1, "borne-1", date, "14:00:00").statusCode());
        assertEquals(409, reserver(2, "borne-1", date, "14:00:00").statusCode());
    }

    @Test
    void memeUtilisateurMemeCreneau_autreBorne_renvoie409() throws Exception {
        assertEquals(201, reserver(1, "borne-1", date, "14:00:00").statusCode());
        assertEquals(409, reserver(1, "borne-2", date, "14:00:00").statusCode());
    }

    @Test
    void creneauxConsecutifs_nSeBloquentPas() throws Exception {
        assertEquals(201, reserver(1, "borne-3", date, "14:30:00").statusCode());
        assertEquals(201, reserver(2, "borne-3", date, "15:00:00").statusCode());
    }

    // ---------- Validation ----------

    @Test
    void datePassee_renvoie400() throws Exception {
        HttpResponse<String> r = reserver(1, "borne-1", "2020-01-01", "14:00:00");
        assertEquals(400, r.statusCode());
        assertTrue(r.body().contains("futur"));
    }

    @Test
    void heureHorsCreneau_renvoie400() throws Exception {
        HttpResponse<String> r = reserver(1, "borne-1", date, "14:15:00");
        assertEquals(400, r.statusCode());
        assertTrue(r.body().contains(":00 ou :30"));
    }

    @Test
    void champsManquants_renvoie400() throws Exception {
        HttpResponse<String> r = envoyer("POST", "/api/reservations", 1, "{\"borneId\":\"borne-1\"}");
        assertEquals(400, r.statusCode());
    }

    @Test
    void dateMalFormee_renvoie400() throws Exception {
        HttpResponse<String> r = reserver(1, "borne-1", "demain", "14:00:00");
        assertEquals(400, r.statusCode());
    }

    // ---------- Isolation entre utilisateurs ----------

    @Test
    void unUtilisateurNeVoitQueSesReservations() throws Exception {
        reserver(1, "borne-1", date, "14:00:00");

        HttpResponse<String> liste1 = envoyer("GET", "/api/reservations", 1, null);
        assertEquals(200, liste1.statusCode());
        assertTrue(liste1.body().contains("borne-1"));

        HttpResponse<String> liste2 = envoyer("GET", "/api/reservations", 2, null);
        assertEquals(200, liste2.statusCode());
        assertEquals("[]", liste2.body());
    }

    @Test
    void consulterLaReservationDUnAutre_renvoie404() throws Exception {
        String id = extraireId(reserver(1, "borne-1", date, "14:00:00").body());

        assertEquals(200, envoyer("GET", "/api/reservations/" + id, 1, null).statusCode());
        assertEquals(404, envoyer("GET", "/api/reservations/" + id, 2, null).statusCode());
    }

    // ---------- Annulation ----------

    @Test
    void annulation_changeLeStatut() throws Exception {
        String id = extraireId(reserver(1, "borne-1", date, "14:00:00").body());

        HttpResponse<String> r = envoyer("PATCH", "/api/reservations/" + id + "/annulation", 1, null);
        assertEquals(200, r.statusCode());
        assertTrue(r.body().contains("\"statut\":\"ANNULEE\""));
    }

    @Test
    void annulerLaReservationDUnAutre_renvoie404() throws Exception {
        String id = extraireId(reserver(1, "borne-1", date, "14:00:00").body());

        HttpResponse<String> r = envoyer("PATCH", "/api/reservations/" + id + "/annulation", 2, null);
        assertEquals(404, r.statusCode());

        // la réservation de l'utilisateur 1 est restée confirmée
        HttpResponse<String> verif = envoyer("GET", "/api/reservations/" + id, 1, null);
        assertTrue(verif.body().contains("\"statut\":\"CONFIRMEE\""));
    }

    @Test
    void apresAnnulation_leCreneauEstLibere() throws Exception {
        String id = extraireId(reserver(1, "borne-1", date, "14:00:00").body());
        envoyer("PATCH", "/api/reservations/" + id + "/annulation", 1, null);

        // Les index uniques ne s'appliquent qu'aux réservations CONFIRMEE
        assertEquals(201, reserver(1, "borne-1", date, "14:00:00").statusCode());
    }
}