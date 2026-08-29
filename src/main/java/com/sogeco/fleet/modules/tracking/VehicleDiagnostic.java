package com.sogeco.fleet.modules.tracking;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Donnees moteur remontees par le bus CAN / OBD-II.
 *
 * Table separee des positions : ces valeurs arrivent moins souvent et
 * n'auraient aucun sens repliquees a chaque trame. Elle reste vide si
 * le boitier ne lit pas le bus CAN, sans que rien ne casse.
 */
@Entity
@Table(name = "vehicle_diagnostics")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class VehicleDiagnostic {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "vehicle_id", nullable = false)
    private Long vehicleId;

    @Column(name = "recorded_at", nullable = false)
    private Instant recordedAt;

    @Column(name = "engine_temperature", precision = 6, scale = 2)
    private BigDecimal engineTemperature;

    @Column(name = "engine_rpm")
    private Integer engineRpm;

    @Column(name = "battery_voltage", precision = 6, scale = 2)
    private BigDecimal batteryVoltage;

    @Column(name = "engine_hours", precision = 10, scale = 2)
    private BigDecimal engineHours;

    /** Codes defaut au format JSON, tels que remontes par le boitier. */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "error_codes", columnDefinition = "jsonb")
    private String errorCodes;

    @Builder.Default
    @Column(name = "dtc_count", nullable = false)
    private Integer dtcCount = 0;

    @Builder.Default
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    public boolean hasFault() {
        return dtcCount != null && dtcCount > 0;
    }
}
