package shippingcalculator;

import java.util.Objects;

// One carrier's service between two cities by one transport mode, priced by one of its tariffs.
// expressDays is null when there is no express service on this route.
record Route(int id, Carrier carrier, TransportMode mode, String origin, String destination,
             String tariffName, int transitDays, Integer expressDays, RateTable rates) {

    Route {
        Objects.requireNonNull(carrier, "carrier");
        Objects.requireNonNull(mode, "mode");
        Objects.requireNonNull(rates, "rates");
        if (transitDays < 1) {
            throw new IllegalArgumentException("Transit days must be at least 1");
        }
    }

    boolean expressAvailable() {
        return expressDays != null;
    }

    int deliveryDays(boolean express) {
        return express && expressAvailable() ? expressDays : transitDays;
    }

    String path() {
        return origin + " -> " + destination;
    }

    String label() {
        return carrier.name() + " (" + path() + ")";
    }
}
