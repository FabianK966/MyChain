package org.fintech;

import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.stage.Stage;
import java.util.*;

public class WalletDetailsDialog {

    public static void show(Wallet w) {
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
        addDetailRow(grid, row++, "Gesamte Transaktionen:", String.valueOf(w.getTransactionHistory().size()));

        addDetailRow(grid, row++, "—".repeat(25), "");

        double initialSC = w.getAddress().equals(WalletManager.SUPPLY_WALLET.getAddress()) ? 0.0 : 0;
        double currentSC = w.getBalance();
        double currentPrice = MyChainGUI.getCurrentCoinPrice();
        double networth = w.calculateNetWorth(currentPrice);

        addDetailRow(grid, row++, "Initial SC Balance:", String.format("%,.3f SC", initialSC));
        addDetailRow(grid, row++, "Initial USD Balance (random):", String.format("%,.2f USD", w.getInitialUsdBalance()));

        addDetailRow(grid, row++, "Aktuelle SC Balance:", String.format("%,.3f SC", currentSC));
        addDetailRow(grid, row++, "Aktuelle USD Balance:", String.format("%,.2f USD", w.getUsdBalance()));

        addDetailRow(grid, row++, "Net Worth:", String.format("%,.2f USD", networth));
        addDetailRow(grid, row++, "Long Position USD-Wert:", String.format("%,.2f USD", w.getLongPositionUsd()));

        addDetailRow(grid, row++, "—".repeat(25), "");

        double scDelta = currentSC - initialSC;
        double usdDelta = w.getUsdBalance() - w.getInitialUsdBalance();

        String scProfitColor = scDelta >= 0 ? "#27ae60" : "#c0392b";
        String usdProfitColor = usdDelta >= 0 ? "#27ae60" : "#c0392b";

        addStyledDetailRow(grid, row++, "SC Delta (Kauf/Verkauf):",
                String.format("%s%,.3f SC", scDelta >= 0 ? "+" : "", scDelta), scProfitColor);
        addStyledDetailRow(grid, row++, "USD Delta (Investition):",
                String.format("%s%,.2f USD", usdDelta >= 0 ? "+" : "", usdDelta), usdProfitColor);

        // Transaktionshistorie
        VBox txHistoryBox = new VBox(5);
        txHistoryBox.getChildren().add(new Label("Transaktionshistorie:"));

        List<Transaction> txs = new ArrayList<>(w.getTransactionHistory());
        Collections.reverse(txs); // Neueste Transaktion zuerst

        for (Transaction tx : txs) {
            int blockIndex = -1; // Hier müsste blockchain.findBlockIndexByTransaction(tx) implementiert werden

            String txEntry = String.format(
                    "Block #%s | TxID: %s... | Preis: %.4f USD | %s -> %s | %.3f SC | %s",
                    blockIndex >= 0 ? String.valueOf(blockIndex) : "?",
                    tx.getTxId().substring(0, 8),
                    tx.getPriceAtExecution(),
                    tx.getSender().length() > 10 ? tx.getSender().substring(0, 10) + "..." : tx.getSender(),
                    tx.getRecipient().length() > 10 ? tx.getRecipient().substring(0, 10) + "..." : tx.getRecipient(),
                    tx.getAmount(),
                    tx.getMessage()
            );
            Label txLabel = new Label(txEntry);
            txLabel.setStyle("-fx-font-family: 'Consolas'; -fx-font-size: 10;");
            txHistoryBox.getChildren().add(txLabel);
        }

        VBox root = new VBox(10, grid, new Separator(), txHistoryBox);
        root.setPadding(new Insets(10));

        Button closeBtn = new Button("Schließen");
        closeBtn.setOnAction(e -> detailStage.close());
        root.getChildren().add(new HBox(closeBtn));

        detailStage.setScene(new Scene(root));
        detailStage.show();
    }

    private static void addDetailRow(GridPane grid, int row, String label, String value) {
        Label l = new Label(label);
        l.setStyle("-fx-font-weight: bold;");
        Label v = new Label(value);
        v.setStyle("-fx-font-family: 'Consolas';");
        v.setWrapText(true);
        grid.add(l, 0, row);
        grid.add(v, 1, row);
    }

    private static void addStyledDetailRow(GridPane grid, int row, String label, String value, String color) {
        Label l = new Label(label);
        l.setStyle("-fx-font-weight: bold;");
        Label v = new Label(value);
        v.setStyle(String.format("-fx-font-family: 'Consolas'; -fx-font-weight: bold; -fx-text-fill: %s;", color));
        v.setWrapText(true);
        grid.add(l, 0, row);
        grid.add(v, 1, row);
    }
}