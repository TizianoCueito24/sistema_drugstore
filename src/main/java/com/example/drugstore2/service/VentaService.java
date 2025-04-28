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
    public boolean guardarVenta(List<Producto> productos) {
        if (productos == null || productos.isEmpty()) {
            System.out.println("Intento de guardar venta vacía.");
            return false; // No hay nada que guardar
        }
        if (connection == null) {
            System.err.println("Error: No se puede guardar la venta, no hay conexión a la BD.");
            return false;
        }

        // --- 1. Agrupar productos y calcular total ---
        Map<String, DetalleVenta> detallesAgrupados = new HashMap<>();
        double totalVentaCalculado = 0.0;

        for (Producto p : productos) {
            String codigoKey = p.getCodigo(); // Usar código como clave para agrupar
            // Si ya existe, incrementa cantidad; si no, crea nuevo detalle
            DetalleVenta detalleExistente = detallesAgrupados.get(codigoKey);
            if (detalleExistente != null) {
                // Crea un nuevo objeto DetalleVenta con cantidad incrementada
                // (DetalleVenta es inmutable si no tiene setters para cantidad)
                int nuevaCantidad = detalleExistente.getCantidad() + 1;
                detallesAgrupados.put(codigoKey, new DetalleVenta(p, nuevaCantidad));
            } else {
                detallesAgrupados.put(codigoKey, new DetalleVenta(p, 1));
            }
            totalVentaCalculado += p.getPrecioVenta(); // Sumar al total general
        }

        LocalDateTime fechaHora = LocalDateTime.now();
        Timestamp fechaHoraSql = Timestamp.valueOf(fechaHora); // Convertir a Timestamp para SQL

        PreparedStatement ventaStmt = null;
        PreparedStatement detalleStmt = null;
        ResultSet generatedKeys = null;

        try {
            // --- 2. Iniciar Transacción ---
            connection.setAutoCommit(false);

            // --- 3. Guardar Venta General ---
            String sqlVenta = "INSERT INTO ventas (fecha_hora, total) VALUES (?, ?)";
            ventaStmt = connection.prepareStatement(sqlVenta, Statement.RETURN_GENERATED_KEYS);
            ventaStmt.setTimestamp(1, fechaHoraSql);
            ventaStmt.setDouble(2, totalVentaCalculado);
            ventaStmt.executeUpdate();

            // Obtener el ID generado para la venta
            generatedKeys = ventaStmt.getGeneratedKeys();
            int ventaId;
            if (generatedKeys.next()) {
                ventaId = generatedKeys.getInt(1);
            } else {
                throw new SQLException("No se pudo obtener el ID de la venta generada.");
            }

            // --- 4. Guardar Detalles y Actualizar Stock ---
            String sqlDetalle = "INSERT INTO detalle_ventas (venta_id, producto_codigo, cantidad, subtotal) VALUES (?, ?, ?, ?)";
            detalleStmt = connection.prepareStatement(sqlDetalle);

            for (DetalleVenta detalle : detallesAgrupados.values()) {
                Producto productoDetalle = detalle.getProducto();
                int cantidadVendida = detalle.getCantidad();
                double subtotalDetalle = detalle.getSubtotal(); // Ya calculado por DetalleVenta

                // i. Insertar detalle
                detalleStmt.setInt(1, ventaId);
                detalleStmt.setString(2, productoDetalle.getCodigo()); // Guarda el código (real o 'MANUAL-xxx')
                detalleStmt.setInt(3, cantidadVendida);
                detalleStmt.setDouble(4, subtotalDetalle);
                detalleStmt.addBatch(); // Agrega la inserción al batch

                // ii. Actualizar Stock (SOLO si no es manual)
                if (!(productoDetalle instanceof ProductoManual)) {
                    // Llamamos a actualizar stock con cantidad negativa
                    boolean stockActualizado = inventarioService.actualizarStock(productoDetalle.getCodigo(), -cantidadVendida);
                    if (!stockActualizado) {
                        // Si actualizarStock devolviera false por algún motivo (ej: stock insuficiente si lo validara allí)
                        throw new SQLException("No se pudo actualizar el stock para el producto: " + productoDetalle.getCodigo());
                        // O podríamos añadir validación de stock aquí antes de insertar detalles si es necesario
                    }
                }
            }
            // Ejecutar todas las inserciones de detalles en batch
            detalleStmt.executeBatch();

            // --- 5. Confirmar Transacción ---
            connection.commit();
            System.out.println("✅ Venta registrada exitosamente en BD (ID: " + ventaId + ")");

            // --- 6. Limpiar historial en memoria y recargar ---
            // Ya no añadimos manualmente a historialVentas.
            // La tabla de historial se actualizará la próxima vez que se carguen los datos.
            // Podríamos forzar la recarga si la pestaña de historial está visible,
            // pero por simplicidad, dejamos que se recargue al seleccionarla o al reiniciar.
            // Opcional: Forzar recarga inmediata (puede ser menos eficiente)
            // cargarHistorialDesdeDB();

            return true; // Éxito

        } catch (SQLException e) {
            System.err.println("❌ Error SQL al guardar la venta: " + e.getMessage());
            e.printStackTrace();
            // --- 5. Revertir Transacción en caso de error ---
            try {
                if (connection != null) {
                    System.err.println("Intentando hacer rollback de la transacción...");
                    connection.rollback();
                    System.err.println("Rollback completado.");
                }
            } catch (SQLException ex) {
                System.err.println("❌ Error CRÍTICO al intentar hacer rollback: " + ex.getMessage());
                ex.printStackTrace();
            }
            return false; // Falla

        } finally {
            // Cerrar recursos y restaurar auto-commit
            try { if (generatedKeys != null) generatedKeys.close(); } catch (SQLException e) { e.printStackTrace(); }
            try { if (ventaStmt != null) ventaStmt.close(); } catch (SQLException e) { e.printStackTrace(); }
            try { if (detalleStmt != null) detalleStmt.close(); } catch (SQLException e) { e.printStackTrace(); }
            try {
                if (connection != null) {
                    connection.setAutoCommit(true);
                }
            } catch (SQLException e) {
                e.printStackTrace();
            }
        }
    }

    // Método para obtener el historial (sigue igual, lee de la lista)
    public ObservableList<Venta> getHistorialVentas() {
        // Opcionalmente, podríamos llamar a cargarHistorialDesdeDB() aquí cada vez
        // para asegurar que siempre esté actualizado, a costa de rendimiento.
        // Por ahora, lo dejamos como está (carga al inicio).
        return historialVentas;
    }

    // Cargar (o recargar) historial desde la Base de Datos
    // Este método es AHORA la única fuente de datos para el historial
    private void cargarHistorialDesdeDB() {
        // Limpiar lista actual antes de cargar para evitar duplicados si se llama de nuevo
        historialVentas.clear();

        // La consulta SQL asume que quieres ver cada línea de detalle como una entrada separada
        // en el historial, lo cual coincide con la estructura de la clase Venta actual.
        String sql = "SELECT v.fecha_hora, dv.cantidad, dv.subtotal, dv.producto_codigo, " +
                "COALESCE(p.nombre, dv.producto_codigo) AS nombre_producto, " + // Usa nombre de producto si existe, si no el código (para manuales)
                "COALESCE(p.precio_venta, dv.subtotal / dv.cantidad) AS precio_unitario " + // Calcula precio si no está en productos
                "FROM ventas v " +
                "JOIN detalle_ventas dv ON v.id = dv.venta_id " +
                "LEFT JOIN productos p ON dv.producto_codigo = p.codigo " + // LEFT JOIN para incluir detalles cuyo código no esté en productos (manuales)
                "ORDER BY v.fecha_hora DESC";

        if (connection == null) {
            System.err.println("Error: No se puede cargar historial, no hay conexión a la BD.");
            return;
        }

        try (Statement stmt = connection.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {

            // Usamos un formateador más flexible si el formato de BD varía
            // DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"); // Ajustar al formato real de tu BD

            while (rs.next()) {
                // Leer fecha/hora desde Timestamp para mayor precisión
                Timestamp ts = rs.getTimestamp("fecha_hora");
                LocalDateTime fechaHora = (ts != null) ? ts.toLocalDateTime() : null;

                String nombre = rs.getString("nombre_producto");
                String codigo = rs.getString("producto_codigo");
                double precio = rs.getDouble("precio_unitario");
                int cantidad = rs.getInt("cantidad");

                // Crear un objeto Producto temporal para el historial
                // No necesitamos el stock real aquí, podemos poner 0 o la cantidad vendida.
                // Tampoco necesitamos la categoría aquí.
                Producto productoHistorial;
                if (codigo.startsWith("MANUAL-")) {
                    productoHistorial = new ProductoManual(nombre, precio);
                    // Sobreescribir código por si el hash cambia o no es útil mostrarlo
                    // productoHistorial.codigoProperty().set(codigo); // Opcional
                } else {

                    productoHistorial = new Producto(codigo, nombre, precio, 0.0, 0, null, 0);
                }

                DetalleVenta detalle = new DetalleVenta(productoHistorial, cantidad);

                // Crear objeto Venta (que representa una línea del historial)
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
    }



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