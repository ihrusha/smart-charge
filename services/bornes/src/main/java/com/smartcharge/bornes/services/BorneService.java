
package com.smartcharge.bornes.service;

import com.smartcharge.bornes.model.Borne;
import com.smartcharge.bornes.repository.BorneRepository;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
public class BorneService {

    private final BorneRepository borneRepository;

    public BorneService(BorneRepository borneRepository) {
        this.borneRepository = borneRepository;
    }

    public List<Borne> getAllBornes() {
        return borneRepository.findAll();
    }

    public Optional<Borne> getBorneById(String id) {
        return borneRepository.findById(id);
    }

    public List<Borne> rechercherBornes(
            String ville,
            Double puissanceMin,
            String typeConnecteur) {

        return borneRepository.findAll().stream()
                .filter(b -> ville == null ||
                        b.getVille().equalsIgnoreCase(ville))
                .filter(b -> puissanceMin == null ||
                        b.getPuissance() >= puissanceMin)
                .filter(b -> typeConnecteur == null ||
                        b.getTypeConnecteur()
                         .equalsIgnoreCase(typeConnecteur))
                .toList();
    }
}
