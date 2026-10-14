
package com.smartcharge.bornes.controller;

import com.smartcharge.bornes.model.Borne;
import com.smartcharge.bornes.service.BorneService;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/bornes")
public class BorneController {

    private final BorneService borneService;

    public BorneController(BorneService borneService) {
        this.borneService = borneService;
    }

    // Récupérer toutes les bornes ou appliquer des filtres
    @GetMapping
    public List<Borne> getBornes(
            @RequestParam(required = false) String ville,
            @RequestParam(required = false) Double puissanceMin,
            @RequestParam(required = false) String typeConnecteur) {

        return borneService.rechercherBornes(
                ville,
                puissanceMin,
                typeConnecteur
        );
    }

    // Récupérer une borne par son identifiant
    @GetMapping("/{id}")
    public ResponseEntity<Borne> getBorneById(
            @PathVariable String id) {

        return borneService.getBorneById(id)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }
}
