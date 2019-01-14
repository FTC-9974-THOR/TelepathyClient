# Telepathy
Telepathy is an enhanced telemetry system built for [ThorCore](https://github.com/FTC-9974-THOR/ThorCore).

# Running
To run Telepathy, you must have JavaFX installed.

Download the latest JAR release, and run it with the following command:

    java --module-path=[PATH_TO_JAVAFX_SDK_ROOT]/lib --add-modules=javafx.controls,javafx.fxml -jar TelepathyClient-x.x.x.jar

For example, if JavaFX is install to ```/home/thor/dev/javafx-sdk-11.0.2```, and have version 0.2.0 of the Telepathy Client installed:

    java --module-path=/home/thor/dex/javafx-sdk-11.0.2/lib --add-modules=javafx.controls,javafx.fxml -jar TelepathyClient-0.2.0.jar