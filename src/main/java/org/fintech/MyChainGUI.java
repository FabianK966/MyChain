package org.fintech;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.scene.Scene;
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
    private ComboBox<String> fromCombo;
    private ComboBox<String> toCombo;
    private NetworkSimulator networkSimulator;
    private WalletManager WalletManager = org.fintech.WalletManager.INSTANCE;
    private static Stage primaryStage;
    private Button stopWalletGenBtn;
    private Button startWalletGenBtn;
    private PriceSimulator priceSimulator;
    private Label currentPriceLabel;

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

        blockchain = BlockchainPersistence.loadBlockchain("MyChain", 1);
        // 🛑 KORREKTUR: Preis laden oder Standardwert (0.1) verwenden
        double initialPrice = PriceSimulator.loadPrice(0.1);
        this.priceSimulator = new PriceSimulator(initialPrice); // PriceSimulator mit geladenem Preis initialisieren

        networkSimulator = new NetworkSimulator(blockchain, WalletManager, priceSimulator);
        networkSimulator.setOnUpdate(() -> {
            updateWalletList();
            updateComboBoxes();
            updateBlockList();
            updatePriceLabel();
            if (!blockchain.getChain().isEmpty()) {
                blockList.getSelectionModel().select(blockchain.getChain().size() - 1);
            }
        });

        stage.setTitle("SimpleCoin Explorer – Deine eigene Kryptowährung");
        BorderPane root = new BorderPane();
        root.setPadding(new Insets(15));

        currentPriceLabel = new Label("SC Preis: 0.10 USD");
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

        GridPane transactionForm = createTransactionForm();

        Button newWalletBtn = new Button("Neue Wallet erstellen");
        newWalletBtn.setStyle("-fx-background-color: #2196F3; -fx-text-fill: white; -fx-font-weight: bold; -fx-padding: 10;");
        newWalletBtn.setOnAction(e -> {
            Wallet w = org.fintech.WalletManager.createWallet();

            updateWalletList();
            updateComboBoxes();

            String scBalance = w.getBalance() > 0 ? String.format("\nInitial SC Grant: %.1f SC (vom Supply abgezogen)", w.getBalance()) : "";
            new Alert(Alert.AlertType.INFORMATION,
                    "Neue Wallet erstellt!\n\nAdresse:\n" + w.getAddress() +
                            "\nStartguthaben: " + String.format("%.2f", w.getUsdBalance()) + " USD" +
                            scBalance,
                    ButtonType.OK).showAndWait();
        });

        Button simulationBtn = new Button("Netzwerk simulieren");
        simulationBtn.setStyle("-fx-background-color: #e74c3c; -fx-text-fill: white; -fx-font-weight: bold; -fx-padding: 10;");
        // 🛑 WICHTIGE ANPASSUNG: Zustand beim Start/Stopp der Hauptsimulation
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

        // 🌟 NEUE LOGIK: Button zum Starten der Wallet-Generierung
        startWalletGenBtn = new Button("Wallet-Gen. starten");
        startWalletGenBtn.setStyle("-fx-background-color: #27ae60; -fx-text-fill: white; -fx-font-weight: bold; -fx-padding: 10;");

        startWalletGenBtn.setOnAction(e -> {
            if (networkSimulator.isRunning()) {
                networkSimulator.startWalletGeneration();
                startWalletGenBtn.setDisable(true);
                stopWalletGenBtn.setDisable(false);
            }
        });

        // 🌟 NEUE LOGIK: Button zum Stoppen der Wallet-Generierung
        stopWalletGenBtn = new Button("Wallet-Gen. stoppen");
        stopWalletGenBtn.setStyle("-fx-background-color: #f39c12; -fx-text-fill: white; -fx-font-weight: bold; -fx-padding: 10;");

        stopWalletGenBtn.setOnAction(e -> {

            if (networkSimulator.isRunning()) {
                networkSimulator.stopWalletGeneration();
                stopWalletGenBtn.setDisable(true); // Deaktiviert, sobald gestoppt
                startWalletGenBtn.setDisable(false);
            }
        });

        Button logoutBtn = new Button("Logout");
        logoutBtn.setOnAction(e -> logoutAndRestart());

        // 🛑 NEU: Alle vier Buttons im Layout (HBox)
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


        VBox walletBox = new VBox(10,
                new Label("Wallet-Übersicht:"),
                walletList,
                sortControls,
                walletButtons
        );
        walletBox.setStyle("-fx-border-color: #ccc; -fx-border-radius: 5; -fx-padding: 10;");

        VBox rightPanel = new VBox(15,
                new Label("Block-Details:"), detailsArea,
                new Separator(),
                new Label("Neue Transaktion:"), transactionForm,
                new Separator(),
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

    private GridPane createTransactionForm() {
        GridPane grid = new GridPane();
        grid.setHgap(12); grid.setVgap(12); grid.setPadding(new Insets(10));

        fromCombo = new ComboBox<>();
        toCombo = new ComboBox<>();
        TextField amountField = new TextField("10.0");
        TextField messageField = new TextField("Danke!");

        Button sendBtn = new Button("Transaktion senden & minen");
        sendBtn.setStyle("-fx-background-color: #4CAF50; -fx-text-fill: white; -fx-font-weight: bold; -fx-padding: 10 20;");
        sendBtn.setOnAction(e -> {
            try {
                double amount = Double.parseDouble(amountField.getText());
                String fromAddr = fromCombo.getValue();
                String toAddr = toCombo.getValue();
                double currentPrice = priceSimulator.getCurrentPrice();

                if (fromAddr == null || toAddr == null || amount <= 0) {
                    new Alert(Alert.AlertType.WARNING, "Ungültige Adressen oder Betrag!").show();
                    return;
                }

                Wallet sender = org.fintech.WalletManager.findWalletByAddress(fromAddr);
                Wallet recipient = org.fintech.WalletManager.findWalletByAddress(toAddr);
                Wallet supplyWallet = org.fintech.WalletManager.SUPPLY_WALLET;

                List<Transaction> txs = new ArrayList<>();
                boolean isTransactionHandled = false;
                String successMessage = "Transaktion gemined! Block angehängt.";


                // Fall 1: SC VERKAUF (SC -> USD)
                if (toAddr.equals(EXCHANGE_ADDRESS)) {
                    if (sender == null || sender.getBalance() < amount) {
                        new Alert(Alert.AlertType.ERROR, "Nicht genug Guthaben (SC) für den Verkauf!").show();
                        return;
                    }

                    // 1. SC-Transaktion: SC wird aus der Wallet entfernt (vom Supply abgezogen/entfernt)
                    txs.add(sender.createTransaction(toAddr, amount, messageField.getText()));

                    // 2. USD-Gutschrift
                    double usdGain = amount * currentPrice;
                    sender.creditUsd(usdGain);
                    successMessage = String.format("SC-Verkauf erfolgreich!\nSie erhielten %.2f USD (Preis: %.4f USD/SC). SC-Menge: %.3f wurde aus dem Umlauf genommen.", usdGain, currentPrice, amount);
                    isTransactionHandled = true;
                }

                // Fall 2: SC KAUF (USD -> SC)
                else if (fromAddr.equals(supplyWallet.getAddress())) {

                    // Der Empfänger ist der Käufer
                    if (recipient == null) {
                        new Alert(Alert.AlertType.ERROR, "Ungültige Empfängeradresse für Kauf!").show();
                        return;
                    }

                    double usdCost = amount * currentPrice;

                    try {
                        // 1. USD-Abbuchung vom Käufer
                        recipient.debitUsd(usdCost);
                    } catch (RuntimeException ex) {
                        new Alert(Alert.AlertType.ERROR, ex.getMessage() + " (Kauf gescheitert)").show();
                        return;
                    }

                    // 2. SC-Transaktion: SC wird von der Supply Wallet gesendet (vom Supply abgezogen)
                    if (supplyWallet.getBalance() < amount) {
                        // Falls Supply leer, USD gutschreiben und abbrechen
                        recipient.creditUsd(usdCost);
                        new Alert(Alert.AlertType.ERROR, "Nicht genug SC in der Supply Wallet!").show();
                        return;
                    }

                    txs.add(supplyWallet.createTransaction(recipient.getAddress(), amount, messageField.getText()));

                    successMessage = String.format("SC-Kauf erfolgreich!\nSie zahlten %.2f USD und erhielten %.3f SC (Preis: %.4f USD/SC). SC-Menge: %.3f vom Supply abgezogen.", usdCost, amount, currentPrice, amount);
                    isTransactionHandled = true;
                }

                // Fall 3: Normale SC-Überweisung (SC -> SC)
                else if (!isTransactionHandled) {
                    if (sender == null || sender.getBalance() < amount) {
                        new Alert(Alert.AlertType.ERROR, "Nicht genug Guthaben (SC)!").show();
                        return;
                    }
                    if (recipient == null) {
                        new Alert(Alert.AlertType.WARNING, "Ungültige Empfängeradresse!").show();
                        return;
                    }
                    txs.add(sender.createTransaction(toAddr, amount, messageField.getText()));
                    successMessage = "Normale SC-Überweisung gemined! Block angehängt.";
                    isTransactionHandled = true;
                }

                // Wenn eine Transaktion erstellt wurde, minen
                if (isTransactionHandled && !txs.isEmpty()) {
                    blockchain.addBlock(txs);
                    BlockchainPersistence.saveBlockchain(blockchain);

                    org.fintech.WalletManager.recalculateAllBalances();
                    updateWalletList();
                    updateComboBoxes();
                    updateBlockList();
                    blockList.getSelectionModel().select(blockchain.getChain().size() - 1);

                    new Alert(Alert.AlertType.INFORMATION, successMessage).show();
                    amountField.clear();
                    messageField.clear();
                } else {
                    new Alert(Alert.AlertType.ERROR, "Es konnte keine gültige Transaktion erstellt werden.").show();
                }

            } catch (Exception ex) {
                new Alert(Alert.AlertType.ERROR, "Fehler: " + ex.getMessage()).show();
            }
        });

        updateComboBoxes();

        grid.add(new Label("Von:"), 0, 0); grid.add(fromCombo, 1, 0);
        grid.add(new Label("An:"), 0, 1); grid.add(toCombo, 1, 1);
        grid.add(new Label("Betrag:"), 0, 2); grid.add(amountField, 1, 2);
        grid.add(new Label("Nachricht:"), 0, 3); grid.add(messageField, 1, 3);
        grid.add(sendBtn, 1, 4);

        return grid;
    }

    // KORRIGIERTE METHODE: Sortiert die Wallets und zeigt erweiterte Balances an (mit Index-Sicherheit)
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
            // ID kürzen
            String idString = String.valueOf(w.getUniqueId()); // ID als String abrufen
            String shortId = idString;
            // Adresse kürzen
            String shortAddr = w.getAddress().substring(0, Math.min(25, w.getAddress().length())) + "...";

            // Eintrag mit ID, Adresse und Balances formatieren
            String entry = String.format("%-6s | %-25s | %10.3f SC | %10.2f USD | %7.2f $",
                    shortId + "...", // Hier zeigen wir die ersten paar Zeichen der ID
                    shortAddr,
                    w.getBalance(),
                    w.getUsdBalance(),
                    w.getInitialUsdBalance());
            walletList.getItems().add(entry);
        }

        final String loggedInAddress = loggedInWallet != null ? loggedInWallet.getAddress() : null;
        final Wallet finalRichestUser = richestUser;
        final List<Wallet> finalSortedWallets = sortedWallets; // Für die innere CellFactory

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
                    // Header hervorheben
                    setStyle("-fx-font-weight: bold; -fx-background-color: #f0f0f0;");
                    setTooltip(null);
                    return;
                }

                // Index für die Wallet im sortierten Array (Index 0 in ListView ist Header)
                int walletIndex = getIndex() - 1;

                // 🛑 KORREKTUR: Explizite Indexprüfung, um IndexOutOfBoundsException zu vermeiden
                if (walletIndex < 0 || walletIndex >= finalSortedWallets.size()) {
                    // Falls die ListView schneller aktualisiert wird als die Wallets-Liste,
                    // ignorieren wir diesen Item-Update und warten auf den nächsten.
                    setStyle("");
                    setTooltip(null);
                    return;
                }

                Wallet currentWallet = finalSortedWallets.get(walletIndex); // <--- Abgesicherter Zugriff
                setTooltip(new Tooltip(currentWallet.getAddress()));

                // Bestehende Hervorhebungs-Logik:
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
                if (selectedIndex == 0) return; // Header ignorieren

                // Liste erneut sortieren, falls sich die Sortierreihenfolge in der Zwischenzeit geändert hat
                List<Wallet> sortedWallets = new ArrayList<>(org.fintech.WalletManager.getWallets());
                if (sortKeyCombo != null && sortKeyCombo.getValue() != null) {
                    sortedWallets.sort(getWalletComparator());
                }

                // Wallet im sortierten Array finden (Index - 1 wegen des Headers)
                int walletIndex = selectedIndex - 1;

                // 🛑 KORREKTUR: Explizite Indexprüfung
                if (walletIndex < 0 || walletIndex >= sortedWallets.size()) {
                    System.err.println("Fehler: Wallet Index " + walletIndex + " außerhalb der Grenzen " + sortedWallets.size() + " beim Doppelklick.");
                    return;
                }

                Wallet w = sortedWallets.get(walletIndex); // <--- Abgesicherter Zugriff

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

    private void updateComboBoxes() {
        List<String> addresses = org.fintech.WalletManager.getWallets().stream().map(Wallet::getAddress).toList();

        fromCombo.getItems().setAll(addresses);

        List<String> toAddresses = new ArrayList<>(addresses);
        toAddresses.add(EXCHANGE_ADDRESS);
        toCombo.getItems().setAll(toAddresses);

        if (loggedInWallet != null) {
            fromCombo.setValue(loggedInWallet.getAddress());
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