package com.sogeco.fleet.modules.partner.dto;

import com.sogeco.fleet.common.enums.PartnerType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.*;

@Schema(description = "Garage, station-service, assureur ou centre de visite")
public record PartnerRequest(

        @Size(max = 20)
        String code,

        @NotBlank(message = "le nom est obligatoire")
        @Size(max = 150)
        @Schema(example = "Garage Central Douala")
        String name,

        @NotNull(message = "le type de partenaire est obligatoire")
        PartnerType partnerType,

        @Size(max = 120) String contactName,
        @Size(max = 30)  String phone,
        @Email(message = "format d'adresse invalide") @Size(max = 150) String email,
        @Size(max = 255) String address,
        Long cityId,
        @Size(max = 50)  String taxNumber,
        @Size(max = 500) String notes
) {
}
