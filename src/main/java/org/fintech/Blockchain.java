package org.fintech;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList; // Behält den Import bei

public class Blockchain {

    // 🛑 KORRIGIERT: Wird nun in den Konstruktoren initialisiert
    private final CopyOnWriteArrayList<Block> chain;
    private final int difficulty;
    private final String name;

    // 🛑 WICHTIG: Konstante für den Initialpreis (wird in PriceSimulator und Genesis Block verwendet)
    private static final double INITIAL_PRICE = 0.1;

    public int findBlockIndexByTransaction(Transaction targetTx) {
        // Annahme: 'chain' ist die Liste von Blöcken in der Blockchain-Klasse
        if (chain == null) return -1;

        for (int i = 0; i < chain.size(); i++) {
            Block block = chain.get(i);
            // Prüft, ob die Transaktion anhand der eindeutigen TX-ID im Block existiert
            if (block.getTransactions().stream().anyMatch(tx -> tx.getTxId().equals(targetTx.getTxId()))) {
                return i;
            }
        }
        return -1;
    }

    // 🛑 KORRIGIERT: Konstruktor für neue Kette (Genesis Block wird hier erstellt)
    public Blockchain(String name, int difficulty) {
        this.name = name;
        this.difficulty = difficulty;
        this.chain = new CopyOnWriteArrayList<>(); // ⬅️ KORREKTUR: Initialisierung hinzugefügt

        if (chain.isEmpty()) {
            Wallet supplyWallet = WalletManager.SUPPLY_WALLET;

            List<Transaction> genesisTxs = new ArrayList<>();
            // Die Genesis-Transaktion nutzt den speziellen Konstruktor
            // Transaction(String sender, String recipient, double amount, String message, double priceAtExecution)

            genesisTxs.add(new Transaction(
                    "system",
                    supplyWallet.getAddress(),
                    1000000000000.0,
                    "Genesis Supply – Ursprung der " + name + " Coins!",
                    INITIAL_PRICE // 🛑 Initialpreis wird übergeben
            ));

            Block genesis = new Block(genesisTxs, "0");
            genesis.mineBlock(difficulty);
            chain.add(genesis);
            System.out.println("Genesis-Block erstellt. 1.000.000.000.000.000 SC an Supply Wallet: " + supplyWallet.getAddress().substring(0,16) + "...");
        }
    }

    // 🛑 KORRIGIERT: Konstruktor für geladene Kette
    public Blockchain(List<Block> loadedBlocks, String name, int difficulty) {
        this.name = name;
        this.difficulty = difficulty;
        this.chain = new CopyOnWriteArrayList<>(); // ⬅️ KORREKTUR: Initialisierung hinzugefügt
        this.chain.addAll(loadedBlocks);
    }

    // Die synchronized-Methode ist bei CopyOnWriteArrayList nicht zwingend notwendig,
    // aber schadet nicht, um die atomare Operation zu gewährleisten.
    public synchronized void addBlock(List<Transaction> transactions) {
        Block last = chain.get(chain.size() - 1);
        Block newBlock = new Block(transactions, last.getHash());
        newBlock.mineBlock(difficulty);
        chain.add(newBlock);
    }

    public static int resets = 0;

    public void resetChain() {
        if (this.chain.size() > 1) {
            // WICHTIG: Entfernt alle Blöcke ab Index 1 (behält den Genesis Block bei Index 0)
            resets++;
            // Das Clear() auf die Sublist funktioniert bei CopyOnWriteArrayList nicht direkt wie bei ArrayList,
            // aber wir können Blöcke ab Index 1 manuell entfernen, um sicherzugehen.

            // Einfache Methode für CoWAL: alle Blöcke außer Genesis entfernen
            while (this.chain.size() > 1) {
                this.chain.remove(this.chain.size() - 1);
            }

            System.out.println("--- Kette zurückgesetzt. Alle Blöcke außer Genesis (#0) wurden gelöscht und die Kette wurde "+ resets+"x resettet. ---");
        } else if (this.chain.size() == 1) {
            System.out.println("--- Kette enthält nur den Genesis Block. Keine Aktion erforderlich. ---");
        } else {
            // Dieser Fall sollte bei korrekt geladener Blockchain nicht eintreten.
            System.err.println("--- Kette ist leer! Kritischer Fehler beim Zurücksetzen. ---");
        }
    }

    public boolean isChainValid() {
        for (int i = 1; i < chain.size(); i++) {
            Block current = chain.get(i);
            Block prev = chain.get(i - 1);
            if (!current.getHash().equals(current.calculateHash())) return false;
            if (!current.getPreviousHash().equals(prev.getHash())) return false;
            if (!current.getHash().startsWith("0".repeat(difficulty))) return false;
        }
        return true;
    }

    public void printChain() {
        System.out.println("=== " + name + " (Difficulty: " + difficulty + ") ===");
        for (Block b : chain) {
            System.out.println(b);
            System.out.println("  Transaktionen:");
            for (Transaction tx : b.getTransactions()) {
                System.out.printf("    • %.8s | %s... → %s... | %.2f Coins (P: %.4f) | %s%n", // 🛑 P: Preis HINZUGEFÜGT
                        tx.getTxId(),
                        tx.getSender().substring(0, 10),
                        tx.getRecipient().substring(0, 10),
                        tx.getAmount(),
                        tx.getPriceAtExecution(), // 🛑 Preis abgerufen
                        tx.getMessage().isEmpty() ? "keine Nachricht" : tx.getMessage());
            }
            System.out.println();
        }
        System.out.println("Kette gültig? " + isChainValid() + "\n");
    }

    public List<Block> getChain() { return chain; }
}