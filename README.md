# Telepathy
Telepathy is an enhanced telemetry system built for [ThorCore](https://github.com/FTC-9974-THOR/ThorCore).

# Dependencies
 * Java 1.10 or higher
 * JavaFX (Telepathy is built with JavaFX 11.0.1, no other versions have been tested)

# Running
Download the latest JAR release, and run it with the following command:

    java --module-path=[PATH_TO_JAVAFX_SDK_ROOT]/lib --add-modules=javafx.controls,javafx.fxml -jar TelepathyClient-x.x.x.jar

For example, if JavaFX is installed to ```/home/thor/dev/javafx-sdk-11.0.1```, and you have version 0.2.0 of the Telepathy Client installed:

    java --module-path=/home/thor/dev/javafx-sdk-11.0.1/lib --add-modules=javafx.controls,javafx.fxml -jar TelepathyClient-0.2.0.jar
