package com.sogeco.fleet.modules.quartier;

import com.sogeco.fleet.common.entity.SoftDeletableEntity;
import com.sogeco.fleet.modules.city.City;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;

/**
 * Quartier (arrondissement/zone) d'une ville. Referentiel ouvert, comme
 * les villes : tout quartier de livraison rencontre y est cree via
 * l'ecran Missions, avec ses coordonnees geocodees automatiquement si
 * elles ne sont pas saisies a la main.
 *
 * Sert a affiner la distance estimee d'un voyage/livraison au-dela du
 * seul centre-ville (voir MissionService.resolveCoordinates).
 */
@Entity
@Table(name = "quartiers")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Quartier extends SoftDeletableEntity {

    @Column(name = "name", nullable = false, length = 100)
    private String name;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "city_id", nullable = false)
    private City city;

    @Column(name = "latitude", precision = 10, scale = 7)
    private BigDecimal latitude;

    @Column(name = "longitude", precision = 10, scale = 7)
    private BigDecimal longitude;
}
