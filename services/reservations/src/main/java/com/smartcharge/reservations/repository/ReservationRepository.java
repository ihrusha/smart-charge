package com.smartcharge.reservations.repository;

import java.time.LocalDate;
import java.util.List;

import org.springframework.data.mongodb.repository.MongoRepository;

import com.smartcharge.reservations.model.Reservation;
import com.smartcharge.reservations.model.Statut;

public interface ReservationRepository extends MongoRepository<Reservation, String> {

    List<Reservation> findByUserIdOrderByDateAscHeureDebutAsc(String userId);

    List<Reservation> findByBorneIdAndDateAndStatut(String borneId, LocalDate date, Statut statut);

    List<Reservation> findByUserIdAndDateAndStatut(String userId, LocalDate date, Statut statut);
}