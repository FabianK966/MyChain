package org.fintech;

import java.security.*;
import java.util.Base64;

public class Transaction {
    private final String sender;
    private final String recipient;
    private final double amount;
    private final String message;
    private final String txId;
    private final byte[] signature;
    private final double priceAtExecution; // NEU: Preis zum Zeitpunkt der Ausführung

    // Normale Transaktion (mit Wallet)
    public Transaction(Wallet senderWallet, String recipient, double amount, String message, double priceAtExecution) {
        this.sender = senderWallet.getAddress();
        this.recipient = recipient;
        this.amount = amount;
        this.message = message;
        this.priceAtExecution = priceAtExecution;
        this.txId = calculateHash();
        this.signature = sign(senderWallet.getPrivateKey());
    }

    // Genesis-Transaktion (System)
    public Transaction(String sender, String recipient, double amount, String message, double priceAtExecution) {
        this.sender = sender != null ? sender : "system";
        this.recipient = recipient;
        this.amount = amount;
        this.message = message;
        this.priceAtExecution = priceAtExecution;
        this.txId = calculateHash();
        this.signature = new byte[0];
    }

    private byte[] sign(PrivateKey key) {
        try {
            Security.addProvider(new org.bouncycastle.jce.provider.BouncyCastleProvider());
            Signature sig = Signature.getInstance("ECDSA", "BC");
            sig.initSign(key);
            String data = sender + recipient + amount + message + txId + priceAtExecution; // Preis in Signaturdaten
            sig.update(data.getBytes());
            return sig.sign();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public boolean verifySignature(PublicKey key) {
        if (sender.equals("system")) return true;

        try {
            Security.addProvider(new org.bouncycastle.jce.provider.BouncyCastleProvider());
            Signature sig = Signature.getInstance("ECDSA", "BC");
            sig.initVerify(key);
            String data = sender + recipient + amount + message + txId + priceAtExecution; // Preis in Verifizierungsdaten
            sig.update(data.getBytes());
            return sig.verify(signature);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private String calculateHash() {
        return StringUtil.applySha256(sender + recipient + amount + message + priceAtExecution + System.nanoTime()); // Preis HINZUGEFÜGT
    }

    // GETTER
    public String getSender() { return sender; }
    public String getRecipient() { return recipient; }
    public double getAmount() { return amount; }
    public String getMessage() { return message; }
    public String getTxId() { return txId; }
    public double getPriceAtExecution() { return priceAtExecution; }
}