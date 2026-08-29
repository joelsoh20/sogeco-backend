package com.sogeco.fleet.modules.client;

import com.sogeco.fleet.common.entity.SoftDeletableEntity;
import com.sogeco.fleet.modules.city.City;
import jakarta.persistence.*;
import lombok.*;

/**
 * Donneur d'ordre.
 *
 * SOGECO facture le transport a ses clients (decision D12) : le client
 * est donc un axe d'analyse a part entiere, avec son chiffre d'affaires
 * et sa marge, comme sur l'ecran Rapports.
 */
@Entity
@Table(name = "clients")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Client extends SoftDeletableEntity {

    @Column(name = "code", nullable = false, length = 20, unique = true)
    private String code;

    @Column(name = "company_name", nullable = false, length = 150)
    private String companyName;

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

    @Builder.Default
    @Column(name = "payment_terms_days", nullable = false)
    private Integer paymentTermsDays = 30;

    @Column(name = "notes", length = 500)
    private String notes;
}
