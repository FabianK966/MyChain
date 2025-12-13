package org.fintech;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.chart.LineChart;
import javafx.scene.chart.NumberAxis;
import javafx.scene.chart.XYChart;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.Stage;
import java.util.*;

public class MyChainGUI extends Application {
    public static final String EXCHANGE_ADDRESS = "EXCHANGE_MARKET_SC_SELL";

    // GUI-Komponenten
    private Blockchain blockchain;
    private ListView<String> blockList;
    private TextArea detailsArea;
    private ComboBox<String> fromCombo;
    private ComboBox<String> toCombo;
    private NetworkSimulator networkSimulator;
    private PriceSimulator priceSimulator;
    private Label currentPriceLabel;
    private Label biasLabel;
    private LineChart<Number, Number> priceChart;
    private XYChart.Series<Number, Number> series;
    private long timeIndex = 0;
    private static Stage primaryStage;

    // Buttons
    private Button stopWalletGenBtn;
    private Button startWalletGenBtn;
    private Button simulationBtn;

    // Manager-Klassen
    private WalletListViewManager walletListViewManager;
    private final Wallet loggedInWallet;
    private static MyChainGUI instance;

    public static double getCurrentCoinPrice() {
        if (instance != null && instance.priceSimulator != null) {
            return instance.priceSimulator.getCurrentPrice();
        }
        return 1.00;
    }

    public MyChainGUI(Wallet loggedInWallet) {
        this.loggedInWallet = loggedInWallet;
        WalletManager.loadWallets();
    }

    public MyChainGUI() {
        this(null);
    }

    @Override
    public void start(Stage stage) {
        instance = this;
        primaryStage = stage;

        initializeComponents();
        setupNetworkSimulator();
        createGUI(stage);

        stage.show();
    }

    private void initializeComponents() {
        fromCombo = new ComboBox<>();
        toCombo = new ComboBox<>();

        blockchain = BlockchainPersistence.loadBlockchain("MyChain", 1);
        double initialPrice = PriceSimulator.loadPrice(1.00);
        this.priceSimulator = new PriceSimulator(initialPrice);

        networkSimulator = new NetworkSimulator(blockchain, WalletManager.INSTANCE, priceSimulator);
    }

    private void setupNetworkSimulator() {
        networkSimulator.setOnUpdate(() -> {
            // 🔧 PERFORMANCE: Wallet-Liste wird jetzt mit Caching aktualisiert
            if (walletListViewManager != null) {
                walletListViewManager.updateWalletList();
            }
            updateComboBoxes();
            updateBlockList();
            Platform.runLater(this::updatePriceChart);
            if (!blockchain.getChain().isEmpty()) {
                blockList.getSelectionModel().select(blockchain.getChain().size() - 1);
            }
        });

        networkSimulator.setOnPriceUpdate(this::updatePriceLabel);
    }

    private void createGUI(Stage stage) {
        stage.setTitle("SimpleCoin Explorer – Deine eigene Kryptowährung");
        BorderPane root = new BorderPane();
        root.setPadding(new Insets(15));

        // Top: Preis-Label
        currentPriceLabel = new Label("SC Preis: 1.00 USD");
        currentPriceLabel.setStyle("-fx-font-weight: bold; -fx-font-size: 1.2em; -fx-padding: 0 0 10 0;");
        HBox topControls = new HBox(10, currentPriceLabel);
        root.setTop(topControls);

        // Left: Block-Liste
        blockList = new ListView<>();
        blockList.setPrefWidth(320);
        updateBlockList();
        blockList.getSelectionModel().selectedItemProperty().addListener((obs, old, newVal) -> {
            if (newVal != null) showBlockDetails(newVal);
        });
        VBox leftPanel = new VBox(10, new Label("Blöcke in der Chain:"), blockList);

        // Center: Details, Chart, Controls, Wallet-Liste
        VBox rightPanel = createRightPanel();

        root.setLeft(leftPanel);
        root.setCenter(rightPanel);

        Scene scene = new Scene(root, 1500, 800);
        stage.setScene(scene);

        setupStageCloseHandler(stage);
        updateComboBoxes();

        if (loggedInWallet != null) {
            fromCombo.setValue(loggedInWallet.getAddress());
            stage.setTitle("SimpleCoin Explorer – Eingeloggt als: " + loggedInWallet.getAddress().substring(0, 16) + "...");
        }

        if (!blockList.getItems().isEmpty()) {
            blockList.getSelectionModel().select(0);
        }
    }

    private VBox createRightPanel() {
        detailsArea = new TextArea();
        detailsArea.setEditable(false);
        detailsArea.setPrefHeight(300);
        detailsArea.setStyle("-fx-font-family: 'Consolas'; -fx-font-size: 12;");

        VBox priceChartBox = createPriceChart();

        // Wallet-Liste mit Manager
        ListView<String> walletList = new ListView<>();
        walletList.setPrefHeight(150);

        ComboBox<String> sortKeyCombo = new ComboBox<>();
        Button sortDirectionButton = new Button("↓ Desc");

        // 🔧 PERFORMANCE: WalletListViewManager verwenden
        walletListViewManager = new WalletListViewManager(walletList, sortKeyCombo, sortDirectionButton, loggedInWallet);
        walletListViewManager.setupWalletDoubleClick();

        HBox sortControls = new HBox(10, new Label("Sortieren nach:"), sortKeyCombo, sortDirectionButton);
        sortControls.setPadding(new Insets(0, 0, 5, 0));

        // Buttons
        HBox walletButtons = createWalletButtons(walletList);

        // Bias Controls
        biasLabel = new Label("Kaufinteresse (Bias): 50% (50:50)");
        biasLabel.setStyle("-fx-font-weight: bold;");
        Slider biasSlider = createBiasSlider();
        VBox biasControl = new VBox(5, biasLabel, biasSlider);
        biasControl.setPadding(new Insets(0, 0, 10, 0));

        // Wallet Box
        VBox walletBox = new VBox(10,
                new Label("Wallet-Übersicht:"),
                walletList,
                sortControls,
                walletButtons
        );
        walletBox.setStyle("-fx-border-color: #ccc; -fx-border-radius: 5; -fx-padding: 10;");

        return new VBox(15,
                new Label("Block-Details:"), detailsArea,
                new Separator(),
                new Label("Live Preis Chart:"), priceChartBox,
                new Separator(),
                new Label("Marktstimmung steuern:"),
                biasControl,
                walletBox
        );
    }

    private HBox createWalletButtons(ListView<String> walletList) {
        Button newWalletBtn = new Button("Neue Wallet erstellen");
        newWalletBtn.setStyle("-fx-background-color: #2196F3; -fx-text-fill: white; -fx-font-weight: bold; -fx-padding: 10;");
        newWalletBtn.setOnAction(e -> createNewWallet(walletList));

        simulationBtn = new Button("Netzwerk simulieren");
        simulationBtn.setStyle("-fx-background-color: #e74c3c; -fx-text-fill: white; -fx-font-weight: bold; -fx-padding: 10;");
        simulationBtn.setOnAction(e -> toggleSimulation());

        startWalletGenBtn = new Button("Wallet-Gen. starten");
        startWalletGenBtn.setStyle("-fx-background-color: #27ae60; -fx-text-fill: white; -fx-font-weight: bold; -fx-padding: 10;");
        startWalletGenBtn.setDisable(true);
        startWalletGenBtn.setOnAction(e -> startWalletGeneration());

        stopWalletGenBtn = new Button("Wallet-Gen. stoppen");
        stopWalletGenBtn.setStyle("-fx-background-color: #f39c12; -fx-text-fill: white; -fx-font-weight: bold; -fx-padding: 10;");
        stopWalletGenBtn.setDisable(true);
        stopWalletGenBtn.setOnAction(e -> stopWalletGeneration());

        Button logoutBtn = new Button("Logout");
        logoutBtn.setOnAction(e -> logoutAndRestart());

        return new HBox(10, newWalletBtn, simulationBtn, stopWalletGenBtn, startWalletGenBtn, logoutBtn);
    }

    private Slider createBiasSlider() {
        Slider biasSlider = new Slider(0.0, 1.0, 0.5);
        biasSlider.setShowTickMarks(true);
        biasSlider.setMajorTickUnit(0.1);
        biasSlider.setMinorTickCount(9);
        biasSlider.setSnapToTicks(true);
        biasSlider.setPrefWidth(300);

        biasSlider.valueProperty().addListener((obs, oldVal, newVal) -> {
            networkSimulator.setBuyBias(newVal.doubleValue());
            double buyPct = newVal.doubleValue() * 100;
            double sellPct = 100 - buyPct;
            biasLabel.setText(String.format("Kaufinteresse (Bias): %.0f%% (%.0f:%.0f)", buyPct, buyPct, sellPct));
        });

        return biasSlider;
    }

    // ========== HELPER METHODS ==========

    private void createNewWallet(ListView<String> walletList) {
        Wallet w = WalletManager.createWallet(blockchain, WalletManager.SUPPLY_WALLET);

        // 🔧 PERFORMANCE: Cache invalidieren
        if (walletListViewManager != null) {
            walletListViewManager.invalidateCache();
            walletListViewManager.updateWalletList();
        }

        updateComboBoxes();

        String scBalance = w.getBalance() > 0 ? String.format("\nInitial SC Grant: %.1f SC (vom Supply abgezogen)", w.getBalance()) : "";
        new Alert(Alert.AlertType.INFORMATION,
                w.getAddress() +
                        "\nStartguthaben: " + String.format("%.2f", w.getUsdBalance()) + " USD" +
                        scBalance,
                ButtonType.OK).showAndWait();
    }

    private void toggleSimulation() {
        if (networkSimulator.isRunning()) {
            networkSimulator.stop();
            simulationBtn.setText("Netzwerk simulieren");
            simulationBtn.setStyle("-fx-background-color: #e74c3c; -fx-text-fill: white; -fx-font-weight: bold; -fx-padding: 10;");
            stopWalletGenBtn.setDisable(true);
            startWalletGenBtn.setDisable(true);
        } else {
            networkSimulator.start();
            simulationBtn.setText("Simulation stoppen");
            simulationBtn.setStyle("-fx-background-color: #27ae60; -fx-text-fill: white; -fx-font-weight: bold; -fx-padding: 10;");
            stopWalletGenBtn.setDisable(false);
            startWalletGenBtn.setDisable(true);
        }
    }

    private void startWalletGeneration() {
        if (networkSimulator.isRunning()) {
            networkSimulator.startWalletGeneration();
            startWalletGenBtn.setDisable(true);
            stopWalletGenBtn.setDisable(false);
        }
    }

    private void stopWalletGeneration() {
        if (networkSimulator.isRunning()) {
            networkSimulator.stopWalletGeneration();
            stopWalletGenBtn.setDisable(true);
            startWalletGenBtn.setDisable(false);
        }
    }

    private void logoutAndRestart() {
        if (networkSimulator != null) {
            networkSimulator.stop();
        }
        WalletManager.saveWallets();

        if (primaryStage != null) {
            primaryStage.close();
        }
    }

    private void updatePriceLabel() {
        if (priceSimulator != null) {
            currentPriceLabel.setText(String.format("SC Preis: %.4f USD", priceSimulator.getCurrentPrice()));
        }
    }

    private void updateBlockList() {
        blockList.getItems().clear();
        int i = 0;
        for (Block b : blockchain.getChain()) {
            blockList.getItems().add(String.format("Block #%d | %.16s... | %d Tx | Nonce: %d",
                    i++, b.getHash(), b.getTransactions().size(), b.getNonce()));
        }
    }

    private void showBlockDetails(String selected) {
        int idx = blockList.getSelectionModel().getSelectedIndex();
        if (idx < 0) return;

        Block block = blockchain.getChain().get(idx);
        StringBuilder sb = new StringBuilder();
        sb.append("BLOCK #").append(idx).append("\n");
        sb.append("Hash:          ").append(block.getHash()).append("\n")
                .append("Previous Hash: ").append(block.getPreviousHash()).append("\n")
                .append("Timestamp:     ").append(new Date(block.getTimeStamp())).append("\n")
                .append("Nonce:         ").append(block.getNonce()).append("\n")
                .append("Transaktionen: ").append(block.getTransactions().size()).append("\n")
                .append("═".repeat(70)).append("\n\n");

        for (Transaction tx : block.getTransactions()) {
            sb.append("TX ").append(tx.getTxId().substring(0, 8)).append("...\n");
            sb.append("   Von:  ").append(tx.getSender().length() > 34 ? tx.getSender().substring(0, 34) + "..." : tx.getSender()).append("\n");
            sb.append("   An:   ").append(tx.getRecipient().length() > 34 ? tx.getRecipient().substring(0, 34) + "..." : tx.getRecipient()).append("\n");
            sb.append("   Betrag: ").append(String.format("%.3f", tx.getAmount())).append(" SC\n");
            sb.append("   Nachricht: ").append(tx.getMessage().isEmpty() ? "(keine)" : tx.getMessage()).append("\n\n");
        }
        detailsArea.setText(sb.toString());
    }

    private VBox createPriceChart() {
        final NumberAxis xAxis = new NumberAxis();
        final NumberAxis yAxis = new NumberAxis();
        xAxis.setLabel("Zeit (in Updates)");
        yAxis.setLabel("Preis (USD)");
        yAxis.setForceZeroInRange(false);

        priceChart = new LineChart<>(xAxis, yAxis);
        priceChart.setTitle("Live SC Preisentwicklung (1-Sekunden-Takt)");
        priceChart.setPrefHeight(300);
        priceChart.setAnimated(false);
        priceChart.setLegendVisible(false);

        series = new XYChart.Series<>();
        series.setName("SC Preis");
        priceChart.getData().add(series);
        series.getData().add(new XYChart.Data<>(timeIndex, priceSimulator.getCurrentPrice()));

        return new VBox(priceChart);
    }

    private void updatePriceChart() {
        if (priceChart != null) {
            timeIndex++;
            double currentPrice = priceSimulator.getCurrentPrice();
            series.getData().add(new XYChart.Data<>(timeIndex, currentPrice));

            if (series.getData().size() > 1000) {
                series.getData().remove(0);
                ((NumberAxis) priceChart.getXAxis()).setLowerBound(timeIndex - 1000);
                ((NumberAxis) priceChart.getXAxis()).setUpperBound(timeIndex);
            }
        }
    }

    private void updateComboBoxes() {
        List<String> addresses = WalletManager.getWallets().stream().map(Wallet::getAddress).toList();

        if (fromCombo == null || toCombo == null) {
            return;
        }

        fromCombo.getItems().setAll(addresses);

        List<String> toAddresses = new ArrayList<>(addresses);
        toAddresses.add(EXCHANGE_ADDRESS);
        toCombo.getItems().setAll(toAddresses);

        if (loggedInWallet != null) {
            if (fromCombo.getItems().contains(loggedInWallet.getAddress())) {
                fromCombo.setValue(loggedInWallet.getAddress());
            }

            String firstAddress = loggedInWallet.getAddress();
            String secondAddress = addresses.stream()
                    .filter(addr -> !addr.equals(firstAddress))
                    .findFirst()
                    .orElse(null);

            if (secondAddress != null) {
                toCombo.setValue(secondAddress);
            }
        } else if (!addresses.isEmpty()) {
            fromCombo.setValue(addresses.get(0));
            if (addresses.size() > 1) toCombo.setValue(addresses.get(1));
        }
    }

    private void setupStageCloseHandler(Stage stage) {
        stage.setOnCloseRequest(e -> {
            PriceSimulator.savePrice(priceSimulator.getCurrentPrice());
            WalletManager.saveWallets();
            if (networkSimulator != null) networkSimulator.stop();
        });
    }

    public static void main(String[] args) {
        launch();
    }
}