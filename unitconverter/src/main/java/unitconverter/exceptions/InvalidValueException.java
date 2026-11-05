package unitconverter.exceptions;

// Thrown when an input or result value is infinite, below absolute zero, or overflows
public class InvalidValueException extends UnitConverterException {

    private static final long serialVersionUID = 1L;

    public InvalidValueException(String message) {
        super(message);
    }
}