-- ---------------------------------------------------------------------
-- La date d'embauche n'est plus exigee a la creation d'un chauffeur --
-- retiree du formulaire de creation a la demande de l'utilisateur,
-- reste saisissable plus tard via la fiche. Driver.seniorityLabel()/
-- getSeniorityMonths() geraient deja une valeur nulle (aucune
-- anciennete affichee tant qu'elle n'est pas renseignee).
-- ---------------------------------------------------------------------

ALTER TABLE drivers ALTER COLUMN hire_date DROP NOT NULL;
