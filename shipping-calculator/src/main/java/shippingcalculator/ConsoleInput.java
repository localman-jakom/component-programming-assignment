package shippingcalculator;

import java.io.InputStream;
import java.io.PrintStream;
import java.util.Locale;
import java.util.Scanner;

// Input handling only: asks questions and re-asks until the answer is well-formed.
// Streams are passed in so tests can use fake input instead of a keyboard.
// Every read method throws NoSuchElementException when the input ends.
final class ConsoleInput {

    private final Scanner scanner;
    private final PrintStream out;

    ConsoleInput(InputStream in, PrintStream out) {
        this.scanner = new Scanner(in);
        this.out = out;
    }

    // A number and a unit, e.g. "2.5 kg". Whether the unit exists is checked later by the library.
    Measurement readMeasurement(String prompt) {
        while (true) {
            out.print(prompt);
            try {
                return Measurement.parse(scanner.nextLine());
            } catch (IllegalArgumentException problem) {
                out.println("  " + problem.getMessage());
            }
        }
    }

    boolean readYesNo(String prompt) {
        while (true) {
            out.print(prompt);
            String answer = scanner.nextLine().trim().toLowerCase(Locale.ROOT);
            if (answer.equals("y") || answer.equals("yes")) {
                return true;
            }
            if (answer.equals("n") || answer.equals("no")) {
                return false;
            }
            out.println("  Please answer y or n.");
        }
    }

    // A whole number from 1 to max
    int readChoice(String prompt, int max) {
        while (true) {
            out.print(prompt);
            String answer = scanner.nextLine().trim();
            try {
                int choice = Integer.parseInt(answer);
                if (choice >= 1 && choice <= max) {
                    return choice;
                }
            } catch (NumberFormatException notANumber) {
                // fall through to the message below
            }
            out.println("  Please enter a number from 1 to " + max + ".");
        }
    }

    // Any non-empty line
    String readText(String prompt) {
        while (true) {
            out.print(prompt);
            String answer = scanner.nextLine().trim();
            if (!answer.isEmpty()) {
                return answer;
            }
            out.println("  Please type something.");
        }
    }
}
