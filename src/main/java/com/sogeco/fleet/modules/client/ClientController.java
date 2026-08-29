package com.sogeco.fleet.modules.client;

import com.sogeco.fleet.common.dto.PageResponse;
import com.sogeco.fleet.modules.client.dto.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.net.URI;
import java.util.List;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Clients et tarifs", description = "Donneurs d'ordre, prestations et grille tarifaire")
public class ClientController {

    private final ClientService clientService;
    private final TariffService tariffService;

    // ---- Clients ----

    @GetMapping("/clients")
    @Operation(summary = "Lister les clients")
    public PageResponse<ClientResponse> list(
            @PageableDefault(size = 20, sort = "companyName", direction = Sort.Direction.ASC) Pageable pageable) {
        return clientService.list(pageable);
    }

    @GetMapping("/clients/active")
    @Operation(summary = "Clients actifs, pour les listes deroulantes")
    public List<ClientResponse> listActive() {
        return clientService.listActive();
    }

    @GetMapping("/clients/search")
    @Operation(summary = "Rechercher par raison sociale")
    public List<ClientResponse> search(@RequestParam String q) {
        return clientService.search(q);
    }

    @GetMapping("/clients/{id}")
    @Operation(summary = "Consulter un client")
    public ClientResponse get(@PathVariable Long id) {
        return clientService.get(id);
    }

    @PostMapping("/clients")
    @Operation(summary = "Creer un client")
    public ResponseEntity<ClientResponse> create(@Valid @RequestBody ClientRequest request) {
        ClientResponse created = clientService.create(request);
        return ResponseEntity.created(URI.create("/api/v1/clients/" + created.id())).body(created);
    }

    @PutMapping("/clients/{id}")
    @Operation(summary = "Modifier un client")
    public ClientResponse update(@PathVariable Long id, @Valid @RequestBody ClientRequest request) {
        return clientService.update(id, request);
    }

    @DeleteMapping("/clients/{id}")
    @Operation(summary = "Desactiver un client")
    public ResponseEntity<Void> deactivate(@PathVariable Long id) {
        clientService.deactivate(id);
        return ResponseEntity.noContent().build();
    }

    // ---- Types de prestation ----

    @GetMapping("/service-types")
    @Operation(summary = "Lister les types de prestation")
    public List<ServiceTypeResponse> serviceTypes() {
        return clientService.listServiceTypes();
    }

    // ---- Tarifs ----

    @GetMapping("/tariffs")
    @Operation(summary = "Lister la grille tarifaire")
    public List<TariffResponse> tariffs() {
        return tariffService.list();
    }

    @GetMapping("/clients/{id}/tariffs")
    @Operation(summary = "Tarifs negocies d'un client")
    public List<TariffResponse> tariffsOfClient(@PathVariable Long id) {
        return tariffService.listForClient(id);
    }

    @PostMapping("/tariffs")
    @Operation(summary = "Creer une ligne de tarif")
    public TariffResponse createTariff(@Valid @RequestBody TariffRequest request) {
        return tariffService.create(request);
    }

    @DeleteMapping("/tariffs/{id}")
    @Operation(summary = "Desactiver un tarif")
    public ResponseEntity<Void> deactivateTariff(@PathVariable Long id) {
        tariffService.deactivate(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/tariffs/preview")
    @Operation(summary = "Montant propose par la grille, avant saisie")
    public TariffPreviewResponse preview(@RequestParam(required = false) Long clientId,
                                         @RequestParam Long serviceTypeId,
                                         @RequestParam(required = false) Long routeId,
                                         @RequestParam(required = false) BigDecimal distanceKm,
                                         @RequestParam(required = false) BigDecimal weightKg) {
        return tariffService.preview(clientId, serviceTypeId, routeId, distanceKm, weightKg);
    }
}
