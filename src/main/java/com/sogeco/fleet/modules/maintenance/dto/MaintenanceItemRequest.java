package com.sogeco.fleet.modules.maintenance.dto;

import com.sogeco.fleet.common.enums.MaintenanceItemType;
import jakarta.validation.constraints.*;

import java.math.BigDecimal;

public record MaintenanceItemRequest(

        @NotNull(message = "le type de ligne est obligatoire")
        MaintenanceItemType itemType,

        @NotBlank(message = "la designation est obligatoire")
        @Size(max = 150)
        String label,

        @NotNull @Positive
        BigDecimal quantity,

        @NotNull @PositiveOrZero
        BigDecimal unitPrice
) {
}
