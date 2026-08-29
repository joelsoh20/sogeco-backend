package com.sogeco.fleet.modules.tracking.adapter;

import com.sogeco.fleet.modules.tracking.dto.TelematicsPayload;

import java.util.List;

/**
 * Traduction du format d'un fournisseur vers le modele canonique.
 *
 * Un fournisseur peut envoyer plusieurs positions dans une seule
 * requete, d'ou le retour en liste.
 */
public interface TelematicsPayloadAdapter {

    /** Valeur du segment {provider} de l'URL du webhook. */
    String provider();

    List<TelematicsPayload> parse(String rawBody);
}
