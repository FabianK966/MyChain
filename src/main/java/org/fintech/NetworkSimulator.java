package org.fintech;

import javafx.application.Platform;
import java.util.*;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.Locale;

public class NetworkSimulator {
    private final Blockchain blockchain;
    private final WalletManager walletManager;
    private final PriceSimulator priceSimulator;
    private Timer walletTimer;
    private Timer transactionTimer;
    private Timer updateTimer;
    private Timer priceUpdateTimer;
    private final AtomicBoolean running = new AtomicBoolean(false);
    private Runnable onUpdateCallback;
    private Runnable onPriceUpdateCallback;
// Konfiguration der Wallet-Generierung
    private static final long MIN_WALLET_CREATION_PERIOD = 500;
// 🛑 DELAY RESET: Muss beim Start neu gesetzt werden
    private long currentWalletCreationPeriod = 2000;
    private final double periodMultiplier = 0.9;
    private final int periodThreshold = 200;
// Konfiguration der Marktstimmung
    private double buyBias = 0.50;
// Wallets dürfen immer verkaufen/shorten.
    private static final double MAX_USD_TO_SHORT = 100000000; // Max. USD-Betrag, den eine Wallet shorten darf, um die Schulden zu begrenzen
// Konfiguration der GUI-Aktualisierung
    private static final long GUI_UPDATE_PERIOD = 5000; // 10 Sekunden für Chart/Listen
// HANDELS-GESCHWINDIGKEITSANALYSE
    private static final long INITIAL_MIN_DELAY = 1290;
// 🛑 DELAY RESET: Muss beim Start neu gesetzt werden
    private static long currentTradeMinDelay = INITIAL_MIN_DELAY;

    public NetworkSimulator(Blockchain blockchain, WalletManager walletManager, PriceSimulator priceSimulator) {
        this.blockchain = blockchain;
        this.walletManager = walletManager;
        this.priceSimulator = priceSimulator;
    }
    public void setOnUpdate(Runnable callback) {
        this.onUpdateCallback = callback;
    }
    public void setOnPriceUpdate(Runnable callback) {
        this.onPriceUpdateCallback = callback;
    }
    public boolean isRunning() {
        return running.get();
    }
    public void setBuyBias(double bias) {
        this.buyBias = Math.max(0.0, Math.min(1.0, bias));
    }
    public void start() {
        if (running.getAndSet(true)) return;
// 🛑 NEUSTART-LOGIK: Setzt die Delays auf die Startwerte zurück
        currentTradeMinDelay = INITIAL_MIN_DELAY;
        this.currentWalletCreationPeriod = 2000;
        System.out.println("=== NETZWERK-SIMULATION GESTARTET ===");
// WALLET-ERSTELLUNG STARTEN
        startWalletGeneration();
// HANDELS-SIMULATION STARTEN
        transactionTimer = new Timer(true);
        scheduleNextTrade(5);
// GUI-AKTUALISIERUNGS-TIMER STARTEN (10 Sekunden)
        updateTimer = new Timer(true);
        updateTimer.scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                triggerUpdate();
            }
        }, 0, GUI_UPDATE_PERIOD);
        // PREIS-UPDATE-TIMER STARTEN (1 Sekunde)
        priceUpdateTimer = new Timer(true);
        priceUpdateTimer.scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                triggerPriceUpdate();
            }
        }, 0, 100);
    }
    public void stop() {
        running.set(false);
        stopWalletGeneration();
        if (transactionTimer != null) {
            transactionTimer.cancel();
            transactionTimer = null;
        }
        if (updateTimer != null) {
            updateTimer.cancel();
            updateTimer = null;
        }
        if (priceUpdateTimer != null) {
            priceUpdateTimer.cancel();
            priceUpdateTimer = null;
        }
        System.out.println("=== NETZWERK-SIMULATION GESTOPPT ===");
    }
// --- WALLET-GENERIERUNGS-STEUERUNG ---
    public void startWalletGeneration() {
        if (running.get() && walletTimer == null) {
            walletTimer = new Timer(true);
            System.out.println("--- Wallet-Generierung wieder gestartet. ---");
            System.out.printf("→ Neue Wallet alle %.2fs (dynamisch, verlangsamt alle %d Wallets um %.0f%%)%n",
                    currentWalletCreationPeriod / 1000.0, periodThreshold, (100 - periodMultiplier * 100));
            scheduleNextWalletCreation(currentWalletCreationPeriod);
        }
    }
    public void stopWalletGeneration() {
        if (walletTimer != null) {
            walletTimer.cancel();
            walletTimer = null;
            System.out.println("--- Wallet-Generierung gestoppt. ---");
        }
    }
// --- INTERNE HILFSMETHODEN ---
    private void triggerUpdate() {
        if (onUpdateCallback != null) {
            Platform.runLater(onUpdateCallback);
        }
    }
    private void triggerPriceUpdate() {
        if (onPriceUpdateCallback != null) {
            Platform.runLater(onPriceUpdateCallback);
        }
    }
    private static synchronized long getAndSetCurrentTradeMinDelay(int userCount, long minDelayBase, int reductionFactor, long minDelayFast) {
        long oldDelay = currentTradeMinDelay;
        long delayReduction = (long) userCount * reductionFactor;
        long newDelay = Math.max(minDelayFast, minDelayBase - delayReduction);
        currentTradeMinDelay = newDelay;
        return oldDelay;
    }
    private void scheduleNextWalletCreation(long delay) {
        if (!running.get() || walletTimer == null) return;
        walletTimer.schedule(new TimerTask() {
            @Override
            public void run() {
                if (!running.get()) return;
                // 🛑 1. Blockchain-Call MUSS synchronisiert werden, da er einen Block hinzufügt
                Wallet newWallet = WalletManager.createWallet(blockchain, WalletManager.SUPPLY_WALLET);
                int userWalletCount = WalletManager.getWallets().size() - 1;
                if (userWalletCount > 0 && userWalletCount % periodThreshold == 0) {
                    long newPeriod = (long) (currentWalletCreationPeriod * periodMultiplier);
                    currentWalletCreationPeriod = Math.max(newPeriod, MIN_WALLET_CREATION_PERIOD);
                    System.out.printf("--- WALLET-SCHWELLE ERREICHT (%d Wallets)! Neue Wallet-Erstellungsdauer: %.0fms (%.2fs) ---%n",
                            userWalletCount, (double) currentWalletCreationPeriod, currentWalletCreationPeriod / 1000.0);
                }
                Platform.runLater(() -> {
                });
                scheduleNextWalletCreation(currentWalletCreationPeriod);
            }
        }, delay);
    }
    private void scheduleNextTrade(long delay) {
        if (!running.get() || transactionTimer == null) return;
        transactionTimer.schedule(new TimerTask() {
            @Override
            public void run() {
                if (!running.get()) return;
                simulateTrade();
                // 🛑 WICHTIG: Nutzt die historische maximale Anzahl an Wallets für die Geschwindigkeit
                int userWalletCount = WalletManager.getMaxWalletCountForSimulation();
                long maxDelayBase = 900;
                long minDelayBase = 890;
                long minDelayFast = 1;
                int reductionFactor = 2;
                long delayReduction = (long) userWalletCount * reductionFactor;
                long actualMinDelay = Math.max(minDelayFast, minDelayBase - delayReduction);
                long actualMaxDelay = Math.max(actualMinDelay, maxDelayBase - delayReduction);
                long oldActualMinDelay = getAndSetCurrentTradeMinDelay(userWalletCount, minDelayBase, reductionFactor, minDelayFast);
                if (actualMinDelay != oldActualMinDelay) {
                    System.out.printf("--- HANDELS-SCHWELLE GEÄNDERT (%d Wallets)! Neue Handelsspanne: %.0fms - %.0fms ---%n",
                            userWalletCount, (double) actualMinDelay, (double) actualMaxDelay);
                }
                long range = actualMaxDelay - actualMinDelay + 1;
                long nextDelay = actualMinDelay + new Random().nextInt((int) range);
                scheduleNextTrade(nextDelay);
            }
        }, delay);
    }
    /**
     * Prüft die Größe der Blockchain-Datei und setzt die Kette bis auf den Genesis Block zurück,
     * falls das Limit überschritten wird.
     *
     * @return true, wenn die Kette zurückgesetzt wurde.
     */
    private boolean simulateTrade() {
// 1. Initialisierung und Vorbereitung
        List<Wallet> allWallets = WalletManager.getWallets();
        Wallet supplyWallet = WalletManager.SUPPLY_WALLET;
        Random r = new Random(); // 🔧 Verwende lokale Random-Instanz
// 🔧 Definiere MARGIN_FACTOR lokal
        final double MARGIN_FACTOR = 0.25;
        List<Wallet> userWallets = allWallets.stream()
                .filter(w -> !w.getAddress().equals(supplyWallet.getAddress()) && !w.getAddress().equals(MyChainGUI.EXCHANGE_ADDRESS))
                .toList();
        if (userWallets.isEmpty()) return false;
// Wallet IMMER AKTUELL aus der Manager-Liste holen, um konsistente Salden zu garantieren
        Wallet tradingWalletCandidate = userWallets.get(r.nextInt(userWallets.size()));
        Wallet tradingWallet = WalletManager.findWalletByAddress(tradingWalletCandidate.getAddress());
        if (tradingWallet == null) return false;
        double currentSCBalance = tradingWallet.getBalance();
// Handelsrichtung wird nur durch den BuyBias bestimmt.
        boolean isBuy = r.nextDouble() < this.buyBias;
        double currentPrice = priceSimulator.getCurrentPrice();
        double actualTradePercentage = 0.33 + r.nextDouble() * 0.67; // 33-100%
        double usdToTrade = 0.0;
        // NEU: Getrennte Trade-Typen wählen, unabhängig von Balance
        double rand = r.nextDouble();
        String tradeType;
        boolean isLongOpen = false, isLongClose = false, isShortOpen = false, isShortClose = false;
        if (rand < buyBias / 2) {
            tradeType = "LONG_OPEN";
            isLongOpen = true;
        } else if (rand < buyBias) {
            tradeType = "SHORT_CLOSE";
            isShortClose = true;
        } else if (rand < (buyBias + (1 - buyBias) / 2)) {
            tradeType = "LONG_CLOSE";
            isLongClose = true;
        } else {
            tradeType = "SHORT_OPEN";
            isShortOpen = true;
        }
// IN simulateTrade():
        if (isLongOpen || isShortClose) {
            double availableUsd = tradingWallet.getUsdBalance();
            usdToTrade = availableUsd * actualTradePercentage;
            // 🔧 WICHTIG: Explizite Prüfung
            if (usdToTrade > availableUsd) {
                usdToTrade = availableUsd;
            }
            if (usdToTrade <= 0 || tradingWallet.getUsdBalance() < usdToTrade) {
                System.out.printf("   ❌ KAUF ABGELEHNT: %s... benötigt %.2f USD, hat aber nur %.2f USD.%n",
                        tradingWallet.getAddress().substring(0, 10), usdToTrade, tradingWallet.getUsdBalance());
                return false;
            }
        } else if (isLongClose) {
            // Long-Close: Basierend auf aktueller Long-Exposure
            double currentLongExposure = tradingWallet.getLongPositionUsd();
            if (currentLongExposure <= 0) return false;
            usdToTrade = currentLongExposure * actualTradePercentage;
            // 🔧 Sicherstellen, dass wir nicht mehr schließen als offen ist
            if (usdToTrade > currentLongExposure) {
                usdToTrade = currentLongExposure;
            }
            if (usdToTrade <= 0) return false;
        } else if (isShortOpen) {
            // Short-Open: Basierend auf USD * Margin (wie bisher)
            double availableUsd = tradingWallet.getUsdBalance();
            if (availableUsd <= 0) return false;
            usdToTrade = availableUsd * actualTradePercentage * MARGIN_FACTOR;
            // 🔧 Prüfe ob genug USD für Margin vorhanden ist
            double requiredMargin = usdToTrade * MARGIN_FACTOR;
            if (usdToTrade <= 0 || tradingWallet.getUsdBalance() < requiredMargin) {
                System.out.printf("   ❌ SHORT ABGELEHNT: %s... benötigt %.2f USD Margin (25%%), hat aber nur %.2f USD.%n",
                        tradingWallet.getAddress().substring(0, 10), requiredMargin, tradingWallet.getUsdBalance());
                return false;
            }
        }
// 🔧 MINIMUM-BETRAG: Stelle sicher, dass der Trade groß genug ist
        usdToTrade = Math.max(1.0, usdToTrade);
        double tradeAmountSC = Math.round((usdToTrade / currentPrice) * 1000.0) / 1000.0;
        double usdValue = tradeAmountSC * currentPrice;
        if (usdValue < 1.0 || tradeAmountSC < 0.001) return false;
        List<Transaction> txs = new ArrayList<>();
// Ausführung basierend auf Typ
        String message;
        boolean isBuyAction = isLongOpen || isShortClose; // Käufe für Long-Open oder Short-Close
        if (isBuyAction) {
            // Von Supply kaufen
            if (supplyWallet.getBalance() < tradeAmountSC + 0.01) return false;
            message = String.format(Locale.US, "SIMULIERT: SC %s für %.2f USD", isLongOpen ? "Kauf (LONG)" : "Kauf (SHORT-COVER)", usdValue);
            txs.add(supplyWallet.createTransaction(tradingWallet.getAddress(), tradeAmountSC, message, currentPrice));
            priceSimulator.executeTrade(tradeAmountSC, true);
            System.out.printf("SIMULIERT %s: %s... kaufte %.3f SC für %.2f USD (%.0f%%) | Neuer Preis: %.4f%n",
                    isLongOpen ? "KAUF (LONG)" : "KAUF (SHORT-COVER)", tradingWallet.getAddress().substring(0, 10), tradeAmountSC, usdValue, actualTradePercentage * 100, priceSimulator.getCurrentPrice());
        } else {
// An Exchange verkaufen
            message = String.format(Locale.US, "SIMULIERT: SC %s für %.2f USD", isLongClose ? "Verkauf (LONG)" : "Verkauf (SHORT)", usdValue);
            txs.add(tradingWallet.createTransaction(MyChainGUI.EXCHANGE_ADDRESS, tradeAmountSC, message, currentPrice));
            priceSimulator.executeTrade(tradeAmountSC, false);
            System.out.printf("SIMULIERT %s: %s... verkaufte %.3f SC für %.2f USD (%.0f%%) | Neuer Preis: %.4f%n",
                    isLongClose ? "VERKAUF (LONG)" : "VERKAUF (SHORT)", tradingWallet.getAddress().substring(0, 10), tradeAmountSC, usdValue, actualTradePercentage * 100, priceSimulator.getCurrentPrice());
        }
// 6. Mining und Speicherung
        if (!txs.isEmpty()) {
            txs.removeIf(tx -> tx == null);
            if (txs.isEmpty()) return false;
            blockchain.addBlock(txs);
            // 🔧 FÜGE DIES HINZU:
            Block newBlock = blockchain.getChain().get(blockchain.getChain().size() - 1);
            WalletManager.updateBalancesFromLastBlock(newBlock);
            return true;
        }
        return false;
    }
}