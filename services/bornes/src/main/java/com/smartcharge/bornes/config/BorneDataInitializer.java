package com.smartcharge.bornes.config;

import com.smartcharge.bornes.model.Borne;
import com.smartcharge.bornes.repository.BorneRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.ArrayList;

@Component
public class BorneDataInitializer implements CommandLineRunner {

    private final BorneRepository repository;

    public BorneDataInitializer(BorneRepository repository) {
        this.repository = repository;
    }

    @Override
    public void run(String... args) {

        if (repository.count() > 0) {
            System.out.println("Les bornes existent déjà.");
            return;
        }

        List<Borne> bornes = new ArrayList<>();

        String[] villes = {
            "Paris", "Versailles", "Guyancourt",
            "Montigny-le-Bretonneux", "Puteaux",
            "Nanterre"
        };

        double[][] coordonnees = {
            {48.8566, 2.3522},
            {48.8014, 2.1301},
            {48.7719, 2.0737},
            {48.7711, 2.0334},
            {48.8847, 2.2380},
            {48.8924, 2.2066}
        };

        for (int i = 0; i < 30; i++) {

            int index = i % villes.length;

            Borne borne = new Borne();

            borne.setNom("Borne Smart Charge " + (i + 1));
            borne.setVille(villes[index]);
            borne.setAdresse("Adresse fictive " + (i + 1));

            borne.setLatitude(
                coordonnees[index][0] + (i / 6) * 0.001
            );
            borne.setLongitude(
                coordonnees[index][1] + (i / 6) * 0.001
            );

            borne.setPuissance(i % 2 == 0 ? 22 : 50);
            borne.setTypeConnecteur(i % 2 == 0 ? "Type 2" : "CCS");

            bornes.add(borne);
        }

        repository.saveAll(bornes);

        System.out.println("30 bornes ajoutées dans MongoDB !");
    }
}
