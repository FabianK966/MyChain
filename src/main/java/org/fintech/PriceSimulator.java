package org.fintech;

import java.util.Random;

public class PriceSimulator {

    private double currentPrice;
    private final Random random = new Random();

    // Parameter zur Steuerung der Marktvolatilität
    private static final double PRICE_IMPACT_FACTOR = 0.000001; // Wie stark 100 SC den Preis beeinflussen
    private static final double BASE_VOLATILITY = 0.00001;     // Basis-Zufallsschwankung
    private static final String PRICE_FILE = "price.txt";

    public PriceSimulator(double initialPrice) {
        this.currentPrice = initialPrice;
    }

    public static void savePrice(double price) {
        try (java.io.FileWriter writer = new java.io.FileWriter(PRICE_FILE)) {
            writer.write(String.valueOf(price));
        } catch (java.io.IOException e) {
            System.err.println("Fehler beim Speichern des Preises: " + e.getMessage());
        }
    }

    public static double loadPrice(double defaultPrice) {
        java.io.File file = new java.io.File(PRICE_FILE);
        if (file.exists() && file.length() > 0) {
            try (java.io.BufferedReader reader = new java.io.BufferedReader(new java.io.FileReader(file))) {
                String line = reader.readLine();
                if (line != null) {
                    return Double.parseDouble(line.trim());
                }
            } catch (java.io.IOException | NumberFormatException e) {
                System.err.println("Fehler beim Laden oder Parsen des Preises: " + e.getMessage());
                // Fallback auf den Standardwert bei Fehler
            }
        }
        return defaultPrice;
    }
    /**
     * Simuliert den Einfluss eines Handels (Kauf oder Verkauf) auf den Preis.
     * @param amount Die gehandelte SC-Menge.
     * @param isBuy True, wenn SC gekauft wird (erhöht Nachfrage/Preis); False, wenn SC verkauft wird (erhöht Angebot/senkt Preis).
     */
    public void executeTrade(double amount, boolean isBuy) {
        // Berechnet den Preis-Impact basierend auf der gehandelten Menge
        double priceImpact = (amount / 100) * PRICE_IMPACT_FACTOR;

        // Preis basierend auf Kauf/Verkauf anpassen
        if (isBuy) {
            currentPrice *= (1 + priceImpact); // Kauf erhöht den Preis
        } else {
            currentPrice *= (1 - priceImpact); // Verkauf senkt den Preis
        }

        // Basis-Volatilität hinzufügen (kleiner Random Walk)
        double volatilityChange = random.nextDouble() * 2 * BASE_VOLATILITY - BASE_VOLATILITY;
        currentPrice *= (1 + volatilityChange);

        // Sicherstellen, dass der Preis nicht negativ oder extrem niedrig wird
        if (currentPrice < 0.01) {
            currentPrice = 0.01;
        }
    }

    public double getCurrentPrice() {
        return currentPrice;
    }
}