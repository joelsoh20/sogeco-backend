package com.sogeco.fleet.common.enums;

/** Type de carrosserie (RG-4.8). */
public enum BodyType {
    TRACTEUR,
    PORTEUR,
    BENNE,
    CITERNE,
    FOURGON,
    PLATEAU,
    /** Petit utilitaire / camionnette — livraison urbaine du dernier kilometre. */
    UTILITAIRE,
    MOTO,
    TRICYCLE,
    /** Voiture legere dediee a la livraison, distincte de l'UTILITAIRE (camionnette). */
    VOITURE_LIVRAISON,
    /** La remorque elle-meme, sans moteur — tractee par un TRACTEUR, pas de reservoir propre. */
    SEMI_REMORQUE
}
