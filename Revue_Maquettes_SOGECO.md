# REVUE DES MAQUETTES — SOGECO FLEET MANAGER

> Analyse des 6 écrans fournis, écarts avec le modèle de données, et ajustements à intégrer avant le développement.

---

# 1. Écrans couverts

| # | Écran | Rôle | Couverture du modèle |
|---|---|---|---|
| 1 | Connexion | Public | Bonne, sauf authentification Google (voir §2.3) |
| 2 | Tableau de bord — Vue PDG | `ROLE_PDG` | Bonne, sauf vocabulaire financier (§2.1) |
| 3 | Tableau de bord — Vue Admin | `ROLE_ADMIN` | Bonne |
| 4 | Carte GPS — Suivi temps réel | Manager | Bonne, sauf niveau de carburant (§2.2) |
| 5 | Gestion des camions | Manager | Bonne, ajustements mineurs |
| 6 | Missions & Livraisons | Manager | Bonne, ajustements mineurs |
| 7 | Carburant | Manager | Très bonne — la plus aboutie |

**Impression générale : les maquettes sont cohérentes, denses et professionnelles.** La navigation est stable d'un écran à l'autre, le pattern « liste + panneau de détail à droite » est appliqué systématiquement, et les indicateurs de tête d'écran sont bien choisis. Elles sont directement exploitables pour le développement.

---

# 2. Points bloquants à trancher

## 2.1 Contradiction sur la nature des clients — **PRIORITÉ 1**

L'écran Missions liste des clients nommés : **Socpalm, Nestlé, Dangote, Tradex, Bolloré, BOCAM, Sotracier, Sociacharm**. Ce sont de grands groupes industriels et agro-industriels camerounais.

Deux lectures possibles, et elles conduisent à deux applications différentes :

| Hypothèse | Ce que cela signifie | Conséquence sur le modèle |
|---|---|---|
| **A — Compte propre** (hypothèse retenue jusqu'ici) | Ces sociétés **achètent** des produits à SOGECO et sont livrées | La flotte est un centre de coûts. Le « CA » est la valeur des marchandises livrées. Pas de marge de transport. |
| **B — Transport pour compte d'autrui** | SOGECO **transporte pour** ces sociétés et facture la prestation | La flotte est un centre de revenus. Le CA et le bénéfice net des maquettes sont exacts. Il faut une tarification, et probablement une facturation. |

La présence sur la fiche mission d'une **« Facture proforma.pdf »** et d'un **« Ordre de mission.pdf »** penche nettement vers l'hypothèse B, ou vers un modèle mixte.

> **Question à trancher avant le sprint 3.** Si l'hypothèse B est la bonne, il faut réintroduire une grille tarifaire (prix au km, au tonnage ou forfaitaire par corridor) et un calcul de marge par mission — et le vocabulaire « chiffre d'affaires / bénéfice net » des maquettes redevient parfaitement juste. Un modèle mixte est également possible : livraisons de produits SOGECO *et* transport facturé pour des tiers, distingués par le type de mission.

## 2.2 Le niveau de carburant en temps réel est une exigence matérielle

Trois écrans affichent le niveau de carburant du camion en direct : « 60 % (120 L) » sur la carte GPS, la fiche camion et le suivi carburant. L'écran Carburant va plus loin avec une alerte « Remplissage anormal VH-003 : −120 L le 28/06 à 22:45 ».

**Cela suppose un boîtier équipé d'une sonde de niveau de carburant, raccordée au réservoir.** C'est une option qui ne figure pas sur les boîtiers d'entrée de gamme et qui exige une pose plus lourde qu'un simple traceur.

> **À intégrer au cahier des charges du fournisseur télématique**, faute de quoi trois écrans afficheront des données vides. C'est aussi la fonction qui permet la détection de siphonnage — probablement l'un des bénéfices les plus attendus du projet.

## 2.3 Authentification Google

L'écran de connexion propose « Connexion avec Google » à côté du couple identifiant / mot de passe. Ce n'est pas prévu au cahier technique, qui repose sur JWT et comptes locaux.

Trois options :

| Option | Coût | Recommandation |
|---|---|---|
| Retirer le bouton | Nul | **Recommandée en v1** — 5 à 10 utilisateurs internes, le gain est marginal |
| OAuth2 Google | ~2 jours (Spring Security OAuth2 Client) | Pertinent uniquement si SOGECO utilise déjà Google Workspace |
| Conserver le bouton sans le brancher | — | À proscrire |

> **[À VALIDER]** SOGECO dispose-t-elle de comptes Google Workspace professionnels ?

## 2.4 Volumétrie affichée

Les maquettes affichent 28 camions sur l'écran de gestion, 48/50 connectés sur le tableau de bord admin, et 24 actifs sur la vue PDG — pour un parc réel de **11 camions**. Il s'agit vraisemblablement de données de démonstration, mais les trois chiffres sont incohérents entre eux.

> À harmoniser sur les données réelles avant toute présentation à la direction : un tableau de bord affichant 48 camions pour une flotte de 11 décrédibilise l'outil dès la première démonstration.

---

# 3. Ajustements du modèle de données

Les maquettes révèlent des champs et des comportements absents du modèle. Voici les corrections à appliquer.

## 3.1 Nouvelle table : `vehicle_documents` à échéance générique

La fiche camion présente un bloc **« Documents & Échéances »** avec quatre lignes : Assurance, Visite technique, Carte grise, **Chronotachygraphe** — chacune avec une date d'expiration et un statut coloré.

Le modèle actuel traite l'assurance et la visite technique dans des tables spécialisées, mais n'a pas de mécanisme générique pour les autres documents à échéance.

**Correction :** la table `documents` porte déjà `expiry_date`. Il faut lui ajouter un `document_type` en liste fermée et un statut calculé, et créer une **vue d'échéancier unifiée** qui agrège assurance, visite technique et documents génériques :

```
document_type ∈ { CARTE_GRISE, CHRONOTACHYGRAPHE, LICENCE_TRANSPORT,
                  AUTORISATION_CIRCULER, AUTRE }
status calculé  ∈ { VALIDE, A_RENOUVELER, EXPIRE }
```

La règle de blocage à l'affectation (RG-T6-05) doit être étendue : **[À VALIDER]** quels documents bloquent réellement une mission ? L'assurance et la visite technique, certainement. La carte grise, oui. Le chronotachygraphe, probablement en alerte simple.

## 3.2 Champs à ajouter sur `vehicles`

| Champ | Justification |
|---|---|
| `capacity_tons NUMERIC(6,2)` | « Capacité : 30 Tonnes » affiché sur la fiche et la carte |
| `body_type VARCHAR(30)` | Colonne « Type » : `TRACTEUR`, `PORTEUR`, `BENNE`, `CITERNE` |
| `fuel_level_percent NUMERIC(5,2)` | Niveau courant, alimenté par la sonde (cache, dernière valeur GPS) |
| `fuel_level_liters NUMERIC(8,2)` | Idem, en litres |
| `next_maintenance_date DATE` | « Prochaine maintenance : 09/06/2024 (dans 5 jours) » sur la carte GPS |
| `daily_km NUMERIC(10,2)` | « Kilométrage du jour : 245 km » — calculé, remis à zéro chaque nuit |

## 3.3 Champs à ajouter sur `missions`

| Champ | Justification |
|---|---|
| `cargo_volume_m3 NUMERIC(10,2)` | « Poids/Volume : 18 Tonnes / 50 m³ » |
| `planned_arrival_datetime` | « Date/Heure prévue arrivée » affichée séparément du départ |
| `mission_number` | Format visible `MS-2024-042` — séquence annuelle, à générer automatiquement |

**Statuts à aligner sur les maquettes.** Le modèle prévoyait `PLANIFIE` ; les maquettes affichent **« En attente »**. Liste retenue :

```
EN_ATTENTE → EN_COURS → TERMINEE
     └────── ANNULEE ──────┘
```

## 3.4 Champs à ajouter sur `fuel_logs`

Le tableau de l'écran Carburant comporte deux colonnes de kilométrage : **« Km avant »** et **« Km après »**. C'est plus rigoureux que le champ unique prévu, et cela fiabilise le calcul de consommation.

| Champ | Justification |
|---|---|
| `odometer_before NUMERIC(12,2)` | Colonne « Km avant » |
| `odometer_after NUMERIC(12,2)` | Colonne « Km après » |
| `status VARCHAR(20)` | Colonne « Statut » : `VALIDE`, `ANOMALIE`, `ANNULE` |
| `computed_consumption NUMERIC(6,2)` | Colonne « Conso (L/100km) », déjà prévue |

Le statut `ANOMALIE` est **calculé automatiquement** par les règles RG-T4-06 à RG-T4-08, pas saisi. Le statut `ANNULE` est manuel et nécessite un motif.

## 3.5 Catégories de dépenses à figer

Les deux tableaux de bord affichent une répartition des coûts. Les catégories diffèrent légèrement entre les deux écrans ; il faut les unifier :

```
CARBURANT · MAINTENANCE · SALAIRES · PEAGES · ASSURANCES · AUTRES
```

Le poste **Péages** (9,5 % sur la vue admin) était absent du modèle : il est bien couvert par la table `expenses` avec `category = PEAGE`, mais doit apparaître explicitement dans les rapports et dans le formulaire de saisie de dépense.

## 3.6 Ajouts au paramétrage

| Élément | Écran concerné | Table |
|---|---|---|
| Message de la Direction | Tableau de bord admin | `system_settings` (clé `dashboard.director_message`) |
| Compteur d'alertes non lues | Badge de la barre latérale | Endpoint dédié, pas de table |
| Météo | Tableau de bord admin | API externe, non persistée |

## 3.7 Précision sur les chauffeurs

Le classement « Top chauffeurs (Performance) » de la vue admin est établi sur le **nombre de missions**, pas sur un revenu généré. C'est cohérent avec un modèle en compte propre, et cela confirme qu'il faut abandonner le champ `total_revenue_generated` au profit de `total_missions_completed`.

Le numéro de téléphone du chauffeur apparaît sur la fiche camion, la fiche mission et le panneau de la carte GPS — il doit donc être remonté par les DTO de ces trois écrans, et non seulement par la fiche chauffeur.

---

# 4. Observations d'ergonomie

**Ce qui est réussi :**
- Le pattern « liste à gauche + panneau de détail à droite » est appliqué partout. Il évite les allers-retours et convient bien à un usage quotidien intensif.
- Les indicateurs de tête d'écran avec variation en pourcentage par rapport à la période précédente donnent immédiatement le sens de l'évolution.
- L'écran Carburant, avec ses trois axes d'analyse (par camion, par jour, par station) et sa détection d'anomalie visible dans le tableau, est le plus abouti du lot.
- La légende de la carte GPS avec les compteurs par statut est directement utile.

**Points d'attention :**
- Aucun écran de **paramétrage des référentiels** ne figure dans les maquettes fournies, alors que la décision D16 en fait le premier livrable. Les formulaires de création de site, de corridor, de client et de partenaire restent à concevoir.
- L'écran Missions affiche un **avancement en pourcentage** avec barre de progression. Rappel de la règle RG-T3-04 : cet avancement est calculé depuis le GPS, jamais saisi. Sur une mission sans données GPS, il faudra afficher « non disponible » plutôt que 0 %.
- Le bouton « Voir l'itinéraire » apparaît sur trois écrans. Prévoir un composant unique réutilisable.
- Les destinations listées (Kribi, Ngaoundéré, Garoua, Maroua, Limbé, Bertoua, Nkongsamba) confirment que les livraisons dépassent largement les trois villes d'implantation. Certaines impliquent des **missions de plusieurs jours** — à prévoir dans le calcul de la quote-part chauffeur et dans l'affichage du planning.

---

# 5. Écrans manquants à fournir

Pour compléter la conception, il reste à recevoir :

| Écran | Priorité | Pourquoi |
|---|---|---|
| **Paramètres / Référentiels** | **Haute** | Premier livrable selon D16 : sites, corridors, clients, partenaires |
| Chauffeurs (liste + dossier + performance) | Haute | Sprint 2 |
| Maintenance | Haute | Sprint 4 |
| Assurance & Visite technique | Haute | Sprint 6, et bloc échéances à préciser |
| Alertes (liste et traitement) | Moyenne | Sprint 5 |
| Rapports & Statistiques | Moyenne | Sprint 7 |
| Formulaire d'ajout de camion | Moyenne | Le bouton existe, le formulaire n'est pas maquetté |
| Formulaire de nouvelle mission | Moyenne | Idem |

---

# 6. Récapitulatif des décisions attendues

| # | Décision | Bloque |
|---|---|---|
| 1 | Nature des clients : compte propre, pour compte d'autrui, ou mixte ? | Sprint 3, modèle financier complet |
| 2 | Sonde de niveau de carburant sur les boîtiers : oui ou non ? | Cahier des charges fournisseur, sprint 5 |
| 3 | Authentification Google : retirer ou implémenter ? | Sprint 1 |
| 4 | Quels documents bloquent l'affectation d'une mission ? | Sprint 2 et 6 |
| 5 | Vocabulaire financier des tableaux de bord : conserver « CA / Bénéfice net » ou basculer sur « Valeur livrée / Coût logistique » ? | Dépend de la décision 1 |

---
---

# PARTIE 2 — Écrans complémentaires

> Maintenance, Chauffeurs & Performance, Assurance & Visite, Rapports & Statistiques, Alertes, Paramètres & Administration. Le jeu de maquettes est désormais complet.

---

# 7. Le point qui change tout : le modèle économique est tranché

L'écran **Rapports & Statistiques** lève l'ambiguïté signalée en partie 1, et il la lève dans le sens de l'hypothèse B.

Preuves, toutes issues du même écran :

| Élément affiché | Ce qu'il implique |
|---|---|
| **« Top 5 Clients (Chiffre d'affaires) »** avec SOCPALM 186 450 000 FCFA (28,7 %), NESTLÉ 142 680 000, DANGOTE 98 250 000 | Le chiffre d'affaires est **ventilé par client**. On ne ventile un CA par client que si on lui facture. |
| **Marge bénéficiaire 34,0 %** | Une marge suppose un prix de vente face à un coût de revient. |
| **Bénéfice net par camion**, avec marge par véhicule (VH-001 : 28 450 000, 38,1 %) | Chaque camion produit un revenu, pas seulement un coût. |
| **Bénéfice par km 1 206 FCFA · Bénéfice par mission 353 720 FCFA** | Indicateurs de rentabilité, pas de coût de revient. |
| Écran Paramètres : intégration **« Facturation — Logiciel de facturation »** déclarée connectée | Un système de facturation existe et couvre ces prestations. |
| Fiche mission : **« Facture proforma.pdf »** | Confirmation par le document joint. |

**Conclusion : SOGECO facture le transport à ses clients.** La flotte est un centre de revenus. Le modèle « compte propre » construit dans les versions précédentes doit être **remis à l'endroit**.

## 7.1 Ce qui redevient valable

Tout le vocabulaire des maquettes est correct : chiffre d'affaires, bénéfice net, marge bénéficiaire, rentabilité par camion, par mission et par agence. Il n'y a rien à renommer.

## 7.2 Ce qu'il faut réintroduire dans le modèle

| Élément | Détail |
|---|---|
| `missions.revenue_amount` | Rétabli — le CA de la mission, et non plus « valeur livrée » |
| `missions.margin_amount` | Rétabli en colonne calculée : `revenue_amount − total_cost` |
| Table **`tariffs`** | Grille tarifaire : client, type de prestation, corridor, mode (forfait / au km / à la tonne), prix unitaire, dates de validité |
| `clients` enrichi | Conditions de paiement, tarif négocié, encours — le client redevient un donneur d'ordre |
| `service_types` | Redevient un **catalogue de prestations facturables**, et non une simple ventilation analytique |
| Marge par client | Nouvel axe d'analyse, présent sur l'écran Rapports |

> **[À CONFIRMER FORMELLEMENT]** Cette conclusion repose sur la lecture des maquettes, pas sur une déclaration explicite. Un modèle **mixte** reste possible : transport facturé à des tiers *et* livraison de produits SOGECO. Dans ce cas, le type de mission détermine si un CA est saisi ou non. C'est l'hypothèse que je retiendrais par défaut, car elle couvre les deux cas sans rien casser.

---

# 8. Exigences matérielles révélées par l'écran Alertes

L'écran **Alertes & Centre de Contrôle** contient trois types d'alertes qui dépassent largement un traceur GPS classique.

| Alerte affichée | Donnée requise | Matériel nécessaire |
|---|---|---|
| « Niveau carburant : Niveau < 20 %, Capacité 400 L » | Niveau de carburant | Sonde de réservoir (déjà signalé en partie 1) |
| **« Température moteur : 105 °C »** | Température du liquide de refroidissement | Lecture bus CAN / OBD-II |
| **« Panne détectée : anomalie moteur, système EGR défaillant, code erreur P0480 »** | **Codes défaut moteur** | Lecture bus CAN / OBD-II |
| « Géorepérage — sortie de zone : zone Douala Centre » | Zones géographiques définies | Logiciel uniquement, mais nouvelle entité à créer |

**Le boîtier à commander n'est donc pas un traceur GPS, mais un boîtier télématique avec lecture du bus CAN.** L'écart de prix est significatif, la pose est plus technique, et tous les camions du parc ne sont pas nécessairement compatibles selon leur âge et leur marque.

> **Action : intégrer au cahier des charges du fournisseur** trois exigences distinctes — position GPS, sonde de niveau de carburant, lecture CAN/OBD-II des codes défaut et de la température moteur. Et vérifier la compatibilité avec les Mercedes Actros, MAN TGX, Scania R450, Renault C430 et Volvo FH500 du parc.
>
> **Repli possible** si le budget ou la compatibilité l'interdisent : conserver GPS + sonde carburant en v1, et traiter les pannes par saisie manuelle depuis l'écran Maintenance. Les écrans concernés doivent alors masquer les blocs correspondants plutôt que d'afficher du vide.

---

# 9. Nouvelles entités à créer

## 9.1 `geofence_zones` — géorepérage

L'alerte « Sortie de la zone définie — Zone : Douala Centre » suppose des zones dessinées et surveillées.

`id`, `name`, `zone_type` (`AUTORISEE`, `INTERDITE`, `CLIENT`, `AGENCE`), `polygon_geojson JSONB`, `city_id`, `active`, `alert_on_entry BOOLEAN`, `alert_on_exit BOOLEAN`

Table de liaison `vehicle_geofences` pour restreindre une zone à certains camions, et `geofence_events` pour historiser les entrées et sorties.

> Écran de tracé à prévoir dans les paramètres : dessin du polygone sur carte Leaflet.

## 9.2 `driver_bonuses` — primes de performance

L'écran Chauffeurs affiche une colonne **« Prime »** (Jean Dupont : 450 000 FCFA) et un bouton **« Attribuer prime »**.

`id`, `driver_id`, `period_month`, `amount`, `performance_score`, `reason`, `status` (`PROPOSEE`, `VALIDEE`, `VERSEE`, `REFUSEE`), `granted_by_user_id`, `granted_at`

> **[À VALIDER]** La prime est-elle calculée automatiquement à partir du score, ou saisie librement ? Une règle simple (barème par palier de score) serait plus équitable et plus simple à défendre auprès des chauffeurs.

## 9.3 `driver_actions` — journal des actions RH

Trois boutons figurent sur la fiche chauffeur : **Attribuer prime**, **Envoyer avertissement**, **Formation**. Ces actions doivent être tracées.

`id`, `driver_id`, `action_type` (`PRIME`, `AVERTISSEMENT`, `FORMATION`, `ENTRETIEN`), `action_date`, `motif`, `comment`, `created_by_user_id`, `document_id`

## 9.4 `tariffs` — grille tarifaire

Voir §7.2. Sans cette table, le chiffre d'affaires reste une saisie libre sans contrôle de cohérence.

---

# 10. Le système de notation doit être revu

Les maquettes précisent le dispositif bien au-delà de ce que prévoyait l'analyse fonctionnelle.

**Notation sur 100, pas sur 5.** Score global (Jean Dupont : 92/100) décomposé en **cinq critères**, chacun noté sur 100 :

| Critère affiché | Source de données | Calculable automatiquement ? |
|---|---|---|
| Conduite sécurisée — 92/100 | Excès de vitesse, freinages, incidents | Oui |
| Consommation économique — 85/100 | Écart à la moyenne du camion | Oui |
| Respect des délais — 90/100 | Ponctualité des missions | Oui |
| Entretien véhicule — 80/100 | Interventions curatives sur ses camions | Partiellement |
| Respect des règles — 88/100 | Non déterminé | **Non — nécessite une saisie manuelle** |

**Répartition par notation :** Excellent (90-100), Bon (70-89), Moyen (50-69), Faible (moins de 50).

> **[À VALIDER]** Deux points. D'abord, la définition exacte de « Respect des règles » — sans source de données, ce critère devra être saisi par un responsable. Ensuite, les **pondérations** entre les cinq critères : à 5 × 20 %, ou faut-il privilégier la sécurité ? Cette décision doit être prise avec la direction, car elle détermine les primes versées.

Autres champs révélés : **ancienneté** calculée et affichée (« 3 ans 2 mois »), **compteur d'incidents** sur la période, et une section **« Alertes chauffeurs »** distincte des alertes véhicules (excès de vitesse répétés, consommation élevée, retard de livraison, formation à prévoir).

---

# 11. Contradiction majeure sur les rôles

L'écran **Paramètres & Administration** comporte une section « Gestion des rôles » qui liste **six rôles**, avec leur description et leur nombre d'utilisateurs :

| Rôle | Utilisateurs | Description affichée |
|---|---|---|
| Administrateur | 3 | Accès complet à toutes les fonctionnalités |
| Direction Générale | 2 | Consultation des statistiques et rapports |
| Gestionnaire | 8 | Gestion des camions et missions |
| Superviseur | 6 | Suivi des missions et alertes |
| Comptable | 3 | Gestion financière et facturation |
| Agent de flotte | 2 | Gestion opérationnelle de la flotte |

Deux écarts avec les décisions prises :

**1. Six rôles au lieu de trois** (décision D2). Ce n'est pas un problème en soi : six valeurs d'enum coûtent autant que trois. Mais la présence d'un écran de gestion des rôles, avec un bouton **« Ajouter un rôle »**, suggère des rôles **créables par l'utilisateur** — ce qui impose les tables `roles` et `permissions` écartées par D2.

| Option | Coût | Quand la choisir |
|---|---|---|
| **A — 6 rôles en enum** + écran de gestion en lecture seule | Faible | Si la liste des rôles est stable. **Recommandée.** |
| **B — Rôles et permissions en base**, entièrement paramétrables | +4 à 5 jours | Si SOGECO veut créer ses propres rôles sans développeur |

**2. Un rôle « Chauffeur » apparaît dans la liste des utilisateurs récents**, alors que la décision D5 établit que les chauffeurs n'ont pas de compte applicatif. Il faut trancher : soit ce rôle disparaît des maquettes, soit les chauffeurs obtiennent un accès en consultation seule — ce qui change la conception de plusieurs écrans et ouvre la question d'une interface mobile.

> **[À VALIDER — bloque le sprint 1]** Combien de rôles, figés ou paramétrables, et les chauffeurs ont-ils un compte ?

---

# 12. Intégrations déclarées dans les paramètres

L'écran Paramètres affiche cinq intégrations avec un statut « Connecté » et une date de dernière synchronisation :

| Intégration | Statut v1 |
|---|---|
| GPS Tracking | **Dans le périmètre** — webhook, sprint 5 |
| Capteurs Carburant | **Dans le périmètre** si le boîtier le permet (§8) |
| Maintenance API | À préciser — de quel système s'agit-il ? |
| Facturation | **Hors périmètre v1**, mais confirme le modèle facturé (§7) |
| Comptabilité | **Hors périmètre v1** |

**Recommandation :** conserver l'écran tel quel, mais afficher les intégrations hors périmètre avec un statut « Non configuré » plutôt que « Connecté ». Une intégration affichée comme active alors qu'elle ne l'est pas décrédibilise l'ensemble de l'écran.

La table `integrations` prévue au modèle couvre déjà ce besoin, y compris la date de dernière synchronisation et le statut.

---

# 13. Compléments par écran

## 13.1 Maintenance

**Bien couvert par le modèle.** Le bloc « Pièces / Prestations » (Désignation, Quantité, Montant) valide la table `maintenance_items` prévue dès l'analyse initiale.

Ajustements :
- Catégories de coûts à figer : `ENTRETIEN_PREVENTIF`, `REPARATION_MECANIQUE`, `PNEUMATIQUE`, `ELECTRICITE`, `AUTRES`.
- Onglet **« Pannes »** distinct des interventions : une panne est un événement subi, une intervention est sa réponse. Prévoir `is_breakdown BOOLEAN` sur `maintenance_logs`, ou une table `breakdowns` distincte si l'on veut tracer les pannes sans réparation immédiate. **Recommandation : un booléen suffit en v1.**
- Colonne **« Prochaine intervention »** avec date et délai (« dans 30 jours ») → champ `next_intervention_date` sur l'intervention, alimenté par les seuils préventifs.
- Statuts : `PLANIFIEE`, `EN_COURS`, `TERMINEE`.

## 13.2 Assurance & Visite technique

**Entièrement couvert.** Trois onglets : Assurances, Visites techniques, Documents — ce qui valide l'échéancier générique proposé en §3.1.

Ajustements mineurs : colonne **« Jours restants »** avec valeur négative pour les documents expirés (« −42 jours »), et **coût moyen d'assurance par camion** en indicateur.

## 13.3 Alertes

Types d'alertes à ajouter au moteur de règles, au-delà de ceux déjà prévus : **température moteur**, **code défaut moteur**, **péage impayé**, **sortie de géorepérage**.

Niveaux à aligner sur les maquettes : `CRITIQUE`, `IMPORTANT`, `MINEUR`, `INFORMATION` — et non la graduation précédemment retenue.

Actions disponibles sur une alerte : **contacter le chauffeur** (lien téléphonique direct), **envoyer une notification**, **créer une intervention** (crée un enregistrement de maintenance rattaché à l'alerte), **générer un rapport**.

> Le lien alerte → intervention est structurant : ajouter `alert_id` sur `maintenance_logs`.

Indicateur central de l'écran : **taux de résolution 62 %**, avec répartition résolues / non résolues et délai depuis le déclenchement affiché en clair (« Non résolue depuis 3 min », « En cours depuis 45 min »). Cela valide la nécessité des horodatages `acknowledged_at` et `resolved_at`.

## 13.4 Paramètres

Sections à prévoir : informations de l'entreprise, préférences générales (langue, format horaire, devise, unités), **sécurité et authentification dont la double authentification**, sauvegarde et restauration, logo.

> La **2FA** n'était pas au cahier technique. Pour 24 utilisateurs dont certains accèdent à des données financières, c'est justifiable, mais cela représente 2 à 3 jours de développement. **[À VALIDER]** — je la classerais en v1.1 plutôt qu'en v1.

Le bloc « Informations système » (version, espace disque, statut du serveur, dernière sauvegarde) est alimenté par Spring Actuator : aucune table nécessaire.

## 13.5 Rapports

Nouveaux indicateurs à calculer, absents du modèle :
- **Taux de remplissage** (78 %) → nécessite `cargo_weight_kg / capacity_tons` par mission. Confirme l'ajout de `capacity_tons` sur les véhicules.
- **Taux d'utilisation de la flotte** (82 %), **taux de ponctualité** (91 %), **disponibilité des camions** (94 %).
- Marge et CA **par client**, **par camion**, **par agence**.

Un cinquième site apparaît dans la rentabilité par agence : **Bonabéri**, aux côtés de Douala, Yaoundé et Bafoussam. À confirmer dans le référentiel des sites.

---

# 14. Récapitulatif consolidé des décisions attendues

| # | Décision | Impact | Bloque |
|---|---|---|---|
| 1 | **Modèle économique** : transport facturé, compte propre, ou mixte ? | Majeur — CA, marge, tarification | Sprint 3 |
| 2 | **Rôles** : combien, figés ou paramétrables ? | Majeur — modèle de sécurité | Sprint 1 |
| 3 | **Chauffeurs** : ont-ils un compte applicatif ? | Majeur — périmètre, interface mobile | Sprint 1 |
| 4 | **Boîtier télématique** : GPS seul, + sonde carburant, ou + lecture CAN/OBD ? | Majeur — budget, 3 écrans concernés | Sprint 5 |
| 5 | Authentification Google : retirer ou implémenter ? | Mineur | Sprint 1 |
| 6 | Double authentification : v1 ou v1.1 ? | Mineur | Sprint 1 |
| 7 | Pondérations des 5 critères de notation | Moyen — conditionne les primes | Sprint 2 |
| 8 | Définition du critère « Respect des règles » | Moyen | Sprint 2 |
| 9 | Prime : calculée par barème ou saisie libre ? | Moyen | Sprint 2 |
| 10 | Géorepérage : dans la v1 ? | Moyen — nouvelle entité + écran de tracé | Sprint 5 |
| 11 | « Maintenance API » : de quel système s'agit-il ? | Moyen | Sprint 4 |
| 12 | Bonabéri est-il un cinquième site ? | Mineur | Sprint 1 |
| 13 | Quels documents bloquent l'affectation d'une mission ? | Moyen | Sprint 2 |

**Les quatre premières décisions conditionnent l'architecture. Les neuf suivantes peuvent être prises au fil des sprints.**
