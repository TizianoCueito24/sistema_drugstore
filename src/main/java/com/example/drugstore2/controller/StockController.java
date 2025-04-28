package com.example.drugstore2.controller;

import com.example.drugstore2.model.Producto;
import com.example.drugstore2.service.InventarioService;
import com.example.drugstore2.util.AlertUtil;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.GridPane;
import javafx.scene.paint.Color;
import java.text.NumberFormat;
import java.text.ParseException;
import java.util.Locale;
import java.util.Optional;

public class StockController {

    private InventarioService inventarioService;
    private ObservableList<Producto> productosList;
    private final NumberFormat numberFormat = NumberFormat.getNumberInstance(Locale.getDefault()); // Para parsear doubles con coma o punto

    @FXML private TableView<Producto> stockTableView;
    @FXML private TableColumn<Producto, String> codigoCol;
    @FXML private TableColumn<Producto, String> nombreCol;
    @FXML private TableColumn<Producto, Double> precioVentaCol;
    @FXML private TableColumn<Producto, Double> precioCostoCol;
    @FXML private TableColumn<Producto, Integer> stockCol;
    @FXML private TableColumn<Producto, Integer> stockMinimoCol;
    @FXML private TableColumn<Producto, String> categoriaCol;

    @FXML private TextField stockCodigoField;
    @FXML private TextField stockNombreField;
    @FXML private TextField stockPrecioField;
    @FXML private TextField stockCostoField;
    @FXML private TextField stockStockField;
    @FXML private TextField stockMinimoField;
    @FXML private ComboBox<String> stockCategoriaComboBox;

    @FXML private Button agregarButton;
    @FXML private Button modificarButton;
    @FXML private Button desactivarButton;
    @FXML private Button limpiarButton;

    @FXML private GridPane ajusteGrid;
    @FXML private TextField ajusteCantidadField;
    @FXML private TextField ajusteMotivoField;
    @FXML private Button ajustarStockButton;

    @FXML
    public void initialize() {
        // El servicio se inyecta desde MainApp
        configurarTabla();
        configurarValidadores(); // Configurar validadores numéricos
        limpiarFormularioStock(); // Estado inicial
        // Los datos se cargan cuando se inyecta el servicio o al seleccionar la pestaña
    }

    public void setInventarioService(InventarioService service) {
        this.inventarioService = service;
        cargarDatosIniciales(); // Cargar datos cuando el servicio está listo
    }

    private void configurarTabla() {
        codigoCol.setCellValueFactory(new PropertyValueFactory<>("codigo"));
        nombreCol.setCellValueFactory(new PropertyValueFactory<>("nombre"));
        categoriaCol.setCellValueFactory(new PropertyValueFactory<>("categoriaNombre"));
        stockMinimoCol.setCellValueFactory(new PropertyValueFactory<>("stockMinimo"));
        stockCol.setCellValueFactory(new PropertyValueFactory<>("stock"));

        // --- Columnas con formato ---
        precioVentaCol.setCellValueFactory(new PropertyValueFactory<>("precioVenta"));
        precioVentaCol.setCellFactory(tc -> new TableCell<Producto, Double>() {
            @Override protected void updateItem(Double price, boolean empty) {
                super.updateItem(price, empty);
                setText(empty || price == null ? null : String.format("$%.2f", price));
            }
        });
        precioVentaCol.setStyle("-fx-alignment: CENTER-RIGHT;");

        precioCostoCol.setCellValueFactory(new PropertyValueFactory<>("precioCosto"));
        precioCostoCol.setCellFactory(tc -> new TableCell<Producto, Double>() {
            @Override protected void updateItem(Double cost, boolean empty) {
                super.updateItem(cost, empty);
                setText(empty || cost == null ? null : String.format("$%.2f", cost));
            }
        });
        precioCostoCol.setStyle("-fx-alignment: CENTER-RIGHT;");

        // --- Columna Stock con color ---
        stockCol.setCellFactory(column -> new TableCell<Producto, Integer>() {
            @Override protected void updateItem(Integer stockActual, boolean empty) {
                super.updateItem(stockActual, empty);
                // Reset styles
                this.setTextFill(Color.BLACK);
                this.setStyle("");
                this.getStyleClass().remove("low-stock"); // Si tuvieras CSS para esto

                if (empty || stockActual == null) {
                    setText(null);
                } else {
                    setText(stockActual.toString());
                    try {
                        // Asegúrate que el índice es válido antes de obtener el item
                        if (getIndex() >= 0 && getIndex() < getTableView().getItems().size()) {
                            Producto producto = getTableView().getItems().get(getIndex());
                            if (producto != null && stockActual <= producto.getStockMinimo()) {
                                this.setTextFill(Color.RED); // Color rojo
                                this.setStyle("-fx-font-weight: bold;"); // Negrita
                                // this.getStyleClass().add("low-stock"); // O usar una clase CSS
                            }
                        }
                    } catch (Exception e) {
                        System.err.println("Error temporal en CellFactory de Stock: " + e.getMessage());
                        // Ignorar si el índice no es válido temporalmente durante actualizaciones
                    }
                }
            }
        });
        stockCol.setStyle("-fx-alignment: CENTER;");


        // --- Listener para selección ---
        stockTableView.getSelectionModel().selectedItemProperty().addListener((obs, oldSelection, newSelection) -> {
            ajusteGrid.setDisable(newSelection == null); // Habilitar/deshabilitar ajuste
            if (newSelection != null) {
                mostrarDetallesProducto(newSelection);
            } else {
                limpiarFormularioStock();
            }
        });

        // --- Placeholder ---
        stockTableView.setPlaceholder(new Label("Cargando productos..."));
    }

    private void configurarValidadores() {
        // Permitir solo números (enteros o decimales con punto o coma)
        String numericPattern = "\\d*([\\.,]\\d*)?";
        stockPrecioField.textProperty().addListener((obs, ov, nv) -> { if (!nv.matches(numericPattern)) stockPrecioField.setText(ov); });
        stockCostoField.textProperty().addListener((obs, ov, nv) -> { if (!nv.matches(numericPattern)) stockCostoField.setText(ov); });

        // Permitir solo números enteros
        String integerPattern = "\\d*";
        stockStockField.textProperty().addListener((obs, ov, nv) -> { if (!nv.matches(integerPattern)) stockStockField.setText(ov); });
        stockMinimoField.textProperty().addListener((obs, ov, nv) -> { if (!nv.matches(integerPattern)) stockMinimoField.setText(ov); });

        // Permitir números enteros, opcionalmente negativos para ajuste
        String ajustePattern = "-?\\d*";
        ajusteCantidadField.textProperty().addListener((obs, ov, nv) -> { if (!nv.matches(ajustePattern)) ajusteCantidadField.setText(ov); });
    }


    private void cargarDatosIniciales() {
        if (inventarioService == null) {
            System.err.println("StockController: InventarioService no está inicializado al cargar datos.");
            stockTableView.setPlaceholder(new Label("Error al cargar servicio de inventario."));
            return;
        }
        try {
            productosList = FXCollections.observableArrayList(inventarioService.getProductos().values());
            stockTableView.setItems(productosList);
            stockCategoriaComboBox.setItems(FXCollections.observableArrayList(inventarioService.obtenerCategorias()));

            if (productosList.isEmpty()) {
                stockTableView.setPlaceholder(new Label("No hay productos activos en el inventario."));
            }
        } catch (Exception e) {
            System.err.println("Error al cargar datos iniciales de stock: " + e.getMessage());
            e.printStackTrace();
            AlertUtil.showAlert(Alert.AlertType.ERROR, "Error de Datos", "No se pudieron cargar los productos o categorías.");
            stockTableView.setPlaceholder(new Label("Error al cargar datos."));
        }
    }

    @FXML
    void handleAgregarProducto(ActionEvent event) {
        if (!validarCampos(true)) return; // true para validar stock inicial

        String codigo = stockCodigoField.getText().trim();
        String nombre = stockNombreField.getText().trim();
        String categoriaNombre = stockCategoriaComboBox.getValue();

        try {
            double precioVenta = parseDouble(stockPrecioField.getText());
            double precioCosto = parseDouble(stockCostoField.getText());
            int stock = Integer.parseInt(stockStockField.getText().trim());
            int minimo = Integer.parseInt(stockMinimoField.getText().trim());

            if (precioVenta <= 0 || precioCosto < 0 || stock < 0 || minimo < 0) {
                AlertUtil.showAlert(Alert.AlertType.WARNING, "Valores Inválidos", "Precios deben ser > 0 (costo >= 0), Stock y Mínimo deben ser >= 0.");
                return;
            }

            // Validar existencia de código
            if (inventarioService.buscarProductoPorCodigo(codigo) != null) {
                AlertUtil.showAlert(Alert.AlertType.ERROR, "Código Duplicado", "Ya existe un producto activo con el código: " + codigo);
                stockCodigoField.requestFocus();
                return;
            }

            int categoriaId = inventarioService.obtenerCategoriaId(categoriaNombre);
            if (categoriaId == -1) {
                AlertUtil.showAlert(Alert.AlertType.ERROR, "Error Categoría", "La categoría seleccionada no es válida.");
                return;
            }

            inventarioService.agregarProducto(codigo, nombre, precioVenta, precioCosto, stock, categoriaId, minimo);
            refrescarTablaProductos();
            limpiarFormularioStock();
            AlertUtil.showAlert(Alert.AlertType.INFORMATION, "Éxito", "Producto '" + nombre + "' agregado correctamente.");

        } catch (NumberFormatException | ParseException e) {
            AlertUtil.showAlert(Alert.AlertType.ERROR, "Formato Numérico Incorrecto", "Verifique los valores de Precio Venta, Precio Costo, Stock Inicial y Stock Mínimo.");
        } catch (Exception e) {
            AlertUtil.showAlert(Alert.AlertType.ERROR, "Error Inesperado", "Ocurrió un error al agregar el producto:\n" + e.getMessage());
            e.printStackTrace();
        }
    }

    @FXML
    void handleModificarProducto(ActionEvent event) {
        Producto seleccionado = stockTableView.getSelectionModel().getSelectedItem();
        if (seleccionado == null) {
            AlertUtil.showAlert(Alert.AlertType.WARNING, "Selección Requerida", "Seleccione un producto de la tabla para modificar.");
            return;
        }
        if (!validarCampos(false)) return; // false para no validar stock inicial

        String nombre = stockNombreField.getText().trim();
        String categoriaNombre = stockCategoriaComboBox.getValue();

        try {
            double precioVenta = parseDouble(stockPrecioField.getText());
            double precioCosto = parseDouble(stockCostoField.getText());
            int minimo = Integer.parseInt(stockMinimoField.getText().trim());

            if (precioVenta <= 0 || precioCosto < 0 || minimo < 0) {
                AlertUtil.showAlert(Alert.AlertType.WARNING, "Valores Inválidos", "Precio Venta debe ser > 0, Precio Costo >= 0, Stock Mínimo >= 0.");
                return;
            }

            int categoriaId = inventarioService.obtenerCategoriaId(categoriaNombre);
            if (categoriaId == -1) {
                AlertUtil.showAlert(Alert.AlertType.ERROR, "Error Categoría", "La categoría seleccionada no es válida.");
                return;
            }

            // Confirmación
            Alert confirmacion = new Alert(Alert.AlertType.CONFIRMATION,
                    "¿Está seguro de que desea guardar los cambios realizados al producto '" + seleccionado.getNombre() + "'?",
                    ButtonType.OK, ButtonType.CANCEL);
            confirmacion.setTitle("Confirmar Modificación");
            confirmacion.setHeaderText("Modificar Producto");
            Optional<ButtonType> resultado = confirmacion.showAndWait();

            if (resultado.isPresent() && resultado.get() == ButtonType.OK) {
                boolean exito = inventarioService.modificarProducto(seleccionado.getCodigo(), nombre, precioVenta, precioCosto, categoriaId, minimo);
                if (exito) {
                    refrescarTablaProductos(); // Refresca y re-selecciona si es posible
                    limpiarFormularioStock(); // Limpia y restablece botones
                    AlertUtil.showAlert(Alert.AlertType.INFORMATION, "Éxito", "Producto modificado correctamente.");
                } else {
                    AlertUtil.showAlert(Alert.AlertType.ERROR, "Error", "No se pudo modificar el producto. Verifique si aún existe y está activo.");
                }
            }

        } catch (NumberFormatException | ParseException e) {
            AlertUtil.showAlert(Alert.AlertType.ERROR, "Formato Numérico Incorrecto", "Verifique los valores de Precio Venta, Precio Costo y Stock Mínimo.");
        } catch (Exception e) {
            AlertUtil.showAlert(Alert.AlertType.ERROR, "Error Inesperado", "Ocurrió un error al modificar el producto:\n" + e.getMessage());
            e.printStackTrace();
        }
    }

    @FXML
    void handleDesactivarProducto(ActionEvent event) {
        Producto seleccionado = stockTableView.getSelectionModel().getSelectedItem();
        if (seleccionado == null) {
            AlertUtil.showAlert(Alert.AlertType.WARNING, "Selección Requerida", "Seleccione un producto de la tabla para desactivar.");
            return;
        }

        Alert confirmacion = new Alert(Alert.AlertType.CONFIRMATION);
        confirmacion.setTitle("Confirmar Desactivación");
        confirmacion.setHeaderText("¿DESACTIVAR el producto?");
        confirmacion.setContentText("Producto: " + seleccionado.getNombre() + "\nCódigo: " + seleccionado.getCodigo() + "\n\nEl producto se marcará como inactivo y no aparecerá en búsquedas ni ventas. ¿Continuar?");
        Optional<ButtonType> resultado = confirmacion.showAndWait();

        if (resultado.isPresent() && resultado.get() == ButtonType.OK) {
            boolean exito = inventarioService.desactivarProducto(seleccionado.getCodigo());
            if (exito) {
                refrescarTablaProductos(); // Quitará el producto de la lista
                limpiarFormularioStock();
                AlertUtil.showAlert(Alert.AlertType.INFORMATION, "Éxito", "Producto desactivado.");
            } else {
                AlertUtil.showAlert(Alert.AlertType.ERROR, "Error", "No se pudo desactivar el producto.");
            }
        }
    }

    @FXML
    void handleLimpiarFormulario(ActionEvent event) {
        stockTableView.getSelectionModel().clearSelection();
        // limpiarFormularioStock() es llamado por el listener de selección al deseleccionar
    }

    @FXML
    void handleAjustarStock(ActionEvent event) {
        Producto seleccionado = stockTableView.getSelectionModel().getSelectedItem();
        if (seleccionado == null) {
            AlertUtil.showAlert(Alert.AlertType.WARNING, "Selección Requerida", "Seleccione un producto de la tabla para ajustar su stock.");
            return;
        }

        String cantidadStr = ajusteCantidadField.getText().trim();
        String motivo = ajusteMotivoField.getText().trim();

        if (cantidadStr.isEmpty()) {
            AlertUtil.showAlert(Alert.AlertType.WARNING, "Cantidad Requerida", "Ingrese la cantidad a ajustar (puede ser negativa).");
            ajusteCantidadField.requestFocus();
            return;
        }

        try {
            int cantidadAjuste = Integer.parseInt(cantidadStr);
            if (cantidadAjuste == 0) {
                AlertUtil.showAlert(Alert.AlertType.INFORMATION, "Sin Cambios", "La cantidad de ajuste es cero. No se realizaron cambios.");
                return;
            }

            // Confirmación para ajustes negativos
            if (cantidadAjuste < 0) {
                Alert confirmacion = new Alert(Alert.AlertType.CONFIRMATION);
                confirmacion.setTitle("Confirmar Ajuste Negativo");
                confirmacion.setHeaderText("Vas a RESTAR " + Math.abs(cantidadAjuste) + " unidades de stock.");
                confirmacion.setContentText("Producto: " + seleccionado.getNombre() + "\nMotivo: " + (motivo.isEmpty() ? "No especificado" : motivo) + "\n\n¿Continuar?");
                Optional<ButtonType> resultado = confirmacion.showAndWait();
                if (resultado.isEmpty() || resultado.get() != ButtonType.OK) {
                    return; // Cancelado por el usuario
                }
            }

            boolean exito = inventarioService.ajustarStockManual(seleccionado.getCodigo(), cantidadAjuste, motivo);
            if (exito) {
                refrescarTablaProductos(); // Refresca la tabla (y re-selecciona)
                // Mantener selección y limpiar campos de ajuste
                ajusteCantidadField.clear();
                ajusteMotivoField.clear();
                Producto actualizado = inventarioService.buscarProductoPorCodigo(seleccionado.getCodigo());
                if (actualizado != null) {
                    stockTableView.getSelectionModel().select(actualizado); // Re-seleccionar por si cambió el objeto
                } else {
                    stockTableView.getSelectionModel().clearSelection(); // Limpiar si ya no existe
                }

                AlertUtil.showAlert(Alert.AlertType.INFORMATION, "Éxito", "Stock ajustado correctamente para '" + seleccionado.getNombre() + "'.");
            } else {
                AlertUtil.showAlert(Alert.AlertType.ERROR, "Error de Ajuste", "No se pudo ajustar el stock. Verifique si el producto existe o si el ajuste resultaría en stock negativo.");
            }

        } catch (NumberFormatException e) {
            AlertUtil.showAlert(Alert.AlertType.ERROR, "Cantidad Inválida", "La cantidad de ajuste debe ser un número entero.");
            ajusteCantidadField.requestFocus();
        } catch (Exception e) {
            AlertUtil.showAlert(Alert.AlertType.ERROR, "Error Inesperado", "Ocurrió un error al ajustar el stock:\n" + e.getMessage());
            e.printStackTrace();
        }
    }


    // --- Métodos de Ayuda ---

    private boolean validarCampos(boolean validarStockInicial) {
        String codigo = stockCodigoField.getText().trim();
        String nombre = stockNombreField.getText().trim();
        String precioVentaStr = stockPrecioField.getText().trim();
        String precioCostoStr = stockCostoField.getText().trim();
        String stockStr = stockStockField.getText().trim();
        String minimoStr = stockMinimoField.getText().trim();
        String categoriaNombre = stockCategoriaComboBox.getValue();

        StringBuilder errores = new StringBuilder();
        if (codigo.isEmpty()) errores.append("- Código es requerido.\n");
        if (nombre.isEmpty()) errores.append("- Nombre es requerido.\n");
        if (precioVentaStr.isEmpty()) errores.append("- Precio Venta es requerido.\n");
        if (precioCostoStr.isEmpty()) errores.append("- Precio Costo es requerido.\n");
        if (validarStockInicial && stockStr.isEmpty()) errores.append("- Stock Inicial es requerido.\n");
        if (minimoStr.isEmpty()) errores.append("- Stock Mínimo es requerido.\n");
        if (categoriaNombre == null || categoriaNombre.isEmpty()) errores.append("- Categoría es requerida.\n");

        if (!errores.isEmpty()) {
            AlertUtil.showAlert(Alert.AlertType.WARNING, "Campos Requeridos", errores.toString());
            return false;
        }
        return true;
    }


    private void mostrarDetallesProducto(Producto producto) {
        stockCodigoField.setText(producto.getCodigo());
        stockCodigoField.setDisable(true); // No se puede editar el código
        stockNombreField.setText(producto.getNombre());
        stockPrecioField.setText(String.format("%.2f", producto.getPrecioVenta()).replace(",", ".")); // Formato consistente
        stockCostoField.setText(String.format("%.2f", producto.getPrecioCosto()).replace(",", "."));
        stockMinimoField.setText(String.valueOf(producto.getStockMinimo()));
        stockCategoriaComboBox.setValue(producto.getCategoriaNombre());

        stockStockField.clear(); // El stock actual se ve en la tabla
        stockStockField.setDisable(true); // No se modifica directamente aquí

        // Habilitar/Deshabilitar botones
        agregarButton.setDisable(true);
        modificarButton.setDisable(false);
        desactivarButton.setDisable(false);

        // Limpiar campos de ajuste
        ajusteCantidadField.clear();
        ajusteMotivoField.clear();
    }

    private void limpiarFormularioStock() {
        stockCodigoField.clear(); stockCodigoField.setDisable(false);
        stockNombreField.clear();
        stockPrecioField.clear();
        stockCostoField.clear();
        stockStockField.clear(); stockStockField.setDisable(false); // Habilitado para agregar
        stockMinimoField.clear();
        stockCategoriaComboBox.setValue(null);

        modificarButton.setDisable(true);
        desactivarButton.setDisable(true);
        agregarButton.setDisable(false);

        // Limpiar y deshabilitar ajuste
        ajusteGrid.setDisable(true);
        ajusteCantidadField.clear();
        ajusteMotivoField.clear();

        stockCodigoField.requestFocus(); // Foco en el primer campo
    }

    // Método público para refrescar desde MainApp si es necesario
    public void refrescarTablaProductos() {
        System.out.println("StockController: Refrescando tabla...");
        Producto seleccionadoAntes = stockTableView.getSelectionModel().getSelectedItem();
        cargarDatosIniciales(); // Recarga datos del servicio
        stockTableView.refresh(); // Refresca visualmente

        // Restaurar selección si aún existe
        if (seleccionadoAntes != null && productosList != null) {
            final String codigoSeleccionado = seleccionadoAntes.getCodigo();
            Optional<Producto> reSeleccionar = productosList.stream()
                    .filter(p -> p.getCodigo().equals(codigoSeleccionado))
                    .findFirst();
            reSeleccionar.ifPresent(p -> {
                stockTableView.getSelectionModel().select(p);
                stockTableView.scrollTo(p); // Asegura que sea visible
                System.out.println("StockController: Selección restaurada para " + codigoSeleccionado);
            });
            if (reSeleccionar.isEmpty()) {
                System.out.println("StockController: Producto seleccionado previamente ("+codigoSeleccionado+") ya no existe.");
                limpiarFormularioStock(); // Limpiar form si el producto ya no está
            }
        } else if (seleccionadoAntes == null){
            // No había selección, asegurar que el form esté limpio
            limpiarFormularioStock();
        }
    }

    // Helper para parsear doubles aceptando coma o punto
    private double parseDouble(String value) throws ParseException {
        return numberFormat.parse(value.trim()).doubleValue();
    }
}
