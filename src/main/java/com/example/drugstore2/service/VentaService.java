package com.example.drugstore2.service;

import com.example.drugstore2.model.DetalleVenta;
import com.example.drugstore2.model.Producto;
import com.example.drugstore2.model.ProductoManual;
import com.example.drugstore2.model.Venta;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;

import java.time.LocalDate;
import java.util.ArrayList;
import java.sql.*;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.HashMap; // Necesario para agrupar productos

public class VentaService {
    private InventarioService inventarioService;
    private Connection connection;
    private ObservableList<Venta> historialVentas; // Mantenemos la lista observable

    public VentaService(InventarioService inventarioService) {
        this.inventarioService = inventarioService;
        this.connection = inventarioService.getConnection();
        if (this.connection == null) {
            // Considerar lanzar una excepción o manejarlo mejor si la conexión es nula al inicio
            System.err.println("CRÍTICO: La conexión a la base de datos es NULA en VentaService.");
        }
        this.historialVentas = FXCollections.observableArrayList();
        // Cargamos el historial inicial desde la BD al crear el servicio
        cargarHistorialDesdeDB();
    }

    /**
     * Guarda una venta completa (con múltiples productos) en la base de datos
     * utilizando transacciones SQL. Actualiza el stock solo para productos del inventario.
     *
     * @param productos La lista de todos los productos (instancias individuales) en la venta.
     * @return true si la venta se registró exitosamente, false en caso contrario.
     */
    // Dentro de la clase VentaService

    public boolean guardarVenta(List<Producto> productos) {
        if (productos == null || productos.isEmpty()) {
            System.out.println("Intento de guardar venta vacía.");
            return false;
        }
        if (connection == null) {
            System.err.println("Error: No se puede guardar la venta, no hay conexión a la BD.");
            return false;
        }

        // --- 1. Agrupar productos y calcular total (Sin cambios) ---
        Map<String, DetalleVenta> detallesAgrupados = new HashMap<>();
        double totalVentaCalculado = 0.0;
        // ... (lógica de agrupación igual que antes) ...
        for (Producto p : productos) {
            String codigoKey = p.getCodigo();
            if (p instanceof ProductoManual) {
                // Usar código fijo 'MANUAL' + nombre específico para agrupar iguales manuales
                codigoKey = "MANUAL_" + p.getNombre() + "_" + p.getPrecioVenta();
            }
            DetalleVenta detalleExistente = detallesAgrupados.get(codigoKey);
            if (detalleExistente != null) {
                int nuevaCantidad = detalleExistente.getCantidad() + 1;
                detallesAgrupados.put(codigoKey, new DetalleVenta(p, nuevaCantidad));
            } else {
                detallesAgrupados.put(codigoKey, new DetalleVenta(p, 1));
            }
            totalVentaCalculado += p.getPrecioVenta();
        }


        LocalDateTime fechaHora = LocalDateTime.now();
        Timestamp fechaHoraSql = Timestamp.valueOf(fechaHora);

        PreparedStatement ventaStmt = null;
        PreparedStatement detalleStmt = null;
        ResultSet generatedKeys = null;

        try {
            // --- 2. Iniciar Transacción (Sin cambios) ---
            connection.setAutoCommit(false);

            // --- 3. Guardar Venta General (Sin cambios) ---
            String sqlVenta = "INSERT INTO ventas (fecha_hora, total) VALUES (?, ?)";
            ventaStmt = connection.prepareStatement(sqlVenta, Statement.RETURN_GENERATED_KEYS);
            ventaStmt.setTimestamp(1, fechaHoraSql);
            ventaStmt.setDouble(2, totalVentaCalculado);
            ventaStmt.executeUpdate();

            generatedKeys = ventaStmt.getGeneratedKeys();
            int ventaId;
            if (generatedKeys.next()) {
                ventaId = generatedKeys.getInt(1);
            } else {
                throw new SQLException("No se pudo obtener el ID de la venta generada.");
            }

            // --- 4. Guardar Detalles y Actualizar Stock ---
            //   **** MODIFICACIÓN AQUÍ: Añadir la nueva columna al INSERT ****
            String sqlDetalle = "INSERT INTO detalle_ventas (venta_id, producto_codigo, cantidad, subtotal, descripcion_manual) VALUES (?, ?, ?, ?, ?)";
            detalleStmt = connection.prepareStatement(sqlDetalle);

            for (DetalleVenta detalle : detallesAgrupados.values()) {
                Producto productoDetalle = detalle.getProducto();
                int cantidadVendida = detalle.getCantidad();
                double subtotalDetalle = detalle.getSubtotal();

                // i. Insertar detalle (parámetros base)
                detalleStmt.setInt(1, ventaId);
                // Usar el código real ('MANUAL' o el código del producto)
                detalleStmt.setString(2, productoDetalle.getCodigo());
                detalleStmt.setInt(3, cantidadVendida);
                detalleStmt.setDouble(4, subtotalDetalle);

                // ---> NUEVO: Establecer valor para descripcion_manual <---
                if (productoDetalle instanceof ProductoManual) {
                    // Si es manual, guarda el nombre específico introducido
                    detalleStmt.setString(5, productoDetalle.getNombre());
                } else {
                    // Si es producto normal, deja la descripción manual como NULL
                    detalleStmt.setNull(5, Types.VARCHAR);
                }
                // ---> FIN NUEVO <---

                detalleStmt.addBatch(); // Añadir a ejecución por lotes

                // ii. Actualizar Stock (SOLO si no es manual) - Sin cambios aquí
                if (!(productoDetalle instanceof ProductoManual)) {
                    boolean stockActualizado = inventarioService.actualizarStock(productoDetalle.getCodigo(), -cantidadVendida);
                    if (!stockActualizado) {
                        throw new SQLException("No se pudo actualizar el stock para el producto: " + productoDetalle.getCodigo() + ". Venta revertida.");
                    }
                }
            }
            // Ejecutar todas las inserciones de detalles
            detalleStmt.executeBatch();

            // --- 5. Confirmar Transacción (Sin cambios) ---
            connection.commit();
            System.out.println("✅ Venta registrada exitosamente en BD (ID: " + ventaId + ")");

            // --- 6. ACTUALIZAR HISTORIAL EN MEMORIA (Sin cambios) ---
            System.out.println("-> Actualizando lista de historial en memoria...");
            cargarHistorialDesdeDB();
            System.out.println("-> Lista de historial actualizada.");

            return true; // Éxito

        } catch (SQLException e) {
            // ---> MEJORADO: Log más detallado del error <---
            System.err.println("❌ Error SQL al guardar la venta: " + e.getMessage());
            e.printStackTrace(); // Imprime toda la traza del error
            System.err.println("SQLState: " + e.getSQLState());
            System.err.println("ErrorCode: " + e.getErrorCode());
            // ---> FIN MEJORADO <---
            try {
                if (connection != null) {
                    System.err.println("Intentando hacer rollback...");
                    connection.rollback();
                    System.err.println("Rollback completado.");
                }
            } catch (SQLException ex) {
                System.err.println("❌ Error CRÍTICO al intentar hacer rollback: " + ex.getMessage());
                ex.printStackTrace();
            }
            return false; // Falla

        } finally {
            // --- Cerrar recursos y restaurar auto-commit (Sin cambios) ---
            try { if (generatedKeys != null) generatedKeys.close(); } catch (SQLException e) { e.printStackTrace(); }
            try { if (ventaStmt != null) ventaStmt.close(); } catch (SQLException e) { e.printStackTrace(); }
            try { if (detalleStmt != null) detalleStmt.close(); } catch (SQLException e) { e.printStackTrace(); }
            try {
                if (connection != null) {
                    connection.setAutoCommit(true);
                }
            } catch (SQLException e) {
                System.err.println("Error al restaurar auto-commit: " + e.getMessage());
                e.printStackTrace();
            }
        }
    } // Fin del método guardarVenta // Fin del método guardarVenta


    // Método para obtener el historial (sigue igual, lee de la lista)
    public ObservableList<Venta> getHistorialVentas() {
        // Opcionalmente, podríamos llamar a cargarHistorialDesdeDB() aquí cada vez
        // para asegurar que siempre esté actualizado, a costa de rendimiento.
        // Por ahora, lo dejamos como está (carga al inicio).
        return historialVentas;
    }

    // Cargar (o recargar) historial desde la Base de Datos
    // Este método es AHORA la única fuente de datos para el historial
    // Dentro de la clase VentaService

    private void cargarHistorialDesdeDB() {
        historialVentas.clear();

        // **** MODIFICACIÓN AQUÍ: Cambiar cómo se obtiene el nombre del producto ****
        String sql = "SELECT v.fecha_hora, dv.cantidad, dv.subtotal, dv.producto_codigo, " +
                // Seleccionar el nombre: Prioridad a descripcion_manual, luego nombre del producto, y si no, el código
                "COALESCE(dv.descripcion_manual, p.nombre, dv.producto_codigo) AS nombre_a_mostrar, " +
                // Obtener precio unitario (sin cambios)
                "COALESCE(p.precio_venta, dv.subtotal / dv.cantidad) AS precio_unitario " +
                "FROM ventas v " +
                "JOIN detalle_ventas dv ON v.id = dv.venta_id " +
                // LEFT JOIN con productos sigue siendo útil para obtener nombre/precio de productos de inventario
                "LEFT JOIN productos p ON dv.producto_codigo = p.codigo " +
                "ORDER BY v.fecha_hora DESC";

        if (connection == null) {
            System.err.println("Error: No se puede cargar historial, no hay conexión a la BD.");
            return;
        }

        try (Statement stmt = connection.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {

            while (rs.next()) {
                Timestamp ts = rs.getTimestamp("fecha_hora");
                LocalDateTime fechaHora = (ts != null) ? ts.toLocalDateTime() : null;

                // **** USA EL NUEVO ALIAS para obtener el nombre correcto ****
                String nombreMostrado = rs.getString("nombre_a_mostrar");
                String codigo = rs.getString("producto_codigo"); // El código se mantiene
                double precio = rs.getDouble("precio_unitario");
                int cantidad = rs.getInt("cantidad");

                // Crear objeto Producto temporal para el historial.
                // Usamos el nombreMostrado que ya tiene la lógica de COALESCE.
                Producto productoHistorial = new Producto(codigo, nombreMostrado, precio, 0.0, 0, null, 0);

                DetalleVenta detalle = new DetalleVenta(productoHistorial, cantidad);

                if (fechaHora != null) {
                    historialVentas.add(new Venta(fechaHora, detalle));
                } else {
                    System.err.println("Advertencia: Se encontró una venta con fecha/hora nula en la BD.");
                }
            }
            System.out.println("Historial de ventas cargado/recargado desde la BD. Items: " + historialVentas.size());
        } catch (SQLException e) {
            System.err.println("❌ Error SQL al cargar historial desde BD: " + e.getMessage());
            e.printStackTrace();
        } catch (Exception e) {
            System.err.println("❌ Error inesperado al cargar historial desde BD: " + e.getMessage());
            e.printStackTrace();
        }
    } // Fin del método cargarHistorialDesdeDB



    public List<Venta> getVentasDetalladasPorRango(LocalDate inicio, LocalDate fin) {
        List<Venta> ventasDetalladas = new ArrayList<>();
        // La consulta es similar a cargarHistorialDesdeDB pero con filtro de fecha
        String sql = "SELECT v.fecha_hora, dv.cantidad, dv.subtotal, dv.producto_codigo, " +
                "COALESCE(p.nombre, dv.producto_codigo) AS nombre_producto, " +
                "COALESCE(p.precio_venta, dv.subtotal / dv.cantidad) AS precio_unitario " +
                "FROM ventas v " +
                "JOIN detalle_ventas dv ON v.id = dv.venta_id " +
                "LEFT JOIN productos p ON dv.producto_codigo = p.codigo " +
                "WHERE DATE(v.fecha_hora) BETWEEN ? AND ? " + // Filtro por rango
                "ORDER BY v.fecha_hora ASC"; // Ordenar por fecha para el reporte

        if (connection == null || inicio == null || fin == null || fin.isBefore(inicio)) {
            System.err.println("⚠️ Parámetros inválidos para getVentasDetalladasPorRango.");
            return ventasDetalladas;
        }

        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setDate(1, Date.valueOf(inicio));
            stmt.setDate(2, Date.valueOf(fin));
            ResultSet rs = stmt.executeQuery();

            while (rs.next()) {
                Timestamp ts = rs.getTimestamp("fecha_hora");
                LocalDateTime fechaHora = (ts != null) ? ts.toLocalDateTime() : null;
                String nombre = rs.getString("nombre_producto");
                String codigo = rs.getString("producto_codigo");
                double precio = rs.getDouble("precio_unitario");
                int cantidad = rs.getInt("cantidad");

                Producto productoHistorial;
                if (codigo.startsWith("MANUAL-")) {
                    productoHistorial = new ProductoManual(nombre, precio);
                } else {
                    // Usamos el constructor más completo con 0 para costo/stock/minimo ya que no son relevantes aquí
                    productoHistorial = new Producto(codigo, nombre, precio, 0.0, 0, null, 0);
                }

                DetalleVenta detalle = new DetalleVenta(productoHistorial, cantidad);

                if (fechaHora != null) {
                    ventasDetalladas.add(new Venta(fechaHora, detalle));
                } else {
                    System.err.println("Advertencia: Se encontró una venta con fecha/hora nula en la BD durante la exportación.");
                }
            }
        } catch (SQLException e) {
            System.err.println("❌ Error SQL al obtener ventas detalladas por rango: " + e.getMessage());
            e.printStackTrace();
        } catch (Exception e) {
            System.err.println("❌ Error inesperado al obtener ventas detalladas por rango: " + e.getMessage());
            e.printStackTrace();
        }
        return ventasDetalladas;
    }









}