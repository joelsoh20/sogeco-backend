package com.sogeco.fleet.modules.client;

import com.sogeco.fleet.common.entity.SoftDeletableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.*;

/**
 * Type de prestation.
 *
 * billable distingue une livraison client facturee d'un simple
 * reapprovisionnement interne : seul le premier porte un chiffre
 * d'affaires.
 */
@Entity
@Table(name = "service_types")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ServiceType extends SoftDeletableEntity {

    public static final String LIVRAISON_CLIENT    = "LIVRAISON_CLIENT";
    public static final String REAPPROVISIONNEMENT = "REAPPROVISIONNEMENT";
    public static final String TRANSFERT           = "TRANSFERT";
    public static final String VOYAGE_HORS_VILLE   = "VOYAGE_HORS_VILLE";

    @Column(name = "code", nullable = false, length = 40, unique = true)
    private String code;

    @Column(name = "label", nullable = false, length = 120)
    private String label;

    @Column(name = "description", length = 255)
    private String description;

    @Builder.Default
    @Column(name = "billable", nullable = false)
    private Boolean billable = Boolean.TRUE;
}
