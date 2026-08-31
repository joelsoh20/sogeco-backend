package com.sogeco.fleet.modules.mission;

import com.sogeco.fleet.common.exception.BusinessException;
import com.sogeco.fleet.common.exception.ResourceNotFoundException;
import com.sogeco.fleet.modules.agency.Agency;
import com.sogeco.fleet.modules.agency.AgencyRepository;
import com.sogeco.fleet.modules.city.City;
import com.sogeco.fleet.modules.city.CityRepository;
import com.sogeco.fleet.modules.client.Client;
import com.sogeco.fleet.modules.client.ClientRepository;
import com.sogeco.fleet.modules.client.ServiceType;
import com.sogeco.fleet.modules.client.ServiceTypeRepository;
import com.sogeco.fleet.modules.driver.Driver;
import com.sogeco.fleet.modules.driver.DriverRepository;
import com.sogeco.fleet.modules.mission.dto.MissionAutomationRequest;
import com.sogeco.fleet.modules.mission.dto.MissionAutomationResponse;
import com.sogeco.fleet.modules.quartier.Quartier;
import com.sogeco.fleet.modules.quartier.QuartierRepository;
import com.sogeco.fleet.modules.vehicle.Vehicle;
import com.sogeco.fleet.modules.vehicle.VehicleRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Livraisons quotidiennes recurrentes : creation du modele et
 * activation/desactivation. La generation quotidienne elle-meme est
 * dans MissionAutomationScheduler ; l'annulation d'un jour precis
 * passe par le mecanisme d'annulation de mission deja existant
 * (MissionService.cancel), pas par ce service.
 */
@Service
@RequiredArgsConstructor
public class MissionAutomationService {

    private final MissionAutomationRepository repository;
    private final CityRepository cityRepository;
    private final ServiceTypeRepository serviceTypeRepository;
    private final ClientRepository clientRepository;
    private final VehicleRepository vehicleRepository;
    private final DriverRepository driverRepository;
    private final AgencyRepository agencyRepository;
    private final QuartierRepository quartierRepository;

    @Transactional(readOnly = true)
    @PreAuthorize("hasAuthority('MISSION_READ')")
    public List<MissionAutomationResponse> list() {
        return repository.findAllByOrderByIdDesc().stream().map(MissionAutomationResponse::from).toList();
    }

    @Transactional
    @PreAuthorize("hasAuthority('MISSION_CREATE')")
    public MissionAutomationResponse create(MissionAutomationRequest request) {
        City city = cityRepository.findById(request.cityId())
                .orElseThrow(() -> new ResourceNotFoundException("Ville", request.cityId()));
        ServiceType serviceType = serviceTypeRepository.findById(request.serviceTypeId())
                .orElseThrow(() -> new ResourceNotFoundException("Type de prestation", request.serviceTypeId()));
        if (ServiceType.VOYAGE_HORS_VILLE.equals(serviceType.getCode())) {
            throw new BusinessException("RG-AUTO-1",
                    "Le voyage hors ville ne peut pas etre automatise : une automatisation ne porte qu'une seule ville",
                    HttpStatus.UNPROCESSABLE_CONTENT);
        }
        Client client = request.clientId() == null ? null : clientRepository.findById(request.clientId())
                .orElseThrow(() -> new ResourceNotFoundException("Client", request.clientId()));
        Vehicle vehicle = vehicleRepository.findById(request.vehicleId())
                .orElseThrow(() -> new ResourceNotFoundException("Camion", request.vehicleId()));
        if (vehicle.getCity() == null || !vehicle.getCity().getId().equals(request.cityId())) {
            throw new BusinessException("RG-AUTO-2",
                    "Le camion choisi n'est pas base dans la ville concernee",
                    HttpStatus.UNPROCESSABLE_CONTENT);
        }
        Driver driver = driverRepository.findById(request.driverId())
                .orElseThrow(() -> new ResourceNotFoundException("Chauffeur", request.driverId()));
        Agency agency = agencyRepository.findById(request.agencyId())
                .orElseThrow(() -> new ResourceNotFoundException("Site", request.agencyId()));
        Quartier destinationQuartier = quartierRepository.findById(request.destinationQuartierId())
                .orElseThrow(() -> new ResourceNotFoundException("Point de livraison", request.destinationQuartierId()));

        MissionAutomation automation = MissionAutomation.builder()
                .label(request.label())
                .city(city)
                .serviceType(serviceType)
                .client(client)
                .vehicle(vehicle)
                .driver(driver)
                .agency(agency)
                .destinationQuartier(destinationQuartier)
                .cargoDescription(request.cargoDescription())
                .build();

        return MissionAutomationResponse.from(repository.save(automation));
    }

    @Transactional
    @PreAuthorize("hasAuthority('MISSION_CREATE')")
    public void deactivate(Long id) {
        MissionAutomation automation = repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Automatisation", id));
        automation.deactivate();
    }
}
