-- Example data, loaded when the database is new or the example data version changes.
-- The shipping lines are fictional; prices are in KES.

INSERT INTO carrier (code, name, fragile_fee, express_rate) VALUES
    ('SWIFT', 'SwiftParcel Kenya',            300.00, 0.5000),
    ('SAVX',  'Savannah Express Couriers',    250.00, 0.6000),
    ('RIFT',  'Rift Valley Road Freight',     400.00, 0.3500),
    ('COAST', 'Coastline Cargo Lines',        350.00, 0.4500),
    ('KILI',  'Kilimanjaro Air Freight',      500.00, 0.4000),
    ('IOCL',  'Indian Ocean Container Lines', 600.00, 0.0000);

INSERT INTO tariff (code, carrier_code, description, extra_per_kg) VALUES
    ('SWIFT-LOCAL',     'SWIFT', 'Short haul (under 200 km)',     100.00),
    ('SWIFT-STD',       'SWIFT', 'Standard domestic',             120.00),
    ('SWIFT-FAR',       'SWIFT', 'Remote domestic',               150.00),
    ('SAVX-UPCOUNTRY',  'SAVX',  'Upcountry',                     115.00),
    ('SAVX-COAST',      'SAVX',  'Coast',                         130.00),
    ('RIFT-ROAD',       'RIFT',  'Domestic trucking',               7.00),
    ('RIFT-REGIONAL',   'RIFT',  'East Africa trucking',           14.00),
    ('COAST-CORRIDOR',  'COAST', 'Northern corridor trucking',      8.00),
    ('COAST-SEA',       'COAST', 'Coastal shipping',               12.00),
    ('COAST-GULF',      'COAST', 'Gulf and India shipping',        21.00),
    ('KILI-EA',         'KILI',  'East Africa air',               300.00),
    ('KILI-INTL',       'KILI',  'International air',             950.00),
    ('IOCL-GULF',       'IOCL',  'Gulf and India, from Mombasa port',  20.00),
    ('IOCL-ASIA',       'IOCL',  'Far East, from Mombasa port',        27.00),
    ('IOCL-EUROPE',     'IOCL',  'Europe, from Mombasa port',          33.00),
    ('IOCL-GULF-NBO',   'IOCL',  'Gulf and India, rail to Mombasa then ship', 23.00),
    ('IOCL-ASIA-NBO',   'IOCL',  'Far East, rail to Mombasa then ship',       30.00),
    ('IOCL-EUROPE-NBO', 'IOCL',  'Europe, rail to Mombasa then ship',         36.00);

INSERT INTO tariff_band (tariff_code, up_to_kg, price) VALUES
    ('SWIFT-LOCAL', 1, 400.00), ('SWIFT-LOCAL', 5, 750.00),  ('SWIFT-LOCAL', 10, 1300.00), ('SWIFT-LOCAL', 20, 2200.00),
    ('SWIFT-STD',   1, 500.00), ('SWIFT-STD',   5, 900.00),  ('SWIFT-STD',   10, 1500.00), ('SWIFT-STD',   20, 2500.00),
    ('SWIFT-FAR',   1, 650.00), ('SWIFT-FAR',   5, 1150.00), ('SWIFT-FAR',   10, 1900.00), ('SWIFT-FAR',   20, 3100.00),
    ('SAVX-UPCOUNTRY', 1, 450.00), ('SAVX-UPCOUNTRY', 5, 850.00), ('SAVX-UPCOUNTRY', 10, 1450.00), ('SAVX-UPCOUNTRY', 20, 2450.00),
    ('SAVX-COAST',     1, 550.00), ('SAVX-COAST',     5, 950.00), ('SAVX-COAST',     10, 1600.00), ('SAVX-COAST',     20, 2700.00),

    -- Road freight has a minimum charge (first band), so small parcels are cheaper by courier
    ('RIFT-ROAD', 25, 1500.00), ('RIFT-ROAD', 50, 1900.00), ('RIFT-ROAD', 100, 3200.00),
    ('RIFT-ROAD', 500, 9500.00), ('RIFT-ROAD', 1000, 16000.00),
    ('RIFT-REGIONAL', 25, 2800.00), ('RIFT-REGIONAL', 50, 4600.00), ('RIFT-REGIONAL', 100, 7800.00),
    ('RIFT-REGIONAL', 500, 22000.00), ('RIFT-REGIONAL', 1000, 38000.00),
    ('COAST-CORRIDOR', 25, 1400.00), ('COAST-CORRIDOR', 100, 6500.00), ('COAST-CORRIDOR', 1000, 18000.00),

    ('COAST-SEA', 5, 1100.00), ('COAST-SEA', 15, 2000.00), ('COAST-SEA', 30, 3300.00), ('COAST-SEA', 50, 5600.00),
    ('COAST-SEA', 500, 18000.00), ('COAST-SEA', 1000, 26000.00),
    ('COAST-GULF', 100, 16500.00), ('COAST-GULF', 500, 30000.00), ('COAST-GULF', 1000, 43000.00), ('COAST-GULF', 5000, 145000.00),

    ('KILI-EA',   0.5, 2200.00), ('KILI-EA',   2, 4800.00),  ('KILI-EA',   5, 8500.00),  ('KILI-EA',   10, 15000.00), ('KILI-EA',   45, 38000.00),
    ('KILI-INTL', 0.5, 4500.00), ('KILI-INTL', 2, 11000.00), ('KILI-INTL', 5, 20000.00), ('KILI-INTL', 10, 36000.00), ('KILI-INTL', 45, 95000.00),

    ('IOCL-GULF',   100, 18000.00), ('IOCL-GULF',   500, 32000.00), ('IOCL-GULF',   1000, 45000.00), ('IOCL-GULF',   5000, 150000.00),
    ('IOCL-ASIA',   100, 22000.00), ('IOCL-ASIA',   500, 40000.00), ('IOCL-ASIA',   1000, 58000.00), ('IOCL-ASIA',   5000, 190000.00),
    ('IOCL-EUROPE', 100, 26000.00), ('IOCL-EUROPE', 500, 48000.00), ('IOCL-EUROPE', 1000, 70000.00), ('IOCL-EUROPE', 5000, 230000.00),
    ('IOCL-GULF-NBO',   100, 21000.00), ('IOCL-GULF-NBO',   500, 37000.00), ('IOCL-GULF-NBO',   1000, 52000.00), ('IOCL-GULF-NBO',   5000, 170000.00),
    ('IOCL-ASIA-NBO',   100, 25000.00), ('IOCL-ASIA-NBO',   500, 45000.00), ('IOCL-ASIA-NBO',   1000, 66000.00), ('IOCL-ASIA-NBO',   5000, 212000.00),
    ('IOCL-EUROPE-NBO', 100, 29000.00), ('IOCL-EUROPE-NBO', 500, 53000.00), ('IOCL-EUROPE-NBO', 1000, 78000.00), ('IOCL-EUROPE-NBO', 5000, 255000.00);

INSERT INTO route (carrier_code, mode, origin, destination, tariff_code, transit_days, express_days) VALUES
    -- Courier: parcels up to 70 kg
    ('SWIFT', 'COURIER', 'Nairobi', 'Mombasa', 'SWIFT-STD',   2, 1),
    ('SWIFT', 'COURIER', 'Mombasa', 'Nairobi', 'SWIFT-STD',   2, 1),
    ('SWIFT', 'COURIER', 'Nairobi', 'Kisumu',  'SWIFT-STD',   2, 1),
    ('SWIFT', 'COURIER', 'Nairobi', 'Eldoret', 'SWIFT-STD',   2, 1),
    ('SWIFT', 'COURIER', 'Nairobi', 'Nakuru',  'SWIFT-LOCAL', 1, NULL),
    ('SWIFT', 'COURIER', 'Nairobi', 'Malindi', 'SWIFT-FAR',   3, 2),
    ('SAVX',  'COURIER', 'Nairobi', 'Kisumu',  'SAVX-UPCOUNTRY', 2, 1),
    ('SAVX',  'COURIER', 'Kisumu',  'Nairobi', 'SAVX-UPCOUNTRY', 2, 1),
    ('SAVX',  'COURIER', 'Nairobi', 'Eldoret', 'SAVX-UPCOUNTRY', 2, 1),
    ('SAVX',  'COURIER', 'Nairobi', 'Nakuru',  'SAVX-UPCOUNTRY', 1, NULL),
    ('SAVX',  'COURIER', 'Nairobi', 'Mombasa', 'SAVX-COAST',     2, 1),

    -- Road: trucks up to 24 t, for cities joined by land
    ('RIFT',  'ROAD', 'Nairobi', 'Nakuru',        'RIFT-ROAD',     2, NULL),
    ('RIFT',  'ROAD', 'Nairobi', 'Eldoret',       'RIFT-ROAD',     3, NULL),
    ('RIFT',  'ROAD', 'Eldoret', 'Nairobi',       'RIFT-ROAD',     3, NULL),
    ('RIFT',  'ROAD', 'Nairobi', 'Kisumu',        'RIFT-ROAD',     3, 2),
    ('RIFT',  'ROAD', 'Kisumu',  'Nairobi',       'RIFT-ROAD',     3, NULL),
    ('RIFT',  'ROAD', 'Nairobi', 'Mombasa',       'RIFT-ROAD',     2, 1),
    ('RIFT',  'ROAD', 'Mombasa', 'Nairobi',       'RIFT-ROAD',     2, 1),
    ('RIFT',  'ROAD', 'Nairobi', 'Malindi',       'RIFT-ROAD',     3, NULL),
    ('RIFT',  'ROAD', 'Nairobi', 'Kampala',       'RIFT-REGIONAL', 4, 3),
    ('RIFT',  'ROAD', 'Mombasa', 'Kampala',       'RIFT-REGIONAL', 5, NULL),
    ('RIFT',  'ROAD', 'Nairobi', 'Kigali',        'RIFT-REGIONAL', 6, NULL),
    ('RIFT',  'ROAD', 'Nairobi', 'Dar es Salaam', 'RIFT-REGIONAL', 3, NULL),
    ('COAST', 'ROAD', 'Nairobi', 'Mombasa',       'COAST-CORRIDOR', 3, 2),
    ('COAST', 'ROAD', 'Mombasa', 'Nairobi',       'COAST-CORRIDOR', 3, 2),
    ('COAST', 'ROAD', 'Mombasa', 'Malindi',       'COAST-CORRIDOR', 1, NULL),

    -- Air: up to 1 t per item
    ('KILI',  'AIR', 'Nairobi', 'Kampala',       'KILI-EA',   2, 1),
    ('KILI',  'AIR', 'Nairobi', 'Kigali',        'KILI-EA',   2, 1),
    ('KILI',  'AIR', 'Nairobi', 'Dar es Salaam', 'KILI-EA',   2, 1),
    ('KILI',  'AIR', 'Mombasa', 'Dar es Salaam', 'KILI-EA',   2, 1),
    ('KILI',  'AIR', 'Nairobi', 'Dubai',         'KILI-INTL', 4, 2),
    ('KILI',  'AIR', 'Nairobi', 'Mumbai',        'KILI-INTL', 4, 2),
    ('KILI',  'AIR', 'Nairobi', 'London',        'KILI-INTL', 5, 3),
    ('KILI',  'AIR', 'Nairobi', 'Shanghai',      'KILI-INTL', 6, 3),

    -- Sea: containers up to 26.5 t, for cities separated by sea (no express service)
    ('COAST', 'SEA', 'Mombasa', 'Dar es Salaam', 'COAST-SEA',  4, NULL),
    ('COAST', 'SEA', 'Mombasa', 'Zanzibar',      'COAST-SEA',  5, NULL),
    ('COAST', 'SEA', 'Mombasa', 'Dubai',         'COAST-GULF', 16, NULL),
    ('COAST', 'SEA', 'Mombasa', 'Mumbai',        'COAST-GULF', 14, NULL),
    ('IOCL',  'SEA', 'Mombasa', 'Dubai',         'IOCL-GULF',   14, NULL),
    ('IOCL',  'SEA', 'Mombasa', 'Mumbai',        'IOCL-GULF',   12, NULL),
    ('IOCL',  'SEA', 'Mombasa', 'Shanghai',      'IOCL-ASIA',   28, NULL),
    ('IOCL',  'SEA', 'Mombasa', 'Rotterdam',     'IOCL-EUROPE', 30, NULL),
    ('IOCL',  'SEA', 'Mombasa', 'London',        'IOCL-EUROPE', 32, NULL),
    ('IOCL',  'SEA', 'Nairobi', 'Dubai',         'IOCL-GULF-NBO',   17, NULL),
    ('IOCL',  'SEA', 'Nairobi', 'Mumbai',        'IOCL-GULF-NBO',   15, NULL),
    ('IOCL',  'SEA', 'Nairobi', 'Shanghai',      'IOCL-ASIA-NBO',   31, NULL),
    ('IOCL',  'SEA', 'Nairobi', 'Rotterdam',     'IOCL-EUROPE-NBO', 33, NULL),
    ('IOCL',  'SEA', 'Nairobi', 'London',        'IOCL-EUROPE-NBO', 35, NULL);
