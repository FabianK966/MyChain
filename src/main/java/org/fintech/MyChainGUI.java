package org.fintech;

import javafx.application.Application;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.Stage;
import java.util.ArrayList;
import java.util.List;

public class MyChainGUI extends Application {

    private Blockchain blockchain;
    private ListView<String> blockList;
    private TextArea detailsArea;
    private ListView<String> walletList;
    private ComboBox<String> fromCombo;
    private ComboBox<String> toCombo;

    @Override
    public void start(Stage stage) {
        // 1. Wallets laden
        WalletManager.loadWallets();
        if (WalletManager.getWallets().isEmpty()) {
            WalletManager.createWallet();
            WalletManager.createWallet();
            WalletManager.createWallet();
        }

        // 2. Blockchain laden
        blockchain = BlockchainPersistence.loadBlockchain("MyChain", 3);

        stage.setTitle("SimpleCoin Explorer – Deine eigene Kryptowährung");
        BorderPane root = new BorderPane();
        root.setPadding(new Insets(15));

        // LINKS: Block-Liste
        blockList = new ListView<>();
        blockList.setPrefWidth(320);
        updateBlockList();

        blockList.getSelectionModel().selectedItemProperty().addListener((obs, old, newVal) -> {
            if (newVal != null) showBlockDetails(newVal);
        });

        VBox leftPanel = new VBox(10, new Label("Blöcke in der Chain:"), blockList);

        // RECHTS: Alles zusammenbauen – aber walletList ZUERST erzeugen!
        detailsArea = new TextArea();
        detailsArea.setEditable(false);
        detailsArea.setPrefHeight(300);
        detailsArea.setStyle("-fx-font-family: 'Consolas'; -fx-font-size: 12;");

        // WICHTIG: walletList VOR createTransactionForm() erzeugen!
        walletList = new ListView<>();
        walletList.setPrefHeight(150);
        updateWalletList();  // ← füllt walletList

        // Jetzt ist walletList != null → createTransactionForm() funktioniert
        GridPane transactionForm = createTransactionForm();

        Button newWalletBtn = new Button("Neue Wallet erstellen");
        newWalletBtn.setStyle("-fx-background-color: #2196F3; -fx-text-fill: white; -fx-font-weight: bold; -fx-padding: 10;");
        newWalletBtn.setOnAction(e -> {
            Wallet newWallet = WalletManager.createWallet();  // erstellt + speichert
            updateWalletList();           // ← aktualisiert die untere Liste
            updateComboBoxes();           // ← DAS FEHLTE! aktualisiert Von:/An:-Dropdowns
            new Alert(Alert.AlertType.INFORMATION,
                    "Neue Wallet erstellt!\n\nAdresse:\n" + newWallet.getAddress(),
                    ButtonType.OK).showAndWait();
        });

        VBox walletBox = new VBox(10,
                new Label("Verfügbare Wallets:"), walletList, newWalletBtn);
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
        stage.setOnCloseRequest(e -> WalletManager.saveWallets());
        stage.show();

        if (!blockList.getItems().isEmpty()) {
            blockList.getSelectionModel().select(0);
        }
    }

    private VBox getVBox(GridPane transactionForm) {
        Button newWalletBtn = new Button("Neue Wallet erstellen");
        newWalletBtn.setStyle("-fx-background-color: #2196F3; -fx-text-fill: white; -fx-font-weight: bold; -fx-padding: 10;");
        newWalletBtn.setOnAction(e -> {
            Wallet newWallet = WalletManager.createWallet();
            updateWalletList();
            updateComboBoxes();
            new Alert(Alert.AlertType.INFORMATION,
                    "Neue Wallet erstellt!\n\nAdresse:\n" + newWallet.getAddress(),
                    ButtonType.OK).showAndWait();
        });

        VBox walletBox = new VBox(10,
                new Label("Verfügbare Wallets:"),
                walletList,
                newWalletBtn
        );
        walletBox.setStyle("-fx-border-color: #ccc; -fx-border-radius: 5; -fx-padding: 10;");

        VBox rightPanel = new VBox(15,
                new Label("Block-Details:"), detailsArea,
                new Separator(),
                new Label("Neue Transaktion:"), transactionForm,
                new Separator(),
                walletBox
        );
        return rightPanel;
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
        sb.append("Hash:          ").append(block.getHash()).append("\n");
        sb.append("Previous Hash: ").append(block.getPreviousHash()).append("\n");
        sb.append("Timestamp:     ").append(new java.util.Date(block.getTimeStamp())).append("\n");
        sb.append("Nonce:         ").append(block.getNonce()).append("\n");
        sb.append("Transaktionen: ").append(block.getTransactions().size()).append("\n");
        sb.append("═".repeat(70)).append("\n\n");

        for (Transaction tx : block.getTransactions()) {
            sb.append("TX ").append(tx.getTxId().substring(0, 8)).append("...\n");
            sb.append("   Von:  ").append(tx.getSender().substring(0, Math.min(34, tx.getSender().length()))).append("...\n");
            sb.append("   An:   ").append(tx.getRecipient().substring(0, Math.min(34, tx.getRecipient().length()))).append("...\n");
            sb.append("   Betrag: ").append(String.format("%.2f", tx.getAmount())).append(" Coins\n");
            sb.append("   Nachricht: ").append(tx.getMessage().isEmpty() ? "(keine)" : tx.getMessage()).append("\n");
            sb.append("\n");
        }

        detailsArea.setText(sb.toString());
    }

    private GridPane createTransactionForm() {
        GridPane grid = new GridPane();
        grid.setHgap(12);
        grid.setVgap(12);
        grid.setPadding(new Insets(10));

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

                if (fromAddr == null || toAddr == null || fromAddr.equals(toAddr)) {
                    new Alert(Alert.AlertType.WARNING, "Ungültige Adressen!").show();
                    return;
                }

                Wallet sender = WalletManager.findWalletByAddress(fromAddr);
                if (sender == null || sender.getBalance() < amount) {
                    new Alert(Alert.AlertType.ERROR,
                            "Nicht genug Guthaben!\nBalance: " + (sender != null ? sender.getBalance() : 0) + " SC").show();
                    return;
                }

                List<Transaction> txs = new ArrayList<>();
                txs.add(sender.createTransaction(toAddr, amount, messageField.getText()));

                blockchain.addBlock(txs);
                BlockchainPersistence.saveBlockchain(blockchain);


                WalletManager.recalculateAllBalances();
                updateWalletList();
                updateComboBoxes();
                updateBlockList();
                blockList.getSelectionModel().select(blockchain.getChain().size() - 1); // springt zum neuen Block

                new Alert(Alert.AlertType.INFORMATION, "Transaktion gemined! Block angehängt.").show();
                amountField.clear();
                messageField.clear();
            } catch (Exception ex) {
                new Alert(Alert.AlertType.ERROR, "Fehler: " + ex.getMessage()).show();
            }
        });

        // ComboBoxen initial füllen
        updateComboBoxes();

        grid.add(new Label("Von:"), 0, 0);
        grid.add(fromCombo, 1, 0);
        grid.add(new Label("An:"), 0, 1);
        grid.add(toCombo, 1, 1);
        grid.add(new Label("Betrag:"), 0, 2);
        grid.add(amountField, 1, 2);
        grid.add(new Label("Nachricht:"), 0, 3);
        grid.add(messageField, 1, 3);
        grid.add(sendBtn, 1, 4);

        return grid;
    }

    private void updateWalletList() {
        walletList.getItems().clear();
        List<Wallet> wallets = WalletManager.getWallets();

        Wallet richest = wallets.stream()
                .max((a, b) -> Double.compare(a.getBalance(), b.getBalance()))
                .orElse(null);

        for (int i = 0; i < wallets.size(); i++) {
            Wallet w = wallets.get(i);
            String shortAddr = w.getAddress().substring(0, Math.min(34, w.getAddress().length())) + "...";
            String balanceStr = String.format("%.2f SC", w.getBalance());
            String entry = String.format("%-40s → %s", shortAddr, balanceStr);
            walletList.getItems().add(entry);
        }

        // Einmalige CellFactory mit Tooltip + Hervorhebung
        walletList.setCellFactory(lv -> new ListCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    setTooltip(null);
                    setStyle("");
                } else {
                    setText(item);
                    setTooltip(new Tooltip(WalletManager.getWallets().get(getIndex()).getAddress()));

                    if (richest != null && item.contains(richest.getAddress().substring(0, 30))) {
                        setStyle("-fx-background-color: #d4edda; -fx-text-fill: #155724; -fx-font-weight: bold;");
                    }
                }
            }
        });
        // Tooltip für jedes Item – einmalig setzen
        walletList.setCellFactory(lv -> new ListCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    setTooltip(null);
                } else {
                    setText(item);
                    String fullAddr = WalletManager.getWallets().get(getIndex()).getAddress();
                    setTooltip(new Tooltip("Vollständige Adresse:\n" + fullAddr));
                }
            }
        });
    }

    private void updateComboBoxes() {
        fromCombo.getItems().clear();
        toCombo.getItems().clear();
        for (Wallet w : WalletManager.getWallets()) {
            String full = w.getAddress();
            fromCombo.getItems().add(full);
            toCombo.getItems().add(full);
        }
        if (!fromCombo.getItems().isEmpty()) {
            fromCombo.setValue(fromCombo.getItems().get(0));
            if (fromCombo.getItems().size() > 1) {
                toCombo.setValue(fromCombo.getItems().get(1));
            }
        }
    }


    public static void main(String[] args) {
        launch();
    }
}