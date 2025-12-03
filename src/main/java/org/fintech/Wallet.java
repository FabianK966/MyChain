package org.fintech;

import org.bouncycastle.jce.provider.BouncyCastleProvider;
import java.security.*;
import java.security.spec.ECGenParameterSpec;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;


public class Wallet {
    private transient PrivateKey privateKey;
    private transient PublicKey publicKey;
    private final String address;
    private double balance = 0.0;

    private final int uniqueId;
    private final String passwordHash;
    private final String clearPassword;

    private double usdBalance;
    // Speichert den initial zufälligen USD-Betrag
    private final double initialUsdBalance;

    private String privateKeyB64;
    private String publicKeyB64;

    // 🌟 NEU: Hauptkonstruktor akzeptiert initialUsdBalance
    public Wallet(String password, double startingUsd) {
        this.uniqueId = WalletManager.getAndIncrementNextId();
        generateKeyPair();
        this.address = generateAddress(publicKey);
        this.passwordHash = StringUtil.applySha256(password);
        this.clearPassword = password;

        this.usdBalance = startingUsd;
        this.initialUsdBalance = startingUsd; // Wert speichern!
    }

    // Für die Supply Wallet, die eine spezielle Balance-Logik hat (immer 0 USD)
    public Wallet(String password) {
        this(password, 0.0);
    }

    // Für normale Wallets, wird von WalletManager mit der korrekten Balance aufgerufen
    public Wallet() {
        this(StringUtil.generateRandomPassword(), 0.0);
    }

    public boolean checkPassword(String inputPassword) {
        return passwordHash.equals(StringUtil.applySha256(inputPassword));
    }

    // Wichtige Methode zur Wiederherstellung nach dem Laden der Datei
    private void readObject(java.io.ObjectInputStream in) throws Exception {
        in.defaultReadObject();
        restoreKeysFromBase64();

        // Fallback-Logik beibehalten
        if (usdBalance == 0.0 && !this.getAddress().equals(WalletManager.SUPPLY_WALLET.getAddress())) {
            if (this.balance > 0) {
                usdBalance = 1000.0;
            }
        }
    }

    public void restoreKeysFromBase64() {
        try {
            if (privateKeyB64 != null) {
                byte[] privBytes = Base64.getDecoder().decode(privateKeyB64);
                PKCS8EncodedKeySpec privSpec = new PKCS8EncodedKeySpec(privBytes);
                KeyFactory kf = KeyFactory.getInstance("ECDSA", "BC");
                this.privateKey = kf.generatePrivate(privSpec);
            }
            if (publicKeyB64 != null) {
                byte[] pubBytes = Base64.getDecoder().decode(publicKeyB64);
                X509EncodedKeySpec pubSpec = new X509EncodedKeySpec(pubBytes);
                KeyFactory kf = KeyFactory.getInstance("ECDSA", "BC");
                this.publicKey = kf.generatePublic(pubSpec);
            }
        } catch (Exception e) {
            throw new RuntimeException("Keys konnten nicht wiederhergestellt werden", e);
        }
    }

    private void generateKeyPair() {
        try {
            Security.addProvider(new BouncyCastleProvider());
            KeyPairGenerator gen = KeyPairGenerator.getInstance("ECDSA", "BC");
            ECGenParameterSpec spec = new ECGenParameterSpec("secp256k1");
            gen.initialize(spec, new SecureRandom());
            KeyPair pair = gen.generateKeyPair();
            this.privateKey = pair.getPrivate();
            this.publicKey = pair.getPublic();

            this.privateKeyB64 = Base64.getEncoder().encodeToString(privateKey.getEncoded());
            this.publicKeyB64 = Base64.getEncoder().encodeToString(publicKey.getEncoded());
        } catch (Exception e) {
            throw new RuntimeException("Key-Generierung fehlgeschlagen", e);
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

    // GETTER
    public PrivateKey getPrivateKey() { return privateKey; }
    public PublicKey getPublicKey() { return publicKey; }
    public String getAddress() { return address; }
    public double getBalance() { return balance; }
    public int getUniqueId() { return uniqueId; }
    public double getUsdBalance() { return usdBalance; }
    public double getInitialUsdBalance() { return initialUsdBalance; }
    public String getPasswordHash() { return passwordHash; }
    public String getClearPassword() { return clearPassword; }

    // BALANCE (SC)
    public void credit(double amount) { this.balance += amount; }
    public void debit(double amount) { this.balance -= amount; }
    public void setBalance(double b) { this.balance = b; }

    // USD Balance Methoden
    public void creditUsd(double amount) { this.usdBalance += amount; }

    public void debitUsd(double amount) {
        if (this.usdBalance < amount) {
            throw new RuntimeException("Nicht genug Guthaben (USD)! Benötigt: " + String.format("%.2f", amount) + " USD");
        }
        this.usdBalance -= amount;
    }

    public void setUsdBalance(double amount) {
        this.usdBalance = amount;
    }

    public Transaction createTransaction(String recipient, double amount, String message) {
        if (balance < amount) throw new RuntimeException("Nicht genug Guthaben (SC)! Benötigt: " + String.format("%.3f", amount) + " SC");
        return new Transaction(this, recipient, amount, message);
    }
}