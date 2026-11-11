package com.sogeco.fleet.modules.oil;

import com.sogeco.fleet.common.entity.BaseEntity;
import com.sogeco.fleet.modules.city.City;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;

/** Approvisionnement en huile moteur (achat d'un fut) pour une ville : date et quantite en litres. */
@Entity
@Table(name = "oil_purchases")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OilPurchase extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "city_id", nullable = false)
    private City city;

    @Column(name = "purchase_date", nullable = false)
    private LocalDate purchaseDate;

    @Column(name = "quantity_liters", nullable = false, precision = 8, scale = 2)
    private BigDecimal quantityLiters;

    @Column(name = "note", length = 200)
    private String note;
}
