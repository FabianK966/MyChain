package org.fintech;

import javafx.application.Platform;
import java.util.*;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;
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
    private static final long MIN_WALLET_CREATION_PERIOD = 200;
    // 🛑 DELAY RESET: Muss beim Start neu gesetzt werden
    private long currentWalletCreationPeriod = 3000;
    private final double periodMultiplier = 0.9;
    private final int periodThreshold = 50;

    // Konfiguration der Marktstimmung
    private double buyBias = 0.50;

    // 🟢 ANGEPASSTE KONSTANTE: Definiert jetzt nur die Logging-Schwelle, NICHT die Handels-Erlaubnis
    // Wallets dürfen immer verkaufen/shorten.
    private static final double MIN_SC_BALANCE_FOR_SHORT_LOGGING = 1.0;
    private static final double MAX_USD_TO_SHORT = 100000000; // Max. USD-Betrag, den eine Wallet shorten darf, um die Schulden zu begrenzen

    // 🌟 KONSTANTEN: Dateigröße und Pfade
    private static final long MAX_FILE_SIZE_BYTES = 1 * 1024 * 1024; // 1 MB Limit
    private static final String BLOCKCHAIN_FILE_PATH = "blockchain.json"; // 🛑 KORRIGIERTER PFAD

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

    // --- ÖFFENTLICHE API ---

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
        this.currentWalletCreationPeriod = 3000;
        // -----------------------------------------------------------------

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

                long maxDelayBase = 1300;
                long minDelayBase = 1290;
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
    private boolean checkAndResetChain() {
        try {
            java.io.File file = new java.io.File(BLOCKCHAIN_FILE_PATH);

            if (file.exists() && file.length() > MAX_FILE_SIZE_BYTES) {
                System.out.printf("🚨 ALARM: Blockchain-Datei (%.2f MB) überschreitet Limit (%.2f MB). Wird auf Genesis Block zurückgesetzt...%n",
                        file.length() / (1024.0 * 1024.0), MAX_FILE_SIZE_BYTES / (1024.0 * 1024.0));

                // 1. Kette zurücksetzen, behält Genesis Block (#0)
                blockchain.resetChain();

                // 2. Wallets neu berechnen: Setzt die Balancen auf den Stand nach der Genesis-Transaktion zurück.
                WalletManager.recalculateAllBalances();
                WalletManager.saveWallets();

                // 3. Neue (kleine) Kette speichern (überschreibt die alte, große Datei)
                BlockchainPersistence.saveBlockchain(blockchain);

                // Da ein Reset die Chain verändert, muss ein UI Update an den Update-Timer gesendet werden.
                triggerUpdate();

                return true;
            }
        } catch (Exception e) {
            System.err.println("Fehler bei der Überprüfung/Zurücksetzung der Blockchain-Datei: " + e.getMessage());
        }
        return false;
    }


    private boolean simulateTrade() {
        // 1. Initialisierung und Vorbereitung
        List<Wallet> allWallets = WalletManager.getWallets();
        Wallet supplyWallet = WalletManager.SUPPLY_WALLET;
        Random r = new Random();

        List<Wallet> userWallets = allWallets.stream()
                .filter(w -> !w.getAddress().equals(supplyWallet.getAddress()) && !w.getAddress().equals(MyChainGUI.EXCHANGE_ADDRESS))
                .toList();

        if (userWallets.isEmpty()) return false;

        // Wallet IMMER AKTUELL aus der Manager-Liste holen, um konsistente Salden zu garantieren
        Wallet tradingWalletCandidate = userWallets.get(r.nextInt(userWallets.size()));
        Wallet tradingWallet = WalletManager.findWalletByAddress(tradingWalletCandidate.getAddress());

        if (tradingWallet == null) return false;

        double currentSCBalance = tradingWallet.getBalance();

        // 🌟 NEU: Keine "mustBuy" Logik. Die Handelsrichtung wird nur durch den BuyBias bestimmt.
        boolean isBuy = r.nextDouble() < this.buyBias;

        // 4. Berechnung des Handelsbetrags
        double currentPrice = priceSimulator.getCurrentPrice();
        final double MIN_PERCENTAGE = 0.33;
        final double MAX_PERCENTAGE = 0.95;
        double actualTradePercentage = MIN_PERCENTAGE + (MAX_PERCENTAGE - MIN_PERCENTAGE) * r.nextDouble();
        double usdToTrade;

        if (isBuy) {
            // 🟢 KAUF (LONG)
            // Basiere den Betrag auf dem verfügbaren USD-Guthaben
            double desiredUsdToTrade = tradingWallet.getUsdBalance() * actualTradePercentage;
            usdToTrade = Math.min(desiredUsdToTrade, tradingWallet.getUsdBalance());

            // Minimale USD-Liquidität erforderlich
            if (usdToTrade < 1.0) return false;

        } else {
            // 🟢 VERKAUF (LONG oder SHORT)

            // SC-Check, um zu bestimmen, ob es ein Long-Verkauf oder ein Short-Sale ist
            // Achtung: MIN_SC_BALANCE_FOR_SHORT_LOGGING muss hier definiert sein
            final double MIN_SC_BALANCE_FOR_SHORT_LOGGING = 0.0; // Angenommener Wert
            boolean isSellingExistingSC = currentSCBalance >= MIN_SC_BALANCE_FOR_SHORT_LOGGING;

            if (isSellingExistingSC) {
                // Fall 1: Normaler Verkauf (Liquidierung einer bestehenden Long-Position)
                // Basiere den Betrag auf dem USD-Wert der *verfügbaren* SC
                usdToTrade = (tradingWallet.getBalance() * actualTradePercentage) * currentPrice;

            } else {
                // Fall 2: Short-Sale (Wallet hat keine/kaum SC oder ist bereits Short)

                // 1. Berechnung der maximalen Short-Menge basierend auf Margin (4x USD-Guthaben bei 25% Margin)
                final double MARGIN_FACTOR = 1.00;
                // Achtung: MAX_USD_TO_SHORT muss hier definiert sein
                final double MAX_USD_TO_SHORT = 10000000.0; // Angenommener Wert

                double maxPossibleShortUsd = tradingWallet.getUsdBalance() / MARGIN_FACTOR;

                // 2. Bestimme den gewünschten Short-Betrag (basierend auf Prozentsatz der Max. Short-Menge)
                double desiredShortUsd = maxPossibleShortUsd * actualTradePercentage;

                // 3. Begrenzung auf das harte (hohe) Limit MAX_USD_TO_SHORT.
                usdToTrade = Math.min(desiredShortUsd, MAX_USD_TO_SHORT * actualTradePercentage);

                // Härte Grenze: Wenn die Margin für 1 USD Short nicht reicht, ablehnen.
                if (tradingWallet.getUsdBalance() < usdToTrade * MARGIN_FACTOR) {
                    System.out.printf("   ❌ SHORT ABGELEHNT: %s... benötigt %.2f USD Margin (25%%), hat aber nur %.2f USD.%n",
                            tradingWallet.getAddress().substring(0, 10), usdToTrade * MARGIN_FACTOR, tradingWallet.getUsdBalance());
                    return false;
                }
            }

            usdToTrade = Math.max(1.0, usdToTrade); // Minimale Trade-Größe
            if (usdToTrade < 1.0) return false;
        }

        double tradeAmountSC = Math.round((usdToTrade / currentPrice) * 1000.0) / 1000.0;
        double usdValue = tradeAmountSC * currentPrice;

        // Trade-Größe prüfen
        if (usdValue < 1.0 || tradeAmountSC < 0.001) return false;

        List<Transaction> txs = new ArrayList<>();

        // 5. Ausführung der Transaktion
        if (isBuy) {
            // 🟢 Kauf (LONG-POSITION)
            if (tradingWallet.getUsdBalance() < usdValue || supplyWallet.getBalance() < tradeAmountSC + 0.01) {
                return false;
            }

            try {
                tradingWallet.debitUsd(usdValue);

                // 🛑 KORREKTUR: Formatierung mit Locale.US, um Tausendertrennzeichen zu unterdrücken
                txs.add(supplyWallet.createTransaction(tradingWallet.getAddress(), tradeAmountSC,
                        String.format(Locale.US, "SIMULIERT: SC Kauf (LONG) von Supply für %.2f USD", usdValue), currentPrice));

                priceSimulator.executeTrade(tradeAmountSC, true);

                System.out.printf("SIMULIERT KAUF: %s... KAUF (LONG) %.3f SC für %.2f USD (%.0f%%) | Neuer Preis: %.4f%n",
                        tradingWallet.getAddress().substring(0, 10), tradeAmountSC, usdValue, actualTradePercentage * 100, priceSimulator.getCurrentPrice());

            } catch (Exception ignored) {
                return false;
            }


        } else {
            // 🟢 Verkauf (LONG oder SHORT)

            // Achtung: MIN_SC_BALANCE_FOR_SHORT_LOGGING muss hier definiert sein
            final double MIN_SC_BALANCE_FOR_SHORT_LOGGING = 0.0; // Angenommener Wert
            double projectedNewBalance = currentSCBalance - tradeAmountSC;
            String logAction = projectedNewBalance >= MIN_SC_BALANCE_FOR_SHORT_LOGGING ? "VERKAUF (LONG)" : "SHORT-SALE";

            try {
                tradingWallet.creditUsd(usdValue);

                // 🛑 KORREKTUR: Formatierung mit Locale.US
                txs.add(tradingWallet.createTransaction(MyChainGUI.EXCHANGE_ADDRESS, tradeAmountSC,
                        String.format(Locale.US, "SIMULIERT: SC Verkauf (%s) an Exchange für %.2f USD", logAction.contains("SHORT") ? "SHORT" : "LONG", usdValue), currentPrice));

                priceSimulator.executeTrade(tradeAmountSC, false);

                System.out.printf("SIMULIERT %s: %s... verkaufte %.3f SC für %.2f USD (%.0f%%) | Neue Balance: %.3f SC | Neuer Preis: %.4f%n",
                        logAction, tradingWallet.getAddress().substring(0, 10), tradeAmountSC, usdValue, actualTradePercentage * 100, projectedNewBalance, priceSimulator.getCurrentPrice());

            } catch (Exception ignored) {
                return false;
            }
        }

        // 6. Mining und Speicherung
        if (!txs.isEmpty()) {
            txs.removeIf(tx -> tx == null); // ⬅️ DIESE ZEILE HINZUFÜGEN
            // Prüfen, ob nach dem Filtern noch gültige Transaktionen übrig sind
            if (txs.isEmpty()) return false;
            blockchain.addBlock(txs);

            // 🛑 WICHTIG: WalletManager.recalculateAllBalances() muss NACH dem Block-Add laufen,
            // um die USD-Salden und Positionen korrekt zu aktualisieren.
            WalletManager.recalculateAllBalances();

            // Prüfung auf Blockchain Reset (Angenommen, diese Methode existiert)
            // checkAndResetChain();

            BlockchainPersistence.saveBlockchain(blockchain);
            WalletManager.saveWallets();

            return true;
        }
        return false;
    }
}