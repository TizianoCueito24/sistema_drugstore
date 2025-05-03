package com.example.drugstore2.controller;

import com.example.drugstore2.model.Producto;
import com.example.drugstore2.model.ProductoManual; // Necesario para la comprobación instanceof
import com.example.drugstore2.service.InventarioService;
import com.example.drugstore2.service.VentaService;
import com.example.drugstore2.service.VentaStateService; // Importante para estado compartido
import com.example.drugstore2.util.AlertUtil;

import javafx.collections.FXCollections; // Necesario para lista de búsqueda
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseEvent;
// Quitado Ikonli si ya no se usan iconos en botones
// import org.kordamp.ikonli.javafx.FontIcon;
// import org.kordamp.ikonli.fontawesome5.FontAwesomeSolid;

import java.util.List;
import java.util.Optional;

/**
 * Controlador para la pestaña de Venta con Escáner.
 * Interactúa con InventarioService, VentaService y VentaStateService.
 * Muestra el ticket de venta compartido gestionado por VentaStateService.
 */
public class VentaEscanerController {

    // --- Servicios ---
    private InventarioService inventarioService;
    private VentaService ventaService;
    private VentaStateService ventaStateService; // Servicio de estado compartido

    // --- Componentes FXML ---
    @FXML private ListView<String> ticketListView; // Muestra el ticket COMPARTIDO
    @FXML private Label totalLabel; // Muestra el total COMPARTIDO
    @FXML private Button confirmarBtn;
    @FXML private Button limpiarTicketBtn;
    @FXML private TextField codigoInput;
    @FXML private TextField nombreSearchField;
    @FXML private ListView<Producto> searchResultsListView; // Para resultados de búsqueda

    /**
     * Inicializa el controlador después de que se cargan los elementos FXML.
     * Configura listeners y la apariencia inicial.
     */
    @FXML
    public void initialize() {
        // Configurar cómo se muestran los productos en la lista de resultados de búsqueda
        searchResultsListView.setCellFactory(lv -> new ListCell<>() {
            @Override
            protected void updateItem(Producto item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                } else {
                    // Formato: Nombre (Codigo) - $Precio
                    setText(String.format("%s (%s) - $%.2f",
                            item.getNombre(), item.getCodigo(), item.getPrecioVenta()));
                }
            }
        });

        // Listener para actualizar la búsqueda por nombre mientras se escribe
        nombreSearchField.textProperty().addListener((observable, oldValue, newValue) -> handleNombreSearchChanged(newValue));

        // La vinculación de ticketListView y totalLabel se hace en setVentaStateService

        // Configura (o quita) iconos de botones
        setupIcons();
        // Pone el foco inicial en el campo de código
        codigoInput.requestFocus();
    }

    // --- Inyección de Servicios (llamados desde MainApp) ---

    public void setInventarioService(InventarioService inventarioService) {
        this.inventarioService = inventarioService;
    }

    public void setVentaService(VentaService ventaService) {
        this.ventaService = ventaService;
    }

    /**
     * Inyecta la instancia compartida de VentaStateService.
     * Este es el paso CRUCIAL para conectar la UI de esta pestaña con el estado compartido.
     * Vincula el ListView y Label locales a las propiedades del servicio y
     * pasa las referencias de estos controles al servicio.
     * @param ventaStateService La instancia compartida del servicio de estado de venta.
     */
    public void setVentaStateService(VentaStateService ventaStateService) {
        this.ventaStateService = ventaStateService;
        if (this.ventaStateService != null) {
            // Vincula el ListView local a la lista observable del servicio
            this.ticketListView.setItems(this.ventaStateService.getTicketItems());

            // Pasa las referencias de los controles UI al servicio
            this.ventaStateService.setTicketListViewRef(this.ticketListView);
            this.ventaStateService.setTotalLabelRef(this.totalLabel);

            // Actualiza la UI por si ya había items al cargar la pestaña
            this.ventaStateService.actualizarUIVenta();
            System.out.println("VentaEscanerController: VentaStateService inyectado y UI vinculada.");

        } else {
            // Error si el servicio no llega
            System.err.println("ERROR CRÍTICO: VentaStateService no fue inyectado en VentaEscanerController.");
            // Mostrar mensaje de error en la UI si es posible
            if (this.ticketListView != null) {
                this.ticketListView.setPlaceholder(new Label("Error: Servicio de estado no disponible."));
            }
            if (this.totalLabel != null){
                this.totalLabel.setText("Error");
            }
        }
    }

    /**
     * Configura los gráficos de los botones. Actualmente los quita (graphic = null).
     */
    private void setupIcons() {
        // Se quitaron los iconos según solicitud previa
        if (confirmarBtn != null) confirmarBtn.setGraphic(null);
        if (limpiarTicketBtn != null) limpiarTicketBtn.setGraphic(null);
    }

    // --- Manejadores de eventos FXML ---

    /**
     * Se ejecuta cuando se presiona Enter en el campo de código.
     * Busca el producto y lo añade al ticket compartido si lo encuentra.
     * @param event El evento de acción.
     */
    @FXML
    void handleCodigoEntered(ActionEvent event) {
        String codigo = codigoInput.getText().trim();
        if (codigo.isEmpty()) {
            return; // No hacer nada si está vacío
        }
        if (inventarioService == null) {
            AlertUtil.showAlert(Alert.AlertType.ERROR, "Error Interno", "Servicio de inventario no disponible.");
            return;
        }

        Producto producto = inventarioService.buscarProductoPorCodigo(codigo);
        if (producto != null) {
            // Usa el método helper que añade al servicio compartido
            handleAgregarProducto(producto);
        } else {
            AlertUtil.showAlert(Alert.AlertType.WARNING, "No Encontrado", "No se encontró producto con código: " + codigo);
        }

        // Limpiar campo y devolver foco para siguiente escaneo/entrada
        codigoInput.clear();
        codigoInput.requestFocus();
    }

    /**
     * Se ejecuta cuando cambia el texto en el campo de búsqueda por nombre.
     * Actualiza la lista de resultados de búsqueda.
     * @param newValue El nuevo texto en el campo de búsqueda.
     */
    private void handleNombreSearchChanged(String newValue) {
        String nombreFrag = newValue.trim();
        // Limpiar resultados anteriores
        searchResultsListView.getItems().clear();

        // No buscar si el fragmento es muy corto o el servicio no está listo
        if (nombreFrag.length() < 2 || inventarioService == null) {
            ocultarListaResultados();
            return;
        }

        // Realizar búsqueda
        List<Producto> encontrados = inventarioService.buscarProductosPorNombre(nombreFrag);

        // Mostrar resultados o ocultar la lista
        if (encontrados.isEmpty()) {
            ocultarListaResultados();
        } else {
            searchResultsListView.setItems(FXCollections.observableArrayList(encontrados));
            searchResultsListView.setVisible(true);
            searchResultsListView.setManaged(true); // Asegura que ocupe espacio
        }
    }

    /**
     * Se ejecuta al hacer clic en un item de la lista de resultados de búsqueda.
     * @param event El evento del ratón.
     */
    @FXML
    void handleSearchResultSelected(MouseEvent event) {
        // Seleccionar con un solo clic
        if (event.getClickCount() >= 1) {
            seleccionarProductoDeLista();
        }
    }

    /**
     * Se ejecuta al presionar teclas (Enter/Escape) sobre la lista de resultados.
     * @param event El evento de teclado.
     */
    @FXML
    void handleSearchResultKeyPressed(KeyEvent event) {
        if (event.getCode() == KeyCode.ENTER) {
            seleccionarProductoDeLista();
            event.consume(); // Evitar que Enter haga otra acción
        } else if (event.getCode() == KeyCode.ESCAPE) {
            ocultarListaResultados();
            codigoInput.requestFocus(); // Devolver foco al código
            event.consume(); // Evitar que Escape cierre algo más
        }
    }

    /**
     * Añade el producto seleccionado de la lista de búsqueda al ticket compartido.
     */
    private void seleccionarProductoDeLista() {
        Producto seleccionado = searchResultsListView.getSelectionModel().getSelectedItem();
        if (seleccionado != null) {
            // Usa el método helper que añade al servicio compartido
            handleAgregarProducto(seleccionado);
            ocultarListaResultados(); // Ocultar lista después de seleccionar
            nombreSearchField.clear(); // Limpiar campo de búsqueda
            codigoInput.requestFocus(); // Devolver foco al código
        }
    }

    /**
     * Oculta la lista de resultados de búsqueda y limpia sus items.
     */
    private void ocultarListaResultados() {
        searchResultsListView.getItems().clear();
        searchResultsListView.setVisible(false);
        searchResultsListView.setManaged(false); // Libera el espacio que ocupaba
    }

    /**
     * Confirma y guarda la venta actual obteniendo los productos del servicio compartido.
     * Limpia el estado compartido si la venta es exitosa.
     * @param event El evento de acción del botón.
     */
    @FXML
    void handleConfirmarVenta(ActionEvent event) {
        // Verificar servicios necesarios
        if (ventaStateService == null) {
            AlertUtil.showAlert(Alert.AlertType.ERROR, "Error Interno", "Servicio de estado de venta no disponible.");
            return;
        }
        if (ventaService == null) {
            AlertUtil.showAlert(Alert.AlertType.ERROR, "Error Interno", "Servicio de ventas no disponible.");
            return;
        }

        // Obtener productos DEL SERVICIO COMPARTIDO
        List<Producto> productosParaVender = ventaStateService.getProductos();

        // Verificar si hay productos en el ticket
        if (productosParaVender.isEmpty()) {
            AlertUtil.showAlert(Alert.AlertType.WARNING, "Venta Vacía", "Agregue productos al ticket antes de confirmar.");
            return;
        }

        // --- Guardado directo de la venta ---
        boolean exito = ventaService.guardarVenta(productosParaVender);

        if (exito) {
            // Limpiar el estado COMPARTIDO a través del servicio
            ventaStateService.limpiarVenta();
            // La UI (ticketListView, totalLabel) se actualiza automáticamente desde el VentaStateService

            // Mostrar mensaje de éxito
            AlertUtil.showAlert(Alert.AlertType.INFORMATION, "Venta Confirmada", "La venta ha sido registrada exitosamente.");
            codigoInput.requestFocus(); // Preparar para siguiente venta
        } else {
            // Mostrar mensaje de error
            AlertUtil.showAlert(Alert.AlertType.ERROR, "Error al Guardar", "No se pudo registrar la venta. Revise conexión y stock.");
        }
    }

    /**
     * Limpia todos los productos del ticket actual (estado compartido).
     * Pide confirmación antes de limpiar.
     * @param event El evento de acción del botón.
     */
    @FXML
    void handleLimpiarTicket(ActionEvent event) {
        if (ventaStateService == null) return; // Salir si el servicio no está listo

        // Verificar si hay productos en el estado COMPARTIDO
        if (!ventaStateService.getProductos().isEmpty()) {
            // Pedir confirmación
            Alert confirm = new Alert(Alert.AlertType.CONFIRMATION,
                    "¿Está seguro de que desea limpiar todos los productos del ticket actual?",
                    ButtonType.YES, ButtonType.NO);
            confirm.setTitle("Limpiar Ticket");
            confirm.setHeaderText(null); // No usar cabecera
            Optional<ButtonType> result = confirm.showAndWait();

            if (result.isPresent() && result.get() == ButtonType.YES) {
                // Limpiar el estado COMPARTIDO a través del servicio
                ventaStateService.limpiarVenta();
                // La UI se actualiza automáticamente desde el VentaStateService

                // Limpiar también la UI de búsqueda y devolver foco
                ocultarListaResultados();
                nombreSearchField.clear();
                codigoInput.requestFocus();
            }
        } else {
            // Si el ticket ya está vacío, solo limpiar búsqueda y poner foco
            ocultarListaResultados();
            nombreSearchField.clear();
            codigoInput.requestFocus();
        }
    }

    // --- Métodos de lógica interna ---

    /**
     * Método centralizado para añadir un producto al ticket (estado compartido).
     * Verifica el stock antes de añadir usando InventarioService.
     * Llama a VentaStateService para realizar la adición y actualizar la UI.
     * @param producto El producto a añadir.
     */
    private void handleAgregarProducto(Producto producto) {
        // Verificar servicios y producto
        if (producto == null || ventaStateService == null || inventarioService == null) {
            System.err.println("Error: No se puede agregar producto, falta producto o servicios.");
            AlertUtil.showAlert(Alert.AlertType.ERROR, "Error Interno", "No se pudo agregar el producto por un problema de servicio.");
            return;
        }

        boolean anadir = false;
        // Si es manual (no debería ocurrir aquí), añadir directamente
        if (producto instanceof ProductoManual) {
            System.err.println("Advertencia: Intentando añadir ProductoManual desde VentaEscanerController.");
            anadir = true;
        } else {
            // Para productos normales, verificar stock actual en BD
            Producto productoActualizado = inventarioService.buscarProductoPorCodigo(producto.getCodigo());
            if (productoActualizado != null && productoActualizado.getStock() > 0) {
                anadir = true; // Hay stock
            } else if (productoActualizado == null){
                // El producto ya no existe o está inactivo
                AlertUtil.showAlert(Alert.AlertType.ERROR, "Error Producto",
                        "El producto '" + producto.getNombre() + "' (Cod: "+producto.getCodigo()+") ya no se encuentra en el inventario activo.");
                anadir = false;
            } else { // Stock es 0 o negativo
                // Asegúrate que esta línea tenga la comilla después de WARNING
                AlertUtil.showAlert(Alert.AlertType.WARNING, "Stock Cero", // <-- CORREGIDO
                        "El producto '" + producto.getNombre() + "' no tiene stock disponible.");
                anadir = false;
            }
        }

        // Añadir al estado compartido si la validación fue exitosa
        if (anadir) {
            ventaStateService.agregarProducto(producto);
            // La UI se actualiza automáticamente por VentaStateService
            System.out.println("DEBUG (VentaEscanerController): Producto '" + producto.getNombre() + "' añadido vía VentaStateService.");
        } else {
            System.out.println("DEBUG (VentaEscanerController): Producto '" + producto.getNombre() + "' NO añadido.");
            // Devolver foco si no se añadió para facilitar corrección o nueva entrada
            codigoInput.requestFocus();
        }
    }

    // El método actualizarTicketEscanerUI() ya no es necesario aquí.

} // Fin de la clase VentaEscanerControllerla clase VentaEscanerController