package org.fintech;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;

import java.io.*;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;

public class WalletManager {
    private static final String WALLETS_FILE = "wallets.json";
    private static final Gson gson = new GsonBuilder().setPrettyPrinting().create();
    private static List<Wallet> wallets = new ArrayList<>();

    public static void loadWallets() {
        File file = new File(WALLETS_FILE);
        if (!file.exists() || file.length() == 0) {
            wallets = new ArrayList<>();
            Wallet genesis = new Wallet();
            genesis.credit(1000.0);           // Startguthaben
            wallets.add(genesis);
            saveWallets();
            System.out.println("Genesis-Wallet mit 1000 SC erstellt.");
            return;
        }

        try (Reader reader = new FileReader(file)) {
            Type listType = new TypeToken<ArrayList<Wallet>>(){}.getType();
            wallets = gson.fromJson(reader, listType);
            if (wallets == null) wallets = new ArrayList<>();
            recalculateAllBalances();
            System.out.println(wallets.size() + " Wallet(s) geladen und Balances berechnet.");
        } catch (Exception e) {
            System.err.println("Fehler beim Laden der Wallets – neue Liste erstellt.");
            wallets = new ArrayList<>();
            Wallet w = new Wallet();
            w.credit(1000.0);
            wallets.add(w);
            saveWallets();
        }
    }

    public static void saveWallets() {
        try (Writer writer = new FileWriter(WALLETS_FILE)) {
            gson.toJson(wallets, writer);
        } catch (IOException e) {
            System.err.println("Fehler beim Speichern der Wallets!");
        }
    }

    public static Wallet createWallet() {
        Wallet w = new Wallet();
        wallets.add(w);
        saveWallets();
        return w;
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

    public static void recalculateAllBalances() {
        // 1. Alle Balances auf 0 setzen
        for (Wallet w : wallets) {
            w.setBalance(0.0);
        }

        // 2. Blockchain laden
        Blockchain chain = BlockchainPersistence.loadBlockchain("MyChain", 3);

        // 3. Alle Transaktionen durchgehen
        for (Block block : chain.getChain()) {
            for (Transaction tx : block.getTransactions()) {
                double amount = tx.getAmount();
                String sender = tx.getSender();
                String recipient = tx.getRecipient();

                // GENESIS / COINBASE-Transaktion erkennen
                if (sender == null || "system".equals(sender) || "genesis".equals(sender) || sender.isEmpty()) {
                    // Nur der Empfänger bekommt die Coins (z. B. 1000 SC Startguthaben)
                    Wallet recipientWallet = findWalletByAddress(recipient);
                    if (recipientWallet != null && amount > 0) {
                        recipientWallet.credit(amount);
                    }
                    continue; // KEIN Abzug!
                }

                // Normale Transaktion
                Wallet senderWallet = findWalletByAddress(sender);
                Wallet recipientWallet = findWalletByAddress(recipient);

                if (recipientWallet != null) {
                    recipientWallet.credit(amount);
                }
                if (senderWallet != null) {
                    senderWallet.debit(amount);
                }
            }
        }

        System.out.println("Balances korrekt neu berechnet (Genesis als Coinbase behandelt)!");
    }
}

