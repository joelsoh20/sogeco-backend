package com.sogeco.fleet.modules.insurance;

import com.sogeco.fleet.common.dto.PageResponse;
import com.sogeco.fleet.common.exception.DuplicateResourceException;
import com.sogeco.fleet.common.exception.ResourceNotFoundException;
import com.sogeco.fleet.common.security.EditWindowGuard;
import com.sogeco.fleet.common.security.SecurityUtils;
import com.sogeco.fleet.modules.insurance.dto.CarteBleueRequest;
import com.sogeco.fleet.modules.insurance.dto.CarteBleueResponse;
import com.sogeco.fleet.modules.setting.SettingService;
import com.sogeco.fleet.modules.vehicle.Vehicle;
import com.sogeco.fleet.modules.vehicle.VehicleRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

/** Cartes bleues — un document de circulation par camion. */
@Slf4j
@Service
@RequiredArgsConstructor
public class CarteBleueService {

    private final CarteBleueRepository repository;
    private final VehicleRepository vehicleRepository;
    private final SettingService settingService;

    @Transactional(readOnly = true)
    @PreAuthorize("hasAuthority('INSURANCE_READ')")
    public PageResponse<CarteBleueResponse> list(Pageable pageable) {
        var page = SecurityUtils.currentCityId()
                .map(cityId -> repository.findAllByVehicle_City_Id(cityId, pageable))
                .orElseGet(() -> repository.findAllBy(pageable));
        return PageResponse.from(page, CarteBleueResponse::from);
    }

    @Transactional(readOnly = true)
    @PreAuthorize("hasAuthority('INSURANCE_READ')")
    public CarteBleueResponse get(Long id) {
        return CarteBleueResponse.from(find(id));
    }

    @Transactional(readOnly = true)
    @PreAuthorize("hasAuthority('INSURANCE_READ')")
    public List<CarteBleueResponse> forVehicle(Long vehicleId) {
        if (!inScope(vehicleId)) {
            return List.of();
        }
        return repository.findByVehicleIdOrderByExpiryDateDesc(vehicleId)
                .stream().map(CarteBleueResponse::from).toList();
    }

    @Transactional
    @PreAuthorize("hasAuthority('INSURANCE_CREATE')")
    public CarteBleueResponse create(CarteBleueRequest request) {
        if (repository.existsByReceiptNumber(request.receiptNumber())) {
            throw new DuplicateResourceException("Carte bleue", "numero de recu", request.receiptNumber());
        }

        Vehicle vehicle = vehicleRepository.findById(request.vehicleId())
                .orElseThrow(() -> new ResourceNotFoundException("Camion", request.vehicleId()));

        CarteBleue carte = CarteBleue.builder()
                .vehicle(vehicle)
                .receiptNumber(request.receiptNumber())
                .category(request.category())
                .issueDate(request.issueDate())
                .expiryDate(request.expiryDate())
                .power(request.power())
                .cost(request.cost())
                .notes(request.notes())
                .build();

        CarteBleue saved = repository.save(carte);

        log.info("Carte bleue {} enregistree pour {} par {}",
                request.receiptNumber(), vehicle.getRegistrationNumber(), SecurityUtils.currentUserEmail());

        return CarteBleueResponse.from(saved);
    }

    @Transactional
    @PreAuthorize("hasAuthority('INSURANCE_UPDATE')")
    public CarteBleueResponse update(Long id, CarteBleueRequest request) {
        CarteBleue carte = find(id);

        EditWindowGuard.assertEditable(carte.getCreatedAt(),
                settingService.getInt("carte_bleue.edit_window_hours", 24), "RG-CB-EDIT", "Cette carte bleue");

        if (!request.receiptNumber().equals(carte.getReceiptNumber())
                && repository.existsByReceiptNumberAndIdNot(request.receiptNumber(), id)) {
            throw new DuplicateResourceException("Carte bleue", "numero de recu", request.receiptNumber());
        }

        Vehicle vehicle = vehicleRepository.findById(request.vehicleId())
                .orElseThrow(() -> new ResourceNotFoundException("Camion", request.vehicleId()));

        carte.setVehicle(vehicle);
        carte.setReceiptNumber(request.receiptNumber());
        carte.setCategory(request.category());
        carte.setIssueDate(request.issueDate());
        carte.setExpiryDate(request.expiryDate());
        carte.setPower(request.power());
        carte.setCost(request.cost());
        carte.setNotes(request.notes());

        log.info("Carte bleue {} corrigee par {}", carte.getReceiptNumber(), SecurityUtils.currentUserEmail());
        return CarteBleueResponse.from(carte);
    }

    /** Cartes bleues arrivant a echeance, pour l'echeancier unifie. */
    @Transactional(readOnly = true)
    public List<CarteBleue> findExpiringBefore(LocalDate limit) {
        return repository.findByExpiryDateLessThanEqual(limit);
    }

    /**
     * Point d'entree unique pour charger une carte bleue par id —
     * centralise ici la restriction de ville (RG-13.4). 404, jamais
     * 403, pour ne pas confirmer que l'id existe ailleurs.
     */
    private CarteBleue find(Long id) {
        CarteBleue carte = repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Carte bleue", id));
        Long cityId = SecurityUtils.currentCityId().orElse(null);
        if (cityId != null && (carte.getVehicle() == null || carte.getVehicle().getCity() == null
                || !cityId.equals(carte.getVehicle().getCity().getId()))) {
            throw new ResourceNotFoundException("Carte bleue", id);
        }
        return carte;
    }

    /** Vrai si le camion est dans la ville geree, ou si l'appelant voit tout (admin). */
    private boolean inScope(Long vehicleId) {
        return SecurityUtils.currentCityId()
                .map(cityId -> vehicleRepository.findById(vehicleId)
                        .map(v -> v.getCity() != null && cityId.equals(v.getCity().getId()))
                        .orElse(false))
                .orElse(true);
    }
}
