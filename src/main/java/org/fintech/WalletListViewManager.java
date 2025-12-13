package org.fintech;

import javafx.application.Platform;
import javafx.scene.control.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;

public class WalletListViewManager {
    // 🔧 PERFORMANCE: Caching für sortierte Wallet-Liste
    private List<Wallet> cachedSortedWallets = null;
    private String lastSortKey = null;
    private boolean lastSortDirection = false;
    private long lastWalletUpdateTime = 0;
    private static final long WALLET_CACHE_TTL_MS = 500; // Cache für 500ms

    // Referenzen zur GUI
    private final ListView<String> walletList;
    private final ComboBox<String> sortKeyCombo;
    private final Button sortDirectionButton;
    private final Wallet loggedInWallet;
    private boolean isAscending = false;

    // 🔧 PERFORMANCE: Cache für formatierte Einträge
    private List<String> cachedWalletStrings = null;
    private double lastCachedPrice = 0.0;

    public WalletListViewManager(ListView<String> walletList,
                                 ComboBox<String> sortKeyCombo,
                                 Button sortDirectionButton,
                                 Wallet loggedInWallet) {
        this.walletList = walletList;
        this.sortKeyCombo = sortKeyCombo;
        this.sortDirectionButton = sortDirectionButton;
        this.loggedInWallet = loggedInWallet;

        setupSortingControls();
    }

    private void setupSortingControls() {
        sortKeyCombo.getItems().addAll("SC Balance", "USD Balance", "LONG Position",
                "Initial USD", "Adresse", "Net Worth");
        sortKeyCombo.setValue("SC Balance");

        sortKeyCombo.setOnAction(e -> updateWalletList());
        sortDirectionButton.setOnAction(e -> {
            isAscending = !isAscending;
            sortDirectionButton.setText(isAscending ? "↑ Asc" : "↓ Desc");
            updateWalletList();
        });
    }

    public void updateWalletList() {
        long now = System.currentTimeMillis();
        double currentPrice = MyChainGUI.getCurrentCoinPrice();

        // 🔧 LAZY UPDATE: Nur aktualisieren, wenn nötig
        if (now - lastWalletUpdateTime < WALLET_CACHE_TTL_MS &&
                cachedSortedWallets != null &&
                sortKeyCombo.getValue().equals(lastSortKey) &&
                isAscending == lastSortDirection &&
                Math.abs(currentPrice - lastCachedPrice) < 0.0001) {
            return; // Cache ist noch gültig
        }

        // Hole alle Wallets (performanter Zugriff)
        List<Wallet> allWallets = WalletManager.getWallets();

        // Sortierung
        String currentSortKey = sortKeyCombo.getValue();
        if (cachedSortedWallets == null ||
                !currentSortKey.equals(lastSortKey) ||
                isAscending != lastSortDirection) {

            cachedSortedWallets = new ArrayList<>(allWallets);
            cachedSortedWallets.sort(getWalletComparator(currentPrice));
            lastSortKey = currentSortKey;
            lastSortDirection = isAscending;
        }

        // Erstelle die Einträge für die ListView
        List<String> entries = new ArrayList<>();
        entries.add(getHeaderString());

        // 🔧 PERFORMANCE: Pre-calculate current price einmalig
        lastCachedPrice = currentPrice;

        // Erstelle die Strings für jede Wallet
        cachedWalletStrings = new ArrayList<>(cachedSortedWallets.size());
        for (Wallet w : cachedSortedWallets) {
            cachedWalletStrings.add(formatWalletEntry(w, currentPrice));
        }
        entries.addAll(cachedWalletStrings);

        // 🔧 PERFORMANCE: Batch-Update der ListView
        Platform.runLater(() -> {
            walletList.getItems().setAll(entries);
            applyWalletHighlights();
        });

        lastWalletUpdateTime = now;
    }

    private Comparator<Wallet> getWalletComparator(double currentPrice) {
        String key = sortKeyCombo.getValue();
        int direction = isAscending ? 1 : -1;

        Comparator<Wallet> comparator = switch (key) {
            case "USD Balance" -> Comparator.comparingDouble(Wallet::getUsdBalance);
            case "LONG Position" -> Comparator.comparingDouble(Wallet::getLongPositionUsd);
            case "Initial USD" -> Comparator.comparingDouble(Wallet::getInitialUsdBalance);
            case "Adresse" -> Comparator.comparing(Wallet::getAddress);
            case "SC Balance" -> Comparator.comparingDouble(Wallet::getBalance);
            case "Net Worth" -> Comparator.comparingDouble(wallet -> wallet.calculateNetWorth(currentPrice));
            default -> Comparator.comparingDouble(Wallet::getBalance);
        };

        return direction == 1 ? comparator : comparator.reversed();
    }

    private String getHeaderString() {
        return String.format("%-10s   | %-30s       | %22s       | %14s       | %14s  |      %16s |        %16s",
                               "ID", "Adresse", "SC Balance", "USD Balance", "LONG ($)", "Initial USD", "Net Worth");
    }

    private String formatWalletEntry(Wallet w, double currentPrice) {
        String idString = String.valueOf(w.getUniqueId());
        String shortAddr = w.getAddress().substring(0, Math.min(25, w.getAddress().length())) + "...";

        String scBalanceString;
        if (w.getBalance() < 1.0 && w.getBalance() > 0.0) {
            scBalanceString = String.format("%.3f SC", w.getBalance());
        } else {
            scBalanceString = String.format("%,d SC", Math.round(w.getBalance()));
        }

        double networth = w.calculateNetWorth(currentPrice);

        return String.format(
                "%-10s | %-30s | %22s | %14.2f | %14.2f | %16.2f | %16.2f",
                idString,
                shortAddr,
                scBalanceString,
                w.getUsdBalance(),
                w.getLongPositionUsd(),
                w.getInitialUsdBalance(),
                networth
        );
    }

    private void applyWalletHighlights() {
        final String loggedInAddress = loggedInWallet != null ? loggedInWallet.getAddress() : null;

        // 🔧 PERFORMANCE: Suche nach reichster Wallet nur bei Änderungen
        Wallet richestUser = findRichestUser();

        walletList.setCellFactory(lv -> new ListCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);

                if (empty || item == null) {
                    setText(null);
                    setTooltip(null);
                    setStyle("");
                    return;
                }

                setText(item);

                if (getIndex() == 0) {
                    setStyle("-fx-font-weight: bold; -fx-background-color: #f0f0f0;");
                    setTooltip(null);
                    return;
                }

                int walletIndex = getIndex() - 1;
                if (walletIndex < 0 || walletIndex >= cachedSortedWallets.size()) {
                    setStyle("");
                    setTooltip(null);
                    return;
                }

                Wallet currentWallet = cachedSortedWallets.get(walletIndex);
                setTooltip(new Tooltip(currentWallet.getAddress()));

                // 🔧 PERFORMANCE: Switch-Statement statt if-else-Kette
                if (loggedInAddress != null && currentWallet.getAddress().equals(loggedInAddress)) {
                    setStyle("-fx-background-color: #fce883; -fx-text-fill: #333333; -fx-font-weight: bold;");
                } else if (currentWallet.getAddress().equals(WalletManager.SUPPLY_WALLET.getAddress())) {
                    setStyle("-fx-background-color: #d1e7f7; -fx-text-fill: #333333; -fx-font-style: italic;");
                } else if (richestUser != null && currentWallet.getAddress().equals(richestUser.getAddress())) {
                    setStyle("-fx-background-color: #d4edda; -fx-text-fill: #155724; -fx-font-weight: bold;");
                } else {
                    setStyle("");
                }
            }
        });
    }

    private Wallet findRichestUser() {
        if (cachedSortedWallets == null || cachedSortedWallets.isEmpty()) {
            return null;
        }

        // 🔧 PERFORMANCE: Stream mit Optional
        return cachedSortedWallets.stream()
                .filter(w -> !w.getAddress().equals(WalletManager.SUPPLY_WALLET.getAddress()))
                .max(Comparator.comparingDouble(Wallet::getBalance))
                .orElse(null);
    }

    public void invalidateCache() {
        cachedSortedWallets = null;
        lastWalletUpdateTime = 0;
        cachedWalletStrings = null;
    }

    public Wallet getWalletAtDisplayIndex(int displayIndex) {
        if (cachedSortedWallets == null || displayIndex < 0 || displayIndex >= cachedSortedWallets.size()) {
            return null;
        }
        return cachedSortedWallets.get(displayIndex);
    }

    public void setupWalletDoubleClick() {
        walletList.setOnMouseClicked(event -> {
            if (event.getClickCount() == 2 && !walletList.getSelectionModel().isEmpty()) {
                int selectedIndex = walletList.getSelectionModel().getSelectedIndex();
                if (selectedIndex == 0) return; // Header-Zeile

                Wallet w = getWalletAtDisplayIndex(selectedIndex - 1);
                if (w == null) {
                    System.err.println("Fehler: Wallet nicht gefunden für Index " + (selectedIndex - 1));
                    return;
                }

                showWalletDetails(w);
            }
        });
    }

    private void showWalletDetails(Wallet w) {
        // 🔧 PERFORMANCE: Separate Methode für Wallet-Details
        // (Kann in eine eigene Klasse ausgelagert werden)
        WalletDetailsDialog.show(w);
    }
}