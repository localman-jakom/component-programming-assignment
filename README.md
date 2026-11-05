# Component Programming Assignment (SCO310)

Two Maven projects:

| Folder | What it is |
|---|---|
| `unitconverter/` | The reusable component: a unit converter library packaged as `unitconverter-1.0.jar` |
| `shipping-calculator/` | A shipping quotation app that reuses the component (window, console and command-line modes, H2 database) |

## Build

Requires JDK 17+ and Maven. Build the component first, because the app gets it from the local Maven repository.

```
cd unitconverter
mvn clean install

cd ../shipping-calculator
mvn clean package
```

Tests run during the build. Coverage reports: `target/site/jacoco/index.html` in each project.

## Run

```
cd shipping-calculator
java -jar target/shipping-calculator-1.0.jar             # window
java -jar target/shipping-calculator-1.0.jar --console   # text version
java -jar target/shipping-calculator-1.0.jar --help      # command-line options
```

The database is created on first run in `~/.shipping-calculator/` (on Windows `C:\Users\<you>\.shipping-calculator\`).
