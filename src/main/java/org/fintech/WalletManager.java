package org.fintech;

import java.io.*;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.HashMap;
import java.util.Map;

public class WalletManager {

    // Dedizierte Wallet für den Coin-Supply
    public static final Wallet SUPPLY_WALLET = new Wallet("admin");
    private static int nextWalletId = 1;


    private static int maxWalletCountForSimulation = 0;

    private static List<Wallet> wallets = new CopyOnWriteArrayList<>();

    public static final WalletManager INSTANCE = new WalletManager();
    private WalletManager() {}

    public static int getAndIncrementNextId() {
        return nextWalletId++;
    }

    public static void loadWallets() {
        // 🛑 ÄNDERUNG: Immer neu initialisieren, nie laden
        maxWalletCountForSimulation = 0;
        wallets.clear();
        wallets.add(SUPPLY_WALLET);

        // Erste Benutzer-Wallet mit spezieller Initialisierung
        Wallet firstUser = createNewUserWallet();
        wallets.add(firstUser);

        System.out.println("Wallets neu initialisiert (kein Laden).");
        updateAllBalancesFromBlockchain();
    }

    /**
     * Speichert nur kritische Wallets (Supply, Exchange) auf die Festplatte,
     * um die Dateigröße klein zu halten. User Wallets bleiben im RAM.
     */
    public static synchronized void saveWallets() {
        // 🛑 ÄNDERUNG: Nichts speichern (leer lassen)
        // Optional: Nur loggen für Debugging
        System.out.println("Wallets würden gespeichert (" + wallets.size() + " Wallets)");
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
            double maxMegaLarge = 10000000000.0;
            startingUsd = minMegaLarge + (maxMegaLarge - minMegaLarge) * r.nextDouble();
            walletType = "ULTRA-GROSSE";
        }

        int cycle50to100 = (userWalletCount + 1) % 100;

        if (cycle50to100 >= 90 || cycle50to100 == 0) {
            double minMegaLarge = 1000000.0;
            double maxMegaLarge = 100000000.0;
            startingUsd = minMegaLarge + (maxMegaLarge - minMegaLarge) * r.nextDouble();
            walletType = "MEGA-GROSSE";
        }
        else {
            int newWalletIndexInCycle12 = (userWalletCount + 1) % 25;

            if (newWalletIndexInCycle12 >= 20 || newWalletIndexInCycle12 == 0) {
                double minLarge = 50000.0;
                double maxLarge = 10000000.0;
                startingUsd = minLarge + (maxLarge - minLarge) * r.nextDouble();
                walletType = "GROSSE";
            }
            else {
                double minNormal = 5000.0;
                double maxNormal = 49999.9;
                startingUsd = minNormal + (maxNormal - minNormal) * r.nextDouble();
            }
        }
        System.out.printf("%s WALLET erstellt (#%d): %.2f USD%n", walletType, userWalletCount + 1, startingUsd);
        return new Wallet(StringUtil.generateRandomPassword(), startingUsd);
    }

    public static synchronized Wallet createWallet(Blockchain blockchain, Wallet supplyWallet) {
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

                    // 🔧 GEÄNDERT: Verwende inkrementelle Update-Methode
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

        // 🛑 ÄNDERUNG: saveWallets() entfernt
        // saveWallets(); // ENTFERNT

        return newWallet;
    }

    public static List<Wallet> getWallets() {
        return wallets;
    }

    public static Wallet findWalletByAddress(String addr) {
        return wallets.stream()
                .filter(w -> w.getAddress().equals(addr))
                .findFirst()
                .orElse(null);
    }

    public static int getMaxWalletCountForSimulation() {
        return maxWalletCountForSimulation;
    }

    /**
     * Ermittelt den USD-Wert des Trades aus der Transaktionsnachricht.
     */
    private static double parseUsdValueFromMessage(String message) {
        if (message != null && message.contains("USD")) {
            try {
                Pattern p = Pattern.compile("([\\d.,]+)\\sUSD");
                Matcher m = p.matcher(message);

                String lastMatch = null;
                while (m.find()) {
                    lastMatch = m.group(1);
                }

                if (lastMatch != null) {
                    String cleanValue = lastMatch.replace(",", ".");
                    return Double.parseDouble(cleanValue);
                }
            } catch (Exception e) {
                System.err.println("Fehler beim Parsen des USD-Werts aus Nachricht: " + message);
            }
        }
        return 0.0;
    }

    // 🔧 HELPER METHODE: Sicherer USD-Debit mit Exception-Handling
    private static boolean safeDebitUsd(Wallet wallet, double amount) {
        if (wallet == null || amount <= 0) return false;

        try {
            wallet.debitUsd(amount);
            return true;
        } catch (Exception e) {
            // 🛑 KEIN FEHLER WERFEN - nur loggen
            System.err.printf("INFO: Wallet %s hat nicht genug USD für historischen Trade (%.2f USD benötigt, %.2f USD verfügbar)%n",
                    wallet.getAddress().substring(0, 10), amount, wallet.getUsdBalance());

            // 🔧 NOTFALL: Setze USD auf 0, wenn nicht genug da ist
            // (Der Trade hat bereits stattgefunden, wir können ihn nicht rückgängig machen)
            wallet.setUsdBalance(0.0);
            return false;
        }
    }

    // 🔧 🔧 🔧 NEUE METHODEN 🔧 🔧 🔧

    /**
     * 🔧 NEUE METHODE: Wird NUR beim Start oder nach Reset aufgerufen.
     * Setzt ALLES zurück und berechnet komplett neu aus der Blockchain.
     */
    public static synchronized void updateAllBalancesFromBlockchain() {
        Blockchain chain = BlockchainPersistence.loadBlockchain("MyChain", 1);

        System.out.println("🔧 Komplette Balance-Berechnung aus Blockchain gestartet...");

        // 1. ALLES zurücksetzen
        for (Wallet w : wallets) {
            w.setBalance(0.0);
            w.setUsdBalance(w.getInitialUsdBalance()); // 🔧 USD auf Initialwert zurücksetzen
            w.setLongPositionUsd(0.0);
            w.setShortPositionUsd(0.0);
            w.setTransactionHistory(new ArrayList<>());
        }

        // 2. Transaktionen durchlaufen und komplett neu berechnen
        for (Block block : chain.getChain()) {
            for (Transaction tx : block.getTransactions()) {
                String sender = tx.getSender();
                String recipient = tx.getRecipient();
                double amount = tx.getAmount();
                double usdValue = parseUsdValueFromMessage(tx.getMessage());
                String message = tx.getMessage().toLowerCase();

                Wallet senderWallet = findWalletByAddress(sender);
                Wallet recipientWallet = findWalletByAddress(recipient);

                // Transaktion zur Historie hinzufügen
                if (senderWallet != null) senderWallet.getTransactionHistory().add(tx);
                if (recipientWallet != null) recipientWallet.getTransactionHistory().add(tx);

                // SC-Balance aktualisieren
                boolean isExchangeSell = MyChainGUI.EXCHANGE_ADDRESS.equals(recipient);
                boolean isCoinbase = "system".equals(sender) || sender == null || sender.isEmpty();

                if (!isCoinbase && senderWallet != null) {
                    senderWallet.debit(amount);
                }
                if (!isExchangeSell && recipientWallet != null) {
                    recipientWallet.credit(amount);
                }

                // 🔧 WICHTIG: USD und Positionen NUR aus historischen Transaktionen berechnen
                // MIT SICHERER DEBIT-METHODE
                if (sender.equals(SUPPLY_WALLET.getAddress()) && message.contains("kauf (long)") && usdValue > 0) {
                    if (recipientWallet != null) {
                        safeDebitUsd(recipientWallet, usdValue); // 🔧 SICHERE METHODE
                        recipientWallet.setLongPositionUsd(recipientWallet.getLongPositionUsd() + usdValue);
                    }
                } else if (sender.equals(SUPPLY_WALLET.getAddress()) && message.contains("kauf (short-cover)") && usdValue > 0) {
                    if (recipientWallet != null) {
                        safeDebitUsd(recipientWallet, usdValue); // 🔧 SICHERE METHODE
                        recipientWallet.setShortPositionUsd(recipientWallet.getShortPositionUsd() - usdValue);
                        if (recipientWallet.getShortPositionUsd() < 0) recipientWallet.setShortPositionUsd(0.0);
                    }
                } else if (recipient.equals(MyChainGUI.EXCHANGE_ADDRESS) && message.contains("verkauf (long)") && usdValue > 0) {
                    if (senderWallet != null) {
                        senderWallet.creditUsd(usdValue); // 🔧 Credit wirft keine Exception
                        senderWallet.setLongPositionUsd(senderWallet.getLongPositionUsd() - usdValue);
                        if (senderWallet.getLongPositionUsd() < 0) senderWallet.setLongPositionUsd(0.0);
                    }
                } else if (recipient.equals(MyChainGUI.EXCHANGE_ADDRESS) && message.contains("verkauf (short)") && usdValue > 0) {
                    if (senderWallet != null) {
                        senderWallet.creditUsd(usdValue); // 🔧 Credit wirft keine Exception
                        senderWallet.setShortPositionUsd(senderWallet.getShortPositionUsd() + usdValue);
                    }
                }
            }
        }

        System.out.println("🔧 Komplette Balance-Berechnung abgeschlossen.");
        saveWallets();
    }

    /**
     * 🔧 NEUE METHODE: Inkrementelle Updates nach jedem Trade.
     * Wird NACH jedem Trade aufgerufen, um nur den letzten Block zu verarbeiten.
     */
    public static synchronized void updateBalancesFromLastBlock(Block lastBlock) {
        if (lastBlock == null) return;

        // 🔧 NUR den letzten Block verarbeiten
        for (Transaction tx : lastBlock.getTransactions()) {
            String sender = tx.getSender();
            String recipient = tx.getRecipient();
            double amount = tx.getAmount();
            double usdValue = parseUsdValueFromMessage(tx.getMessage());
            String message = tx.getMessage().toLowerCase();

            Wallet senderWallet = findWalletByAddress(sender);
            Wallet recipientWallet = findWalletByAddress(recipient);

            // Transaktion zur Historie hinzufügen
            if (senderWallet != null) senderWallet.getTransactionHistory().add(tx);
            if (recipientWallet != null) recipientWallet.getTransactionHistory().add(tx);

            // SC-Balance aktualisieren
            boolean isExchangeSell = MyChainGUI.EXCHANGE_ADDRESS.equals(recipient);
            boolean isCoinbase = "system".equals(sender) || sender == null || sender.isEmpty();

            if (!isCoinbase && senderWallet != null) {
                senderWallet.debit(amount);
            }
            if (!isExchangeSell && recipientWallet != null) {
                recipientWallet.credit(amount);
            }

            // 🔧 USD und Positionen SOFORT aktualisieren
            // 🔧 ACHTUNG: Hier sollte eigentlich immer genug USD da sein, da der Trade bereits geprüft wurde
            if (sender.equals(SUPPLY_WALLET.getAddress()) && message.contains("kauf (long)") && usdValue > 0) {
                if (recipientWallet != null) {
                    try {
                        recipientWallet.debitUsd(usdValue);
                        recipientWallet.setLongPositionUsd(recipientWallet.getLongPositionUsd() + usdValue);
                    } catch (Exception e) {
                        // 🔧 KRITISCHER FEHLER: Trade wurde erlaubt, aber nicht genug USD
                        System.err.printf("❌ KRITISCH: Wallet %s hatte bei Trade-Execution nicht genug USD! Benötigt: %.2f, Verfügbar: %.2f%n",
                                recipientWallet.getAddress().substring(0, 10), usdValue, recipientWallet.getUsdBalance());
                        // Notfall: Setze auf 0
                        recipientWallet.setUsdBalance(0.0);
                        recipientWallet.setLongPositionUsd(recipientWallet.getLongPositionUsd() + usdValue);
                    }
                }
            } else if (sender.equals(SUPPLY_WALLET.getAddress()) && message.contains("kauf (short-cover)") && usdValue > 0) {
                if (recipientWallet != null) {
                    try {
                        recipientWallet.debitUsd(usdValue);
                        recipientWallet.setShortPositionUsd(recipientWallet.getShortPositionUsd() - usdValue);
                        if (recipientWallet.getShortPositionUsd() < 0) recipientWallet.setShortPositionUsd(0.0);
                    } catch (Exception e) {
                        System.err.printf("❌ KRITISCH: Wallet %s hatte bei Trade-Execution nicht genug USD! Benötigt: %.2f, Verfügbar: %.2f%n",
                                recipientWallet.getAddress().substring(0, 10), usdValue, recipientWallet.getUsdBalance());
                        recipientWallet.setUsdBalance(0.0);
                        recipientWallet.setShortPositionUsd(recipientWallet.getShortPositionUsd() - usdValue);
                        if (recipientWallet.getShortPositionUsd() < 0) recipientWallet.setShortPositionUsd(0.0);
                    }
                }
            } else if (recipient.equals(MyChainGUI.EXCHANGE_ADDRESS) && message.contains("verkauf (long)") && usdValue > 0) {
                if (senderWallet != null) {
                    // 🔧 CreditUsd wirft keine Exception
                    senderWallet.creditUsd(usdValue);
                    senderWallet.setLongPositionUsd(senderWallet.getLongPositionUsd() - usdValue);
                    if (senderWallet.getLongPositionUsd() < 0) senderWallet.setLongPositionUsd(0.0);
                }
            } else if (recipient.equals(MyChainGUI.EXCHANGE_ADDRESS) && message.contains("verkauf (short)") && usdValue > 0) {
                if (senderWallet != null) {
                    // 🔧 CreditUsd wirft keine Exception
                    senderWallet.creditUsd(usdValue);
                    senderWallet.setShortPositionUsd(senderWallet.getShortPositionUsd() + usdValue);
                }
            }
        }

        saveWallets();
    }

    /**
     * 🔧 NEUE METHODE: Nur für GUI - Berechnet aktuelle Salden für die Anzeige.
     * Ändert NICHT die Wallet-Objekte, sondern gibt nur Maps zurück.
     */
    public static synchronized Map<String, Map<String, Double>> refreshGUIBalances() {
        Map<String, Map<String, Double>> guiData = new HashMap<>();

        // Erstelle temporäre Maps für SC, USD und Positionen
        for (Wallet w : wallets) {
            Map<String, Double> walletData = new HashMap<>();
            walletData.put("scBalance", w.getBalance());
            walletData.put("usdBalance", w.getUsdBalance());
            walletData.put("longPosition", w.getLongPositionUsd());
            walletData.put("shortPosition", w.getShortPositionUsd());
            walletData.put("totalValue", w.getBalance() * MyChainGUI.getCurrentCoinPrice() + w.getUsdBalance());

            guiData.put(w.getAddress(), walletData);
        }

        return guiData;
    }

    /**
     * 🔧 DEPRECATED: Alte Methode für Kompatibilität.
     * Wird durch die neuen Methoden ersetzt.
     */
    @Deprecated
    public static synchronized void recalculateAllBalances() {
        // 🔧 Aufruf auf die neue Methode umleiten
        updateAllBalancesFromBlockchain();
    }
}