package org.fintech;

import java.util.ArrayList;
import java.util.List;

public class Blockchain {
    private final List<Block> chain = new ArrayList<>();
    private final int difficulty;
    private final String name;

    public Blockchain(String name, int difficulty) {
        this.name = name;
        this.difficulty = difficulty;

        // Genesis-Block mit echter Empfänger-Adresse
        Wallet firstWallet = WalletManager.getWallets().isEmpty() ?
                WalletManager.createWallet() : WalletManager.getWallets().get(0);

        List<Transaction> genesisTxs = new ArrayList<>();
        genesisTxs.add(new Transaction("system", firstWallet.getAddress(), 1000.0, "Genesis Reward"));

        Block genesis = new Block(genesisTxs, "0");
        genesis.mineBlock(difficulty);
        chain.add(genesis);
    }

    public Blockchain(List<Block> loadedBlocks, String name, int difficulty) {
        this.name = name;
        this.difficulty = difficulty;
        this.chain.addAll(loadedBlocks);
    }

    public void addBlock(List<Transaction> transactions) {
        Block last = chain.get(chain.size() - 1);
        Block newBlock = new Block(transactions, last.getHash());
        newBlock.mineBlock(difficulty);
        chain.add(newBlock);
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
                System.out.printf("    • %.8s | %s... → %s... | %.2f Coins | %s%n",
                        tx.getTxId(),
                        tx.getSender().substring(0, 10),
                        tx.getRecipient().substring(0, 10),
                        tx.getAmount(),
                        tx.getMessage().isEmpty() ? "keine Nachricht" : tx.getMessage());
            }
            System.out.println();
        }
        System.out.println("Kette gültig? " + isChainValid() + "\n");
    }

    public List<Block> getChain() { return chain; }
}