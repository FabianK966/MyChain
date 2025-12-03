package org.fintech;

import javafx.application.Application;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

import java.util.List;
import java.util.stream.Collectors;

public class LoginGUI extends Application {

    private ComboBox<String> walletAddressCombo;
    private PasswordField passwordField;

    @Override
    public void start(Stage primaryStage) {
        // Zuerst Wallets laden, damit sie zur Auswahl stehen
        WalletManager.loadWallets();

        primaryStage.setTitle("MyChain – Login");
        primaryStage.setResizable(false);

        // UI-Elemente
        Label title = new Label("MyChain Login");
        title.setStyle("-fx-font-size: 20px; -fx-font-weight: bold;");

        walletAddressCombo = new ComboBox<>();
        walletAddressCombo.setPromptText("Wählen Sie Ihre Adresse");
        walletAddressCombo.setPrefWidth(300);

        passwordField = new PasswordField();
        passwordField.setPromptText("Passwort eingeben");
        passwordField.setPrefWidth(300);

        Button loginButton = new Button("Anmelden");
        loginButton.setPrefWidth(300);

        // Füllen der ComboBox mit den gekürzten Adressen
        List<Wallet> wallets = WalletManager.getWallets();
        wallets.stream()
                .map(w -> w.getAddress().substring(0, 16) + "...") // Kürzen für bessere Lesbarkeit
                .forEach(address -> walletAddressCombo.getItems().add(address));

        if (!walletAddressCombo.getItems().isEmpty()) {
            walletAddressCombo.setValue(walletAddressCombo.getItems().get(0));
        }

        // Layout
        VBox root = new VBox(20, title, walletAddressCombo, passwordField, loginButton);
        root.setPadding(new Insets(30));
        root.setAlignment(Pos.CENTER);

        // Event-Handler
        loginButton.setOnAction(e -> handleLogin(primaryStage));
        passwordField.setOnAction(e -> handleLogin(primaryStage)); // Login bei Enter

        Scene scene = new Scene(root, 360, 300);
        primaryStage.setScene(scene);
        primaryStage.show();
    }

    private void handleLogin(Stage primaryStage) {
        String selectedDisplayAddress = walletAddressCombo.getValue();
        String password = passwordField.getText();

        if (selectedDisplayAddress == null || selectedDisplayAddress.isEmpty()) {
            showAlert("Fehler", "Bitte wählen Sie eine Wallet-Adresse.", Alert.AlertType.ERROR);
            return;
        }

        // Die vollständige Wallet finden (durch Vergleich des gekürzten Strings)
        Wallet walletToLogin = WalletManager.getWallets().stream()
                .filter(w -> selectedDisplayAddress.startsWith(w.getAddress().substring(0, 16)))
                .findFirst()
                .orElse(null);


        if (walletToLogin != null && walletToLogin.checkPassword(password)) {
            // Login erfolgreich
            primaryStage.close();

            // Startet die Haupt-GUI mit der eingeloggten Wallet
            MyChainGUI mainGUI = new MyChainGUI(walletToLogin);
            try {
                // Manuelles Aufrufen der Start-Methode
                mainGUI.start(new Stage());
            } catch (Exception ex) {
                ex.printStackTrace();
            }

        } else {
            showAlert("Fehler", "Falsches Passwort oder Wallet-Adresse.", Alert.AlertType.ERROR);
        }
    }

    private void showAlert(String title, String message, Alert.AlertType type) {
        Alert alert = new Alert(type);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }
}