package com.example.drugstore2.util;

import javafx.scene.control.Alert;

public class AlertUtil {

    public static void showAlert(Alert.AlertType alertType, String title, String message) {
        Alert alert = new Alert(alertType);
        alert.setTitle(title);
        alert.setHeaderText(null); // Sin texto de cabecera
        alert.setContentText(message);
        // Podrías añadir aquí para que espere en el hilo correcto si fuera necesario
        // Platform.runLater(alert::showAndWait); // Aunque showAndWait suele bloquear
        alert.showAndWait();
    }
}