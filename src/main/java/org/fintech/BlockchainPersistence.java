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
        List<Block> blocks = blockchain.getChain();
        String json = gson.toJson(blocks);

        try (Writer writer = new FileWriter(FILE_NAME)) {
            writer.write(json);
        } catch (IOException e) {
            System.err.println("Fehler beim Speichern: " + e.getMessage());
        }
    }

    // Blockchain laden (oder neue erstellen, falls keine Datei)
    public static Blockchain loadBlockchain(String name, int difficulty) {
        File file = new File(FILE_NAME);
        if (!file.exists()) {
            System.out.println("Keine gespeicherte Blockchain gefunden → neue wird erstellt.");
            return new Blockchain(name, difficulty);
        }

        try (Reader reader = new FileReader(FILE_NAME)) {
            Type listType = new TypeToken<ArrayList<Block>>(){}.getType();
            List<Block> loadedBlocks = gson.fromJson(reader, listType);

            if (loadedBlocks == null || loadedBlocks.isEmpty()) {
                System.out.println("Datei leer → neue Chain");
                return new Blockchain(name, difficulty);
            }
            return new Blockchain(loadedBlocks, name, difficulty);

        } catch (Exception e) {
            System.out.println("Fehler beim Laden – neue Chain wird erstellt. Fehler: " + e.getMessage());
            return new Blockchain(name, difficulty);
        }
    }
}