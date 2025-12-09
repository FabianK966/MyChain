package org.fintech;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;

import java.io.*;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;

public class BlockchainPersistence {

    private static final String FILE_NAME = "blockchain.json";
    private static final Gson gson = new GsonBuilder()
            .registerTypeAdapter(Block.class, new Block.BlockAdapter())  // WICHTIG!
            .create();

    // Blockchain speichern
    public static void saveBlockchain(Blockchain blockchain) {
        // 🛑 ÄNDERUNG: Nichts speichern (leer lassen)
        // Optional: Nur loggen für Debugging
        // System.out.println("Blockchain würde gespeichert (" + blockchain.getChain().size() + " Blöcke)");
    }

    // Blockchain laden (oder neue erstellen, falls keine Datei)
    public static Blockchain loadBlockchain(String name, int difficulty) {
        // 🛑 ÄNDERUNG: Immer neue Blockchain erstellen, nie laden
        System.out.println("Neue Blockchain wird erstellt (kein Laden).");
        return new Blockchain(name, difficulty);
    }
}