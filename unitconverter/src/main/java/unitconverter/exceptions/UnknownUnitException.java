package unitconverter.exceptions;

// Thrown when an input unit name or alias is unrecognized, null, or empty
public class UnknownUnitException extends UnitConverterException {

    private static final long serialVersionUID = 1L;

    private final String unitName;

    public UnknownUnitException(String unitName) {
        super("Unknown unit: '" + unitName + "'");
        this.unitName = unitName;
    }

    public String getUnitName() {
        return unitName;
    }
}
