package com.sogeco.fleet.common.enums;

/**
 * Nature d'un partenaire.
 *
 * Une table unique remplace les champs texte libres garageName et
 * stationName : sans elle, les analyses "par prestataire" du cahier
 * fonctionnel seraient impossibles.
 */
public enum PartnerType {
    GARAGE,
    STATION_SERVICE,
    ASSUREUR,
    CENTRE_VISITE,
    FOURNISSEUR,
    AUTRE
}
