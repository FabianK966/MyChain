package org.fintech;

import java.util.Random;

public class PriceSimulator {

    private double currentPrice;
    private final Random random = new Random();
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
    public void executeTrade(double amountSC, boolean isBuy) {
        if (amountSC <= 0) return;

        // Standard-Parameter (können in den Feldern definiert sein)
        final double VOLATILITY_FACTOR = 0.0000001; // Beispielwert
        final double MAX_PRICE_CHANGE_PERCENT = 0.70;  // 🛑 50% Limit

        // Berechnen der Preisänderung basierend auf Handelsvolumen
        double priceChange = amountSC * VOLATILITY_FACTOR * getCurrentPrice();

        if (!isBuy) {
            priceChange *= -1; // Bei Verkauf wird der Preis gesenkt
        }

        // ----------------------------------------------------
        // 🛑 NEUE KONTROLL-LOGIK (50%-Limit)
        // ----------------------------------------------------
        double potentialNewPrice = currentPrice + priceChange;

        // Maximale erlaubte Preisänderung (absolut)
        double maxAllowedDrop = currentPrice * MAX_PRICE_CHANGE_PERCENT;

        // Prüfen, ob die Preisänderung das 50%-Limit überschreitet
        if (!isBuy && priceChange < 0 && Math.abs(priceChange) > maxAllowedDrop) {

            System.out.printf("🚨 TRADE ABGELEHNT: Verkauf von %.3f SC würde Preis um %.2f%% droppen (Limit 50.00%%).%n",
                    amountSC, (Math.abs(priceChange) / currentPrice) * 100);

            // Den Trade blockieren, indem die Methode beendet wird, ohne den Preis zu ändern.
            return;
        }
        // ----------------------------------------------------

        // Preis aktualisieren (nur wenn der Trade nicht abgelehnt wurde)
        currentPrice = potentialNewPrice;

        // Sicherstellen, dass der Preis nicht negativ oder extrem niedrig wird
        if (currentPrice < 0.3) {
            currentPrice = 0.3;
        }
    }

    public double getCurrentPrice() {
        return currentPrice;
    }
}