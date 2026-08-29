package com.sogeco.fleet.modules.geofence;

import com.google.gson.JsonArray;
import com.google.gson.JsonParser;
import com.sogeco.fleet.common.enums.*;
import com.sogeco.fleet.common.util.GeoUtils;
import com.sogeco.fleet.modules.alert.AlertRuleRepository;
import com.sogeco.fleet.modules.alert.AlertService;
import com.sogeco.fleet.modules.vehicle.Vehicle;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/**
 * Detection des franchissements de zone.
 *
 * Un evenement n'est produit qu'au CHANGEMENT d'etat : un camion
 * stationne dans une zone pendant deux heures ne genere pas une entree
 * toutes les trente secondes. C'est le dernier evenement enregistre
 * qui dit si le camion etait dedans ou dehors.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class GeofenceEvaluationService {

    private final GeofenceZoneRepository zoneRepository;
    private final GeofenceEventRepository eventRepository;
    private final AlertRuleRepository ruleRepository;
    private final AlertService alertService;

    @Transactional
    public void evaluate(Vehicle vehicle, BigDecimal latitude, BigDecimal longitude, Instant when) {
        if (latitude == null || longitude == null) {
            return;
        }

        for (GeofenceZone zone : zoneRepository.findByActiveTrue()) {

            if (!zone.appliesTo(vehicle.getId())) {
                continue;
            }

            double[][] polygon = parsePolygon(zone);
            if (polygon == null) {
                continue;
            }

            boolean inside = GeoUtils.isInsidePolygon(
                    latitude.doubleValue(), longitude.doubleValue(), polygon);

            boolean wasInside = eventRepository.findLastEvent(vehicle.getId(), zone.getId())
                    .map(event -> event.getEventType() == GeofenceEventType.ENTREE)
                    .orElse(false);

            if (inside == wasInside) {
                continue;   // Aucun changement d'etat
            }

            GeofenceEventType type = inside ? GeofenceEventType.ENTREE : GeofenceEventType.SORTIE;

            eventRepository.save(GeofenceEvent.builder()
                    .vehicleId(vehicle.getId())
                    .geofenceZoneId(zone.getId())
                    .eventType(type)
                    .occurredAt(when)
                    .latitude(latitude)
                    .longitude(longitude)
                    .build());

            log.info("{} de la zone {} par le camion {}",
                    type, zone.getName(), vehicle.getRegistrationNumber());

            raiseAlertIfNeeded(zone, vehicle, type, latitude, longitude);
        }
    }

    private void raiseAlertIfNeeded(GeofenceZone zone, Vehicle vehicle, GeofenceEventType type,
                                    BigDecimal latitude, BigDecimal longitude) {

        boolean shouldAlert = (type == GeofenceEventType.ENTREE && Boolean.TRUE.equals(zone.getAlertOnEntry()))
                || (type == GeofenceEventType.SORTIE && Boolean.TRUE.equals(zone.getAlertOnExit()));

        if (!shouldAlert) {
            return;
        }

        ruleRepository.findByAlertTypeAndActiveTrue(AlertType.GEOREPERAGE).ifPresent(rule ->
                alertService.raise(AlertService.AlertRequest.builder()
                        .type(AlertType.GEOREPERAGE)
                        .level(rule.getLevel())
                        .rule(rule)
                        .title("Georeperage — %s de zone".formatted(
                                type == GeofenceEventType.ENTREE ? "entree" : "sortie"))
                        .description("%s de la zone %s (%s)".formatted(
                                type == GeofenceEventType.ENTREE ? "Entree" : "Sortie",
                                zone.getName(), zone.getZoneType()))
                        .vehicle(vehicle)
                        .latitude(latitude)
                        .longitude(longitude)
                        .locationLabel(zone.getName())
                        .build()));
    }

    /**
     * Extrait les sommets d'un polygone GeoJSON.
     * Convention GeoJSON : [longitude, latitude] — l'ordre inverse de
     * l'usage courant, source d'erreur classique.
     */
    private double[][] parsePolygon(GeofenceZone zone) {
        try {
            var root = JsonParser.parseString(zone.getPolygonGeojson()).getAsJsonObject();

            JsonArray coordinates = root.has("geometry")
                    ? root.getAsJsonObject("geometry").getAsJsonArray("coordinates")
                    : root.getAsJsonArray("coordinates");

            JsonArray ring = coordinates.get(0).getAsJsonArray();
            double[][] polygon = new double[ring.size()][2];

            for (int i = 0; i < ring.size(); i++) {
                JsonArray point = ring.get(i).getAsJsonArray();
                polygon[i][0] = point.get(1).getAsDouble();   // latitude
                polygon[i][1] = point.get(0).getAsDouble();   // longitude
            }

            return polygon;

        } catch (RuntimeException e) {
            log.warn("Polygone illisible pour la zone {} : {}", zone.getName(), e.getMessage());
            return null;
        }
    }
}
