package com.sogeco.fleet.modules.alert.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record AlertResolveRequest(

        @NotBlank(message = "une note de resolution est obligatoire")
        @Size(max = 500)
        String note
) {
}
