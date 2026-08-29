package com.sogeco.fleet.common.enums;

/** Nature d'une zone de georeperage. */
public enum GeofenceZoneType {
    /** Sortie de la zone : alerte. */
    AUTORISEE,
    /** Entree dans la zone : alerte. */
    INTERDITE,
    /** Point de livraison client. */
    CLIENT,
    /** Site SOGECO. */
    AGENCE
}
