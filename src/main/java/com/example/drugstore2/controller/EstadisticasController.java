package com.example.drugstore2.controller;
import com.example.drugstore2.model.CategoriaVenta;
import com.example.drugstore2.model.Producto;
import com.example.drugstore2.model.Venta;
import com.example.drugstore2.service.EstadisticasService;
import com.example.drugstore2.service.InventarioService;
import com.example.drugstore2.service.VentaService;
import com.example.drugstore2.util.AlertUtil;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.paint.Color;
import javafx.stage.FileChooser;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class EstadisticasController {

    private EstadisticasService estadisticasService;
    private VentaService ventaService; // Necesario para exportar ventas detalladas
    private InventarioService inventarioService; // Necesario para exportar stock

    @FXML private DatePicker datePickerDesde;
    @FXML private DatePicker datePickerHasta;
    @FXML private Button refrescarBtn;

    @FXML private Label ventasPeriodoLabel;
    @FXML private Label gananciaPeriodoLabel;

    @FXML private TableView<CategoriaVenta> categoriaTableView; // <- Sin MainApp.
    @FXML private TableColumn<CategoriaVenta, String> nombreCatCol; // <- Sin MainApp.
    @FXML private TableColumn<CategoriaVenta, Double> totalCatCol; // <- Sin MainApp.
    private ObservableList<CategoriaVenta> categoriasVentasList; // <- Sin MainApp.

    @FXML private Label stockValorizadoLabel;
    @FXML private Label productoTopLabel;
    @FXML private Label ingresosTotalesLabel;
    @FXML private Label gananciaBrutaTotalLabel;

    @FXML private Button exportarVentasBtn;
    @FXML private Button exportarStockBtn;

    @FXML
    public void initialize() {
        configurarTablaCategorias();
        LocalDate hoy = LocalDate.now();
        datePickerDesde.setValue(hoy.withDayOfMonth(1));
        datePickerHasta.setValue(hoy.withDayOfMonth(hoy.lengthOfMonth()));

    }
    public void setEstadisticasService(EstadisticasService estadisticasService) {
        this.estadisticasService = estadisticasService;
    }
    public void setVentaService(VentaService ventaService) {
        this.ventaService = ventaService;
    }
    public void setInventarioService(InventarioService inventarioService) {
        this.inventarioService = inventarioService;
        // Una vez que todos los servicios están listos, actualizar
        if (this.estadisticasService != null) {
            actualizarEstadisticas();
        }
    }

    private void configurarTablaCategorias() {
        nombreCatCol.setCellValueFactory(new PropertyValueFactory<>("nombre"));
        totalCatCol.setCellValueFactory(new PropertyValueFactory<>("total"));

        // Formato para la columna total
        totalCatCol.setCellFactory(tc -> new TableCell<CategoriaVenta, Double>() {
            @Override protected void updateItem(Double price, boolean empty) {
                super.updateItem(price, empty);
                setText(empty || price == null ? null : String.format("$%.2f", price));
            }
        });

        categoriasVentasList = FXCollections.observableArrayList();
        categoriaTableView.setItems(categoriasVentasList);
        categoriaTableView.setPlaceholder(new Label("Seleccione un período para ver las ventas por categoría."));
    }


    @FXML
    void handleActualizarEstadisticas(ActionEvent event) {
        actualizarEstadisticas();
    }

    // Método público para ser llamado desde MainApp al seleccionar la pestaña
    public void actualizarEstadisticas() {
        if (estadisticasService == null) {
            System.err.println("EstadisticasController: Service no disponible.");
            AlertUtil.showAlert(Alert.AlertType.ERROR, "Error", "El servicio de estadísticas no está disponible.");
            return;
        }

        LocalDate fechaDesde = datePickerDesde.getValue();
        LocalDate fechaHasta = datePickerHasta.getValue();

        if (fechaDesde == null || fechaHasta == null) {
            AlertUtil.showAlert(Alert.AlertType.WARNING, "Fechas Requeridas", "Seleccione las fechas 'Desde' y 'Hasta'.");
            // Opcional: usar mes actual por defecto si son null
            // LocalDate hoy = LocalDate.now();
            // fechaDesde = (fechaDesde == null) ? hoy.withDayOfMonth(1) : fechaDesde;
            // fechaHasta = (fechaHasta == null) ? hoy.withDayOfMonth(hoy.lengthOfMonth()) : fechaHasta;
            // datePickerDesde.setValue(fechaDesde);
            // datePickerHasta.setValue(fechaHasta);
            return;
        }

        if (fechaHasta.isBefore(fechaDesde)) {
            AlertUtil.showAlert(Alert.AlertType.WARNING, "Fechas Inválidas", "La fecha 'Hasta' no puede ser anterior a la fecha 'Desde'.");
            return;
        }

        System.out.println("Actualizando estadísticas para el período: " + fechaDesde + " a " + fechaHasta);

        // --- Calcular y Actualizar Estadísticas por Período ---
        double vPeriodo = estadisticasService.getVentasPorRangoFechas(fechaDesde, fechaHasta);
        double gPeriodo = estadisticasService.getGananciaBrutaPorRangoFechas(fechaDesde, fechaHasta);
        ventasPeriodoLabel.setText(String.format("$%.2f", vPeriodo));
        gananciaPeriodoLabel.setText(String.format("$%.2f", gPeriodo));
        gananciaPeriodoLabel.setTextFill(gPeriodo >= 0 ? Color.DARKGREEN : Color.RED); // Color dinámico

        // --- Calcular y Actualizar Ventas por Categoría ---
        Map<String, Double> ventasCatMap = estadisticasService.getVentasPorCategoria(fechaDesde, fechaHasta);
        categoriasVentasList.clear();
        for (Map.Entry<String, Double> entry : ventasCatMap.entrySet()) {
            // Asegurarse que CategoriaVenta sea accesible o moverla a model
            categoriasVentasList.add(new CategoriaVenta(entry.getKey(), entry.getValue()));
        }
        if (categoriasVentasList.isEmpty()) {
            categoriaTableView.setPlaceholder(new Label("No hay ventas por categoría en este período."));
        }

        // --- Calcular y Actualizar Indicadores Generales/Históricos ---
        Optional<Map.Entry<String, Integer>> pTO = estadisticasService.getProductoMasVendido();
        double iT = estadisticasService.getIngresosTotales();
        double gBTotal = estadisticasService.getGananciaBrutaTotal();
        double stockVal = estadisticasService.getStockValorizado();

        // Actualizar Labels Indicadores Generales
        if (pTO.isPresent()) {
            Map.Entry<String, Integer> pT = pTO.get();
            productoTopLabel.setText(String.format("%s (%d u.)", pT.getKey(), pT.getValue()));
        } else {
            productoTopLabel.setText("N/A");
        }
        ingresosTotalesLabel.setText(String.format("$%.2f", iT));
        gananciaBrutaTotalLabel.setText(String.format("$%.2f", gBTotal));
        gananciaBrutaTotalLabel.setTextFill(gBTotal >= 0 ? Color.DARKGREEN : Color.DARKRED); // Color dinámico
        stockValorizadoLabel.setText(String.format("$%.2f", stockVal));

        System.out.println("Estadísticas actualizadas completamente.");
    }

    @FXML
    void handleExportarVentasCSV(ActionEvent event) {
        LocalDate fechaDesde = datePickerDesde.getValue();
        LocalDate fechaHasta = datePickerHasta.getValue();

        if (fechaDesde == null || fechaHasta == null || fechaHasta.isBefore(fechaDesde)) {
            AlertUtil.showAlert(Alert.AlertType.WARNING, "Período Inválido", "Seleccione un período de fechas válido para exportar las ventas.");
            return;
        }
        if (ventaService == null) {
            AlertUtil.showAlert(Alert.AlertType.ERROR, "Error Servicio", "El servicio de ventas no está disponible para la exportación.");
            return;
        }

        // Obtener los datos detallados
        List<Venta> ventasExportar = ventaService.getVentasDetalladasPorRango(fechaDesde, fechaHasta);

        if (ventasExportar.isEmpty()) {
            AlertUtil.showAlert(Alert.AlertType.INFORMATION, "Sin Datos", "No hay ventas registradas en el período seleccionado para exportar.");
            return;
        }

        FileChooser fileChooser = createFileChooser("Guardar Reporte de Ventas", "Ventas_" + fechaDesde + "_a_" + fechaHasta + ".csv");
        File file = fileChooser.showSaveDialog(exportarVentasBtn.getScene().getWindow()); // Obtener stage

        if (file != null) {
            String[] HEADERS = { "FechaHora", "CodProducto", "NombreProducto", "Cantidad", "PrecioUnitario", "Subtotal" };
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

            try (FileWriter writer = new FileWriter(file);
                 CSVPrinter csvPrinter = new CSVPrinter(writer, CSVFormat.DEFAULT.withHeader(HEADERS))) {

                for (Venta v : ventasExportar) {
                    // ... (lógica de escritura CSV igual a la original en MainApp) ...
                    csvPrinter.printRecord(
                            v.getFechaHora().format(formatter),
                            v.getDetalle().getProducto().getCodigo(),
                            v.getDetalle().getProducto().getNombre(),
                            v.getDetalle().getCantidad(),
                            v.getDetalle().getProducto().getPrecioVenta(),
                            v.getTotal()
                    );
                }
                csvPrinter.flush();
                AlertUtil.showAlert(Alert.AlertType.INFORMATION, "Exportación Completa", "Reporte de ventas guardado en:\n" + file.getAbsolutePath());

            } catch (IOException e) {
                handleExportError(e);
            }
        }
    }

    @FXML
    void handleExportarStockCSV(ActionEvent event) {
        if (inventarioService == null) {
            AlertUtil.showAlert(Alert.AlertType.ERROR, "Error Servicio", "El servicio de inventario no está disponible para la exportación.");
            return;
        }
        // Obtener la lista actual de productos activos
        Collection<Producto> productosExportar = inventarioService.getProductos().values();

        if (productosExportar.isEmpty()) {
            AlertUtil.showAlert(Alert.AlertType.INFORMATION, "Sin Datos", "No hay productos activos en el inventario para exportar.");
            return;
        }

        FileChooser fileChooser = createFileChooser("Guardar Reporte de Stock Valorizado", "Stock_Valorizado_" + LocalDate.now() + ".csv");
        File file = fileChooser.showSaveDialog(exportarStockBtn.getScene().getWindow());

        if (file != null) {
            String[] HEADERS = { "Codigo", "Nombre", "Categoria", "Stock", "PrecioCosto", "StockMinimo", "ValorTotalProducto" };

            try (FileWriter writer = new FileWriter(file);
                 CSVPrinter csvPrinter = new CSVPrinter(writer, CSVFormat.DEFAULT.withHeader(HEADERS))) {

                double valorTotalInventario = 0;
                for (Producto p : productosExportar) {
                    // ... (lógica de escritura CSV igual a la original en MainApp) ...
                    double valorProducto = p.getStock() * p.getPrecioCosto();
                    valorTotalInventario += valorProducto;
                    csvPrinter.printRecord(
                            p.getCodigo(),
                            p.getNombre(),
                            p.getCategoriaNombre() != null ? p.getCategoriaNombre() : "",
                            p.getStock(),
                            p.getPrecioCosto(),
                            p.getStockMinimo(),
                            valorProducto
                    );
                }
                // Opcional: Fila total
                csvPrinter.printRecord("", "", "", "", "", "TOTAL INVENTARIO:", valorTotalInventario);

                csvPrinter.flush();
                AlertUtil.showAlert(Alert.AlertType.INFORMATION, "Exportación Completa", "Reporte de stock valorizado guardado en:\n" + file.getAbsolutePath());

            } catch (IOException e) {
                handleExportError(e);
            }
        }
    }


    // --- Métodos de Ayuda ---
    private FileChooser createFileChooser(String title, String initialFileName) {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle(title);
        fileChooser.setInitialFileName(initialFileName);
        fileChooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("Archivos CSV (*.csv)", "*.csv"));
        // Opcional: Establecer directorio inicial
        // fileChooser.setInitialDirectory(new File(System.getProperty("user.home")));
        return fileChooser;
    }

    private void handleExportError(IOException e) {
        System.err.println("Error al exportar a CSV: " + e.getMessage());
        e.printStackTrace();
        AlertUtil.showAlert(Alert.AlertType.ERROR, "Error de Exportación", "No se pudo guardar el archivo CSV.\nVerifique los permisos o si el archivo está en uso.\n" + e.getMessage());
    }

}