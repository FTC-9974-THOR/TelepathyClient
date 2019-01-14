package org.ftc9974.thorcore.telepathyclient;

import javafx.animation.Animation;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.beans.value.ObservableValue;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.chart.*;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.layout.*;
import javafx.stage.Screen;
import javafx.stage.Stage;
import javafx.util.Duration;
import net.sourceforge.argparse4j.ArgumentParsers;
import net.sourceforge.argparse4j.inf.ArgumentParser;
import net.sourceforge.argparse4j.inf.ArgumentParserException;
import net.sourceforge.argparse4j.inf.Namespace;
import org.slf4j.simple.SimpleLogger;
import org.slf4j.simple.SimpleLoggerFactory;

import javax.swing.text.TabExpander;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

public class Main extends Application {

    private static SimpleLogger logger;

    private HashMap<String, Label> labels;
    private HashMap<String, LineChart<Number, Number>> charts;
    private HashMap<String, TelepathyAPI.Message> latestMessages;

    @FXML
    private GridPane telemetryContainer;

    @FXML
    private ScrollPane scrollPane;

    @FXML
    private Label messageLabel;

    @FXML
    private AnchorPane root;

    @FXML
    private Button connectButton;

    private static double SECONDS_BETWEEN_RETRY;
    private static String IP_ADDRESS;
    private static int PORT;

    private static String VERSION = "0.2.0";

    private AtomicBoolean telepathyInitialized, connected;
    private Timeline connectTimeline;

    @Override
    public void start(Stage stage) throws Exception {
        logger.info(String.format("IP address: %s", IP_ADDRESS));
        logger.info(String.format("Port: %d", PORT));
        telepathyInitialized = new AtomicBoolean(false);
        stage.setOnCloseRequest(actionEvent -> {
            logger.info("Exiting");
            if (telepathyInitialized.get()) {
                TelepathyAPI.shutdown();
            }
            Platform.exit();
            System.exit(0);
        });
        stage.getIcons().add(new Image(getClass().getResourceAsStream("/telepathylogo.png")));
        stage.setMinWidth(300);
        stage.setMinHeight(300);
        stage.setScene(new Scene(FXMLLoader.load(Objects.requireNonNull(getClass().getClassLoader().getResource("TelemetryScreen.fxml"))), 600, 400));
        stage.setTitle("Telepathy Client (v" + VERSION + ")");
        stage.show();
    }

    public void initialize() {
        labels = new HashMap<>();
        charts = new HashMap<>();
        latestMessages = new HashMap<>();

        scrollPane.setVisible(false);

        connected = new AtomicBoolean(false);

        telemetryContainer.getColumnConstraints().clear();
        telemetryContainer.getColumnConstraints().addAll(new ColumnConstraints(300), new ColumnConstraints(300));
        telemetryContainer.getRowConstraints().clear();

        connectTimeline = new Timeline();
        connectTimeline.setCycleCount(Animation.INDEFINITE);
        connectTimeline.getKeyFrames().add(new KeyFrame(Duration.seconds(1), actionEvent -> tryConnect()));
        connectTimeline.getKeyFrames().add(new KeyFrame(Duration.seconds(SECONDS_BETWEEN_RETRY), actionEvent -> {}));
        connectTimeline.play();

        root.widthProperty().addListener(this::onWidthChanged);
    }

    @FXML
    private synchronized void tryConnect() {
        logger.info("Attempting to connect");
        try {
            TelepathyAPI.initialize(IP_ADDRESS, PORT);
            TelepathyAPI.addNewMessageListener(this::onMessageReceived);
            TelepathyAPI.addDisconnectionListener(this::onDisconnect);
            messageLabel.setText("Connected, but no messages yet");
            connectButton.setVisible(false);
            connectTimeline.stop();
            connected.set(true);
        } catch (Exception e) {
            if (SECONDS_BETWEEN_RETRY == (long) SECONDS_BETWEEN_RETRY) {
                logger.error(String.format("Connection failed. Check your wireless connection. Retrying in %ds", (long) SECONDS_BETWEEN_RETRY));
            } else {
                logger.error(String.format("Connection failed. Check your wireless connection. Retrying in %fs", SECONDS_BETWEEN_RETRY));
            }
        }
    }

    private void onDisconnect() {
        logger.warn("Telepathy server closed unexpectedly");
        Platform.runLater(() -> {
            connected.set(false);
            messageLabel.setText("No connection");
            scrollPane.setVisible(false);
            telemetryContainer.getChildren().clear();
            connectButton.setVisible(true);
        });
    }

    private void onMessageReceived(TelepathyAPI.Message message) {
        if (connected.get()) {
            for (byte b : message.raw) {
                logger.info("{}", b);
            }
            latestMessages.put(message.key, message);
            Platform.runLater(() -> {
                if (!scrollPane.isVisible()) {
                    scrollPane.setVisible(true);
                }

                if (labels.containsKey(message.key) || charts.containsKey(message.key)) {
                    updateNode(message);
                } else {
                    addNode(message, false);
                }
            });
        }
    }

    private void addNode(TelepathyAPI.Message message, boolean showAsGraph) {
        logger.info("Adding Node: {} Graph: {} Type: {}", message.key, showAsGraph, message.type);
        int row = telemetryContainer.getRowCount();
        if (showAsGraph) {
            if (message.type == TelepathyAPI.Type.STRING || message.type == TelepathyAPI.Type.CHAR) {
                logger.error("STRING/CHAR cannot be graphed");
                return;
            }

            Label keyLabel = new Label(message.key);
            keyLabel.setPadding(new Insets(10));
            keyLabel.setPrefWidth(300);
            telemetryContainer.add(keyLabel, 0, row);

            LineChart<Number, Number> chart = new LineChart<>(new NumberAxis(), new NumberAxis());
            XYChart.Series<Number, Number> series = new XYChart.Series<>();
            double dataPoint = 0;
            switch (message.type) {
                case BYTE:
                    dataPoint = (double) ((byte) message.message);
                    break;
                case INT:
                    dataPoint = (double) ((int) message.message);
                    break;
                case SHORT:
                    dataPoint = (double) ((short) message.message);
                    break;
                case FLOAT:
                    dataPoint = (double) ((float) message.message);
                    break;
                case LONG:
                    dataPoint = (double) ((long) message.message);
                    break;
                case DOUBLE:
                    dataPoint = (double) message.message;
                    break;
            }
            series.getData().add(new XYChart.Data<>(0, dataPoint));
            chart.getData().add(series);

            ContextMenu contextMenu = new ContextMenu();
            MenuItem showAsText = new MenuItem("Show as text");
            contextMenu.getItems().add(showAsText);

            showAsText.setOnAction((event) -> {
                Integer currentRow = GridPane.getRowIndex(keyLabel);
                if (currentRow == null) {
                    currentRow = 0;
                }
                logger.info("Moving row to {} from {}", currentRow, telemetryContainer.getRowCount() - 1);
                removeNode(message.key);
                addNode(latestMessages.get(message.key), false);
                moveRowInPlace(telemetryContainer, telemetryContainer.getRowCount() - 1, currentRow);
                if (currentRow % 2 == 1) {
                    setRowStyle(telemetryContainer, currentRow, "-fx-background-color: #a1a1a1;");
                } else {
                    setRowStyle(telemetryContainer, currentRow, "");
                }
            });

            keyLabel.setContextMenu(contextMenu);

            if (row % 2 == 1) {
                keyLabel.setStyle("-fx-background-color: #a1a1a1;");
                chart.setStyle("-fx-background-color: #a1a1a1;");
            }

            telemetryContainer.add(chart, 1, row);

            charts.put(message.key, chart);
        } else {
            Label keyLabel = new Label(message.key);
            keyLabel.setPadding(new Insets(10));
            keyLabel.setPrefWidth(300);
            telemetryContainer.add(keyLabel, 0, row);

            Label valueLabel = new Label(message.message.toString());
            valueLabel.setPadding(new Insets(10, 0, 10, 0));
            valueLabel.setMinWidth(300);

            if (!(message.type == TelepathyAPI.Type.STRING || message.type == TelepathyAPI.Type.CHAR)) {
                ContextMenu contextMenu = new ContextMenu();
                MenuItem showAsGraphItem = new MenuItem("Show as graph");
                contextMenu.getItems().add(showAsGraphItem);

                showAsGraphItem.setOnAction((event) -> {
                    Integer currentRow = GridPane.getRowIndex(keyLabel);
                    if (currentRow == null) {
                        currentRow = 0;
                    }
                    logger.info("Moving row to {} from {}", currentRow, telemetryContainer.getRowCount() - 1);
                    removeNode(message.key);
                    addNode(latestMessages.get(message.key), true);
                    moveRowInPlace(telemetryContainer, telemetryContainer.getRowCount() - 1, currentRow);
                    if (currentRow % 2 == 1) {
                        setRowStyle(telemetryContainer, currentRow, "-fx-background-color: #a1a1a1;");
                    } else {
                        setRowStyle(telemetryContainer, currentRow, "");
                    }
                });

                keyLabel.setContextMenu(contextMenu);
            }

            telemetryContainer.add(valueLabel, 1, row);

            if (row % 2 == 1) {
                keyLabel.setStyle("-fx-background-color: #a1a1a1;");
                valueLabel.setStyle("-fx-background-color: #a1a1a1;");
            }

            labels.put(message.key, valueLabel);
        }
    }

    private void updateNode(TelepathyAPI.Message message) {
        if (labels.containsKey(message.key)) {
            labels.get(message.key).setText(message.message.toString());
        } else if (charts.containsKey(message.key)) {
            XYChart.Series<Number, Number> series = charts.get(message.key).getData().get(0);
            double dataPoint = 0;
            switch (message.type) {
                case BYTE:
                    dataPoint = (double) ((byte) message.message);
                    break;
                case INT:
                    dataPoint = (double) ((int) message.message);
                    break;
                case SHORT:
                    dataPoint = (double) ((short) message.message);
                    break;
                case FLOAT:
                    dataPoint = (double) ((float) message.message);
                    break;
                case LONG:
                    dataPoint = (double) ((long) message.message);
                    break;
                case DOUBLE:
                    dataPoint = (double) message.message;
                    break;
            }
            series.getData().add(new XYChart.Data<>(series.getData().size(), dataPoint));
        } else {
            logger.warn("Update on nonexistent node: {}", message.key);
        }
    }

    private void removeNode(String key) {
        Integer row = 0;
        if (labels.containsKey(key)) {
            row = GridPane.getRowIndex(labels.get(key));
        } else if (charts.containsKey(key)) {
            row = GridPane.getRowIndex(charts.get(key));
        }
        if (row == null) {
            row = 0;
        }
        deleteRowInPlace(telemetryContainer, row);
        labels.remove(key);
        charts.remove(key);
    }

    // via https://stackoverflow.com/a/40517410
    private void deleteRowInPlace(GridPane grid, final int row) {
        Set<Node> deleteNodes = new HashSet<>();
        for (Node child : grid.getChildren()) {
            // get index from child
            Integer rowIndex = GridPane.getRowIndex(child);

            // handle null values for index=0
            int r = rowIndex == null ? 0 : rowIndex;

            if (r == row) {
                // collect matching rows for deletion
                deleteNodes.add(child);
            }
        }

        // remove nodes from row
        grid.getChildren().removeAll(deleteNodes);
    }

    private void moveRowInPlace(GridPane grid, final int from, final int to) {
        for (Node child : grid.getChildren()) {
            Integer rowIndex = GridPane.getRowIndex(child);

            int r = rowIndex == null ? 0 : rowIndex;

            if (r == from) {
                GridPane.setRowIndex(child, to);
            }
        }
    }

    private void setRowStyle(GridPane grid, int row, String style) {
        for (Node child : grid.getChildren()) {
            Integer rowIndex = GridPane.getRowIndex(child);

            int r = rowIndex == null ? 0 : rowIndex;

            if (r == row) {
                child.setStyle(style);
            }
        }
    }

    public static void main(String[] args) {
        SimpleLoggerFactory simpleLoggerFactory = new SimpleLoggerFactory();
        logger = (SimpleLogger) simpleLoggerFactory.getLogger("Main");
        logger.info("Telepathy Client v" + VERSION + "; Initializing");
        ArgumentParser parser = ArgumentParsers.newFor("Telepathy Client").build()
                .defaultHelp(true)
                .description("Client for ThorCore's Telepathy. Serves as a more advanced form of telemetry.")
                .version(VERSION);
        parser.addArgument("-d", "--retry-delay")
                .help("Delay (in seconds) between retrying connection.")
                .dest("rd")
                .setDefault(20d)
                .type(Double.class);
        parser.addArgument("-a", "--ip-address")
                .help("IP Address to connect to.")
                .dest("ip")
                .setDefault("192.168.49.1")
                .type(String.class);
        parser.addArgument("-p", "--port")
                .help("Port to connect to.")
                .dest("port")
                .setDefault(6387)
                .type(Integer.class);
        Namespace ns;
        try {
            ns = parser.parseArgs(args);
        } catch (ArgumentParserException e) {
            parser.handleError(e);
            System.exit(1);
            return;
        }
        SECONDS_BETWEEN_RETRY = ns.getDouble("rd");
        IP_ADDRESS = ns.getString("ip");
        PORT = ns.getInt("port");
        /*Thread testServerThread = new Thread(() -> {
            try {
                TestServer.main(args);
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
        testServerThread.start();*/
        Application.launch(Main.class, args);
    }

    private void onWidthChanged(ObservableValue<? extends Number> observableValue, Number number, Number t1) {
        Platform.runLater(() -> {
            logger.info("Resize event");
            if (telemetryContainer != null) {
                for (Node child : telemetryContainer.getChildren()) {
                    Integer col = GridPane.getColumnIndex(child);

                    int c = col == null ? 0 : col;

                    if (c == 1) {
                        ((Region) child).setPrefWidth(observableValue.getValue().doubleValue() - 260);
                    }
                }
                telemetryContainer.getColumnConstraints().get(1).setPrefWidth(observableValue.getValue().doubleValue() - 280);
            }
        });
    }
}
