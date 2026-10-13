package com.smartcharge.reservations.controller;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import com.smartcharge.reservations.model.Reservation;
import com.smartcharge.reservations.model.Statut;
import com.smartcharge.reservations.repository.ReservationRepository;

@RestController
@RequestMapping("/api/reservations")
public class ReservationController {

    private static final ZoneId ZONE = ZoneId.of("Europe/Paris");
    private static final int BORNE_ID_MAX = 50;

    public record CreateReservationRequest(String borneId, LocalDate date, LocalTime heureDebut) {}

    private final ReservationRepository repository;

    public ReservationController(ReservationRepository repository) {
        this.repository = repository;
    }

    @GetMapping
    public List<Reservation> list(@RequestAttribute("userId") String userId) {
        return repository.findByUserIdOrderByDateAscHeureDebutAsc(userId);
    }

    @GetMapping("/{id}")
    public Reservation get(@PathVariable String id,
                           @RequestAttribute("userId") String userId) {
        return findOwned(id, userId);
    }

    @PostMapping
    public ResponseEntity<?> create(@RequestBody CreateReservationRequest body,
                                    @RequestAttribute("userId") String userId) {
        String erreur = valider(body);
        if (erreur != null) {
            return ResponseEntity.badRequest().body(Map.of("error", erreur));
        }

        Reservation r = new Reservation();
        r.setBorneId(body.borneId().trim());
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
        if (body.borneId().trim().length() > BORNE_ID_MAX) {
            return "borneId trop long (" + BORNE_ID_MAX + " caractères maximum)";
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