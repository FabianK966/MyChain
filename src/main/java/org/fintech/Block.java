package org.fintech;

import com.google.gson.*;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

public class Block {
    private String hash;
    private final String previousHash;
    private final List<Transaction> transactions;
    private long timeStamp;
    private int nonce;

    // Normaler Block
    public Block(List<Transaction> transactions, String previousHash) {
        this.transactions = new ArrayList<>(transactions);
        this.previousHash = previousHash;
        this.timeStamp = new Date().getTime();
        this.nonce = 0;
        this.hash = calculateHash();
    }

    // Genesis-Block
    public Block(String genesisMessage) {
        this.transactions = new ArrayList<>();

        // 🛑 ANPASSUNG: Transaction benötigt jetzt den Preis (angenommen 1.0 für Genesis)
        // Transaction(String sender, String recipient, double amount, String message, double priceAtExecution)
        Transaction genesisTx = new Transaction("system", "genesis", 1000.0, genesisMessage, 1.00); // 🛑 Preis HINZUGEFÜGT

        this.transactions.add(genesisTx);
        this.previousHash = "0";
        this.timeStamp = new Date().getTime();
        this.nonce = 0;
        this.hash = calculateHash();
    }

    public String calculateHash() {
        StringBuilder txData = new StringBuilder();
        // 🛑 ANPASSUNG: Nur der Hash der Transaktion (TX-ID) sollte in den Block-Hash eingehen,
        // da die TX-ID bereits alle TX-Details (inkl. Preis) gehasht hat.
        for (Transaction tx : transactions) {
            txData.append(tx.getTxId());
        }
        String input = previousHash + timeStamp + nonce + txData;
        return StringUtil.applySha256(input);
    }

    public void mineBlock(int difficulty) {
        String target = "0".repeat(difficulty);
        while (!hash.startsWith(target)) {
            nonce++;
            hash = calculateHash();
        }
    }

    // GETTER
    public String getHash() { return hash; }
    public String getPreviousHash() { return previousHash; }
    public List<Transaction> getTransactions() { return new ArrayList<>(transactions); }
    public long getTimeStamp() { return timeStamp; }
    public int getNonce() { return nonce; }

    @Override
    public String toString() {
        return String.format("Block{hash='%.16s...', prev='%.16s...', tx=%d, nonce=%d}",
                hash, previousHash, transactions.size(), nonce);
    }

    // GSON Adapter
    public static class BlockAdapter implements JsonSerializer<Block>, JsonDeserializer<Block> {
        @Override
        public JsonElement serialize(Block block, Type type, JsonSerializationContext ctx) {
            JsonObject obj = new JsonObject();
            obj.addProperty("hash", block.hash);
            obj.addProperty("previousHash", block.previousHash);
            obj.add("transactions", ctx.serialize(block.transactions));
            obj.addProperty("timeStamp", block.timeStamp);
            obj.addProperty("nonce", block.nonce);
            return obj;
        }

        @Override
        public Block deserialize(JsonElement json, Type type, JsonDeserializationContext ctx) throws JsonParseException {
            JsonObject obj = json.getAsJsonObject();
            Type txListType = new com.google.gson.reflect.TypeToken<List<Transaction>>(){}.getType();
            List<Transaction> loadedTxs = ctx.deserialize(obj.get("transactions"), txListType);

            // 🛑 Beim Deserialisieren den normalen Block-Konstruktor verwenden
            Block block = new Block(loadedTxs, obj.get("previousHash").getAsString());
            block.hash = obj.get("hash").getAsString();
            block.nonce = obj.get("nonce").getAsInt();
            block.timeStamp = obj.get("timeStamp").getAsLong();
            return block;
        }
    }
}