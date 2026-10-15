package com.smartcharge.reservations;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.Date;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicReference;
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
import com.sun.net.httpserver.HttpServer;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
            "spring.data.mongodb.auto-index-creation=true",
            "jwt.secret=" + ReservationApiTest.SECRET,
            "bornes.timeout-ms=500"
        })
class ReservationApiTest {

    static final String SECRET = "secret-de-test-0123456789-abcdefghijklmnop";
    static final String AUTRE_SECRET = "un-autre-secret-de-test-9876543210-zyxwvutsrq";
    static final String ISSUER = "smartcharge-auth";
    static final String AUDIENCE = "smartcharge-api";

    static final String USER_1 = "11111111-1111-1111-1111-111111111111";
    static final String USER_2 = "22222222-2222-2222-2222-222222222222";

    // Identifiants de bornes (ObjectId : 24 caractères hexadécimaux)
    static final String BORNE_1 = "650000000000000000000001";
    static final String BORNE_2 = "650000000000000000000002";
    static final String BORNE_3 = "650000000000000000000003";
    static final String BORNE_ABC = "65abcdef0123456789abcdef";
    static final String BORNE_INCONNUE = "650000000000000000000404";
    static final String BORNE_EN_PANNE = "650000000000000000000500";
    static final String BORNE_LENTE = "650000000000000000000998";

    static final Set<String> BORNES_CONNUES = Set.of(BORNE_1, BORNE_2, BORNE_3, BORNE_ABC);
    static final AtomicReference<String> DERNIERE_AUTH = new AtomicReference<>();

    // Faux service bornes : 200 si connue, 500 si en panne, réponse lente, 404 sinon
    static final HttpServer BORNES = demarrerFauxServiceBornes();

    static HttpServer demarrerFauxServiceBornes() {
        try {
            HttpServer serveur = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            serveur.setExecutor(Executors.newCachedThreadPool(r -> {
                Thread t = new Thread(r);
                t.setDaemon(true);
                return t;
            }));
            serveur.createContext("/bornes/", exchange -> {
                DERNIERE_AUTH.set(exchange.getRequestHeaders().getFirst("Authorization"));
                String id = exchange.getRequestURI().getPath().substring("/bornes/".length());
                if (BORNE_LENTE.equals(id)) {
                    try {
                        Thread.sleep(1500);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                }
                int code = BORNES_CONNUES.contains(id) ? 200 : BORNE_EN_PANNE.equals(id) ? 500 : 404;
                byte[] corps = ("{\"id\":\"" + id + "\"}").getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().add("Content-Type", "application/json");
                exchange.sendResponseHeaders(code, corps.length);
                try (OutputStream os = exchange.getResponseBody()) {
                    os.write(corps);
                }
            });
            serveur.start();
            return serveur;
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    // MongoDB jetable (même version majeure que ton MongoDB local : 7.0)
    static final GenericContainer<?> MONGO = new GenericContainer<>("mongo:7.0")
            .withExposedPorts(27017)
            .waitingFor(Wait.forLogMessage(".*[Ww]aiting for connections.*\\n", 1));

    static {
        MONGO.start();
    }

    @DynamicPropertySource
    static void proprietes(DynamicPropertyRegistry registry) {
        String uri = "mongodb://" + MONGO.getHost() + ":" + MONGO.getMappedPort(27017)
                + "/smartcharge_test";
        registry.add("spring.mongodb.uri", () -> uri);
        registry.add("spring.data.mongodb.uri", () -> uri);
        registry.add("bornes.url", () -> "http://127.0.0.1:" + BORNES.getAddress().getPort());
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
        DERNIERE_AUTH.set(null);
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
        HttpResponse<String> r = reserver(USER_1, BORNE_1, date, "14:00:00");
        assertEquals(201, r.statusCode());
        assertTrue(r.body().contains("\"statut\":\"CONFIRMEE\""));
        assertTrue(r.body().contains("\"userId\":\"" + USER_1 + "\""));
    }

    @Test
    void doublon_memeUtilisateur_memeBorne_renvoie409() throws Exception {
        assertEquals(201, reserver(USER_1, BORNE_1, date, "14:00:00").statusCode());
        HttpResponse<String> r = reserver(USER_1, BORNE_1, date, "14:00:00");
        assertEquals(409, r.statusCode());
        assertTrue(r.body().contains("déjà réservé"));
    }

    @Test
    void memeBorneMemeCreneau_autreUtilisateur_renvoie409() throws Exception {
        assertEquals(201, reserver(USER_1, BORNE_1, date, "14:00:00").statusCode());
        assertEquals(409, reserver(USER_2, BORNE_1, date, "14:00:00").statusCode());
    }

    @Test
    void memeUtilisateurMemeCreneau_autreBorne_renvoie409() throws Exception {
        assertEquals(201, reserver(USER_1, BORNE_1, date, "14:00:00").statusCode());
        assertEquals(409, reserver(USER_1, BORNE_2, date, "14:00:00").statusCode());
    }

    @Test
    void creneauxConsecutifs_nSeBloquentPas() throws Exception {
        assertEquals(201, reserver(USER_1, BORNE_3, date, "14:30:00").statusCode());
        assertEquals(201, reserver(USER_2, BORNE_3, date, "15:00:00").statusCode());
    }

    // ---------- Validation ----------

    @Test
    void datePassee_renvoie400() throws Exception {
        HttpResponse<String> r = reserver(USER_1, BORNE_1, "2020-01-01", "14:00:00");
        assertEquals(400, r.statusCode());
        assertTrue(r.body().contains("futur"));
    }

    @Test
    void heureHorsCreneau_renvoie400() throws Exception {
        HttpResponse<String> r = reserver(USER_1, BORNE_1, date, "14:15:00");
        assertEquals(400, r.statusCode());
        assertTrue(r.body().contains(":00 ou :30"));
    }

    @Test
    void champsManquants_renvoie400() throws Exception {
        HttpResponse<String> r = envoyer("POST", "/api/reservations", USER_1,
                "{\"borneId\":\"" + BORNE_1 + "\"}");
        assertEquals(400, r.statusCode());
    }

    @Test
    void dateMalFormee_renvoie400() throws Exception {
        HttpResponse<String> r = reserver(USER_1, BORNE_1, "demain", "14:00:00");
        assertEquals(400, r.statusCode());
    }

    @Test
    void borneIdInvalide_renvoie400() throws Exception {
        HttpResponse<String> r = reserver(USER_1, "borne-1", date, "14:00:00");
        assertEquals(400, r.statusCode());
        assertTrue(r.body().contains("borneId invalide"));
    }

    // ---------- Vérification de la borne ----------

    @Test
    void borneInconnue_renvoie404_etRienNEstEnregistre() throws Exception {
        HttpResponse<String> r = reserver(USER_1, BORNE_INCONNUE, date, "14:00:00");
        assertEquals(404, r.statusCode());
        assertTrue(r.body().contains("Borne introuvable"));

        assertEquals("[]", envoyer("GET", "/api/reservations", USER_1, null).body());
    }

    @Test
    void serviceBornesEnPanne_renvoie503() throws Exception {
        HttpResponse<String> r = reserver(USER_1, BORNE_EN_PANNE, date, "14:00:00");
        assertEquals(503, r.statusCode());
        assertTrue(r.body().contains("indisponible"));
        assertEquals("[]", envoyer("GET", "/api/reservations", USER_1, null).body());
    }

    @Test
    void serviceBornesTropLent_renvoie503() throws Exception {
        HttpResponse<String> r = reserver(USER_1, BORNE_LENTE, date, "14:00:00");
        assertEquals(503, r.statusCode());
        assertEquals("[]", envoyer("GET", "/api/reservations", USER_1, null).body());
    }

    @Test
    void borneIdEnMajuscules_estNormaliseEnMinuscules() throws Exception {
        HttpResponse<String> r = reserver(USER_1, BORNE_ABC.toUpperCase(), date, "14:00:00");
        assertEquals(201, r.statusCode());
        assertTrue(r.body().contains("\"borneId\":\"" + BORNE_ABC + "\""));

        // La même borne écrite en minuscules doit entrer en conflit (index unique)
        assertEquals(409, reserver(USER_2, BORNE_ABC, date, "14:00:00").statusCode());
    }

    @Test
    void leTokenDeLUtilisateurEstTransmisAuServiceBornes() throws Exception {
        assertEquals(201, reserver(USER_1, BORNE_1, date, "14:00:00").statusCode());
        String transmis = DERNIERE_AUTH.get();
        assertTrue(transmis != null && transmis.startsWith("Bearer "),
                "En-tête Authorization non transmis : " + transmis);
    }

    // ---------- Disponibilités ----------

    @Test
    void disponibilites_listeLesCreneauxDeLaJournee() throws Exception {
        HttpResponse<String> r = envoyer("GET",
                "/api/reservations/disponibilites?borneId=" + BORNE_1 + "&date=" + date, USER_1, null);

        assertEquals(200, r.statusCode());
        // Minuit à 23h30 par pas de 30 minutes = 48 créneaux
        assertEquals(48, r.body().split("\"heureDebut\"", -1).length - 1);
        assertTrue(r.body().contains("\"dureeMinutes\":30"));
    }

    @Test
    void disponibilites_marqueLeCreneauPrisSurLaBorne() throws Exception {
        assertEquals(201, reserver(USER_2, BORNE_1, date, "14:00:00").statusCode());

        HttpResponse<String> r = envoyer("GET",
                "/api/reservations/disponibilites?borneId=" + BORNE_1 + "&date=" + date, USER_1, null);

        assertEquals(200, r.statusCode());
        assertTrue(r.body().contains("BORNE_OCCUPEE"));
    }

    @Test
    void disponibilites_marqueLeCreneauOuLUtilisateurEstDejaPris() throws Exception {
        // L'utilisateur réserve une AUTRE borne sur ce créneau
        assertEquals(201, reserver(USER_1, BORNE_2, date, "14:00:00").statusCode());

        HttpResponse<String> r = envoyer("GET",
                "/api/reservations/disponibilites?borneId=" + BORNE_1 + "&date=" + date, USER_1, null);

        assertEquals(200, r.statusCode());
        assertTrue(r.body().contains("DEJA_RESERVE"));
    }

    @Test
    void disponibilites_borneIdInvalide_renvoie400() throws Exception {
        HttpResponse<String> r = envoyer("GET",
                "/api/reservations/disponibilites?borneId=borne-1&date=" + date, USER_1, null);
        assertEquals(400, r.statusCode());
    }

    // ---------- Isolation entre utilisateurs ----------

    @Test
    void unUtilisateurNeVoitQueSesReservations() throws Exception {
        reserver(USER_1, BORNE_1, date, "14:00:00");

        HttpResponse<String> liste1 = envoyer("GET", "/api/reservations", USER_1, null);
        assertEquals(200, liste1.statusCode());
        assertTrue(liste1.body().contains(BORNE_1));

        HttpResponse<String> liste2 = envoyer("GET", "/api/reservations", USER_2, null);
        assertEquals(200, liste2.statusCode());
        assertEquals("[]", liste2.body());
    }

    @Test
    void consulterLaReservationDUnAutre_renvoie404() throws Exception {
        String id = extraireId(reserver(USER_1, BORNE_1, date, "14:00:00").body());

        assertEquals(200, envoyer("GET", "/api/reservations/" + id, USER_1, null).statusCode());
        assertEquals(404, envoyer("GET", "/api/reservations/" + id, USER_2, null).statusCode());
    }

    // ---------- Annulation ----------

    @Test
    void annulation_changeLeStatut() throws Exception {
        String id = extraireId(reserver(USER_1, BORNE_1, date, "14:00:00").body());

        HttpResponse<String> r = envoyer("PATCH", "/api/reservations/" + id + "/annulation", USER_1, null);
        assertEquals(200, r.statusCode());
        assertTrue(r.body().contains("\"statut\":\"ANNULEE\""));
    }

    @Test
    void annulerLaReservationDUnAutre_renvoie404() throws Exception {
        String id = extraireId(reserver(USER_1, BORNE_1, date, "14:00:00").body());

        HttpResponse<String> r = envoyer("PATCH", "/api/reservations/" + id + "/annulation", USER_2, null);
        assertEquals(404, r.statusCode());

        // la réservation de l'utilisateur 1 est restée confirmée
        HttpResponse<String> verif = envoyer("GET", "/api/reservations/" + id, USER_1, null);
        assertTrue(verif.body().contains("\"statut\":\"CONFIRMEE\""));
    }

    @Test
    void apresAnnulation_leCreneauEstLibere() throws Exception {
        String id = extraireId(reserver(USER_1, BORNE_1, date, "14:00:00").body());
        envoyer("PATCH", "/api/reservations/" + id + "/annulation", USER_1, null);

        // Les index uniques ne s'appliquent qu'aux réservations CONFIRMEE
        assertEquals(201, reserver(USER_1, BORNE_1, date, "14:00:00").statusCode());
    }

        // ---------- Suppression ----------

    @Test
    void supprimerUneReservationAnnulee_renvoie204() throws Exception {
        String id = extraireId(reserver(USER_1, BORNE_1, date, "14:00:00").body());
        envoyer("PATCH", "/api/reservations/" + id + "/annulation", USER_1, null);

        HttpResponse<String> r = envoyer("DELETE", "/api/reservations/" + id, USER_1, null);
        assertEquals(204, r.statusCode());

        assertEquals("[]", envoyer("GET", "/api/reservations", USER_1, null).body());
    }

    @Test
    void supprimerUneReservationConfirmee_renvoie409() throws Exception {
        String id = extraireId(reserver(USER_1, BORNE_1, date, "14:00:00").body());

        HttpResponse<String> r = envoyer("DELETE", "/api/reservations/" + id, USER_1, null);
        assertEquals(409, r.statusCode());
        assertTrue(r.body().contains("Annulez la réservation"));
    }

    @Test
    void supprimerLaReservationDUnAutre_renvoie404() throws Exception {
        String id = extraireId(reserver(USER_1, BORNE_1, date, "14:00:00").body());
        envoyer("PATCH", "/api/reservations/" + id + "/annulation", USER_1, null);

        assertEquals(404, envoyer("DELETE", "/api/reservations/" + id, USER_2, null).statusCode());
    }
}