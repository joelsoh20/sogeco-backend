package com.sogeco.fleet.modules.agency;

import com.sogeco.fleet.common.entity.SoftDeletableEntity;
import com.sogeco.fleet.common.enums.SiteType;
import com.sogeco.fleet.modules.city.City;
import com.sogeco.fleet.modules.quartier.Quartier;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;

/**
 * Implantation SOGECO : siege, agence ou depot.
 *
 * Les coordonnees sont relevees sur Google Maps puis saisies via le
 * formulaire (collage d'un couple, clic sur carte, ou saisie manuelle).
 */
@Entity
@Table(name = "agencies")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Agency extends SoftDeletableEntity {

    @Column(name = "code", nullable = false, length = 20, unique = true)
    private String code;

    @Column(name = "name", nullable = false, length = 120)
    private String name;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "city_id", nullable = false)
    private City city;

    /** Nullable : seuls les sites crees apres l'introduction des quartiers en ont un. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "quartier_id")
    private Quartier quartier;

    @Enumerated(EnumType.STRING)
    @Column(name = "site_type", nullable = false, length = 20)
    private SiteType siteType;

    @Column(name = "address", length = 255)
    private String address;

    @Column(name = "phone", length = 30)
    private String phone;

    @Column(name = "latitude", precision = 10, scale = 7)
    private BigDecimal latitude;

    @Column(name = "longitude", precision = 10, scale = 7)
    private BigDecimal longitude;
}
