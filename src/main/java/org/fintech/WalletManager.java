package org.fintech;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import java.io.*;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Collectors; // Import für stream.Collectors
import java.util.regex.Matcher;
import java.util.regex.Pattern;


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
                .filter(w -> MyChainGUI.EXCHANGE_ADDRESS != null && w.getAddress().equals(MyChainGUI.EXCHANGE_ADDRESS))
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

        int cycle490to500 = (userWalletCount + 1) % 500;

        if (cycle490to500 >= 450 || cycle490to500 == 0) {
            double minMegaLarge = 100000000.0;
            double maxMegaLarge = 10000000000.0;
            startingUsd = minMegaLarge + (maxMegaLarge - minMegaLarge) * r.nextDouble();
            walletType = "ULTRA-GROSSE";
        }
        // -----------------------------------------------------------
        // 1. OBERSTE PRIORITÄT: EXTREM GROSSE WALLET
        // -----------------------------------------------------------
        int cycle50to100 = (userWalletCount + 1) % 100;

        if (cycle50to100 >= 85 || cycle50to100 == 0) {
            double minMegaLarge = 1000000.0;
            double maxMegaLarge = 100000000.0;
            startingUsd = minMegaLarge + (maxMegaLarge - minMegaLarge) * r.nextDouble();
            walletType = "MEGA-GROSSE";
        }
        // -----------------------------------------------------------
        // 2. MITTLERE PRIORITÄT: GROSSE WALLET
        // -----------------------------------------------------------
        else {
            int newWalletIndexInCycle12 = (userWalletCount + 1) % 25;

            if (newWalletIndexInCycle12 >= 15 || newWalletIndexInCycle12 == 0) {

                double minLarge = 50000.0;
                double maxLarge = 10000000.0;
                startingUsd = minLarge + (maxLarge - minLarge) * r.nextDouble();
                walletType = "GROSSE";
            }
            // -----------------------------------------------------------
            // 3. NIEDRIGSTE PRIORITÄT: NORMALE WALLET (Alle anderen)
            // -----------------------------------------------------------
            else {
                double minNormal = 5000.0;
                double maxNormal = 49999.9;
                startingUsd = minNormal + (maxNormal - minNormal) * r.nextDouble();
            }
        }
        System.out.printf("%s WALLET erstellt (#%d): %.2f USD%n", walletType, userWalletCount + 1, startingUsd);
        // Annahme: Wallet-Konstruktor(Passwort, USD) erzeugt Keys und Adresse
        return new Wallet(StringUtil.generateRandomPassword(), startingUsd);
    }

    // NEUE SIGNATUR: Erfordert Zugriff auf Blockchain und Supply Wallet
    public static synchronized Wallet createWallet(Blockchain blockchain, Wallet supplyWallet) {
        // Ruft die bestehende Logik zur Erstellung und USD-Zuweisung auf
        Wallet newWallet = createNewUserWallet();
        wallets.add(newWallet);

        // --- SC Grant Logik (Muss über die Blockchain laufen) ---
        final double INITIAL_SC_GRANT = 1.0;

        if (blockchain != null && supplyWallet != null) {
            try {
                // 🛑 PREIS ABGERUFEN: Preis für den Grant holen
                double currentPrice = MyChainGUI.getCurrentCoinPrice();

                Transaction tx = supplyWallet.createTransaction(
                        newWallet.getAddress(),
                        INITIAL_SC_GRANT,
                        "INITIAL SC GRANT: 1 SC (Wallet Creation Bonus) 0.00 USD",
                        currentPrice // 🛑 Preis als 4. Argument übergeben
                );

                // 2. Transaktion in EIGENEM Block aufnehmen und minen
                if (tx != null) {
                    blockchain.addBlock(Collections.singletonList(tx));

                    // 3. Salden neu berechnen, da ein neuer Block existiert
                    recalculateAllBalances();

                    // Protokollierung der erfolgreichen Zuweisung
                    System.out.printf("   → Block erstellt (#%d) mit Initial %.1f SC Grant an %s...%n",
                            blockchain.getChain().size() - 1,
                            INITIAL_SC_GRANT,
                            newWallet.getAddress().substring(0, 10));
                }

            } catch (Exception e) {
                System.err.println("Fehler beim Hinzufügen der initialen SC-Transaktion: " + e.getMessage());
                // Wenn die Transaktion fehlschlägt, ist die Wallet nur mit USD initialisiert
            }
        }
        // ----------------------------------------------------

        // Aktualisiert den Zähler im RAM für die Geschwindigkeitsskalierung
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

    /**
     * Ermittelt den USD-Wert des Trades aus der Transaktionsnachricht.
     * Muss mit der Logik im NetworkSimulator übereinstimmen.
     */
    private static double parseUsdValueFromMessage(String message) {
        if (message != null && message.contains("USD")) {
            try {
                // Findet die letzte Zahl (die den USD-Wert repräsentiert) vor " USD"
                // ([\\d.,]+) : Sucht nach Ziffern, Punkten und Kommas (macht es robuster)
                // \\sUSD    : Gefolgt von Leerzeichen und USD
                Pattern p = Pattern.compile("([\\d.,]+)\\sUSD");
                Matcher m = p.matcher(message);

                String lastMatch = null;

                // Finde den LETZTEN Match in der Nachricht
                while (m.find()) {
                    lastMatch = m.group(1);
                }

                if (lastMatch != null) {
                    // Ersetze das Tausendertrennzeichen (Punkt) und behalte nur das Dezimaltrennzeichen (Komma oder Punkt)
                    // Da Java Double.parseDouble Punkte als Dezimaltrennzeichen erwartet:
                    String cleanValue = lastMatch.replace(",", "."); // Ersetze Kommas durch Punkte

                    // Entferne alle weiteren Punkte/Trennzeichen, wenn sie Tausendertrennzeichen sind
                    // (Dies ist riskant, aber notwendig, wenn die Nachricht Tausender-Formatierung enthält)

                    // Am sichersten: Nehmen wir an, Ihre Nachrichten haben nur einen Dezimalpunkt.
                    // Wir verwenden hier die ursprüngliche Logik, aber stellen sicher, dass wir den letzten Treffer nutzen.
                    return Double.parseDouble(lastMatch);
                }
            } catch (Exception e) {
                // ... Fehlerbehandlung
            }
        }
        return 0.0;
    }

    // 🛑 KORRIGIERTE METHODE: Implementiert das Netting der Long/Short-Positionen
    public static synchronized void recalculateAllBalances() {
        Blockchain chain = BlockchainPersistence.loadBlockchain("MyChain", 1);

        // 1. Alle Salden und Positionen zurücksetzen
        for (Wallet w : wallets) {
            w.setBalance(0.0);
            w.setUsdBalance(w.getInitialUsdBalance()); // USD auf Initialwert zurücksetzen

            // 🛑 WICHTIG: Setzt Positionen auf 0 für das Netting
            w.setLongPositionUsd(0.0);
            w.setShortPositionUsd(0.0);

            w.setTransactionHistory(new ArrayList<>()); // Historie zurücksetzen
        }

        // 2. Transaktionen durchlaufen und Salden/Positionen/Historie aktualisieren
        for (Block block : chain.getChain()) {
            for (Transaction tx : block.getTransactions()) {
                String sender = tx.getSender();
                String recipient = tx.getRecipient();
                double amount = tx.getAmount();

                Wallet senderWallet = findWalletByAddress(sender);
                Wallet recipientWallet = findWalletByAddress(recipient);

                // Füge Transaktion zur Historie beider Wallets hinzu (falls existent)
                if (senderWallet != null) senderWallet.getTransactionHistory().add(tx);
                if (recipientWallet != null) recipientWallet.getTransactionHistory().add(tx);


                // --- SC Balance und Positionen berechnen ---

                boolean isExchangeSell = MyChainGUI.EXCHANGE_ADDRESS.equals(recipient);
                boolean isCoinbase = "system".equals(sender) || sender == null || sender.isEmpty();

                // 1. SC-Übertragungen (Normales Krypto-Guthaben)
                if (!isCoinbase) {
                    if (senderWallet != null) senderWallet.debit(amount);
                }
                if (!isExchangeSell) {
                    if (recipientWallet != null) recipientWallet.credit(amount);
                }

                // 2. USD/Positions-Logik (Nur bei Trades mit der Exchange/Supply Wallet)
                double usdValue = parseUsdValueFromMessage(tx.getMessage());
                String message = tx.getMessage(); // Zum Prüfen des Trade-Typs

                // Fall 1: KAUF (LONG / SHORT-COVER) - Supply Wallet sendet
                if (recipientWallet != null && sender.equals(WalletManager.SUPPLY_WALLET.getAddress()) && message.contains("Kauf (LONG)") && usdValue > 0) {
                    // Wallet zahlt USD (Debit)
                    try {
                        recipientWallet.debitUsd(usdValue);
                    } catch (Exception e) {
                        System.err.println("Kritischer Fehler: USD-Guthaben nicht ausreichend beim Recalculate für Kauf-TX!");
                    }

                    // 🛑 NETTING LOGIK FÜR SHORT-COVER ODER LONG-ÖFFNUNG
                    // Prüfe, ob die Wallet Short war, bevor der Kauf stattfand (aktueller Saldo zeigt den Status nach TX an)
                    // Da die SC-Balance bereits oben aktualisiert wurde:
                    if (recipientWallet.getBalance() < 0) {
                        // -> Wallet war Short (negative Balance) und dieser Kauf dient dem Eindecken (Covering)
                        recipientWallet.setShortPositionUsd(recipientWallet.getShortPositionUsd() - usdValue);
                        if (recipientWallet.getShortPositionUsd() < 0) recipientWallet.setShortPositionUsd(0.0);
                    } else {
                        // -> Wallet war nicht Short oder ist jetzt wieder Long: Erhöht Long-Exposure
                        recipientWallet.setLongPositionUsd(recipientWallet.getLongPositionUsd() + usdValue);
                    }
                }

                // Fall 2: VERKAUF (LONG) - Schließt Long-Position
                else if (senderWallet != null && recipient.equals(MyChainGUI.EXCHANGE_ADDRESS) && message.contains("Verkauf (LONG)") && usdValue > 0) {
                    // Wallet erhält USD (Credit)
                    senderWallet.creditUsd(usdValue);

                    // 🛑 NETTING LOGIK FÜR LONG-SCHLIESSUNG
                    // Die Long-Position wurde geschlossen/teilweise reduziert
                    senderWallet.setLongPositionUsd(senderWallet.getLongPositionUsd() - usdValue);
                    if (senderWallet.getLongPositionUsd() < 0) senderWallet.setLongPositionUsd(0.0);
                }

                // Fall 3: SHORT-SALE - Eröffnet Short-Position
                else if (senderWallet != null && recipient.equals(MyChainGUI.EXCHANGE_ADDRESS) && message.contains("Verkauf (SHORT)") && usdValue > 0) {
                    // Wallet erhält USD (Credit)
                    senderWallet.creditUsd(usdValue);

                    // Dies ist eine Short-Position (Short-Exposure wird hinzugefügt)
                    senderWallet.setShortPositionUsd(senderWallet.getShortPositionUsd() + usdValue);
                }

                // Fall 4: Initial SC Grant
                else if (recipientWallet != null && sender.equals(WalletManager.SUPPLY_WALLET.getAddress()) && usdValue == 0.0) {
                    // Keine USD- oder Positionsänderung
                }
            }
        }
        saveWallets();
    }
}