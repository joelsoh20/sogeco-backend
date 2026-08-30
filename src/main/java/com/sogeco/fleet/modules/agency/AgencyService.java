package com.sogeco.fleet.modules.agency;

import com.sogeco.fleet.common.dto.PageResponse;
import com.sogeco.fleet.common.enums.SiteType;
import com.sogeco.fleet.common.exception.BusinessException;
import com.sogeco.fleet.common.exception.DuplicateResourceException;
import com.sogeco.fleet.common.exception.ResourceNotFoundException;
import com.sogeco.fleet.common.security.SecurityUtils;
import com.sogeco.fleet.common.util.CoordinateParser;
import com.sogeco.fleet.modules.agency.dto.AgencyRequest;
import com.sogeco.fleet.modules.agency.dto.AgencyResponse;
import com.sogeco.fleet.modules.audit.AuditAction;
import com.sogeco.fleet.modules.audit.AuditService;
import com.sogeco.fleet.modules.city.City;
import com.sogeco.fleet.modules.city.CityRepository;
import com.sogeco.fleet.modules.quartier.Quartier;
import com.sogeco.fleet.modules.quartier.QuartierRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class AgencyService {

    private static final String ENTITY = "Agency";

    private final AgencyRepository repository;
    private final CityRepository cityRepository;
    private final QuartierRepository quartierRepository;
    private final AuditService auditService;

    @Transactional(readOnly = true)
    @PreAuthorize("isAuthenticated()")
    public PageResponse<AgencyResponse> list(Pageable pageable) {
        return PageResponse.from(repository.findAll(pageable), AgencyResponse::from);
    }

    @Transactional(readOnly = true)
    @PreAuthorize("isAuthenticated()")
    public List<AgencyResponse> listActive() {
        return repository.findByActiveTrueOrderByNameAsc().stream().map(AgencyResponse::from).toList();
    }

    @Transactional(readOnly = true)
    @PreAuthorize("isAuthenticated()")
    public AgencyResponse get(Long id) {
        return AgencyResponse.from(find(id));
    }

    @Transactional
    @PreAuthorize("hasAuthority('AGENCY_MANAGE')")
    public AgencyResponse create(AgencyRequest request) {
        City city = findCity(request.cityId());
        Quartier quartier = findQuartier(request.quartierId(), city);
        String name = request.name() == null || request.name().isBlank()
                ? buildName(request.siteType(), city, quartier)
                : request.name();
        String code = request.code() == null || request.code().isBlank()
                ? buildCode(request.siteType(), city)
                : request.code();

        if (repository.existsByCodeIgnoreCase(code)) {
            throw new DuplicateResourceException("Site", "code", code);
        }

        Agency agency = Agency.builder()
                .code(code.toUpperCase())
                .name(name)
                .city(city)
                .quartier(quartier)
                .siteType(request.siteType())
                .address(request.address())
                .phone(request.phone())
                .build();

        applyCoordinates(agency, request.coordinates());
        Agency saved = repository.save(agency);

        // La ville accueille desormais une implantation.
        saved.getCity().setHasSite(true);

        auditService.record(SecurityUtils.currentUserEmail(), AuditAction.AGENCY_CREATED, ENTITY, saved.getId(), null);
        return AgencyResponse.from(saved);
    }

    @Transactional
    @PreAuthorize("hasAuthority('AGENCY_MANAGE')")
    public AgencyResponse update(Long id, AgencyRequest request) {
        Agency agency = find(id);

        // Code/nom absents : on garde ceux deja attribues plutot que d'en regenerer.
        if (request.code() != null && !request.code().isBlank()) {
            repository.findByCodeIgnoreCase(request.code())
                    .filter(existing -> !existing.getId().equals(id))
                    .ifPresent(existing -> {
                        throw new DuplicateResourceException("Site", "code", request.code());
                    });
            agency.setCode(request.code().toUpperCase());
        }
        if (request.name() != null && !request.name().isBlank()) {
            agency.setName(request.name());
        }
        City city = findCity(request.cityId());
        agency.setCity(city);
        agency.setQuartier(findQuartier(request.quartierId(), city));
        agency.setSiteType(request.siteType());
        agency.setAddress(request.address());
        agency.setPhone(request.phone());
        applyCoordinates(agency, request.coordinates());

        return AgencyResponse.from(agency);
    }

    @Transactional
    @PreAuthorize("hasAuthority('AGENCY_MANAGE')")
    public void deactivate(Long id) {
        Agency agency = find(id);

        // Un site rattachant encore des utilisateurs ne peut pas etre ferme :
        // le controle sera etendu aux camions et chauffeurs au sprint 2.
        if (repository.countByActiveTrue() <= 1) {
            throw new BusinessException("RG-13.5",
                    "Impossible de desactiver le dernier site actif", HttpStatus.CONFLICT);
        }

        agency.deactivate();
    }

    private void applyCoordinates(Agency agency, String raw) {
        if (raw == null || raw.isBlank()) {
            return;
        }
        CoordinateParser.GeoPoint point = CoordinateParser.parse(raw);
        agency.setLatitude(point.latitude());
        agency.setLongitude(point.longitude());

        if (CoordinateParser.isOutsideCameroon(point.latitude(), point.longitude())) {
            log.warn("Coordonnees hors Cameroun pour le site {} : {}", agency.getName(), raw);
        }
    }

    private static final java.util.Map<SiteType, String> SITE_TYPE_LABELS = java.util.Map.of(
            SiteType.SIEGE, "Siège",
            SiteType.AGENCE, "Agence",
            SiteType.DEPOT, "Dépôt");

    /**
     * "Agence Bonapriso" (quartier, plus precis) ou a defaut "Agence Douala" (ville) —
     * suffixe d'un numero si ce nom existe deja.
     */
    private String buildName(SiteType siteType, City city, Quartier quartier) {
        String place = quartier != null ? quartier.getName() : city.getName();
        String base = "%s %s".formatted(SITE_TYPE_LABELS.get(siteType), place);
        String candidate = base;
        int suffix = 2;
        while (repository.existsByNameIgnoreCase(candidate)) {
            candidate = "%s %d".formatted(base, suffix++);
        }
        return candidate;
    }

    /** Code court derive de la ville et du type de site, suffixe si deja pris. */
    private String buildCode(SiteType siteType, City city) {
        String base = "%s-%s".formatted(city.getCode(), siteType.name().substring(0, 3));
        String candidate = base;
        int suffix = 1;
        while (repository.existsByCodeIgnoreCase(candidate)) {
            candidate = base + suffix++;
        }
        return candidate;
    }

    private City findCity(Long cityId) {
        return cityRepository.findById(cityId)
                .orElseThrow(() -> new ResourceNotFoundException("Ville", cityId));
    }

    /** Le quartier doit appartenir a la ville choisie — sinon la localisation serait incoherente. */
    private Quartier findQuartier(Long quartierId, City city) {
        Quartier quartier = quartierRepository.findById(quartierId)
                .orElseThrow(() -> new ResourceNotFoundException("Quartier", quartierId));
        if (!quartier.getCity().getId().equals(city.getId())) {
            throw new BusinessException("RG-13.6",
                    "Le quartier %s n'appartient pas a %s".formatted(quartier.getName(), city.getName()),
                    HttpStatus.UNPROCESSABLE_CONTENT);
        }
        return quartier;
    }

    private Agency find(Long id) {
        return repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Site", id));
    }
}
