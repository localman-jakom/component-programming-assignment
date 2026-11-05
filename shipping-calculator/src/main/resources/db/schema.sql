-- Shipping lines, the tariffs (price lists) they use, the routes they serve and saved quotes.
-- Saved quotes copy the carrier and route names, so they survive when the example data is reloaded.

CREATE TABLE IF NOT EXISTS app_info (
    info_name  VARCHAR(40) PRIMARY KEY,
    info_value VARCHAR(80) NOT NULL
);

CREATE TABLE IF NOT EXISTS carrier (
    code         VARCHAR(10)   PRIMARY KEY,
    name         VARCHAR(80)   NOT NULL,
    fragile_fee  DECIMAL(10,2) NOT NULL CHECK (fragile_fee >= 0),
    express_rate DECIMAL(5,4)  NOT NULL CHECK (express_rate >= 0)
);

CREATE TABLE IF NOT EXISTS tariff (
    code         VARCHAR(20)   PRIMARY KEY,
    carrier_code VARCHAR(10)   NOT NULL REFERENCES carrier(code),
    description  VARCHAR(80)   NOT NULL,
    extra_per_kg DECIMAL(10,2) NOT NULL CHECK (extra_per_kg >= 0)
);

CREATE TABLE IF NOT EXISTS tariff_band (
    tariff_code VARCHAR(20)   NOT NULL REFERENCES tariff(code),
    up_to_kg    DECIMAL(10,2) NOT NULL CHECK (up_to_kg > 0),
    price       DECIMAL(12,2) NOT NULL CHECK (price > 0),
    PRIMARY KEY (tariff_code, up_to_kg)
);

-- mode is COURIER, AIR, ROAD or SEA (see TransportMode.java for the limits of each)
CREATE TABLE IF NOT EXISTS route (
    id           INT AUTO_INCREMENT PRIMARY KEY,
    carrier_code VARCHAR(10) NOT NULL REFERENCES carrier(code),
    mode         VARCHAR(10) NOT NULL CHECK (mode IN ('COURIER', 'AIR', 'ROAD', 'SEA')),
    origin       VARCHAR(40) NOT NULL,
    destination  VARCHAR(40) NOT NULL,
    tariff_code  VARCHAR(20) NOT NULL REFERENCES tariff(code),
    transit_days INT         NOT NULL CHECK (transit_days > 0),
    express_days INT         CHECK (express_days > 0),
    CONSTRAINT route_unique UNIQUE (carrier_code, mode, origin, destination)
);

CREATE TABLE IF NOT EXISTS quote (
    id            BIGINT AUTO_INCREMENT PRIMARY KEY,
    created_at    TIMESTAMP     NOT NULL,
    carrier_code  VARCHAR(10)   NOT NULL,
    carrier_name  VARCHAR(80)   NOT NULL,
    mode          VARCHAR(10),
    origin        VARCHAR(40)   NOT NULL,
    destination   VARCHAR(40)   NOT NULL,
    parcel_count  INT           NOT NULL,
    chargeable_kg DECIMAL(12,2) NOT NULL,
    fragile       BOOLEAN       NOT NULL,
    express       BOOLEAN       NOT NULL,
    total         DECIMAL(14,2) NOT NULL,
    receipt       CLOB          NOT NULL
);
