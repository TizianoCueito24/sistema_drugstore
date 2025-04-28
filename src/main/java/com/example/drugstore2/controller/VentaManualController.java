package com.example.drugstore2.controller;


import com.example.drugstore2.model.ProductoManual;
import com.example.drugstore2.service.VentaStateService;
import com.example.drugstore2.util.AlertUtil;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.TextField;

import java.text.NumberFormat;
import java.text.ParseException;
import java.util.Locale;

public class VentaManualController {

    private VentaStateService ventaStateService; // Servicio compartido
    private final NumberFormat numberFormat = NumberFormat.getNumberInstance(Locale.getDefault());

    @FXML private TextField nombreManualField;
    @FXML private TextField precioManualField;
    @FXML private TextField cantidadManualField;
    @FXML private Button agregarManualBtn;

    @FXML
    public void initialize() {
        // Configurar validadores numéricos
        configurarValidadores();
        cantidadManualField.setText("1"); // Valor inicial
    }

    public void setVentaStateService(VentaStateService ventaStateService) {
        this.ventaStateService = ventaStateService;
    }

    private void configurarValidadores() {
        // Permitir solo números (enteros o decimales con punto o coma) para precio
        String numericPattern = "\\d*([\\.,]\\d*)?";
        precioManualField.textProperty().addListener((obs, ov, nv) -> { if (!nv.matches(numericPattern)) precioManualField.setText(ov); });

        // Permitir solo números enteros para cantidad
        String integerPattern = "\\d*";
        cantidadManualField.textProperty().addListener((obs, ov, nv) -> { if (!nv.matches(integerPattern) || "0".equals(nv)) cantidadManualField.setText(ov); }); // No permitir 0
    }


    @FXML
    void handleAgregarManual(ActionEvent event) {
        String nombre = nombreManualField.getText().trim();
        String precioStr = precioManualField.getText().trim();
        String cantidadStr = cantidadManualField.getText().trim();

        if (nombre.isEmpty() || precioStr.isEmpty() || cantidadStr.isEmpty()) {
            AlertUtil.showAlert(Alert.AlertType.WARNING, "Campos Vacíos", "Debe ingresar Nombre, Precio y Cantidad.");
            return;
        }

        try {
            double precio = parseDouble(precioStr);
            int cantidad = Integer.parseInt(cantidadStr);

            if (precio <= 0 || cantidad <= 0) {
                AlertUtil.showAlert(Alert.AlertType.WARNING, "Valores Inválidos", "Precio y Cantidad deben ser mayores a 0.");
                return;
            }

            // Crear el producto manual
            ProductoManual prodManual = new ProductoManual(nombre, precio);

            // Añadir al ticket usando el servicio compartido
            for (int i = 0; i < cantidad; i++) {
                ventaStateService.agregarProducto(prodManual);
            }

            // Limpiar formulario y poner foco
            nombreManualField.clear();
            precioManualField.clear();
            cantidadManualField.setText("1");
            nombreManualField.requestFocus();

            // La UI del ticket se actualiza automáticamente por VentaStateService

        } catch (NumberFormatException | ParseException e) {
            AlertUtil.showAlert(Alert.AlertType.ERROR, "Formato Incorrecto", "Precio y Cantidad deben ser números válidos.");
        } catch (Exception e) {
            AlertUtil.showAlert(Alert.AlertType.ERROR, "Error Inesperado", "Ocurrió un error al agregar el producto manual:\n" + e.getMessage());
            e.printStackTrace();
        }
    }

    // Helper para parsear doubles aceptando coma o punto
    private double parseDouble(String value) throws ParseException {
        return numberFormat.parse(value.trim()).doubleValue();
    }

}