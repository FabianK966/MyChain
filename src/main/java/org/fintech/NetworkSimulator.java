package org.fintech;

import javafx.application.Platform;
import java.util.*;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

public class NetworkSimulator {

    private final Blockchain blockchain;
    private final WalletManager walletManager;
    private final PriceSimulator priceSimulator;
    private Timer walletTimer;
    private Timer transactionTimer;
    private final AtomicBoolean running = new AtomicBoolean(false);
    private Runnable onUpdateCallback;
    private static final long MIN_WALLET_CREATION_PERIOD = 10;
    private long currentWalletCreationPeriod = 3000;
    private final double periodMultiplier = 0.9;
    private final int periodThreshold = 20;

    // 🌟 NEUE FELDER FÜR DIE HANDELS-GESCHWINDIGKEITSANALYSE
    private static final long INITIAL_MIN_DELAY = 900;
    private static long currentTradeMinDelay = INITIAL_MIN_DELAY; // Speichert die zuletzt berechnete minimale Handelsverzögerung

    public NetworkSimulator(Blockchain blockchain, WalletManager walletManager, PriceSimulator priceSimulator) {
        this.blockchain = blockchain;
        this.walletManager = walletManager;
        this.priceSimulator = priceSimulator;
    }

    public void setOnUpdate(Runnable callback) {
        this.onUpdateCallback = callback;
    }

    public void triggerUpdate() {
        if (onUpdateCallback != null) {
            Platform.runLater(onUpdateCallback);
        }
    }

    public void start() {
        if (running.getAndSet(true)) return;

        // Beim Start den initialen Wert setzen, falls er durch einen Neustart beeinflusst wurde
        currentTradeMinDelay = INITIAL_MIN_DELAY;

        System.out.println("=== NETZWERK-SIMULATION GESTARTET ===");

        // DYNAMISCHE WALLET-ERSTELLUNG STARTEN
        walletTimer = new Timer(true);
        System.out.printf("→ Neue Wallet alle %.2fs (dynamisch, verlangsamt alle %d Wallets um %.0f%%)%n",
                currentWalletCreationPeriod / 1000.0, periodThreshold, (periodMultiplier * 100 - 100));
        scheduleNextWalletCreation(currentWalletCreationPeriod);

        transactionTimer = new Timer(true);

        // Timer 2: Handels-Simulation
        System.out.println("→ Kauf/Verkauf-Simulationen (Frequenz passt sich der Anzahl der Wallets an)");
        scheduleNextTrade(5);
    }

    // 🌟 NEUE HILFSMETHODE: Zur Verwaltung der minimalen Handelsverzögerung
    // Muss synchronisiert sein, da sie von zwei Threads (oder mehr) gleichzeitig gelesen/geschrieben werden könnte.
    private static synchronized long getAndSetCurrentTradeMinDelay(int userCount, long minDelayBase, int reductionFactor, long minDelayFast) {
        long oldDelay = currentTradeMinDelay;

        // Berechnung wie in scheduleNextTrade
        long delayReduction = (long) userCount * reductionFactor;
        long newDelay = Math.max(minDelayFast, minDelayBase - delayReduction);

        currentTradeMinDelay = newDelay;
        return oldDelay;
    }


    // 🌟 NEUE METHODE: Dynamische Planung der nächsten Wallet-Erstellung
    private void scheduleNextWalletCreation(long delay) {
        if (!running.get()) return;

        walletTimer.schedule(new TimerTask() {
            @Override
            public void run() {
                if (!running.get()) return;

                Platform.runLater(() -> {
                    // 1. Wallet erstellen (erhöht die Wallet-Anzahl)
                    Wallet newWallet = WalletManager.createWallet();

                    // 2. 🌟 NEUE LOGIK: Verzögerung anpassen (Beschleunigung), wenn Schwelle erreicht
                    int userWalletCount = WalletManager.getWallets().size() - 1;

                    if (userWalletCount > 0 && userWalletCount % periodThreshold == 0) {
                        // Verzögerung reduzieren (Beschleunigen)
                        long newPeriod = (long) (currentWalletCreationPeriod * periodMultiplier);

                        // Mindestwert prüfen, damit es nicht zu schnell wird
                        currentWalletCreationPeriod = Math.max(newPeriod, MIN_WALLET_CREATION_PERIOD);

                        System.out.printf("--- WALLET-SCHWELLE ERREICHT (%d Wallets)! Neue Wallet-Erstellungsdauer: %.0fms (%.2fs) ---%n",
                                userWalletCount, (double)currentWalletCreationPeriod, currentWalletCreationPeriod / 1000.0);
                    }

                    triggerUpdate();
                    System.out.println("NEUE WALLET erstellt: " + newWallet.getAddress().substring(0, 16) + "...");

                    // 3. Nächste Erstellung mit der (möglicherweise) neuen Verzögerung planen
                    scheduleNextWalletCreation(currentWalletCreationPeriod);
                });
            }
        }, delay);
    }


    private void scheduleNextTrade(long delay) {
        if (!running.get()) return;

        transactionTimer.schedule(new TimerTask() {
            @Override
            public void run() {
                if (!running.get()) return;

                // 1. Handelslogik ausführen (läuft auf dem TimerTask-Thread)
                simulateTrade();

                // 2. Dynamische Verzögerung basierend auf Wallet-Anzahl
                List<Wallet> allWallets = WalletManager.getWallets();
                int userWalletCount = allWallets.size() - 1;

                // Definition der Konstanten (Basiswerte)
                long maxDelayBase = 1500;
                long minDelayBase = 900;
                long minDelayFast = 300;
                int reductionFactor = 3;

                // Berechnung der Reduktion, die auf beide Delays angewandt wird
                long delayReduction = (long) userWalletCount * reductionFactor;

                // -------------------------------------------------------------------
                // NEUE BERECHNUNG DER DYNAMISCHEN MINIMAL- UND MAXIMALWERTE
                // -------------------------------------------------------------------

                // Die minimale tatsächliche Verzögerung kann nicht unter minDelayFast fallen
                long actualMinDelay = Math.max(minDelayFast, minDelayBase - delayReduction);

                // Die maximale tatsächliche Verzögerung wird um dieselbe Reduktion gekürzt.
                // Der neue maximale Delay sollte nicht unter dem neuen minimalen Delay liegen!
                long actualMaxDelay = Math.max(actualMinDelay, maxDelayBase - delayReduction);

                // -------------------------------------------------------------------

                // 3. Statusprüfung und Ausgabe (unter Verwendung der Hilfsmethode)
                long oldActualMinDelay = getAndSetCurrentTradeMinDelay(userWalletCount, minDelayBase, reductionFactor, minDelayFast);

                if (actualMinDelay != oldActualMinDelay) {
                    System.out.printf("--- HANDELS-SCHWELLE GEÄNDERT (%d Wallets)! Neue Handelsspanne: %.0fms - %.0fms ---%n",
                            userWalletCount, (double)actualMinDelay, (double)actualMaxDelay);
                }

                // 4. Nächste Verzögerung wählen (zufällig zwischen actualMinDelay und actualMaxDelay)
                long range = actualMaxDelay - actualMinDelay + 1;
                long nextDelay = actualMinDelay + new Random().nextInt((int) range);

                // 5. Nächsten Trade mit zufälliger Verzögerung planen
                scheduleNextTrade(nextDelay);
                System.out.println(nextDelay);
            }
        }, delay);
    }

    private void simulateTrade() {
        // 1. Initialisierung und Vorbereitung
        List<Wallet> allWallets = WalletManager.getWallets();
        Wallet supplyWallet = WalletManager.SUPPLY_WALLET;
        Random r = new Random();

        List<Wallet> userWallets = allWallets.stream()
                .filter(w -> !w.getAddress().equals(supplyWallet.getAddress()))
                .toList();

        if (userWallets.isEmpty()) return;

        // 2. Auswahl der Wallet und Handelsrichtung
        Wallet tradingWallet = userWallets.get(r.nextInt(userWallets.size()));

        // Bedingung: Wenn die Wallet (fast) keine SC hat, MUSS sie kaufen.
        boolean mustBuy = tradingWallet.getBalance() < 0.01;
        boolean isBuy = mustBuy || r.nextBoolean();

        // 3. Berechnung des Handelsbetrags (garantiert 33% bis 50%)
        double currentPrice = priceSimulator.getCurrentPrice();

        final double MIN_PERCENTAGE = 0.33; // 33%
        final double MAX_PERCENTAGE = 0.98; // 50%

        // 🛑 NEU: Zufälliger Prozentsatz zwischen MIN_PERCENTAGE und MAX_PERCENTAGE
        // Dies ist der garantierte Prozentsatz der Balance, der gehandelt wird.
        double actualTradePercentage = MIN_PERCENTAGE + (MAX_PERCENTAGE - MIN_PERCENTAGE) * r.nextDouble();

        double usdToTrade;

        if (isBuy) {
            // Kauf: Berechne USD-Betrag als Prozentsatz der USD-Balance
            usdToTrade = tradingWallet.getUsdBalance() * actualTradePercentage;
        } else {
            // Verkauf: Berechne SC-Betrag als Prozentsatz der SC-Balance und konvertiere in USD
            // tradingWallet.getBalance() * actualTradePercentage (SC-Menge) * currentPrice (USD-Wert)
            usdToTrade = (tradingWallet.getBalance() * actualTradePercentage) * currentPrice;
        }

        // 🛑 ABER: Wir müssen verhindern, dass Wallets mit sehr kleinen Balancen fast nichts handeln.
        usdToTrade = Math.max(1.0, usdToTrade); // Mindestbetrag von 1.0 USD beibehalten (Schutz vor Kleinstbeträgen)

        // Maximaler Limit-Schutz
        usdToTrade = Math.min(usdToTrade, 100000000.0); // Limit auf 1 Million USD pro Trade

        // Konvertierung in SC-Coins
        double tradeAmountSC = usdToTrade / currentPrice;

        // Runden des SC-Betrags auf 3 Dezimalstellen
        tradeAmountSC = Math.round(tradeAmountSC * 1000.0) / 1000.0;

        double usdValue = tradeAmountSC * currentPrice; // Der tatsächliche USD-Wert des SC-Betrags

        if (usdValue < 1.0 || tradeAmountSC < 0.001) {
            // Verhindern von extrem kleinen Transaktionen (auch nach der Berechnung)
            return;
        }

        List<Transaction> txs = new ArrayList<>();

        // 4. Ausführung der Transaktion
        if (isBuy) {
            // SC KAUFEN (USD -> SC, von Supply)

            // Finaler Check: Hat die Wallet USD und die Supply Wallet SC?
            if (tradingWallet.getUsdBalance() < usdValue || supplyWallet.getBalance() < tradeAmountSC + 0.01) {
                return;
            }

            try {
                tradingWallet.debitUsd(usdValue);
                txs.add(supplyWallet.createTransaction(tradingWallet.getAddress(), tradeAmountSC, "SIMULIERT: SC Kauf von Supply"));

                priceSimulator.executeTrade(tradeAmountSC, true);

                System.out.printf("SIMULIERT KAUF: %s... kaufte %.3f SC für %.2f USD (%.0f%% der USD-Balance) | Neuer Preis: %.4f%n",
                        tradingWallet.getAddress().substring(0, 10), tradeAmountSC, usdValue, actualTradePercentage * 100, priceSimulator.getCurrentPrice());

            } catch (Exception ignored) { return; }


        } else {
            // SC VERKAUFEN (SC -> USD, an Exchange)

            // Finaler Check: Hat die Wallet genug SC?
            if (tradingWallet.getBalance() < tradeAmountSC + 0.01) {
                return;
            }

            try {
                tradingWallet.creditUsd(usdValue);
                txs.add(tradingWallet.createTransaction(MyChainGUI.EXCHANGE_ADDRESS, tradeAmountSC, "SIMULIERT: SC Verkauf an Exchange"));

                priceSimulator.executeTrade(tradeAmountSC, false);

                System.out.printf("SIMULIERT VERKAUF: %s... verkaufte %.3f SC für %.2f USD (%.0f%% der SC-Balance) | Neuer Preis: %.4f%n",
                        tradingWallet.getAddress().substring(0, 10), tradeAmountSC, usdValue, actualTradePercentage * 100, priceSimulator.getCurrentPrice());

            } catch (Exception ignored) { return; }
        }

        // 5. Mining und Speicherung
        if (!txs.isEmpty()) {
            blockchain.addBlock(txs);
            BlockchainPersistence.saveBlockchain(blockchain);
            WalletManager.recalculateAllBalances();
            WalletManager.saveWallets();

            // GUI-Aktualisierung
            Platform.runLater(this::triggerUpdate);
        }
    }

    public void stopWalletGeneration() {
        if (walletTimer != null) {
            walletTimer.cancel();
            walletTimer = null;
            System.out.println("--- Wallet-Generierung gestoppt. ---");
        }
    }

    public void startWalletGeneration() {
        if (running.get() && walletTimer == null) {
            walletTimer = new Timer(true);
            System.out.println("--- Wallet-Generierung wieder gestartet. ---");
            // Planen Sie die erste Erstellung mit der aktuellen Periodendauer
            scheduleNextWalletCreation(currentWalletCreationPeriod);
        }
    }

    public void stop() {
        running.set(false);

        if (walletTimer != null) {
            walletTimer.cancel();
            walletTimer = null;
        }
        if (transactionTimer != null) {
            transactionTimer.cancel();
            transactionTimer = null;
        }
        System.out.println("=== NETZWERK-SIMULATION GESTOPPT ===");
    }

    public boolean isRunning() {
        return running.get();
    }
}