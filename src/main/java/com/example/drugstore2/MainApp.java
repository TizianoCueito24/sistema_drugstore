package com.example.drugstore2;

import com.example.drugstore2.controller.*;
import com.example.drugstore2.service.*;
import com.example.drugstore2.util.AlertUtil;
import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.StackPane;
import javafx.stage.Stage;

import java.io.IOException;
import java.io.InputStream;
import java.sql.SQLException;

public class MainApp extends Application {

    // Servicios (serán inicializados y pasados a los controladores)
    private InventarioService inventarioService;
    private VentaService ventaService;
    private EstadisticasService estadisticasService;
    private CajaService cajaService;
    private VentaStateService ventaStateService; // Para estado compartido de venta

    public static void main(String[] args) {
        launch(args);
    }

    @Override
    public void start(Stage primaryStage) {
        // --- Inicialización de servicios ---
        if (!initializeServices()) {
            return; // Salir si la inicialización falla
        }

        // Crear el TabPane
        TabPane tabPane = new TabPane();
        tabPane.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);

        // --- Cargar Pestañas desde FXML ---
        try {
            loadTab(tabPane, "🛒 Venta con Escáner", "/com/example/venta-escaner-view.fxml", VentaEscanerController.class);
            loadTab(tabPane, "⌨️ Venta Manual", "/com/example/venta-manual-view.fxml", VentaManualController.class);
            loadTab(tabPane, "📦 Gestión de Stock", "/com/example/stock-view.fxml", StockController.class);
            loadTab(tabPane, "📜 Historial de Ventas", "/com/example/historial-ventas-view.fxml", HistorialVentasController.class);
            loadTab(tabPane, "📊 Estadísticas", "/com/example/estadisticas-view.fxml", EstadisticasController.class);
            loadTab(tabPane, "↩️ Devoluciones", "/com/example/devoluciones-view.fxml .fxml", DevolucionesController.class);
            loadTab(tabPane, "💰 Gestión de Caja", "/com/example/caja-view.fxml", CajaController.class);

        } catch (IOException e) {
            e.printStackTrace();
            AlertUtil.showAlert(Alert.AlertType.ERROR, "Error Crítico de Carga",
                    "No se pudo cargar una de las pestañas (FXML).\n" + e.getMessage());
            return;
        }

        // --- Configuración de Escena y Stage ---
        BorderPane mainLayout = createLayoutWithBranding(tabPane);
        Scene scene = new Scene(mainLayout, 1050, 750); // Ajusta tamaño si es necesario

        // --- Cargar CSS ---
        try {
            String estilosCSS = getClass().getResource("/com/example/estilos.css").toExternalForm();
            String estadistCSS = getClass().getResource("/com/example/estadist.css").toExternalForm();
            if (estilosCSS != null) scene.getStylesheets().add(estilosCSS);
            if (estadistCSS != null) scene.getStylesheets().add(estadistCSS);
            System.out.println("CSS cargados.");
        } catch (NullPointerException e) {
            System.err.println("Advertencia: No se pudo cargar algún archivo CSS. Verifica las rutas en /resources. " + e.getMessage());
        } catch (Exception e) {
            System.err.println("Error inesperado al cargar CSS: " + e.getMessage());
        }


        primaryStage.setTitle("Drugstore System");
        primaryStage.setScene(scene);
        primaryStage.setOnCloseRequest(e -> closeServices());
        primaryStage.setMaximized(true);
        primaryStage.show();
    }

    // Método para inicializar los servicios
    private boolean initializeServices() {
        try {
            // Asegurarse que el driver esté cargado
            Class.forName("com.mysql.cj.jdbc.Driver");

            // Inicializar InventarioService (que maneja la conexión)
            inventarioService = new InventarioService();
            if (inventarioService.getConnection() == null) {
                throw new SQLException("La conexión a la base de datos falló en InventarioService.");
            }

            // Inicializar otros servicios pasando la conexión o el servicio necesario
            ventaService = new VentaService(inventarioService);
            estadisticasService = new EstadisticasService(inventarioService.getConnection());
            cajaService = new CajaService(inventarioService.getConnection());
            ventaStateService = new VentaStateService(); // Servicio de estado UI

            System.out.println("Servicios inicializados correctamente.");
            return true;

        } catch (ClassNotFoundException e) {
            e.printStackTrace();
            AlertUtil.showAlert(Alert.AlertType.ERROR, "Error Crítico", "Driver MySQL no encontrado. Asegúrate que la dependencia mysql-connector-j esté en pom.xml.");
            return false;
        } catch (SQLException e) {
            e.printStackTrace();
            AlertUtil.showAlert(Alert.AlertType.ERROR, "Error Crítico de Conexión", "No se pudo conectar a la base de datos.\nVerifica la URL, usuario, contraseña y que el servidor MySQL esté corriendo.\nDetalle: " + e.getMessage());
            return false;
        } catch (Exception e) {
            e.printStackTrace();
            AlertUtil.showAlert(Alert.AlertType.ERROR, "Error Crítico Inesperado", "Ocurrió un error inesperado durante la inicialización.\nDetalle: " + e.getMessage());
            return false;
        }
    }

    // Método genérico para cargar una pestaña desde FXML
    private <T> void loadTab(TabPane tabPane, String tabTitle, String fxmlPath, Class<T> controllerClass) throws IOException {
        FXMLLoader loader = new FXMLLoader(getClass().getResource(fxmlPath));
        Node content = loader.load(); // Carga FXML y crea la vista
        T controller = loader.getController(); // Obtiene la instancia del controlador

        // --- Inyectar dependencias en el controlador ---
        // (Esto podría mejorarse con un framework de inyección)
        injectServices(controller);

        Tab tab = new Tab(tabTitle, content);
        tabPane.getTabs().add(tab);

        // Configurar acciones al seleccionar la pestaña (si es necesario)
        // Ejemplo: Refrescar datos en la pestaña de Stock
        if (controller instanceof StockController stockCtrl) {
            tab.setOnSelectionChanged(event -> {
                if (tab.isSelected()) {
                    stockCtrl.refrescarTablaProductos();
                }
            });
        }
        // Ejemplo: Actualizar estado de caja
        else if (controller instanceof CajaController cajaCtrl) {
            tab.setOnSelectionChanged(event -> {
                if (tab.isSelected()) {
                    cajaCtrl.actualizarEstadoCajaUI();
                }
            });
        }
        // Ejemplo: Actualizar estadísticas
        else if (controller instanceof EstadisticasController estCtrl) {
            tab.setOnSelectionChanged(event -> {
                if (tab.isSelected()) {
                    estCtrl.actualizarEstadisticas();
                }
            });
        }
        // Ejemplo: Recargar historial (si se implementa recarga en el controlador)
        else if (controller instanceof HistorialVentasController histCtrl) {
            tab.setOnSelectionChanged(event -> {
                if (tab.isSelected()) {
                    // histCtrl.recargarHistorial(); // Si se añade este método
                }
            });
        }
    }

    // Método para inyectar servicios en los controladores (simplificado)
    private void injectServices(Object controller) {
        if (controller instanceof StockController c) {
            c.setInventarioService(inventarioService);
        } else if (controller instanceof VentaEscanerController c) {
            c.setInventarioService(inventarioService);
            c.setVentaService(ventaService);
            c.setVentaStateService(ventaStateService);
        } else if (controller instanceof VentaManualController c) {
            // VentaManual no necesita acceso directo a inventario/venta service
            c.setVentaStateService(ventaStateService);
        } else if (controller instanceof HistorialVentasController c) {
            c.setVentaService(ventaService);
        } else if (controller instanceof EstadisticasController c) {
            c.setEstadisticasService(estadisticasService);
            c.setVentaService(ventaService); // Para exportar ventas
            c.setInventarioService(inventarioService); // Para exportar stock
        } else if (controller instanceof DevolucionesController c) {
            c.setInventarioService(inventarioService);
        } else if (controller instanceof CajaController c) {
            c.setCajaService(cajaService);
        }
        else if (controller instanceof DevolucionesController c) {
            c.setInventarioService(inventarioService); // <-- Añade esta línea
        }

    }


    // Crear layout principal con logo/marca de agua (similar al original)
    private BorderPane createLayoutWithBranding(TabPane tabPane) {
        BorderPane borderPane = new BorderPane();
        borderPane.setCenter(tabPane);
        Image logoImage = loadImage("/com/example/marca_agua.png");

        if (logoImage != null) {
            try {
                // Logo superior
                ImageView logoImageView = new ImageView(logoImage);
                logoImageView.setFitHeight(50);
                logoImageView.setFitWidth(50);
                logoImageView.setPreserveRatio(true);
                StackPane topPane = new StackPane(logoImageView);
                topPane.setAlignment(Pos.TOP_RIGHT);
                topPane.setPadding(new Insets(10));
                borderPane.setTop(topPane);

                // Marca de agua inferior
                ImageView marcaAguaImageView = new ImageView(logoImage);
                marcaAguaImageView.setFitHeight(150);
                marcaAguaImageView.setFitWidth(150);
                marcaAguaImageView.setPreserveRatio(true);
                marcaAguaImageView.setOpacity(0.15);
                StackPane bottomPane = new StackPane(marcaAguaImageView);
                bottomPane.setAlignment(Pos.BOTTOM_RIGHT);
                bottomPane.setPadding(new Insets(15));
                bottomPane.setMouseTransparent(true); // Para no interferir con clics
                borderPane.setBottom(bottomPane);

            } catch (Exception e) {
                System.err.println("Error al crear/añadir ImageView para la marca: " + e.getMessage());
                e.printStackTrace();
                borderPane.setTop(null);
                borderPane.setBottom(null);
            }
        } else {
            System.out.println("No se encontró la imagen 'marca_agua.png', no se añadirán logos.");
        }
        return borderPane;
    }

    // Helper para cargar imagen de forma segura
    private Image loadImage(String path) {
        Image image = null;
        try (InputStream stream = getClass().getResourceAsStream(path)) {
            if (stream != null) {
                image = new Image(stream);
                if (image.isError()) {
                    System.err.println("Error al cargar imagen: " + path);
                    if (image.getException() != null) {
                        image.getException().printStackTrace();
                    }
                    return null;
                }
            } else {
                System.err.println("Recurso de imagen no encontrado: " + path);
                return null;
            }
        } catch (IOException e) {
            System.err.println("IOException al cargar imagen: " + path + " - " + e.getMessage());
            return null;
        } catch (Exception e) {
            System.err.println("Error inesperado al cargar imagen: " + path + " - " + e.getMessage());
            e.printStackTrace();
            return null;
        }
        return image;
    }

    // Método para cerrar recursos, como la conexión a BD
    private void closeServices() {
        System.out.println("Cerrando aplicación y servicios...");
        if (inventarioService != null && inventarioService.getConnection() != null) {
            try {
                inventarioService.getConnection().close();
                System.out.println("Conexión a BD cerrada.");
            } catch (SQLException ex) {
                System.err.println("Error al cerrar conexión BD: " + ex.getMessage());
            }
        }
        // Aquí podrías añadir lógica para cerrar otros recursos si fuera necesario
    }


    public class CategoriaVenta {
    }
}
