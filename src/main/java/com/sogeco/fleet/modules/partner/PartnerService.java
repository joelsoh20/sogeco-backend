package com.sogeco.fleet.modules.partner;

import com.sogeco.fleet.common.dto.PageResponse;
import com.sogeco.fleet.common.enums.PartnerType;
import com.sogeco.fleet.common.exception.DuplicateResourceException;
import com.sogeco.fleet.common.exception.ResourceNotFoundException;
import com.sogeco.fleet.modules.city.CityRepository;
import com.sogeco.fleet.modules.partner.dto.PartnerRequest;
import com.sogeco.fleet.modules.partner.dto.PartnerResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class PartnerService {

    private final PartnerRepository repository;
    private final CityRepository cityRepository;

    @Transactional(readOnly = true)
    @PreAuthorize("hasAuthority('PARTNER_READ')")
    public PageResponse<PartnerResponse> list(Pageable pageable) {
        return PageResponse.from(repository.findAllBy(pageable), PartnerResponse::from);
    }

    @Transactional(readOnly = true)
    @PreAuthorize("hasAuthority('PARTNER_READ')")
    public List<PartnerResponse> listByType(PartnerType type) {
        return repository.findByPartnerTypeAndActiveTrueOrderByNameAsc(type)
                .stream().map(PartnerResponse::from).toList();
    }

    @Transactional(readOnly = true)
    @PreAuthorize("hasAuthority('PARTNER_READ')")
    public List<PartnerResponse> listActive() {
        return repository.findByActiveTrueOrderByNameAsc()
                .stream().map(PartnerResponse::from).toList();
    }

    @Transactional
    @PreAuthorize("hasAuthority('PARTNER_MANAGE')")
    public PartnerResponse create(PartnerRequest request) {
        if (request.code() != null && repository.existsByCodeIgnoreCase(request.code())) {
            throw new DuplicateResourceException("Partenaire", "code", request.code());
        }

        Partner partner = Partner.builder()
                .code(request.code() == null || request.code().isBlank()
                        ? null : request.code().toUpperCase())
                .name(request.name())
                .partnerType(request.partnerType())
                .contactName(request.contactName())
                .phone(request.phone())
                .email(request.email())
                .address(request.address())
                .city(request.cityId() == null ? null : cityRepository.findById(request.cityId())
                        .orElseThrow(() -> new ResourceNotFoundException("Ville", request.cityId())))
                .taxNumber(request.taxNumber())
                .notes(request.notes())
                .build();

        return PartnerResponse.from(repository.save(partner));
    }

    @Transactional
    @PreAuthorize("hasAuthority('PARTNER_MANAGE')")
    public PartnerResponse update(Long id, PartnerRequest request) {
        Partner partner = find(id);
        partner.setName(request.name());
        partner.setPartnerType(request.partnerType());
        partner.setContactName(request.contactName());
        partner.setPhone(request.phone());
        partner.setEmail(request.email());
        partner.setAddress(request.address());
        partner.setCity(request.cityId() == null ? null : cityRepository.findById(request.cityId())
                .orElseThrow(() -> new ResourceNotFoundException("Ville", request.cityId())));
        partner.setTaxNumber(request.taxNumber());
        partner.setNotes(request.notes());
        return PartnerResponse.from(partner);
    }

    @Transactional
    @PreAuthorize("hasAuthority('PARTNER_MANAGE')")
    public void deactivate(Long id) {
        find(id).deactivate();
    }

    Partner find(Long id) {
        return repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Partenaire", id));
    }
}
