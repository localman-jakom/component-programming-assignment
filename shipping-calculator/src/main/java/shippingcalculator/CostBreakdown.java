package shippingcalculator;

import java.math.BigDecimal;

// Every figure for one parcel, kept together so the receipt can show the full working.
// Money is BigDecimal so totals are exact to the cent.
record CostBreakdown(double actualWeightKg,
                     double volumetricWeightKg,
                     double chargeableWeightKg,
                     BigDecimal baseCost,
                     BigDecimal fragileSurcharge,
                     BigDecimal expressSurcharge) {

    BigDecimal total() {
        return baseCost.add(fragileSurcharge).add(expressSurcharge);
    }

    boolean volumetricWeightApplies() {
        return volumetricWeightKg > actualWeightKg;
    }
}
