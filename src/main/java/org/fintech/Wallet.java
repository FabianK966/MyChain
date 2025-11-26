package org.fintech;

import org.bouncycastle.jce.provider.BouncyCastleProvider;
import java.security.*;
import java.security.spec.ECGenParameterSpec;

public class Wallet {
    private final PrivateKey privateKey;
    private final PublicKey publicKey;
    private final String address;
    private double balance = 0.0;

    public Wallet() {
        try {
            Security.addProvider(new BouncyCastleProvider());
            KeyPairGenerator keyGen = KeyPairGenerator.getInstance("ECDSA", "BC");
            ECGenParameterSpec ecSpec = new ECGenParameterSpec("secp256k1");
            keyGen.initialize(ecSpec, new SecureRandom());
            KeyPair pair = keyGen.generateKeyPair();
            this.privateKey = pair.getPrivate();
            this.publicKey = pair.getPublic();
            this.address = generateAddress(publicKey);
        } catch (Exception e) {
            throw new RuntimeException("Fehler beim Erstellen der Wallet", e);
        }
    }

    private String generateAddress(PublicKey publicKey) {
        try {
            byte[] pubBytes = publicKey.getEncoded();
            MessageDigest sha256 = MessageDigest.getInstance("SHA-256");
            byte[] shaHash = sha256.digest(pubBytes);
            MessageDigest ripemd160 = MessageDigest.getInstance("RIPEMD160", "BC");
            byte[] ripeHash = ripemd160.digest(shaHash);

            byte[] versioned = new byte[ripeHash.length + 1];
            versioned[0] = 0x00;
            System.arraycopy(ripeHash, 0, versioned, 1, ripeHash.length);

            MessageDigest doubleSha = MessageDigest.getInstance("SHA-256");
            byte[] checksum = doubleSha.digest(doubleSha.digest(versioned));
            byte[] finalBytes = new byte[versioned.length + 4];
            System.arraycopy(versioned, 0, finalBytes, 0, versioned.length);
            System.arraycopy(checksum, 0, finalBytes, versioned.length, 4);

            return "1" + StringUtil.base58Encode(finalBytes);
        } catch (Exception e) {
            return "1Error" + System.currentTimeMillis();
        }
    }

    // === GETTER ===
    public PrivateKey getPrivateKey() { return privateKey; }
    public PublicKey getPublicKey() { return publicKey; }
    public String getAddress() { return address; }
    public double getBalance() { return balance; }

    // === BALANCE ===
    public void credit(double amount) { this.balance += amount; }
    public void debit(double amount) { this.balance -= amount; }
    public void setBalance(double balance) { this.balance = balance; }

    // WICHTIG: Wallet selbst wird übergeben → Signatur intern
    public Transaction createTransaction(String recipient, double amount, String message) {
        if (balance < amount) {
            throw new RuntimeException("Nicht genug Guthaben! Balance: " + balance + " SC");
        }
        return new Transaction(this, recipient, amount, message);
    }
}