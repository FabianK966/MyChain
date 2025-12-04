package org.fintech;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import java.io.*;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Collectors; // Import für stream.Collectors

public class WalletManager {
    private static final String WALLETS_FILE = "wallets.json";
    private static final Gson gson = new GsonBuilder().create();

    // Dedizierte Wallet für den Coin-Supply
    public static final Wallet SUPPLY_WALLET = new Wallet("admin");
    private static int nextWalletId = 1;

    // 🛑 NEU: Historischer Zähler für die Simulationsgeschwindigkeit
    private static int maxWalletCountForSimulation = 0;

    private static List<Wallet> wallets = new CopyOnWriteArrayList<>();

    public static final WalletManager INSTANCE = new WalletManager();
    private WalletManager() {}

    public static int getAndIncrementNextId() {
        return nextWalletId++;
    }

    public static void loadWallets() {
        // 🛑 NEUSTART-LOGIK: Setzt den historischen Zähler beim Programmstart auf 0.
        maxWalletCountForSimulation = 0;

        wallets.clear();
        wallets.add(SUPPLY_WALLET);

        File file = new File(WALLETS_FILE);

        if (file.exists() && file.length() > 0) {
            try (Reader reader = new FileReader(file)) {
                Type listType = new TypeToken<ArrayList<Wallet>>(){}.getType();
                List<Wallet> loadedWallets = gson.fromJson(reader, listType);

                if (loadedWallets == null) loadedWallets = new ArrayList<>();

                // Füge geladene Wallets hinzu (nur die kritischen Wallets sollten in der Datei sein)
                loadedWallets.stream()
                        .filter(w -> !w.getAddress().equals(SUPPLY_WALLET.getAddress()))
                        .forEach(wallets::add);

                // Bestimme die nächste freie ID basierend auf den geladenen Wallets
                int maxId = loadedWallets.stream()
                        // Annahme: Wallet.getUniqueId() existiert und liefert int
                        .mapToInt(Wallet::getUniqueId)
                        .max()
                        .orElse(0);

                nextWalletId = maxId + 1;
                System.out.println("System meldet: Nächste Wallet ID startet bei: " + nextWalletId);

            } catch (Exception e) {
                System.err.println("Fehler beim Laden der Wallets: " + e.getMessage());
                // Wenn Fehler, nur die Supply Wallet behalten
            }
        }
        else {
            // Initialisierung für leere Datei/ersten Start
            wallets = new CopyOnWriteArrayList<>();
            SUPPLY_WALLET.setUsdBalance(0.0);
            wallets.add(SUPPLY_WALLET);

            // Erste Benutzer-Wallet mit spezieller Initialisierung
            Wallet firstUser = createNewUserWallet();
            wallets.add(firstUser);
        }

        // 🛑 Aktualisiert den Zähler mit der aktuellen geladenen Größe
        if (wallets.size() > maxWalletCountForSimulation) {
            maxWalletCountForSimulation = wallets.size();
        }

        System.out.println(wallets.size() + " Wallet(s) geladen/initialisiert.");
        recalculateAllBalances();
    }

    /**
     * Speichert nur kritische Wallets (Supply, Exchange) auf die Festplatte,
     * um die Dateigröße klein zu halten. User Wallets bleiben im RAM.
     */
    public static synchronized void saveWallets() {
        List<Wallet> allWallets = getWallets();
        List<Wallet> walletsToSave = new ArrayList<>();

        // 1. Supply Wallet speichern
        if (!allWallets.isEmpty()) {
            walletsToSave.add(SUPPLY_WALLET);
        }

        // 2. Exchange Wallet speichern (Voraussetzung: MyChainGUI.EXCHANGE_ADDRESS muss existieren)
        Wallet exchange = allWallets.stream()
                .filter(w -> w.getAddress().equals(MyChainGUI.EXCHANGE_ADDRESS))
                .findFirst().orElse(null);
        if (exchange != null && !walletsToSave.contains(exchange)) {
            walletsToSave.add(exchange);
        }

        // 🛑 Nur die kritischen Wallets speichern
        try (Writer writer = new FileWriter(WALLETS_FILE)) {
            gson.toJson(walletsToSave, writer);
        } catch (IOException e) {
            System.err.println("Fehler beim Speichern der Wallets: " + e.getMessage());
        }
    }


    private static Wallet createNewUserWallet() {
        Random r = new Random();
        int newWalletIndex = wallets.size();
        int userWalletCount = newWalletIndex - 1;
        double startingUsd;
        String walletType = "NORMALE";

        // -----------------------------------------------------------
        // 1. OBERSTE PRIORITÄT: EXTREM GROSSE WALLET
        // -----------------------------------------------------------
        int cycle50to100 = (userWalletCount + 1) % 100;

        if (cycle50to100 >= 95 || cycle50to100 == 0) {
            double minMegaLarge = 10000000.0;
            double maxMegaLarge = 100000000.0;
            startingUsd = minMegaLarge + (maxMegaLarge - minMegaLarge) * r.nextDouble();
            walletType = "MEGA-GROSSE";
        }
        // -----------------------------------------------------------
        // 2. MITTLERE PRIORITÄT: GROSSE WALLET
        // -----------------------------------------------------------
        else {
            int newWalletIndexInCycle12 = (userWalletCount + 1) % 12;

            if (newWalletIndexInCycle12 >= 8 || newWalletIndexInCycle12 == 0) {

                double minLarge = 500000.0;
                double maxLarge = 10000000.0;
                startingUsd = minLarge + (maxLarge - minLarge) * r.nextDouble();
                walletType = "GROSSE";
            }
            // -----------------------------------------------------------
            // 3. NIEDRIGSTE PRIORITÄT: NORMALE WALLET (Alle anderen)
            // -----------------------------------------------------------
            else {
                double minNormal = 5000.0;
                double maxNormal = 499999.9;
                startingUsd = minNormal + (maxNormal - minNormal) * r.nextDouble();
            }
        }
        System.out.printf("%s WALLET erstellt (#%d): %.2f USD%n", walletType, userWalletCount + 1, startingUsd);
        // Annahme: Wallet-Konstruktor(Passwort, USD) erzeugt Keys und Adresse
        return new Wallet(StringUtil.generateRandomPassword(), startingUsd);
    }

    public static synchronized Wallet createWallet() {
        Wallet newWallet = createNewUserWallet();
        wallets.add(newWallet);
        // 🛑 WICHTIG: Aktualisiert den Zähler im RAM für die Geschwindigkeitsskalierung
        if (wallets.size() > maxWalletCountForSimulation) {
            maxWalletCountForSimulation = wallets.size();
        }
        saveWallets(); // Speichert nur kritische Wallets
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

    // --- NEUE HILFSFUNKTION FÜR DEN SIMULATOR ---

    public static int getMaxWalletCountForSimulation() {
        return maxWalletCountForSimulation;
    }

    public static synchronized void recalculateAllBalances() {
        for (Wallet w : wallets) w.setBalance(0.0);

        Blockchain chain = BlockchainPersistence.loadBlockchain("MyChain", 1);

        for (Block block : chain.getChain()) {
            for (Transaction tx : block.getTransactions()) {
                String sender = tx.getSender();
                String recipient = tx.getRecipient();
                double amount = tx.getAmount();

                boolean isCoinbase = "system".equals(sender) || sender == null || sender.isEmpty();
                boolean isExchangeSell = MyChainGUI.EXCHANGE_ADDRESS.equals(recipient);

                if (!isCoinbase) {
                    Wallet from = findWalletByAddress(sender);
                    if (from != null) from.debit(amount);
                }

                if (!isExchangeSell) {
                    Wallet to = findWalletByAddress(recipient);
                    if (to != null) to.credit(amount);
                }
            }
        }
        saveWallets();
    }
}