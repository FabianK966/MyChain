package org.fintech;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

public class BlockchainPersistence {
    // Blockchain speichern
    public static void saveBlockchain(Blockchain blockchain) {
        System.out.println("Blockchain würde gespeichert (" + blockchain.getChain().size() + " Blöcke)");
    }

    // Blockchain laden (oder neue erstellen, falls keine erstellt)
    public static Blockchain loadBlockchain(String name, int difficulty) {
        System.out.println("Neue Blockchain wird erstellt (kein Laden).");
        return new Blockchain(name, difficulty);
    }
}