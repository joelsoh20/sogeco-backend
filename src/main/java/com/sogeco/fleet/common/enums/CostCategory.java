package com.sogeco.fleet.common.enums;

/**
 * Categories de la repartition des couts de l'ecran Rapports.
 *
 * Ne couvre pas les memes montants que FleetKpis.totalDirectCost : la
 * quote-part salariale des chauffeurs (driverCost) en est volontairement
 * exclue (ce n'est pas une charge de gestion de flotte au sens de cet
 * ecran), et les primes d'assurance et frais de visite technique y sont
 * inclus (absents de FleetKpis). Les deux totaux ne reconcilient donc
 * plus terme a terme.
 *
 * LAVERIE est extraite de MAINTENANCE (categorie MaintenanceCategory.LAVERIE) :
 * MAINTENANCE ici ne compte donc plus les lavages, pour que les deux
 * apparaissent comme des lignes distinctes du donut.
 */
public enum CostCategory {
    CARBURANT,
    MAINTENANCE,
    LAVERIE,
    ASSURANCE,
    VISITE_TECHNIQUE,
    PEAGES,
    FRAIS_MISSION,
    AUTRES
}
