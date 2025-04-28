package com.example.drugstore2.controller;

import com.example.drugstore2.model.Producto;
import com.example.drugstore2.service.InventarioService;
import com.example.drugstore2.service.VentaService;
import com.example.drugstore2.service.VentaStateService;
import com.example.drugstore2.util.AlertUtil;
import javafx.collections.FXCollections;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseEvent;

import java.util.List;
import java.util.Optional;

public class VentaEscanerController {

    private InventarioService inventarioService;
    private VentaService ventaService;
    private VentaStateService ventaStateService; // Servicio compartido

    @FXML private ListView<String> ticketListView;
    @FXML private Label totalLabel;
    @FXML private Button confirmarBtn;
    @FXML private Button limpiarTicketBtn;
    @FXML private TextField codigoInput;
    @FXML private TextField nombreSearchField;
    @FXML private ListView<Producto> searchResultsListView;

    @FXML
    public void initialize() {
        // Configurar cell factory para resultados de búsqueda
        searchResultsListView.setCellFactory(lv -> new ListCell<>() {
            @Override
            protected void updateItem(Producto item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                } else {
                    setText(String.format("%s (%s) - $%.2f",
                            item.getNombre(), item.getCodigo(), item.getPrecioVenta()));
                }
            }
        });

        // Listener para búsqueda por nombre
        nombreSearchField.textProperty().addListener((observable, oldValue, newValue) -> handleNombreSearchChanged(newValue));

        // Focus inicial
        codigoInput.requestFocus();
    }

    // Inyectar servicios desde MainApp
    public void setInventarioService(InventarioService inventarioService) {
        this.inventarioService = inventarioService;
    }
    public void setVentaService(VentaService ventaService) {
        this.ventaService = ventaService;
    }
    public void setVentaStateService(VentaStateService ventaStateService) {
        this.ventaStateService = ventaStateService;
        // Vincular UI con el estado compartido
        this.ventaStateService.setTicketListViewRef(ticketListView);
        this.ventaStateService.setTotalLabelRef(totalLabel);
        // Asegurar que la UI refleje el estado actual al inicio
        this.ventaStateService.actualizarUIVenta();
    }


    @FXML
    void handleCodigoEntered(ActionEvent event) {
        String codigo = codigoInput.getText().trim();
        if (!codigo.isEmpty()) {
            Producto producto = inventarioService.buscarProductoPorCodigo(codigo);
            if (producto != null) {
                agregarProductoAlTicket(producto);
            } else {
                AlertUtil.showAlert(Alert.AlertType.WARNING, "No Encontrado", "No se encontró producto con código: " + codigo);
                // Opcional: Mover foco a búsqueda por nombre
                // nombreSearchField.requestFocus();
            }
        }
        codigoInput.clear();
        codigoInput.requestFocus(); // Devolver foco al campo de código
    }


    private void handleNombreSearchChanged(String newValue) {
        String nombreFrag = newValue.trim();
        searchResultsListView.getItems().clear();
        if (nombreFrag.length() < 2) { // Mínimo 2 caracteres para buscar
            searchResultsListView.setVisible(false);
            searchResultsListView.setManaged(false);
            return;
        }

        List<Producto> encontrados = inventarioService.buscarProductosPorNombre(nombreFrag);
        if (encontrados.isEmpty()) {
            searchResultsListView.setVisible(false);
            searchResultsListView.setManaged(false);
        } else {
            searchResultsListView.setItems(FXCollections.observableArrayList(encontrados));
            searchResultsListView.setVisible(true);
            searchResultsListView.setManaged(true);
        }
    }

    @FXML
    void handleSearchResultSelected(MouseEvent event) {
        // Doble clic o clic simple pueden seleccionar
        if (event.getClickCount() >= 1) {
            seleccionarProductoDeLista();
        }
    }

    @FXML
    void handleSearchResultKeyPressed(KeyEvent event) {
        if (event.getCode() == KeyCode.ENTER) {
            seleccionarProductoDeLista();
        } else if (event.getCode() == KeyCode.ESCAPE) {
            ocultarListaResultados();
            codigoInput.requestFocus();
        }
    }


    private void seleccionarProductoDeLista() {
        Producto seleccionado = searchResultsListView.getSelectionModel().getSelectedItem();
        if (seleccionado != null) {
            agregarProductoAlTicket(seleccionado);
            ocultarListaResultados();
            nombreSearchField.clear();
            codigoInput.requestFocus();
        }
    }

    private void ocultarListaResultados() {
        searchResultsListView.getItems().clear();
        searchResultsListView.setVisible(false);
        searchResultsListView.setManaged(false);
    }


    @FXML
    void handleConfirmarVenta(ActionEvent event) {
        if (ventaStateService.getProductos().isEmpty()) {
            AlertUtil.showAlert(Alert.AlertType.WARNING, "Venta Vacía", "Agregue productos al ticket antes de confirmar.");
            return;
        }

        // Confirmación opcional
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION,
                "¿Desea confirmar y registrar la venta actual por un total de " + ventaStateService.getTotalText().replace("Total: ", "") + "?",
                ButtonType.YES, ButtonType.NO);
        confirm.setTitle("Confirmar Venta");
        confirm.setHeaderText(null);
        Optional<ButtonType> result = confirm.showAndWait();

        if (result.isPresent() && result.get() == ButtonType.YES) {
            boolean exito = ventaService.guardarVenta(ventaStateService.getProductos());
            if (exito) {
                ventaStateService.limpiarVenta(); // Limpia estado y actualiza UI
                AlertUtil.showAlert(Alert.AlertType.INFORMATION, "Venta Confirmada", "La venta ha sido registrada exitosamente.");
                codigoInput.requestFocus();
            } else {
                AlertUtil.showAlert(Alert.AlertType.ERROR, "Error al Guardar", "No se pudo registrar la venta en la base de datos. Revise la conexión y el stock.");
            }
        }
    }

    @FXML
    void handleLimpiarTicket(ActionEvent event) {
        if (!ventaStateService.getProductos().isEmpty()) {
            Alert confirm = new Alert(Alert.AlertType.CONFIRMATION,
                    "¿Está seguro de que desea limpiar todos los productos del ticket actual?",
                    ButtonType.YES, ButtonType.NO);
            confirm.setTitle("Limpiar Ticket");
            confirm.setHeaderText(null);
            Optional<ButtonType> result = confirm.showAndWait();
            if (result.isPresent() && result.get() == ButtonType.YES) {
                ventaStateService.limpiarVenta();
                ocultarListaResultados();
                nombreSearchField.clear();
                codigoInput.requestFocus();
            }
        } else {
            // Si ya está vacío, solo limpiar campos
            ocultarListaResultados();
            nombreSearchField.clear();
            codigoInput.requestFocus();
        }
    }


    // Método para añadir producto (valida stock)
    private void agregarProductoAlTicket(Producto producto) {
        if (producto == null) return;

        // Usar VentaStateService para añadir y actualizar UI
        // La validación de stock (si no es manual) se hace antes de llamar a agregarProducto
        boolean anadir = false;
        if (producto instanceof com.example.drugstore2.model.ProductoManual) {
            anadir = true; // Los manuales se añaden siempre
        } else if (producto.getStock() > 0) {
            anadir = true; // Los de inventario solo si hay stock
        } else {
            AlertUtil.showAlert(Alert.AlertType.WARNING, "Stock Cero",
                    "El producto '" + producto.getNombre() + "' (Código: " + producto.getCodigo() + ") no tiene stock disponible.");
        }

        if (anadir) {
            ventaStateService.agregarProducto(producto);
        }
    }
}