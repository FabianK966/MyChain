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
        Random r = new Random();
        final double MARGIN_FACTOR = 0.25;

        List<Wallet> userWallets = allWallets.stream()
                .filter(w -> !w.getAddress().equals(supplyWallet.getAddress()) && !w.getAddress().equals(MyChainGUI.EXCHANGE_ADDRESS))
                .toList();
        if (userWallets.isEmpty()) return false;

        // Wallet IMMER AKTUELL aus der Manager-Liste holen
        Wallet tradingWalletCandidate = userWallets.get(r.nextInt(userWallets.size()));
        Wallet tradingWallet = WalletManager.findWalletByAddress(tradingWalletCandidate.getAddress());
        if (tradingWallet == null) return false;

        double currentSCBalance = tradingWallet.getBalance();
        double currentPrice = priceSimulator.getCurrentPrice();
        double actualTradePercentage = 0.33 + r.nextDouble() * 0.67; // 33-100%
        double usdToTrade = 0.0;

        // --- NEUE LOGIK: Zustandsbasierte Trade-Typ-Wahl ---
        String tradeType = null;
        boolean isLongOpen = false, isLongClose = false, isShortOpen = false, isShortClose = false;

        double longExposure = tradingWallet.getLongPositionUsd();
        double shortExposure = tradingWallet.getShortPositionUsd();

        // Zustand bestimmen (Keine gleichzeitigen Long/Short-Positionen erlaubt)
        boolean isLongOnly = longExposure > 0.0 && shortExposure <= 0.0;
        boolean isShortOnly = shortExposure > 0.0 && longExposure <= 0.0;
        boolean isNeutral = longExposure <= 0.0 && shortExposure <= 0.0;

        // 1. PRÜFUNG: Wenn Long offen ist
        if (isLongOnly) {
            // Wenn Long offen, besteht die Wahl zwischen: Position halten (Abbruch) oder Long schließen.
            // buyBias (Kaufneigung) ist hier die Neigung zum Halten/Abbrechen. (1 - buyBias) ist die Neigung zum Verkauf/Schließen.
            if (r.nextDouble() < (1.0 - this.buyBias)) {
                tradeType = "LONG_CLOSE";
                isLongClose = true;
            } else {
                // Entscheidung, die Position zu halten oder ein Trade ist nicht möglich
                return false;
            }

            // 2. PRÜFUNG: Wenn Short offen ist
        } else if (isShortOnly) {
            // Wenn Short offen, besteht die Wahl zwischen: Position halten (Abbruch) oder Short schließen.
            // buyBias ist die Neigung zum Kauf/Schließen.
            if (r.nextDouble() < this.buyBias) {
                tradeType = "SHORT_CLOSE";
                isShortClose = true;
            } else {
                // Entscheidung, die Position zu halten oder ein Trade ist nicht möglich
                return false;
            }

            // 3. PRÜFUNG: Wenn Neutral (keine Position offen)
        } else if (isNeutral) {
            // Wenn Neutral, wählen wir eine neue Position (Long Open oder Short Open) basierend auf dem Bias.
            if (r.nextDouble() < this.buyBias) { // KAUF-Bias
                tradeType = "LONG_OPEN";
                isLongOpen = true;
            } else { // VERKAUF-Bias (1 - buyBias)
                tradeType = "SHORT_OPEN";
                isShortOpen = true;
            }
        } else {
            // Fehlerzustand: Wallet hat sowohl Long als auch Short (sollte durch die Logik vermieden werden)
            // oder Exposures sind 0, aber isNeutral war false (Logikfehler)
            return false;
        }

        // --- PRÜFUNG DER VORAUSSETZUNGEN FÜR DEN GEWÄHLTEN TRADE-TYP ---

        if (isLongOpen || isShortClose) {
            // KAUF-AKTION (Long Open oder Short Close)

            if (isShortClose) {
                // Short-Close: Basierend auf aktueller Short-Exposure (Die SC-Menge, die gekauft wird, um zu covern)
                double currentShortExposure = tradingWallet.getShortPositionUsd(); // Oder die USD-Basis der Short-Position

                // Hier muss die USD-Basis der Short-Position verwendet werden,
                // da der Trade ja geschlossen wird.
                usdToTrade = currentShortExposure * actualTradePercentage;

                if (usdToTrade > currentShortExposure) {
                    usdToTrade = currentShortExposure;
                }
                if (usdToTrade <= 0) return false;

            } else if (isLongOpen) {
                // Long-Open: Basierend auf verfügbarer USD-Liquidität
                double availableUsd = tradingWallet.getUsdBalance();
                usdToTrade = availableUsd * actualTradePercentage;

                if (usdToTrade > availableUsd) {
                    usdToTrade = availableUsd;
                }
                if (usdToTrade <= 0 || tradingWallet.getUsdBalance() < usdToTrade) {
                    System.out.printf("   ❌ KAUF (LONG) ABGELEHNT: %s... benötigt %.2f USD, hat aber nur %.2f USD.%n",
                            tradingWallet.getAddress().substring(0, 10), usdToTrade, tradingWallet.getUsdBalance());
                    return false;
                }
            }

        } else if (isLongClose) {
            // VERKAUF-AKTION (Long Close)

            // Long-Close: Basierend auf aktueller Long-Exposure
            double currentLongExposure = tradingWallet.getLongPositionUsd();
            // Die Prüfung currentLongExposure <= 0 ist bereits durch isLongOnly am Anfang abgedeckt.

            usdToTrade = currentLongExposure * actualTradePercentage;

            if (usdToTrade > currentLongExposure) {
                usdToTrade = currentLongExposure;
            }
            if (usdToTrade <= 0) return false;

        } else if (isShortOpen) {
            // VERKAUF-AKTION (Short Open)

            // Short-Open: Basierend auf USD * Margin (wie bisher)
            double availableUsd = tradingWallet.getUsdBalance();
            if (availableUsd <= 0) return false;
            usdToTrade = availableUsd * actualTradePercentage * MARGIN_FACTOR;

            // Prüfe ob genug USD für Margin vorhanden ist
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

            // Balancen-Update nach Trade
            Block newBlock = blockchain.getChain().get(blockchain.getChain().size() - 1);
            WalletManager.updateBalancesFromLastBlock(newBlock);

            // 🚀 NEU: Liquidationsprüfung nach Balancenaktualisierung
            processLiquidation(tradingWallet);

            return true;
        }
        return false;
    }


    private void processLiquidation(Wallet wallet) {

        // Die Wallet MUSS IMMER AKTUELL aus dem Manager geholt werden.
        Wallet tradingWallet = WalletManager.findWalletByAddress(wallet.getAddress());
        if (tradingWallet == null) return;

        // 1. LIQUIDATIONSBEDINGUNG PRÜFEN: Negative SC Balance UND 0 USD Balance
        // In einer realistischeren Simulation müsste hier der Netto-Vermögenswert (Margin - Verlust) geprüft werden.
        // Wir verwenden die vereinfachte, von Ihnen vorgeschlagene Bedingung:
        if (tradingWallet.getBalance() < 0 && tradingWallet.getUsdBalance() <= 0.0) {

            double scDebt = Math.abs(tradingWallet.getBalance());
            String walletAddressShort = tradingWallet.getAddress().substring(0, 10);

            System.out.printf("🚨 LIQUIDATION: Wallet %s... wird liquidiert! SC-Schuld: %.3f. USD-Balance: %.2f%n",
                    walletAddressShort, scDebt, tradingWallet.getUsdBalance());

            // 2. Liquidations-Transaktion erstellen (Short-Position Zwangsschließen)
            // Die SC-Schuld wird von der TradingWallet an die SupplyWallet (oder Exchange) zurückgegeben.
            // Das bedeutet, die TradingWallet zahlt die geliehenen SC zurück, um die SC-Bilanz auf 0 zu setzen.
            String message = String.format(Locale.US, "LIQUIDATION: Zwangs-Kauf (Short-Cover) von %.3f SC.", scDebt);

            // Annahme: Die SupplyWallet dient als Quelle/Gegenpartei für die Leihe
            Wallet supplyWallet = WalletManager.SUPPLY_WALLET;

            // WICHTIG: Wir brauchen eine Transaktion, die die SC-Schuld *begleicht*.
            // Da die TradingWallet die SC-Schuld hat (neg. Saldo), muss sie die SC 'zurückgeben'.
            // Da die Wallet kein USD mehr hat, wird die Position zum aktuellen Kurs geschlossen
            // und der verbleibende Verlust (der zum Liquidation geführt hat) wird durch die Margin abgedeckt.

            // Die einfachste simulatorische Abbildung:
            // Die negativen SC werden auf 0 gesetzt und das USD-Konto wird auch auf 0 gesetzt,
            // da die gesamten USD (Margin) verloren sind.
            // Dies erfolgt durch eine spezielle "Liquidations-Transaktion"
            // die das Guthaben auf 0 setzt, ohne SC/USD zu bewegen,
            // oder durch eine Transaktion mit der Exchange, die genau die Schuld deckt.

            // Variante 1: Spezial-Transaktion, um Salden auf 0 zu setzen (einfacher für Simulator-Design)
            // **HINWEIS:** Da die SC-Balance negativ ist, ist keine reguläre Transaktion möglich.
            // Wir müssen *annehmen*, dass der WalletManager eine Methode bereitstellt,
            // um die Bilanz direkt zu bereinigen, wenn der Block gemined ist.

            try {
                List<Transaction> liquidationTxs = new ArrayList<>();
                // Wir simulieren den zwangsweisen Kauf (SHORT_CLOSE) zum aktuellen Preis.
                double currentPrice = priceSimulator.getCurrentPrice();
                double usdValueLost = scDebt * currentPrice;

                // 1. Die Wallet 'kauft' die SC von der SupplyWallet zurück.
                // (Die SupplyWallet stellt die SC zur Verfügung, die der Exchange braucht, um die Leihe zu decken)
                // Dies ist ein SC-Transfer von Supply an TradingWallet (begleicht die neg. SC)
                // UND ein USD-Transfer von TradingWallet an SupplyWallet (zum Schließen des Trades)

                // Da die USD-Balance 0 ist, müssen wir annehmen, dass die *gebundene* Margin
                // den Verlust bis zur 0-Grenze abdeckt. Die Liquidation stellt sicher,
                // dass die TradingWallet keine USD-Schuld mehr hat (USD-Balance = 0)
                // und die SC-Schuld beglichen ist (SC-Balance = 0).

                // Realistische Simulation des Zwangs-Kaufs:
                // 1. Wallet **erhält** SC, um Schuld zu decken (SC-Schuld: -930 -> 0)
                // 2. Wallet **gibt** USD (die Margin) ab, um den Kauf zu bezahlen.

                // Wir schicken eine Transaktion von der TradingWallet an die Exchange,
                // die die SC-Schuld auflöst (durch den Trade-Type "SHORT_CLOSE").

                // **WICHTIGSTE ANNAHME:** Die Liquidation ist ein SHORT_CLOSE, bei dem die gesamte Margin
                // der Wallet verbraucht wird, um die Position zu schließen, und USD auf 0 gesetzt wird.

                // Erzeugen einer Zwangsschließungs-Transaktion (Short-Cover)
                Transaction liquidationTx = supplyWallet.createTransaction(
                        tradingWallet.getAddress(),
                        scDebt, // Menge, die 'gekauft' wird, um die Schuld zu decken
                        message,
                        currentPrice
                );

                // Dies ist ein KAUF-Vorgang (SC an TradingWallet, USD an SupplyWallet)
                if (liquidationTx != null) {
                    liquidationTxs.add(liquidationTx);

                    // Block für die Liquidation minen
                    blockchain.addBlock(liquidationTxs);

                    // Balancen nach Liquidation aktualisieren
                    Block liquidationBlock = blockchain.getChain().get(blockchain.getChain().size() - 1);
                    WalletManager.updateBalancesFromLastBlock(liquidationBlock);

                    // Abschließende Konsistenzprüfung (setzt USD implizit auf 0 durch Verlust)
                    WalletManager.setUsdBalanceExplicitly(tradingWallet.getAddress(), 0.0);

                    System.out.printf("✅ LIQUIDIERT: %s... Position geschlossen. SC: 0.000, USD: 0.00%n", walletAddressShort);

                } else {
                    System.out.printf("❌ FEHLER: Liquidations-Transaktion für %s... konnte nicht erstellt werden.%n", walletAddressShort);
                }

            } catch (Exception e) {
                System.err.println("Fehler während der Liquidationsverarbeitung: " + e.getMessage());
                e.printStackTrace();
            }
        }
    }
}