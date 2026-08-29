package com.sogeco.fleet.modules.client;

import com.sogeco.fleet.common.dto.PageResponse;
import com.sogeco.fleet.common.exception.DuplicateResourceException;
import com.sogeco.fleet.common.exception.ResourceNotFoundException;
import com.sogeco.fleet.modules.city.City;
import com.sogeco.fleet.modules.city.CityRepository;
import com.sogeco.fleet.modules.client.dto.ClientRequest;
import com.sogeco.fleet.modules.client.dto.ClientResponse;
import com.sogeco.fleet.modules.client.dto.ServiceTypeResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ClientService {

    private final ClientRepository repository;
    private final ServiceTypeRepository serviceTypeRepository;
    private final CityRepository cityRepository;

    @Transactional(readOnly = true)
    @PreAuthorize("hasAuthority('CLIENT_READ')")
    public PageResponse<ClientResponse> list(Pageable pageable) {
        return PageResponse.from(repository.findAllBy(pageable), ClientResponse::from);
    }

    @Transactional(readOnly = true)
    @PreAuthorize("hasAuthority('CLIENT_READ')")
    public List<ClientResponse> listActive() {
        return repository.findByActiveTrueOrderByCompanyNameAsc().stream()
                .map(ClientResponse::from).toList();
    }

    @Transactional(readOnly = true)
    @PreAuthorize("hasAuthority('CLIENT_READ')")
    public List<ClientResponse> search(String fragment) {
        return repository.findByCompanyNameContainingIgnoreCaseAndActiveTrue(fragment).stream()
                .map(ClientResponse::from).toList();
    }

    @Transactional(readOnly = true)
    @PreAuthorize("hasAuthority('CLIENT_READ')")
    public ClientResponse get(Long id) {
        return ClientResponse.from(find(id));
    }

    @Transactional
    @PreAuthorize("hasAuthority('CLIENT_MANAGE')")
    public ClientResponse create(ClientRequest request) {
        String code = request.code() == null || request.code().isBlank()
                ? buildCode(request.companyName())
                : request.code();

        if (repository.existsByCodeIgnoreCase(code)) {
            throw new DuplicateResourceException("Client", "code", code);
        }

        Client client = Client.builder()
                .code(code.toUpperCase())
                .companyName(request.companyName())
                .contactName(request.contactName())
                .phone(request.phone())
                .email(request.email())
                .address(request.address())
                .city(resolveCity(request.cityId()))
                .taxNumber(request.taxNumber())
                .paymentTermsDays(request.paymentTermsDays() == null ? 30 : request.paymentTermsDays())
                .notes(request.notes())
                .build();

        return ClientResponse.from(repository.save(client));
    }

    @Transactional
    @PreAuthorize("hasAuthority('CLIENT_MANAGE')")
    public ClientResponse update(Long id, ClientRequest request) {
        Client client = find(id);

        String code = request.code() == null || request.code().isBlank()
                ? client.getCode()
                : request.code();

        repository.findByCodeIgnoreCase(code)
                .filter(existing -> !existing.getId().equals(id))
                .ifPresent(existing -> {
                    throw new DuplicateResourceException("Client", "code", code);
                });

        client.setCode(code.toUpperCase());
        client.setCompanyName(request.companyName());
        client.setContactName(request.contactName());
        client.setPhone(request.phone());
        client.setEmail(request.email());
        client.setAddress(request.address());
        client.setCity(resolveCity(request.cityId()));
        client.setTaxNumber(request.taxNumber());
        if (request.paymentTermsDays() != null) {
            client.setPaymentTermsDays(request.paymentTermsDays());
        }
        client.setNotes(request.notes());

        return ClientResponse.from(client);
    }

    @Transactional
    @PreAuthorize("hasAuthority('CLIENT_MANAGE')")
    public void deactivate(Long id) {
        find(id).deactivate();
    }

    @Transactional(readOnly = true)
    @PreAuthorize("isAuthenticated()")
    public List<ServiceTypeResponse> listServiceTypes() {
        return serviceTypeRepository.findByActiveTrueOrderByLabelAsc().stream()
                .map(ServiceTypeResponse::from).toList();
    }

    /** Meme convention que CityService : 3 premieres lettres, suffixe numerique si collision. */
    private String buildCode(String name) {
        String base = name.replaceAll("[^A-Za-z]", "").toUpperCase();
        base = base.length() >= 3 ? base.substring(0, 3) : base;
        String candidate = base;
        int suffix = 1;
        while (repository.existsByCodeIgnoreCase(candidate)) {
            candidate = base + suffix++;
        }
        return candidate;
    }

    private City resolveCity(Long cityId) {
        if (cityId == null) {
            return null;
        }
        return cityRepository.findById(cityId)
                .orElseThrow(() -> new ResourceNotFoundException("Ville", cityId));
    }

    Client find(Long id) {
        return repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Client", id));
    }
}
