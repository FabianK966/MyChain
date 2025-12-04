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
import java.util.List;
import java.util.Comparator;

public class MyChainGUI extends Application {

    // Wichtige Konstante für SC-Verkäufe (zum Verbrennen der Coins)
    public static final String EXCHANGE_ADDRESS = "EXCHANGE_MARKET_SC_SELL";

    private Blockchain blockchain;
    private ListView<String> blockList;
    private TextArea detailsArea;
    private ListView<String> walletList;
    // Felder beibehalten, da sie in updateComboBoxes() verwendet werden
    private ComboBox<String> fromCombo;
    private ComboBox<String> toCombo;
    private NetworkSimulator networkSimulator;
    private WalletManager WalletManager = org.fintech.WalletManager.INSTANCE;
    private static Stage primaryStage;
    private Button stopWalletGenBtn;
    private Button startWalletGenBtn;
    private PriceSimulator priceSimulator;
    private Label currentPriceLabel;
    private Label biasLabel;
    private LineChart<Number, Number> priceChart;
    private XYChart.Series<Number, Number> series;
    private long timeIndex = 0;

    private final Wallet loggedInWallet;

    // NEUE FELDER FÜR SORTIERUNG
    private ComboBox<String> sortKeyCombo;
    private Button sortDirectionButton;
    private boolean isAscending = false;


    public MyChainGUI(Wallet loggedInWallet) {
        this.loggedInWallet = loggedInWallet;
        org.fintech.WalletManager.loadWallets();
    }

    public MyChainGUI() {
        this.loggedInWallet = null;
        org.fintech.WalletManager.loadWallets();
    }

    @Override
    public void start(Stage stage) {
        primaryStage = stage;

        // 🛑 KORREKTUR 1: Initialisierung der ComboBoxen zur Vermeidung von NullPointerException
        fromCombo = new ComboBox<>();
        toCombo = new ComboBox<>();
        // ------------------------------------------------------------------------------------

        blockchain = BlockchainPersistence.loadBlockchain("MyChain", 1);
        double initialPrice = PriceSimulator.loadPrice(1.0);
        this.priceSimulator = new PriceSimulator(initialPrice);

        networkSimulator = new NetworkSimulator(blockchain, WalletManager, priceSimulator);

        // 🛑 KORREKTUR: Update-Logik um Chart-Aktualisierung erweitert
        networkSimulator.setOnUpdate(() -> {
            updateWalletList();
            updateComboBoxes();
            updateBlockList();
            // Chart-Update im FX-Thread
            Platform.runLater(this::updatePriceChart);
            if (!blockchain.getChain().isEmpty()) {
                blockList.getSelectionModel().select(blockchain.getChain().size() - 1);
            }
        });

        networkSimulator.setOnPriceUpdate(this::updatePriceLabel);

        stage.setTitle("SimpleCoin Explorer – Deine eigene Kryptowährung");
        BorderPane root = new BorderPane();
        root.setPadding(new Insets(15));

        currentPriceLabel = new Label("SC Preis: 1.00 USD");
        currentPriceLabel.setStyle("-fx-font-weight: bold; -fx-font-size: 1.2em; -fx-padding: 0 0 10 0;");

        HBox topControls = new HBox(10);
        topControls.getChildren().addAll(currentPriceLabel);
        root.setTop(topControls);

        blockList = new ListView<>();
        blockList.setPrefWidth(320);
        updateBlockList();

        blockList.getSelectionModel().selectedItemProperty().addListener((obs, old, newVal) -> {
            if (newVal != null) showBlockDetails(newVal);
        });

        VBox leftPanel = new VBox(10, new Label("Blöcke in der Chain:"), blockList);

        detailsArea = new TextArea();
        detailsArea.setEditable(false);
        detailsArea.setPrefHeight(300);
        detailsArea.setStyle("-fx-font-family: 'Consolas'; -fx-font-size: 12;");

        walletList = new ListView<>();
        walletList.setPrefHeight(150);
        updateWalletList();
        setupWalletDoubleClick();


        Button newWalletBtn = new Button("Neue Wallet erstellen");
        newWalletBtn.setStyle("-fx-background-color: #2196F3; -fx-text-fill: white; -fx-font-weight: bold; -fx-padding: 10;");
        newWalletBtn.setOnAction(e -> {
            Wallet w = org.fintech.WalletManager.createWallet();

            updateWalletList();
            updateComboBoxes();

            String scBalance = w.getBalance() > 0 ? String.format("\nInitial SC Grant: %.1f SC (vom Supply abgezogen)", w.getBalance()) : "";
            new Alert(Alert.AlertType.INFORMATION,
                     w.getAddress() +
                            "\nStartguthaben: " + String.format("%.2f", w.getUsdBalance()) + " USD" +
                            scBalance,
                    ButtonType.OK).showAndWait();
        });

        Button simulationBtn = new Button("Netzwerk simulieren");
        simulationBtn.setStyle("-fx-background-color: #e74c3c; -fx-text-fill: white; -fx-font-weight: bold; -fx-padding: 10;");

        simulationBtn.setOnAction(e -> {
            if (networkSimulator.isRunning()) {
                networkSimulator.stop();
                simulationBtn.setText("Netzwerk simulieren");
                simulationBtn.setStyle("-fx-background-color: #e74c3c; -fx-text-fill: white; -fx-font-weight: bold; -fx-padding: 10;");

                // Bei Gesamtstopp: beide Wallet-Buttons deaktivieren
                stopWalletGenBtn.setDisable(true);
                startWalletGenBtn.setDisable(true);

            } else {
                networkSimulator.start();
                simulationBtn.setText("Simulation stoppen");
                simulationBtn.setStyle("-fx-background-color: #27ae60; -fx-text-fill: white; -fx-font-weight: bold; -fx-padding: 10;");

                // Beim Gesamtstart: Wallet-Generierung läuft standardmäßig, also Stopp aktivieren, Start deaktivieren
                stopWalletGenBtn.setDisable(false);
                startWalletGenBtn.setDisable(true);
            }
        });

        // 🌟 Button zum Starten der Wallet-Generierung
        startWalletGenBtn = new Button("Wallet-Gen. starten");
        startWalletGenBtn.setStyle("-fx-background-color: #27ae60; -fx-text-fill: white; -fx-font-weight: bold; -fx-padding: 10;");
        startWalletGenBtn.setDisable(true); // Standardmäßig deaktiviert

        startWalletGenBtn.setOnAction(e -> {
            if (networkSimulator.isRunning()) {
                networkSimulator.startWalletGeneration();
                startWalletGenBtn.setDisable(true);
                stopWalletGenBtn.setDisable(false);
            }
        });

        // 🌟 Button zum Stoppen der Wallet-Generierung
        stopWalletGenBtn = new Button("Wallet-Gen. stoppen");
        stopWalletGenBtn.setStyle("-fx-background-color: #f39c12; -fx-text-fill: white; -fx-font-weight: bold; -fx-padding: 10;");
        stopWalletGenBtn.setDisable(true); // Standardmäßig deaktiviert

        stopWalletGenBtn.setOnAction(e -> {
            if (networkSimulator.isRunning()) {
                networkSimulator.stopWalletGeneration();
                stopWalletGenBtn.setDisable(true);
                startWalletGenBtn.setDisable(false); // ⬅️ Aktiviert den Start-Button
            }
        });

        Button logoutBtn = new Button("Logout");
        logoutBtn.setOnAction(e -> logoutAndRestart());

        // Alle Buttons im Layout (HBox)
        HBox walletButtons = new HBox(10, newWalletBtn, simulationBtn, stopWalletGenBtn, startWalletGenBtn, logoutBtn);

        // Sortierungselemente erstellen und in die walletBox integrieren
        sortKeyCombo = new ComboBox<>();
        sortKeyCombo.getItems().addAll("SC Balance", "USD Balance", "Initial USD", "Adresse");
        sortKeyCombo.setValue("SC Balance");
        sortKeyCombo.setOnAction(e -> updateWalletList());

        sortDirectionButton = new Button("↓ Desc");
        sortDirectionButton.setOnAction(e -> {
            isAscending = !isAscending;
            sortDirectionButton.setText(isAscending ? "↑ Asc" : "↓ Desc");
            updateWalletList();
        });

        HBox sortControls = new HBox(10, new Label("Sortieren nach:"), sortKeyCombo, sortDirectionButton);
        sortControls.setPadding(new Insets(0, 0, 5, 0));

        // Label, das den aktuellen Bias-Wert anzeigt
        biasLabel = new Label("Kaufinteresse (Bias): 50% (50:50)");
        biasLabel.setStyle("-fx-font-weight: bold;");

        // Slider von 0.0 bis 1.0
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

        VBox biasControl = new VBox(5, biasLabel, biasSlider);
        biasControl.setPadding(new Insets(0, 0, 10, 0));

        VBox walletBox = new VBox(10,
                new Label("Wallet-Übersicht:"),
                walletList,
                sortControls,
                walletButtons
        );
        walletBox.setStyle("-fx-border-color: #ccc; -fx-border-radius: 5; -fx-padding: 10;");

        VBox priceChartBox = createPriceChart(); // ⬅️ Chart-Ersatz für das Transaktionsformular

        VBox rightPanel = new VBox(15,
                new Label("Block-Details:"), detailsArea,
                new Separator(),
                new Label("Live Preis Chart:"), priceChartBox,
                new Separator(),
                new Label("Marktstimmung steuern:"),
                biasControl,
                walletBox
        );

        root.setLeft(leftPanel);
        root.setCenter(rightPanel);

        Scene scene = new Scene(root, 1350, 800);
        stage.setScene(scene);

        stage.setOnCloseRequest(e -> {
            PriceSimulator.savePrice(priceSimulator.getCurrentPrice());
            org.fintech.WalletManager.saveWallets();
            if (networkSimulator != null) networkSimulator.stop();
        });

        // 🛑 KORREKTUR 2: ComboBoxen mit Daten befüllen, bevor setValue aufgerufen wird.
        updateComboBoxes();

        if (loggedInWallet != null) {
            fromCombo.setValue(loggedInWallet.getAddress());
            stage.setTitle("SimpleCoin Explorer – Eingeloggt als: " + loggedInWallet.getAddress().substring(0, 16) + "...");
        }

        stage.show();

        if (!blockList.getItems().isEmpty()) {
            blockList.getSelectionModel().select(0);
        }
    }

    private Comparator<Wallet> getWalletComparator() {
        String key = sortKeyCombo.getValue();

        int direction = isAscending ? 1 : -1;

        Comparator<Wallet> comparator = switch (key) {
            case "USD Balance" -> Comparator.comparingDouble(Wallet::getUsdBalance);
            case "Initial USD" -> Comparator.comparingDouble(Wallet::getInitialUsdBalance);
            case "Adresse" -> Comparator.comparing(Wallet::getAddress);
            case "SC Balance" -> Comparator.comparingDouble(Wallet::getBalance);

            default -> Comparator.comparingDouble(Wallet::getBalance);
        };

        return direction == 1 ? comparator : comparator.reversed();
    }


    private void logoutAndRestart() {
        if (networkSimulator != null) {
            networkSimulator.stop();
        }
        org.fintech.WalletManager.saveWallets();

        if (primaryStage != null) {
            primaryStage.close();
        }

        Platform.runLater(() -> {
            try {
                // Annahme: LoginGUI existiert
                new LoginGUI().start(new Stage());
            } catch (Exception e) {
                System.err.println("Fehler beim Neustart der LoginGUI: " + e.getMessage());
                e.printStackTrace();
            }
        });
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

    /**
     * Erstellt und initialisiert das Live-Liniendiagramm für die Preisentwicklung.
     */
    private VBox createPriceChart() {
        // 1. Achsen definieren
        final NumberAxis xAxis = new NumberAxis();
        final NumberAxis yAxis = new NumberAxis();
        xAxis.setLabel("Zeit (in Updates)");
        yAxis.setLabel("Preis (USD)");
        yAxis.setForceZeroInRange(false);

        // 2. Chart erstellen
        priceChart = new LineChart<>(xAxis, yAxis);
        priceChart.setTitle("Live SC Preisentwicklung (1-Sekunden-Takt)");
        priceChart.setPrefHeight(300);
        priceChart.setAnimated(false);
        priceChart.setLegendVisible(false);

        // 3. Datenreihe hinzufügen
        series = new XYChart.Series<>();
        series.setName("SC Preis");
        priceChart.getData().add(series);

        // Initialen Preis hinzufügen
        series.getData().add(new XYChart.Data<>(timeIndex, priceSimulator.getCurrentPrice()));

        return new VBox(priceChart);
    }

    /**
     * Aktualisiert das Liniendiagramm mit dem aktuellen Preis (im FX-Thread aufgerufen).
     */
    private void updatePriceChart() {
        if (priceChart != null) {
            timeIndex++;
            double currentPrice = priceSimulator.getCurrentPrice();

            // Fügt den neuen Datenpunkt hinzu
            series.getData().add(new XYChart.Data<>(timeIndex, currentPrice));

            // Optional: Hält das Chart auf die letzten 100 Punkte begrenzt
            if (series.getData().size() > 100) {
                series.getData().remove(0);
                // Die Achse muss neu skaliert werden, damit der Zeitindex bei 0 beginnt (visuell)
                ((NumberAxis) priceChart.getXAxis()).setLowerBound(timeIndex - 100);
                ((NumberAxis) priceChart.getXAxis()).setUpperBound(timeIndex);
            }
        }
    }


    private void updateWalletList() {
        // 1. Sortierung anwenden
        List<Wallet> sortedWallets = new ArrayList<>(org.fintech.WalletManager.getWallets());

        if (sortKeyCombo != null && sortKeyCombo.getValue() != null) {
            sortedWallets.sort(getWalletComparator());
        }

        // 2. Reichste Wallet finden (zur Hervorhebung, basierend auf SC)
        Wallet richestUser = sortedWallets.stream()
                .filter(w -> !w.getAddress().equals(org.fintech.WalletManager.SUPPLY_WALLET.getAddress()))
                .max(Comparator.comparingDouble(Wallet::getBalance))
                .orElse(null);

        walletList.getItems().clear();

        // Header für die Liste hinzufügen
        String header = String.format("%-6s | %-25s | %10s | %10s | %s", "ID", "Adresse", "SC Balance", "USD Balance", "Initial USD");
        walletList.getItems().add(header);

        for (Wallet w : sortedWallets) {
            String idString = String.valueOf(w.getUniqueId());
            String shortAddr = w.getAddress().substring(0, Math.min(25, w.getAddress().length())) + "...";

            String entry = String.format("%-6s | %-25s | %10.3f SC | %10.2f USD | %7.2f $",
                    idString + "...",
                    shortAddr,
                    w.getBalance(),
                    w.getUsdBalance(),
                    w.getInitialUsdBalance());
            walletList.getItems().add(entry);
        }

        final String loggedInAddress = loggedInWallet != null ? loggedInWallet.getAddress() : null;
        final Wallet finalRichestUser = richestUser;
        final List<Wallet> finalSortedWallets = sortedWallets;

        walletList.setCellFactory(lv -> new ListCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);

                if (empty || item == null) {
                    setText(null); setTooltip(null); setStyle("");
                    return;
                }

                setText(item);

                if (getIndex() == 0) {
                    setStyle("-fx-font-weight: bold; -fx-background-color: #f0f0f0;");
                    setTooltip(null);
                    return;
                }

                int walletIndex = getIndex() - 1;

                if (walletIndex < 0 || walletIndex >= finalSortedWallets.size()) {
                    setStyle("");
                    setTooltip(null);
                    return;
                }

                Wallet currentWallet = finalSortedWallets.get(walletIndex);
                setTooltip(new Tooltip(currentWallet.getAddress()));

                // Hervorhebungs-Logik:
                if (loggedInAddress != null && currentWallet.getAddress().equals(loggedInAddress)) {
                    setStyle("-fx-background-color: #fce883; -fx-text-fill: #333333; -fx-font-weight: bold;");
                } else if (currentWallet.getAddress().equals(org.fintech.WalletManager.SUPPLY_WALLET.getAddress())) {
                    setStyle("-fx-background-color: #d1e7f7; -fx-text-fill: #333333; -fx-font-style: italic;");
                } else if (finalRichestUser != null && currentWallet.getAddress().equals(finalRichestUser.getAddress())) {
                    setStyle("-fx-background-color: #d4edda; -fx-text-fill: #155724; -fx-font-weight: bold;");
                } else {
                    setStyle("");
                }
            }
        });
    }

    private void setupWalletDoubleClick() {
        walletList.setOnMouseClicked(event -> {
            if (event.getClickCount() == 2 && !walletList.getSelectionModel().isEmpty()) {

                int selectedIndex = walletList.getSelectionModel().getSelectedIndex();
                if (selectedIndex == 0) return;

                List<Wallet> sortedWallets = new ArrayList<>(org.fintech.WalletManager.getWallets());
                if (sortKeyCombo != null && sortKeyCombo.getValue() != null) {
                    sortedWallets.sort(getWalletComparator());
                }

                int walletIndex = selectedIndex - 1;

                if (walletIndex < 0 || walletIndex >= sortedWallets.size()) {
                    System.err.println("Fehler: Wallet Index " + walletIndex + " außerhalb der Grenzen " + sortedWallets.size() + " beim Doppelklick.");
                    return;
                }

                Wallet w = sortedWallets.get(walletIndex);

                Stage detailStage = new Stage();
                detailStage.setTitle("Wallet-Details: " + w.getAddress().substring(0, 16) + "...");

                GridPane grid = new GridPane();
                grid.setHgap(10);
                grid.setVgap(5);
                grid.setPadding(new Insets(20));

                int row = 0;
                addDetailRow(grid, row++, "Unique ID:", String.valueOf(w.getUniqueId()));
                addDetailRow(grid, row++, "Adresse:", w.getAddress());
                addDetailRow(grid, row++, "Passwort (Klartext):", w.getClearPassword());
                addDetailRow(grid, row++, "Passwort (SHA-256):", w.getPasswordHash());
                addDetailRow(grid, row++, "Öffentlicher Schlüssel:", Base64.getEncoder().encodeToString(w.getPublicKey().getEncoded()));
                addDetailRow(grid, row++, "Gesamte Transaktionen:", String.valueOf(countTransactions(w)));

                addDetailRow(grid, row++, "—".repeat(50), "");

                double initialSC = w.getAddress().equals(org.fintech.WalletManager.SUPPLY_WALLET.getAddress()) ? 0.0 : 0;
                double currentSC = w.getBalance();

                addDetailRow(grid, row++, "Initial SC Balance:", String.format("%.3f SC", initialSC));
                addDetailRow(grid, row++, "Initial USD Balance (random):", String.format("%.2f USD", w.getInitialUsdBalance()));

                addDetailRow(grid, row++, "Aktuelle SC Balance:", String.format("%.3f SC", currentSC));
                addDetailRow(grid, row++, "Aktuelle USD Balance:", String.format("%.2f USD", w.getUsdBalance()));

                double scDelta = currentSC - initialSC;
                double usdDelta = w.getUsdBalance() - w.getInitialUsdBalance();

                String scProfitColor = scDelta >= 0 ? "#27ae60" : "#c0392b";
                String usdProfitColor = usdDelta >= 0 ? "#27ae60" : "#c0392b";

                addStyledDetailRow(grid, row++, "SC Delta (Kauf/Verkauf):", String.format("%s%.3f SC", scDelta >= 0 ? "+" : "", scDelta), scProfitColor);
                addStyledDetailRow(grid, row++, "USD Delta (Investition):", String.format("%s%.2f USD", usdDelta >= 0 ? "+" : "", usdDelta), usdProfitColor);

                VBox root = new VBox(10, grid);
                root.setPadding(new Insets(10));

                Button closeBtn = new Button("Schließen");
                closeBtn.setOnAction(e -> detailStage.close());
                root.getChildren().add(new HBox(closeBtn));

                detailStage.setScene(new Scene(root));
                detailStage.show();
            }
        });
    }

    private void addStyledDetailRow(GridPane grid, int row, String label, String value, String color) {
        Label l = new Label(label);
        l.setStyle("-fx-font-weight: bold;");
        Label v = new Label(value);
        v.setStyle(String.format("-fx-font-family: 'Consolas'; -fx-font-weight: bold; -fx-text-fill: %s;", color));
        v.setWrapText(true);
        grid.add(l, 0, row);
        grid.add(v, 1, row);
    }

    private List<Transaction> getWalletTransactions(String address) {
        List<Transaction> txs = new ArrayList<>();
        if (blockchain == null) return txs;

        for (Block block : blockchain.getChain()) {
            for (Transaction tx : block.getTransactions()) {
                if (tx.getSender().equals(address) || tx.getRecipient().equals(address)) {
                    txs.add(tx);
                }
            }
        }
        return txs;
    }

    private void addDetailRow(GridPane grid, int row, String label, String value) {
        Label l = new Label(label);
        l.setStyle("-fx-font-weight: bold;");
        Label v = new Label(value);
        v.setStyle("-fx-font-family: 'Consolas';");
        v.setWrapText(true);
        grid.add(l, 0, row);
        grid.add(v, 1, row);
    }

    private long countTransactions(Wallet w) {
        return blockchain.getChain().stream()
                .flatMap(b -> b.getTransactions().stream())
                .filter(tx -> tx.getSender().equals(w.getAddress()) || tx.getRecipient().equals(w.getAddress()))
                .count();
    }

    private boolean isGenesisWallet(Wallet w) {
        if (blockchain.getChain().isEmpty()) return false;
        return blockchain.getChain().get(0).getTransactions().stream()
                .anyMatch(tx -> tx.getRecipient().equals(w.getAddress()) && tx.getAmount() >= 1000.0);
    }

    // 🛑 SICHERHEITS-UPDATE für updateComboBoxes()
    private void updateComboBoxes() {
        List<String> addresses = org.fintech.WalletManager.getWallets().stream().map(Wallet::getAddress).toList();

        // 🛑 Wichtig: Prüfung auf Initialisierung
        if (fromCombo == null || toCombo == null) {
            return;
        }

        fromCombo.getItems().setAll(addresses);

        List<String> toAddresses = new ArrayList<>(addresses);
        toAddresses.add(EXCHANGE_ADDRESS);
        toCombo.getItems().setAll(toAddresses);

        if (loggedInWallet != null) {
            // Setzt den Wert nur, wenn die Adresse auch in der Liste ist
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

    public static void main(String[] args) {
        launch();
    }
}