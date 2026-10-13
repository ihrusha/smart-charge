package com.smartcharge.reservations.repository;

import org.springframework.data.mongodb.repository.MongoRepository;

import com.smartcharge.reservations.model.Reservation;

public interface ReservationRepository extends MongoRepository<Reservation, String> {
}
