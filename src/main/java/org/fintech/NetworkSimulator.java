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
    private static final long MIN_WALLET_CREATION_PERIOD = 50;
    private long currentWalletCreationPeriod = 1000;
    private final double periodMultiplier = 0.9;
    private final int periodThreshold = 50;
    private double buyBias = 0.50;
    private static final long GUI_UPDATE_PERIOD = 1000;
    private static final long INITIAL_MIN_DELAY = 1290;
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

        currentTradeMinDelay = INITIAL_MIN_DELAY;
        this.currentWalletCreationPeriod = 2000;
        System.out.println("=== NETZWERK-SIMULATION GESTARTET ===");

        startWalletGeneration();

        transactionTimer = new Timer(true);
        scheduleNextTrade(5);

        updateTimer = new Timer(true);
        updateTimer.scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                triggerUpdate();
            }
        }, 0, GUI_UPDATE_PERIOD);

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
                Wallet newWallet = WalletManager.createWallet(blockchain, WalletManager.SUPPLY_WALLET);
                int userWalletCount = WalletManager.getWallets().size() - 1;
                if (userWalletCount > 0 && userWalletCount % periodThreshold == 0) {
                    long newPeriod = (long) (currentWalletCreationPeriod * periodMultiplier);
                    currentWalletCreationPeriod = Math.max(newPeriod, MIN_WALLET_CREATION_PERIOD);
                    System.out.printf("--- WALLET-SCHWELLE ERREICHT (%d Wallets)! Neue Wallet-Erstellungsdauer: %.0fms (%.2fs) ---%n",
                            userWalletCount, (double) currentWalletCreationPeriod, currentWalletCreationPeriod / 1000.0);
                }
                Platform.runLater(() -> {});
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
                int userWalletCount = WalletManager.getMaxWalletCountForSimulation();
                long maxDelayBase = 800;
                long minDelayBase = 790;
                long minDelayFast = 1;
                int reductionFactor = 1;
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

    private boolean simulateTrade() {
        List<Wallet> allWallets = WalletManager.getWallets();
        Wallet supplyWallet = WalletManager.SUPPLY_WALLET;
        Random r = new Random();

        List<Wallet> userWallets = allWallets.stream()
                .filter(w -> !w.getAddress().equals(supplyWallet.getAddress()) && !w.getAddress().equals(MyChainGUI.EXCHANGE_ADDRESS))
                .toList();
        if (userWallets.isEmpty()) return false;

        Wallet tradingWalletCandidate = userWallets.get(r.nextInt(userWallets.size()));
        Wallet tradingWallet = WalletManager.findWalletByAddress(tradingWalletCandidate.getAddress());
        if (tradingWallet == null) return false;

        double currentPrice = priceSimulator.getCurrentPrice();
        double actualTradePercentage = 0.33 + r.nextDouble() * 0.67;
        double usdToTrade = 0.0;

        double longExposure = tradingWallet.getLongPositionUsd();
        boolean isLongOnly = longExposure > 0.0;
        boolean isNeutral = longExposure <= 0.0;

        if (isLongOnly) {
            // Wenn Long offen ist: entweder halten oder schließen
            if (r.nextDouble() < (1.0 - this.buyBias)) {
                // Long schließen (Verkauf)
                usdToTrade = longExposure * actualTradePercentage;
                if (usdToTrade > longExposure) {
                    usdToTrade = longExposure;
                }
                if (usdToTrade <= 0) return false;

                double tradeAmountSC = Math.round((usdToTrade / currentPrice) * 1000.0) / 1000.0;
                double usdValue = tradeAmountSC * currentPrice;

                if (usdValue < 1.0 || tradeAmountSC < 0.001) return false;
                if (tradeAmountSC > tradingWallet.getBalance()) return false;

                String message = String.format(Locale.US, "SIMULIERT: SC Verkauf (LONG) für %.2f USD", usdValue);
                Transaction tx = tradingWallet.createTransaction(MyChainGUI.EXCHANGE_ADDRESS, tradeAmountSC, message, currentPrice);

                if (tx != null) {
                    priceSimulator.executeTrade(tradeAmountSC, false);
                    System.out.printf("SIMULIERT VERKAUF (LONG): %s... verkaufte %.3f SC für %.2f USD (%.0f%%) | Neuer Preis: %.4f%n",
                            tradingWallet.getAddress().substring(0, 10), tradeAmountSC, usdValue, actualTradePercentage * 100, priceSimulator.getCurrentPrice());

                    List<Transaction> txs = new ArrayList<>();
                    txs.add(tx);
                    blockchain.addBlock(txs);

                    Block newBlock = blockchain.getChain().get(blockchain.getChain().size() - 1);
                    WalletManager.updateBalancesFromLastBlock(newBlock);

                    return true;
                }
            }
        } else if (isNeutral) {
            // Wenn neutral: Long eröffnen (Kauf) basierend auf Bias
            if (r.nextDouble() < this.buyBias) {
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

                double tradeAmountSC = Math.round((usdToTrade / currentPrice) * 1000.0) / 1000.0;
                double usdValue = tradeAmountSC * currentPrice;

                if (usdValue < 1.0 || tradeAmountSC < 0.001) return false;
                if (supplyWallet.getBalance() < tradeAmountSC + 0.01) return false;

                String message = String.format(Locale.US, "SIMULIERT: SC Kauf (LONG) für %.2f USD", usdValue);
                Transaction tx = supplyWallet.createTransaction(tradingWallet.getAddress(), tradeAmountSC, message, currentPrice);

                if (tx != null) {
                    priceSimulator.executeTrade(tradeAmountSC, true);
                    System.out.printf("SIMULIERT KAUF (LONG): %s... kaufte %.3f SC für %.2f USD (%.0f%%) | Neuer Preis: %.4f%n",
                            tradingWallet.getAddress().substring(0, 10), tradeAmountSC, usdValue, actualTradePercentage * 100, priceSimulator.getCurrentPrice());

                    List<Transaction> txs = new ArrayList<>();
                    txs.add(tx);
                    blockchain.addBlock(txs);

                    Block newBlock = blockchain.getChain().get(blockchain.getChain().size() - 1);
                    WalletManager.updateBalancesFromLastBlock(newBlock);

                    return true;
                }
            }
        }

        return false;
    }
}