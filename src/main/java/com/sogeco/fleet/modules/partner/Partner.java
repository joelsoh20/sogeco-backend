package com.sogeco.fleet.modules.partner;

import com.sogeco.fleet.common.entity.SoftDeletableEntity;
import com.sogeco.fleet.common.enums.PartnerType;
import com.sogeco.fleet.modules.city.City;
import jakarta.persistence.*;
import lombok.*;

/**
 * Prestataire externe : garage, station-service, assureur, centre de
 * visite technique.
 *
 * Une seule table plutot qu'une par nature : ils partagent les memes
 * attributs, et le comparatif "par prestataire" traverse les types.
 */
@Entity
@Table(name = "partners")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Partner extends SoftDeletableEntity {

    @Column(name = "code", length = 20, unique = true)
    private String code;

    @Column(name = "name", nullable = false, length = 150)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "partner_type", nullable = false, length = 30)
    private PartnerType partnerType;

    @Column(name = "contact_name", length = 120)
    private String contactName;

    @Column(name = "phone", length = 30)
    private String phone;

    @Column(name = "email", length = 150)
    private String email;

    @Column(name = "address", length = 255)
    private String address;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "city_id")
    private City city;

    @Column(name = "tax_number", length = 50)
    private String taxNumber;

    @Column(name = "notes", length = 500)
    private String notes;
}
