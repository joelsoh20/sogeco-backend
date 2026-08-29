package com.sogeco.fleet.modules.city;

import com.sogeco.fleet.common.entity.SoftDeletableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.*;

import java.math.BigDecimal;

/**
 * Ville. Referentiel ouvert : toute ville de livraison rencontree y est
 * creee, avec ses coordonnees issues du geocodage.
 * has_site n'est vrai que pour Douala, Yaounde et Bafoussam.
 */
@Entity
@Table(name = "cities")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class City extends SoftDeletableEntity {

    @Column(name = "code", nullable = false, length = 10, unique = true)
    private String code;

    @Column(name = "name", nullable = false, length = 100)
    private String name;

    @Column(name = "region", length = 100)
    private String region;

    @Column(name = "latitude", precision = 10, scale = 7)
    private BigDecimal latitude;

    @Column(name = "longitude", precision = 10, scale = 7)
    private BigDecimal longitude;

    @Builder.Default
    @Column(name = "has_site", nullable = false)
    private Boolean hasSite = Boolean.FALSE;
}
