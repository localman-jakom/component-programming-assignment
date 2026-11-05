package unitconverter.exceptions;

// Thrown when attempting to convert between different categories (e.g., kilograms to metres)
public class IncompatibleUnitException extends UnitConverterException {

    private static final long serialVersionUID = 1L;

    public IncompatibleUnitException(String fromUnit, String fromCategory,
                                     String toUnit, String toCategory) {
        super("Cannot convert " + fromUnit + " (" + fromCategory + ") to "
                + toUnit + " (" + toCategory + "): units are in different categories");
    }
}
