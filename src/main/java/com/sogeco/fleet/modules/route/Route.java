package com.sogeco.fleet.modules.route;

import com.sogeco.fleet.common.entity.SoftDeletableEntity;
import com.sogeco.fleet.modules.city.City;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Corridor inter-agences.
 *
 * Deux usages, tous deux structurants :
 *   - referentiel de cout standard : un ecart de consommation devient
 *     detectable sans analyse manuelle ;
 *   - detection de deviation d'itineraire par comparaison au trace
 *     stocke, sans appel a un service de calcul a chaque position.
 *
 * Les valeurs de reference sont RELEVEES SUR LES TRAJETS REELS.
 */
@Entity
@Table(name = "routes")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Route extends SoftDeletableEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "origin_city_id", nullable = false)
    private City originCity;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "destination_city_id", nullable = false)
    private City destinationCity;

    @Column(name = "label", nullable = false, length = 120)
    private String label;

    @Column(name = "reference_distance_km", precision = 10, scale = 2)
    private BigDecimal referenceDistanceKm;

    @Column(name = "reference_duration_minutes")
    private Integer referenceDurationMinutes;

    @Column(name = "reference_fuel_liters", precision = 8, scale = 2)
    private BigDecimal referenceFuelLiters;

    /** Trace de l'axe, pour la detection de sortie de corridor (sprint 5). */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "corridor_geojson", columnDefinition = "jsonb")
    private String corridorGeojson;

    @Builder.Default
    @Column(name = "tolerance_km", nullable = false, precision = 6, scale = 2)
    private BigDecimal toleranceKm = BigDecimal.valueOf(5);

    // ------------------------------------------------------------------

    /** Consommation de reference du corridor, en L/100 km. */
    public BigDecimal referenceConsumption() {
        if (referenceFuelLiters == null || referenceDistanceKm == null
                || referenceDistanceKm.signum() == 0) {
            return null;
        }
        return referenceFuelLiters
                .multiply(BigDecimal.valueOf(100))
                .divide(referenceDistanceKm, 2, RoundingMode.HALF_UP);
    }

    /**
     * Ecart en pourcentage entre une consommation constatee et la
     * reference. Positif si le camion a consomme davantage.
     */
    public BigDecimal deviationPercent(BigDecimal actualLiters) {
        if (referenceFuelLiters == null || actualLiters == null
                || referenceFuelLiters.signum() == 0) {
            return null;
        }
        return actualLiters.subtract(referenceFuelLiters)
                .multiply(BigDecimal.valueOf(100))
                .divide(referenceFuelLiters, 2, RoundingMode.HALF_UP);
    }

    public String buildLabel() {
        return "%s → %s".formatted(originCity.getName(), destinationCity.getName());
    }
}
