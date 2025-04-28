package com.example.drugstore2.controller;

import com.example.drugstore2.service.InventarioService;
import com.example.drugstore2.model.Producto;
import com.example.drugstore2.util.AlertUtil;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.paint.Color;

import java.util.Optional;

public class DevolucionesController {

    private InventarioService inventarioService;
    private StockController stockController; // Referencia para refrescar tabla stock

    @FXML private TextField devolucionCodigoField;
    @FXML private Label nombreProductoLabel;
    @FXML private Spinner<Integer> devolucionCantidadSpinner;
    @FXML private TextField devolucionMotivoField;
    @FXML private Button registrarDevolucionButton;

    @FXML
    public void initialize() {
        // Configurar Spinner
        SpinnerValueFactory<Integer> valueFactory = new SpinnerValueFactory.IntegerSpinnerValueFactory(1, 1000, 1); // Max 1000, inicial 1
        devolucionCantidadSpinner.setValueFactory(valueFactory);
        devolucionCantidadSpinner.setEditable(true); // Permitir escribir cantidad

        // Listener para limpiar label si se borra el código
        devolucionCodigoField.textProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal == null || newVal.trim().isEmpty()) {
                nombreProductoLabel.setText("");
            }
        });
    }

    public void setInventarioService(InventarioService inventarioService) {
        this.inventarioService = inventarioService;
    }

    // Método para inyectar el controlador de Stock si es necesario (para refrescar)
    // public void setStockController(StockController stockController) {
    //     this.stockController = stockController;
    // }

    @FXML
    void handleCodigoEntered(ActionEvent event) {
        buscarProductoPorCodigo();
    }

    private void buscarProductoPorCodigo() {
        String codigo = devolucionCodigoField.getText().trim();
        if (codigo.isEmpty()) {
            nombreProductoLabel.setText("");
            return;
        }
        Producto p = inventarioService.buscarProductoPorCodigo(codigo);
        if (p != null) {
            nombreProductoLabel.setText("Producto: " + p.getNombre() + " (Stock actual: " + p.getStock() + ")");
            nombreProductoLabel.setTextFill(Color.DARKGREEN);
        } else {
            nombreProductoLabel.setText("Producto no encontrado en inventario activo.");
            nombreProductoLabel.setTextFill(Color.RED);
        }
    }

    @FXML
    void handleRegistrarDevolucion(ActionEvent event) {
        String codigo = devolucionCodigoField.getText().trim();
        Integer cantidad = devolucionCantidadSpinner.getValue(); // Ya es Integer
        String motivo = devolucionMotivoField.getText().trim();

        if (codigo.isEmpty()) {
            AlertUtil.showAlert(Alert.AlertType.WARNING, "Falta Código", "Ingrese el código del producto a devolver.");
            devolucionCodigoField.requestFocus();
            return;
        }
        if (cantidad == null || cantidad <= 0) {
            // El spinner debería prevenir null/<=0 si está bien configurado, pero validamos igual
            AlertUtil.showAlert(Alert.AlertType.WARNING, "Cantidad Inválida", "La cantidad a devolver debe ser mayor a 0.");
            return;
        }

        // Volver a buscar por si el usuario cambió el código después de la búsqueda inicial
        Producto productoDevuelto = inventarioService.buscarProductoPorCodigo(codigo);
        if (productoDevuelto == null) {
            AlertUtil.showAlert(Alert.AlertType.ERROR, "Producto No Encontrado", "El código '" + codigo + "' no corresponde a un producto activo.");
            nombreProductoLabel.setText("Producto no encontrado en inventario activo.");
            nombreProductoLabel.setTextFill(Color.RED);
            return;
        }

        // Confirmación
        Alert confirmacion = new Alert(Alert.AlertType.CONFIRMATION);
        confirmacion.setTitle("Confirmar Devolución");
        confirmacion.setHeaderText("Registrar devolución de " + cantidad + " unidad(es) de:");
        confirmacion.setContentText(productoDevuelto.getNombre() + " (Código: " + codigo + ")" +
                "\nMotivo: " + (motivo.isEmpty() ? "No especificado" : motivo) + "\n\n¿Está seguro?");
        Optional<ButtonType> resultado = confirmacion.showAndWait();

        if (resultado.isPresent() && resultado.get() == ButtonType.OK) {
            // Usar ajustarStockManual para que quede registrado en el log de eventos
            boolean exito = inventarioService.ajustarStockManual(codigo, cantidad, "Devolución: " + motivo);

            if (exito) {
                AlertUtil.showAlert(Alert.AlertType.INFORMATION, "Devolución Registrada",
                        cantidad + " unidad(es) de '" + productoDevuelto.getNombre() + "' devueltas al stock.");
                // Limpiar formulario
                devolucionCodigoField.clear();
                devolucionCantidadSpinner.getValueFactory().setValue(1); // Reset a 1
                devolucionMotivoField.clear();
                nombreProductoLabel.setText("");
                devolucionCodigoField.requestFocus();

                // Opcional: Refrescar tabla de stock si se inyectó el controlador
                // if (stockController != null) {
                //     stockController.refrescarTablaProductos();
                // }
            } else {
                AlertUtil.showAlert(Alert.AlertType.ERROR, "Error al Registrar", "No se pudo actualizar el stock del producto devuelto.");
            }
        }
    }
}