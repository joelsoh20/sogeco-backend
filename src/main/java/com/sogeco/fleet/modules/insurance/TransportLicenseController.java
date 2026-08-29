package com.sogeco.fleet.modules.insurance;

import com.sogeco.fleet.common.dto.PageResponse;
import com.sogeco.fleet.modules.insurance.dto.TransportLicenseRequest;
import com.sogeco.fleet.modules.insurance.dto.TransportLicenseResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/transport-licenses")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Licence de transport", description = "Autorisation couvrant l'ensemble de la flotte")
public class TransportLicenseController {

    private final TransportLicenseService service;

    @GetMapping
    @Operation(summary = "Lister les licences de transport")
    public PageResponse<TransportLicenseResponse> list(
            @PageableDefault(size = 20, sort = "expiryDate", direction = Sort.Direction.DESC)
            Pageable pageable) {
        return service.list(pageable);
    }

    @PostMapping
    @Operation(summary = "Enregistrer une licence de transport")
    public TransportLicenseResponse create(@Valid @RequestBody TransportLicenseRequest request) {
        return service.create(request);
    }
}
