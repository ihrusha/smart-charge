
package com.smartcharge.bornes.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

@Document(collection = "bornes")
public class Borne {

    @Id
    private String id;

    private String nom;
    private String adresse;
    private String ville;

    private double latitude;
    private double longitude;

    private double puissance;
    private String typeConnecteur;
    private String statut = "DISPONIBLE";


    public Borne() {
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getNom() {
        return nom;
    }

    public void setNom(String nom) {
        this.nom = nom;
    }

    public String getAdresse() {
        return adresse;
    }

    public void setAdresse(String adresse) {
        this.adresse = adresse;
    }

    public String getVille() {
        return ville;
    }

    public void setVille(String ville) {
        this.ville = ville;
    }

    public double getLatitude() {
        return latitude;
    }

    public void setLatitude(double latitude) {
        this.latitude = latitude;
    }

    public double getLongitude() {
        return longitude;
    }

    public void setLongitude(double longitude) {
        this.longitude = longitude;
    }

    public double getPuissance() {
        return puissance;
    }

    public void setPuissance(double puissance) {
        this.puissance = puissance;
    }

    public String getTypeConnecteur() {
        return typeConnecteur;
    }

    public void setTypeConnecteur(String typeConnecteur) {
        this.typeConnecteur = typeConnecteur;
    }

    public String getStatut() {
        return statut;
    }

    public void setStatut(String statut) {
        if (statut == null) {
            this.statut = "DISPONIBLE";
        } else if (
            statut.equals("DISPONIBLE") ||
            statut.equals("HORS_SERVICE")
        ) {
            this.statut = statut;
        } else {
            throw new IllegalArgumentException(
                "Statut invalide : " + statut
             );    
    }
}
