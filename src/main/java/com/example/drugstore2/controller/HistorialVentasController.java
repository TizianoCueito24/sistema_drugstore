package com.example.drugstore2.controller;

import com.example.drugstore2.model.Venta;
import com.example.drugstore2.service.VentaService;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.*;

public class HistorialVentasController {

    private VentaService ventaService;

    @FXML private TableView<Venta> historialTableView;
    @FXML private TableColumn<Venta, String> fechaCol;
    @FXML private TableColumn<Venta, String> detalleCol; // Nombre producto
    @FXML private TableColumn<Venta, Integer> cantidadCol;
    @FXML private TableColumn<Venta, Double> totalCol; // Subtotal linea
    @FXML private Button recargarHistorialBtn;

    @FXML
    public void initialize() {
        configurarTabla();
        // Los datos se cargarán cuando se inyecte el servicio
        historialTableView.setPlaceholder(new Label("Cargando historial..."));
    }

    public void setVentaService(VentaService ventaService) {
        this.ventaService = ventaService;
        cargarHistorial(); // Carga inicial
    }

    private void configurarTabla() {
        fechaCol.setCellValueFactory(cellData -> new SimpleStringProperty(cellData.getValue().getFechaHoraString()));
        detalleCol.setCellValueFactory(cellData -> new SimpleStringProperty(cellData.getValue().getDetalle().getProducto().getNombre()));
        cantidadCol.setCellValueFactory(cellData -> new SimpleIntegerProperty(cellData.getValue().getDetalle().getCantidad()).asObject());
        totalCol.setCellValueFactory(cellData -> new SimpleDoubleProperty(cellData.getValue().getTotal()).asObject());

        // Formato para la columna de total (subtotal)
        totalCol.setCellFactory(tc -> new TableCell<Venta, Double>() {
            @Override protected void updateItem(Double price, boolean empty) {
                super.updateItem(price, empty);
                setText(empty || price == null ? null : String.format("$%.2f", price));
            }
        });
    }

    private void cargarHistorial() {
        if (ventaService != null) {
            // Asume que getHistorialVentas() obtiene la lista actualizada (o la recarga)
            historialTableView.setItems(ventaService.getHistorialVentas());
            if (historialTableView.getItems().isEmpty()) {
                historialTableView.setPlaceholder(new Label("No hay ventas registradas en la base de datos."));
            }
        } else {
            historialTableView.setPlaceholder(new Label("Error: Servicio de ventas no disponible."));
            System.err.println("HistorialVentasController: VentaService es null al cargar historial.");
        }
        historialTableView.refresh(); // Asegurar refresco visual
    }


    @FXML
    void handleRecargarHistorial(ActionEvent event) {
        System.out.println("Recargando historial manualmente...");
        // Si VentaService tiene un método explícito para recargar, llamarlo aquí.
        // Ejemplo: ventaService.cargarHistorialDesdeDB();
        cargarHistorial(); // Vuelve a obtener la lista del servicio
    }

    // Este método podría ser llamado por MainApp si la pestaña se selecciona
    public void recargarHistorial() {
        if (historialTableView != null) { // Asegurarse que la UI está lista
            cargarHistorial();
        }
    }
}