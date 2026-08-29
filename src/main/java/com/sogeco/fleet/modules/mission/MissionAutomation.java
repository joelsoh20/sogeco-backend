package com.sogeco.fleet.modules.mission;

import com.sogeco.fleet.common.entity.BaseEntity;
import com.sogeco.fleet.modules.agency.Agency;
import com.sogeco.fleet.modules.city.City;
import com.sogeco.fleet.modules.client.Client;
import com.sogeco.fleet.modules.client.ServiceType;
import com.sogeco.fleet.modules.driver.Driver;
import com.sogeco.fleet.modules.quartier.Quartier;
import com.sogeco.fleet.modules.vehicle.Vehicle;
import jakarta.persistence.*;
import lombok.*;

/**
 * Modele de livraison quotidienne recurrente — une livraison qui se
 * fait generalement tous les jours dans une meme ville, avec le meme
 * camion/chauffeur et le meme trajet (site de depart -> quartier de
 * livraison).
 *
 * MissionAutomationScheduler genere une Mission a partir de ce modele
 * chaque jour a 9h30, tant que active reste vrai. Un jour ou la
 * livraison n'est pas necessaire, il suffit d'annuler la mission du
 * jour (mecanisme d'annulation deja existant) : l'automatisation
 * elle-meme continue de tourner les jours suivants. deactivate() est
 * la seule facon d'arreter definitivement la generation.
 */
@Entity
@Table(name = "mission_automations")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MissionAutomation extends BaseEntity {

    @Column(name = "label", length = 150)
    private String label;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "city_id", nullable = false)
    private City city;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "service_type_id", nullable = false)
    private ServiceType serviceType;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "client_id")
    private Client client;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "vehicle_id", nullable = false)
    private Vehicle vehicle;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "driver_id", nullable = false)
    private Driver driver;

    /** Site de depart. */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "agency_id", nullable = false)
    private Agency agency;

    /** Point de livraison quotidien. */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "destination_quartier_id", nullable = false)
    private Quartier destinationQuartier;

    @Column(name = "cargo_description", length = 255)
    private String cargoDescription;

    @Builder.Default
    @Column(name = "active", nullable = false)
    private Boolean active = Boolean.TRUE;

    public void deactivate() {
        this.active = Boolean.FALSE;
    }
}
