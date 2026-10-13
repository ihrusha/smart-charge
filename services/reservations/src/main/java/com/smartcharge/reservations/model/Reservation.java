package com.smartcharge.reservations.model;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.mapping.Document;

@Document(collection = "reservations")
@CompoundIndexes({
    @CompoundIndex(
        name = "uniq_borne_creneau",
        def = "{'borneId': 1, 'date': 1, 'heureDebut': 1}",
        unique = true,
        partialFilter = "{'statut': 'CONFIRMEE'}"),
    @CompoundIndex(
        name = "uniq_user_creneau",
        def = "{'userId': 1, 'date': 1, 'heureDebut': 1}",
        unique = true,
        partialFilter = "{'statut': 'CONFIRMEE'}")
})
public class Reservation {

    @Id
    private String id;
    private String borneId;
    private String userId;
    private LocalDate date;
    private LocalTime heureDebut;
    private Statut statut;
    private Instant creeLe;

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getBorneId() { return borneId; }
    public void setBorneId(String borneId) { this.borneId = borneId; }

    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }

    public LocalDate getDate() { return date; }
    public void setDate(LocalDate date) { this.date = date; }

    public LocalTime getHeureDebut() { return heureDebut; }
    public void setHeureDebut(LocalTime heureDebut) { this.heureDebut = heureDebut; }

    public Statut getStatut() { return statut; }
    public void setStatut(Statut statut) { this.statut = statut; }

    public Instant getCreeLe() { return creeLe; }
    public void setCreeLe(Instant creeLe) { this.creeLe = creeLe; }
}
