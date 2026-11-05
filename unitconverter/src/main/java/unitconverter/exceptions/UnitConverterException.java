package unitconverter.exceptions;

// Unchecked base exception for all errors thrown by the unit converter library
public class UnitConverterException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public UnitConverterException(String message) {
        super(message);
    }
}
