package org.fintech;

import java.security.*;

public class Transaction {
    private final String sender;
    private final String recipient;
    private final double amount;
    private final String message;
    private final String txId;
    private final byte[] signature;

    // Normale Transaktion
    public Transaction(Wallet senderWallet, String recipient, double amount, String message) {
        this.sender = senderWallet.getAddress();
        this.recipient = recipient;
        this.amount = amount;
        this.message = message;
        this.txId = calculateHash();
        this.signature = sign(senderWallet.getPrivateKey());
    }

    // Genesis-Transaktion
    public Transaction(String sender, String recipient, double amount, String message) {
        this.sender = sender != null ? sender : "system";  // sicherstellen
        this.recipient = recipient;
        this.amount = amount;
        this.message = message;
        this.txId = calculateHash();
        this.signature = new byte[0];
    }

    private byte[] sign(PrivateKey key) {
        try {
            // Sicherstellen, dass der Provider registriert ist
            Security.addProvider(new org.bouncycastle.jce.provider.BouncyCastleProvider());
            Signature sig = Signature.getInstance("ECDSA", "BC");
            sig.initSign(key);
            String data = sender + recipient + amount + message + txId;
            sig.update(data.getBytes());
            return sig.sign();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    // 🌟 NEUE METHODE: Überprüft die Signatur der Transaktion
    public boolean verifySignature(PublicKey key) {
        // System-Transaktionen sind immer gültig
        if (sender.equals("system")) return true;

        try {
            // Sicherstellen, dass der Provider registriert ist
            Security.addProvider(new org.bouncycastle.jce.provider.BouncyCastleProvider());
            Signature sig = Signature.getInstance("ECDSA", "BC");
            sig.initVerify(key);
            String data = sender + recipient + amount + message + txId;
            sig.update(data.getBytes());
            return sig.verify(signature);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private String calculateHash() {
        return StringUtil.applySha256(sender + recipient + amount + message + System.nanoTime());
    }

    // GETTER
    public String getSender() { return sender; }
    public String getRecipient() { return recipient; }
    public double getAmount() { return amount; }
    public String getMessage() { return message; }
    public String getTxId() { return txId; }
}