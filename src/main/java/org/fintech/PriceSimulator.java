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
        final double VOLATILITY_FACTOR_NEW = 10000.0; // 💡 Beispielwert, bitte anpassen!
        // ----------------------------------------------

        // Maximale erlaubte Preisänderungen
        final double MAX_PRICE_DROP_PERCENT = 0.10; // Maximaler Drop: 10%
        final double MAX_PRICE_RISE_PERCENT = 0.10; // Maximaler Anstieg: 10% (Preis verdoppelt sich)

        // 🟢 NEUE LOGIK: Preisänderung ist INVERS zum aktuellen Preis.
        double priceChange = (amountSC * VOLATILITY_FACTOR_NEW) / getCurrentPrice();

        if (!isBuy) {
            priceChange *= -1; // Bei Verkauf wird der Preis gesenkt
        }

        // ----------------------------------------------------
        // 🛑 KONTROLL-LOGIK (Limit)
        // ----------------------------------------------------
        double potentialNewPrice = currentPrice + priceChange;

        // --- 1. KONTROLLE: MAXIMALE PREISSTEIGERUNG (KAUF/LONG) ---
        if (isBuy && priceChange > 0) {
            double maxAllowedRise = currentPrice * MAX_PRICE_RISE_PERCENT;

            if (priceChange > maxAllowedRise) {
                priceChange = maxAllowedRise;

                System.out.printf("⚠️ TRADE BEGRENZT: Kauf von %.3f SC auf maximalen Anstieg von %.2f%% begrenzt.%n",
                        amountSC, (MAX_PRICE_RISE_PERCENT * 100));

                potentialNewPrice = currentPrice + priceChange;
            }
        }

        // --- 2. KONTROLLE: MAXIMALER PREISABFALL (VERKAUF/SHORT) ---
        else if (!isBuy && priceChange < 0) {
            double maxAllowedDrop = currentPrice * MAX_PRICE_DROP_PERCENT;

            // Preisänderung ist negativ, daher vergleichen wir mit dem Betrag des Drops
            if (Math.abs(priceChange) > maxAllowedDrop) {

                // Begrenzen des Drops auf das Maximum (priceChange ist hier negativ)
                priceChange = -maxAllowedDrop;

                System.out.printf("⚠️ TRADE BEGRENZT: Verkauf von %.3f SC auf maximalen Drop von %.2f%% begrenzt.%n",
                        amountSC, (MAX_PRICE_DROP_PERCENT * 100));

                potentialNewPrice = currentPrice + priceChange;
            }
        }
        // ----------------------------------------------------

        // Preis aktualisieren
        currentPrice = potentialNewPrice;

        // Sicherstellen, dass der Preis nicht negativ oder extrem niedrig wird
        if (currentPrice < 0.05) {
            currentPrice = 0.05;
            savePrice(currentPrice); // Speichern des Endpreises nach dem Trade
        } else {
            savePrice(currentPrice);
        }
    }

    public double getCurrentPrice() {
        return currentPrice;
    }
}