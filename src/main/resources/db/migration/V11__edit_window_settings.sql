-- Aligne le delai de modification du carburant sur celui des autres modules (1h)
-- et introduit la meme fenetre pour maintenance, camions, missions et chauffeurs.
UPDATE system_settings SET setting_value = '1' WHERE setting_key = 'fuel.edit_window_hours';

INSERT INTO system_settings (setting_key, setting_value, value_type, category, label, created_by) VALUES
    ('maintenance.edit_window_hours', '1', 'INTEGER', 'MAINTENANCE', 'Delai de modification d''une intervention par le gestionnaire (heures)', 'system'),
    ('vehicle.edit_window_hours',     '1', 'INTEGER', 'CAMIONS',     'Delai de modification d''un camion par le gestionnaire (heures)',        'system'),
    ('mission.edit_window_hours',     '1', 'INTEGER', 'MISSIONS',    'Delai de modification d''une mission par le gestionnaire (heures)',      'system'),
    ('driver.edit_window_hours',      '1', 'INTEGER', 'CHAUFFEURS',  'Delai de modification d''un chauffeur par le gestionnaire (heures)',     'system');
