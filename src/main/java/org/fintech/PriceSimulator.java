package org.fintech;

import java.util.Random;

public class PriceSimulator {

    private double currentPrice;
    private final Random random = new Random();
    private static final String PRICE_FILE = "price.txt";

    public PriceSimulator(double initialPrice) {
        // Initialer Preis sollte geladen werden, falls vorhanden
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
            }
        }
        return defaultPrice;
    }
    /**
     * Simuliert den Einfluss eines Handels (Kauf oder Verkauf) auf den Preis.
     * @param amountSC Die gehandelte SC-Menge.
     * @param isBuy True, wenn SC gekauft wird; False, wenn SC verkauft wird.
     */
    public void executeTrade(double amountSC, boolean isBuy) {
        if (amountSC <= 0) return;

        // --- NEUE PARAMETER FÜR INVERSE SKALIERUNG ---
        // Dieser Faktor muss sehr viel höher sein, da wir durch den Preis teilen.
        final double VOLATILITY_FACTOR_NEW = 0.0000001; // 💡 Beispielwert, bitte anpassen!
        // ----------------------------------------------
        final double MAX_PRICE_CHANGE_PERCENT = 0.95;

        // 🟢 NEUE LOGIK: Preisänderung ist INVERS zum aktuellen Preis.
        // Große Preise = kleine absolute Änderung.
        double priceChange = (amountSC * VOLATILITY_FACTOR_NEW) / getCurrentPrice();

        if (!isBuy) {
            priceChange *= -1; // Bei Verkauf wird der Preis gesenkt
        }

        // ----------------------------------------------------
        // 🛑 KONTROLL-LOGIK (Limit)
        // ----------------------------------------------------
        double potentialNewPrice = currentPrice + priceChange;

        // Maximale erlaubte absolute Änderung (maximal 95% Drop)
        double maxAllowedDrop = currentPrice * MAX_PRICE_CHANGE_PERCENT;

        // Prüfen, ob der Preis unter den maximal erlaubten Drop fällt (nur bei Verkauf)
        if (!isBuy && priceChange < 0 && (currentPrice - Math.abs(priceChange)) < (currentPrice - maxAllowedDrop)) {

            // Um den Trade nicht vollständig abzulehnen, begrenzen wir den Drop auf das Maximum.
            // Dies ist meistens besser als den Trade komplett zu blockieren.
            priceChange = -maxAllowedDrop;

            System.out.printf("⚠️ TRADE BEGRENZT: Verkauf von %.3f SC auf maximalen Drop von %.2f%% begrenzt (statt berechnetem %.2f%%).%n",
                    amountSC, (MAX_PRICE_CHANGE_PERCENT * 100), (Math.abs(priceChange) / currentPrice) * 100);

            potentialNewPrice = currentPrice + priceChange;
        }
        // ----------------------------------------------------

        // Preis aktualisieren (nur wenn der Trade nicht abgelehnt oder begrenzt wurde)
        currentPrice = potentialNewPrice;

        // Sicherstellen, dass der Preis nicht negativ oder extrem niedrig wird
        if (currentPrice < 0.01) {
            currentPrice = 0.01;
        }
    }

    public double getCurrentPrice() {
        return currentPrice;
    }
}