package org.fintech;

import java.io.*;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.locks.ReentrantReadWriteLock;

public class WalletManager {

    // Dedizierte Wallet für den Coin-Supply
    public static final Wallet SUPPLY_WALLET = new Wallet("admin");
    private static int nextWalletId = 1;
    private static int maxWalletCountForSimulation = 0;

    // 🔧 PERFORMANCE: Ersetze CopyOnWriteArrayList durch normale ArrayList mit Lock
    private static final List<Wallet> wallets = new ArrayList<>();
    private static final ReentrantReadWriteLock walletsLock = new ReentrantReadWriteLock();

    // 🔧 CACHING: Cache für GUI-Daten (vermeidet häufige Neuberechnungen)
    private static volatile Map<String, Map<String, Double>> guiCache = null;
    private static volatile long lastCacheUpdate = 0;
    private static final long CACHE_TTL_MS = 1000; // Cache für 1 Sekunde

    // 🔧 PERFORMANCE: Transaktions-Parser einmalig initialisieren
    private static final Pattern USD_VALUE_PATTERN = Pattern.compile("([\\d.,]+)\\sUSD");

    public static final WalletManager INSTANCE = new WalletManager();
    private WalletManager() {}

    public static int getAndIncrementNextId() {
        return nextWalletId++;
    }

    public static void loadWallets() {
        walletsLock.writeLock().lock();
        try {
            maxWalletCountForSimulation = 0;
            wallets.clear();
            wallets.add(SUPPLY_WALLET);

            Wallet firstUser = createNewUserWallet();
            wallets.add(firstUser);

            System.out.println("Wallets neu initialisiert (kein Laden).");
            updateAllBalancesFromBlockchain();
            invalidateCache(); // 🔧 Cache invalidieren
        } finally {
            walletsLock.writeLock().unlock();
        }
    }

    public static synchronized void saveWallets() {
        System.out.println("Wallets würden gespeichert (" + getWallets().size() + " Wallets)");
    }

    // 🔧 NEUE EINHEITLICHE METHODE: Verarbeitet eine einzelne Transaktion
    private static void processSingleTransaction(Transaction tx) {
        String sender = tx.getSender();
        String recipient = tx.getRecipient();
        double amount = tx.getAmount();
        double usdValue = parseUsdValueFromMessage(tx.getMessage());
        String message = tx.getMessage().toLowerCase();

        Wallet senderWallet = findWalletByAddress(sender);
        Wallet recipientWallet = findWalletByAddress(recipient);

        // Transaktion zur Historie hinzufügen
        if (senderWallet != null) {
            senderWallet.getTransactionHistory().add(tx);
        }
        if (recipientWallet != null) {
            recipientWallet.getTransactionHistory().add(tx);
        }

        // SC-Balance aktualisieren
        boolean isExchangeSell = MyChainGUI.EXCHANGE_ADDRESS.equals(recipient);
        boolean isCoinbase = "system".equals(sender) || sender == null || sender.isEmpty();

        if (!isCoinbase && senderWallet != null) {
            senderWallet.debit(amount);
        }
        if (!isExchangeSell && recipientWallet != null) {
            recipientWallet.credit(amount);
        }

        // 🔧 KONSOLIDIERTE LOGIK: USD und Positionen aktualisieren
        if (sender.equals(SUPPLY_WALLET.getAddress()) && message.contains("kauf (long)") && usdValue > 0) {
            if (recipientWallet != null) {
                safeDebitUsd(recipientWallet, usdValue);
                recipientWallet.setLongPositionUsd(recipientWallet.getLongPositionUsd() + usdValue);
            }
        } else if (recipient.equals(MyChainGUI.EXCHANGE_ADDRESS) && message.contains("verkauf (long)") && usdValue > 0) {
            if (senderWallet != null) {
                senderWallet.creditUsd(usdValue);
                senderWallet.setLongPositionUsd(senderWallet.getLongPositionUsd() - usdValue);
                if (senderWallet.getLongPositionUsd() < 0) senderWallet.setLongPositionUsd(0.0);
            }
        }
    }

    // 🔧 EINHEITLICHE METHODE FÜR ALLE TRANSAKTIONEN
    public static synchronized void updateAllBalancesFromBlockchain() {
        Blockchain chain = BlockchainPersistence.loadBlockchain("MyChain", 1);
        System.out.println("🔧 Komplette Balance-Berechnung aus Blockchain gestartet...");

        walletsLock.writeLock().lock();
        try {
            // 1. ALLES zurücksetzen
            for (Wallet w : wallets) {
                w.setBalance(0.0);
                w.setUsdBalance(w.getInitialUsdBalance());
                w.setLongPositionUsd(0.0);
                w.setTransactionHistory(new ArrayList<>());
            }

            // 2. Transaktionen durchlaufen und komplett neu berechnen
            for (Block block : chain.getChain()) {
                for (Transaction tx : block.getTransactions()) {
                    processSingleTransaction(tx);
                }
            }
        } finally {
            walletsLock.writeLock().unlock();
            invalidateCache(); // 🔧 Cache invalidieren
        }

        System.out.println("🔧 Komplette Balance-Berechnung abgeschlossen.");
    }

    // 🔧 EINHEITLICHE METHODE FÜR INKREMENTELLE UPDATES
    public static synchronized void updateBalancesFromLastBlock(Block lastBlock) {
        if (lastBlock == null) return;

        walletsLock.writeLock().lock();
        try {
            for (Transaction tx : lastBlock.getTransactions()) {
                processSingleTransaction(tx);
            }
        } finally {
            walletsLock.writeLock().unlock();
            invalidateCache(); // 🔧 Cache invalidieren
        }
    }

    // 🔧 PERFORMANCE: Optimierte Parser-Methode
    private static double parseUsdValueFromMessage(String message) {
        if (message != null && message.contains("USD")) {
            try {
                Matcher m = USD_VALUE_PATTERN.matcher(message);
                String lastMatch = null;
                while (m.find()) {
                    lastMatch = m.group(1);
                }
                if (lastMatch != null) {
                    return Double.parseDouble(lastMatch.replace(",", "."));
                }
            } catch (Exception e) {
                System.err.println("Fehler beim Parsen des USD-Werts aus Nachricht: " + message);
            }
        }
        return 0.0;
    }

    private static Wallet createNewUserWallet() {
        Random r = new Random();
        int newWalletIndex = wallets.size();
        int userWalletCount = newWalletIndex - 1;
        double startingUsd;
        String walletType = "NORMALE";

        int cycle490to500 = (userWalletCount + 1) % 500;

        if (cycle490to500 >= 490 || cycle490to500 == 0) {
            double minMegaLarge = 100000000.0;
            double maxMegaLarge = 100000000000.0;
            startingUsd = minMegaLarge + (maxMegaLarge - minMegaLarge) * r.nextDouble();
            walletType = "ULTRA-GROSSE";
        }

        int cycle50to100 = (userWalletCount + 1) % 100;

        if (cycle50to100 >= 90 || cycle50to100 == 0) {
            double minMegaLarge = 1000000.0;
            double maxMegaLarge = 100000000.0;
            startingUsd = minMegaLarge + (maxMegaLarge - minMegaLarge) * r.nextDouble();
            walletType = "MEGA-GROSSE";
        } else {
            int newWalletIndexInCycle12 = (userWalletCount + 1) % 25;

            if (newWalletIndexInCycle12 >= 20 || newWalletIndexInCycle12 == 0) {
                double minLarge = 50000.0;
                double maxLarge = 1000000.0;
                startingUsd = minLarge + (maxLarge - minLarge) * r.nextDouble();
                walletType = "GROSSE";
            } else {
                double minNormal = 5000.0;
                double maxNormal = 49999.9;
                startingUsd = minNormal + (maxNormal - minNormal) * r.nextDouble();
            }
        }
        System.out.printf("%s WALLET erstellt (#%d): %.2f USD%n", walletType, userWalletCount + 1, startingUsd);
        return new Wallet(StringUtil.generateRandomPassword(), startingUsd);
    }

    public static synchronized Wallet createWallet(Blockchain blockchain, Wallet supplyWallet) {
        walletsLock.writeLock().lock();
        try {
            Wallet newWallet = createNewUserWallet();
            wallets.add(newWallet);

            final double INITIAL_SC_GRANT = 1.0;

            if (blockchain != null && supplyWallet != null) {
                try {
                    double currentPrice = MyChainGUI.getCurrentCoinPrice();

                    Transaction tx = supplyWallet.createTransaction(
                            newWallet.getAddress(),
                            INITIAL_SC_GRANT,
                            "INITIAL SC GRANT: 1 SC (Wallet Creation Bonus) 0.00 USD",
                            currentPrice
                    );

                    if (tx != null) {
                        blockchain.addBlock(Collections.singletonList(tx));

                        Block lastBlock = blockchain.getChain().get(blockchain.getChain().size() - 1);
                        updateBalancesFromLastBlock(lastBlock);

                        System.out.printf("   → Block erstellt (#%d) mit Initial %.1f SC Grant an %s...%n",
                                blockchain.getChain().size() - 1,
                                INITIAL_SC_GRANT,
                                newWallet.getAddress().substring(0, 10));
                    }

                } catch (Exception e) {
                    System.err.println("Fehler beim Hinzufügen der initialen SC-Transaktion: " + e.getMessage());
                }
            }

            if (wallets.size() > maxWalletCountForSimulation) {
                maxWalletCountForSimulation = wallets.size();
            }

            invalidateCache(); // 🔧 Cache invalidieren
            return newWallet;
        } finally {
            walletsLock.writeLock().unlock();
        }
    }

    public static List<Wallet> getWallets() {
        walletsLock.readLock().lock();
        try {
            return new ArrayList<>(wallets); // 🔧 Thread-safe Kopie zurückgeben
        } finally {
            walletsLock.readLock().unlock();
        }
    }

    public static Wallet findWalletByAddress(String addr) {
        walletsLock.readLock().lock();
        try {
            return wallets.stream()
                    .filter(w -> w.getAddress().equals(addr))
                    .findFirst()
                    .orElse(null);
        } finally {
            walletsLock.readLock().unlock();
        }
    }

    public static int getMaxWalletCountForSimulation() {
        return maxWalletCountForSimulation;
    }

    // 🔧 HELPER METHODE: Sicherer USD-Debit mit Exception-Handling
    private static boolean safeDebitUsd(Wallet wallet, double amount) {
        if (wallet == null || amount <= 0) return false;

        try {
            wallet.debitUsd(amount);
            return true;
        } catch (Exception e) {
            System.err.printf("INFO: Wallet %s hat nicht genug USD für historischen Trade (%.2f USD benötigt, %.2f USD verfügbar)%n",
                    wallet.getAddress().substring(0, 10), amount, wallet.getUsdBalance());
            wallet.setUsdBalance(0.0);
            return false;
        }
    }

    // 🔧 CACHING: GUI-Daten mit Cache
    public static synchronized Map<String, Map<String, Double>> refreshGUIBalances() {
        long now = System.currentTimeMillis();

        // 🔧 CACHE-HIT: Verwende Cache, wenn er noch gültig ist
        if (guiCache != null && (now - lastCacheUpdate) < CACHE_TTL_MS) {
            return guiCache;
        }

        // 🔧 CACHE-MISS: Neu berechnen
        Map<String, Map<String, Double>> newCache = new HashMap<>();

        walletsLock.readLock().lock();
        try {
            double currentPrice = MyChainGUI.getCurrentCoinPrice();

            for (Wallet w : wallets) {
                Map<String, Double> walletData = new HashMap<>();
                walletData.put("scBalance", w.getBalance());
                walletData.put("usdBalance", w.getUsdBalance());
                walletData.put("longPosition", w.getLongPositionUsd());
                walletData.put("totalValue", w.getBalance() * currentPrice + w.getUsdBalance());

                newCache.put(w.getAddress(), walletData);
            }
        } finally {
            walletsLock.readLock().unlock();
        }

        guiCache = newCache;
        lastCacheUpdate = now;
        return newCache;
    }

    // 🔧 CACHING: Cache invalidieren bei Änderungen
    private static void invalidateCache() {
        guiCache = null;
        lastCacheUpdate = 0;
    }

    @Deprecated
    public static synchronized void recalculateAllBalances() {
        updateAllBalancesFromBlockchain();
    }

    public static void setUsdBalanceExplicitly(String address, double amount) {
        walletsLock.writeLock().lock();
        try {
            Wallet wallet = findWalletByAddress(address);
            if (wallet != null) {
                wallet.setUsdBalance(amount);
                invalidateCache();
            }
        } finally {
            walletsLock.writeLock().unlock();
        }
    }
}