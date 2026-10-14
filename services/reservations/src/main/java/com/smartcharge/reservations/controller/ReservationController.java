package com.smartcharge.reservations.controller;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import com.smartcharge.reservations.client.BorneClient;
import com.smartcharge.reservations.model.Reservation;
import com.smartcharge.reservations.model.Statut;
import com.smartcharge.reservations.repository.ReservationRepository;

@RestController
@RequestMapping("/api/reservations")
public class ReservationController {

    private static final ZoneId ZONE = ZoneId.of("Europe/Paris");
    // Identifiant MongoDB (ObjectId) : 24 caractères hexadécimaux
    private static final Pattern OBJECT_ID = Pattern.compile("^[a-fA-F0-9]{24}$");
    private static final LocalTime OUVERTURE = LocalTime.MIDNIGHT;
    private static final Duration DUREE_CRENEAU = Duration.ofMinutes(30);

    public record CreateReservationRequest(String borneId, LocalDate date, LocalTime heureDebut) {}
    public record Creneau(String heureDebut, boolean disponible, String motif) {}

    private final ReservationRepository repository;
    private final BorneClient borneClient;

    public ReservationController(ReservationRepository repository, BorneClient borneClient) {
        this.repository = repository;
        this.borneClient = borneClient;
    }

    @GetMapping
    public List<Reservation> list(@RequestAttribute("userId") String userId) {
        return repository.findByUserIdOrderByDateAscHeureDebutAsc(userId);
    }

    @GetMapping("/disponibilites")
    public ResponseEntity<?> disponibilites(
            @RequestParam String borneId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestAttribute("userId") String userId) {

        if (!OBJECT_ID.matcher(borneId.trim()).matches()) {
            return ResponseEntity.badRequest().body(Map.of("error",
                    "borneId invalide : identifiant de borne attendu (24 caractères hexadécimaux)"));
        }

        String borne = borneId.trim().toLowerCase(Locale.ROOT);

        // Créneaux déjà pris sur cette borne, et créneaux où l'utilisateur est déjà occupé
        Set<LocalTime> borneOccupee = repository
                .findByBorneIdAndDateAndStatut(borne, date, Statut.CONFIRMEE)
                .stream().map(Reservation::getHeureDebut)
                .collect(Collectors.toCollection(HashSet::new));

        Set<LocalTime> utilisateurOccupe = repository
                .findByUserIdAndDateAndStatut(userId, date, Statut.CONFIRMEE)
                .stream().map(Reservation::getHeureDebut)
                .collect(Collectors.toCollection(HashSet::new));

        LocalDateTime maintenant = LocalDateTime.now(ZONE);
        List<Creneau> creneaux = new ArrayList<>();

        LocalTime heure = OUVERTURE;
        do {
            String motif = null;
            if (!LocalDateTime.of(date, heure).isAfter(maintenant)) {
                motif = "PASSE";
            } else if (borneOccupee.contains(heure)) {
                motif = "BORNE_OCCUPEE";
            } else if (utilisateurOccupe.contains(heure)) {
                motif = "DEJA_RESERVE";
            }

            creneaux.add(new Creneau(heure.toString(), motif == null, motif));
            heure = heure.plus(DUREE_CRENEAU);
        } while (!heure.equals(OUVERTURE));

        return ResponseEntity.ok(Map.of(
                "borneId", borne,
                "date", date.toString(),
                "dureeMinutes", DUREE_CRENEAU.toMinutes(),
                "creneaux", creneaux));
    }

    @GetMapping("/{id}")
    public Reservation get(@PathVariable String id,
                           @RequestAttribute("userId") String userId) {
        return findOwned(id, userId);
    }

    @PostMapping
    public ResponseEntity<?> create(@RequestBody CreateReservationRequest body,
                                    @RequestAttribute("userId") String userId,
                                    @RequestHeader("Authorization") String authorization) {
        String erreur = valider(body);
        if (erreur != null) {
            return ResponseEntity.badRequest().body(Map.of("error", erreur));
        }

        // Minuscules : sinon "ABC..." et "abc..." contourneraient les index uniques
        String borneId = body.borneId().trim().toLowerCase(Locale.ROOT);

        BorneClient.Resultat borne = borneClient.verifier(borneId, authorization);
        if (borne == BorneClient.Resultat.INTROUVABLE) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("error", "Borne introuvable"));
        }
        if (borne == BorneClient.Resultat.INDISPONIBLE) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body(Map.of("error", "Service bornes indisponible, réessayez plus tard"));
        }

        Reservation r = new Reservation();
        r.setBorneId(borneId);
        r.setUserId(userId);
        r.setDate(body.date());
        r.setHeureDebut(body.heureDebut());
        r.setStatut(Statut.CONFIRMEE);
        r.setCreeLe(Instant.now());

        try {
            return ResponseEntity.status(HttpStatus.CREATED).body(repository.save(r));
        } catch (DuplicateKeyException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(Map.of("error", "Ce créneau est déjà réservé (borne ou utilisateur)"));
        }
    }

    @PatchMapping("/{id}/annulation")
    public Reservation cancel(@PathVariable String id,
                              @RequestAttribute("userId") String userId) {
        Reservation r = findOwned(id, userId);
        r.setStatut(Statut.ANNULEE);
        return repository.save(r);
    }

    // Renvoie un message d'erreur, ou null si la requête est valide.
    private String valider(CreateReservationRequest body) {
        if (body.borneId() == null || body.borneId().isBlank()
                || body.date() == null || body.heureDebut() == null) {
            return "borneId, date et heureDebut sont obligatoires";
        }
        if (!OBJECT_ID.matcher(body.borneId().trim()).matches()) {
            return "borneId invalide : identifiant de borne attendu (24 caractères hexadécimaux)";
        }
        int minute = body.heureDebut().getMinute();
        if ((minute != 0 && minute != 30)
                || body.heureDebut().getSecond() != 0
                || body.heureDebut().getNano() != 0) {
            return "heureDebut doit commencer à :00 ou :30 (ex. 14:00:00 ou 14:30:00)";
        }
        LocalDateTime debut = LocalDateTime.of(body.date(), body.heureDebut());
        if (!debut.isAfter(LocalDateTime.now(ZONE))) {
            return "Le créneau doit être dans le futur";
        }
        return null;
    }

    private Reservation findOwned(String id, String userId) {
        return repository.findById(id)
                .filter(r -> userId.equals(r.getUserId()))
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Réservation introuvable"));
    }
}