package com.example.drugstore2.service;

import java.sql.*;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

public class EstadisticasService {

    private Connection connection;


    // Recibe la conexión de la base de datos al ser creado
    public EstadisticasService(Connection connection) {
        this.connection = connection;
        if (this.connection == null) {
            // Considera una mejor gestión de errores aquí
            System.err.println("❌ Error: La conexión a la base de datos es nula en EstadisticasService.");
            // Podrías lanzar una excepción o intentar reconectar
        }
    }
    public double getVentasPorRangoFechas(LocalDate inicio, LocalDate fin) {
        double total = 0.0;
        String sql = "SELECT SUM(dv.subtotal) AS total_periodo " +
                "FROM detalle_ventas dv " +
                "JOIN ventas v ON dv.venta_id = v.id " +
                "WHERE DATE(v.fecha_hora) BETWEEN ? AND ?";

        if (connection == null || inicio == null || fin == null || fin.isBefore(inicio)) {
            System.err.println("⚠️ Parámetros inválidos para getVentasPorRangoFechas.");
            return total; // Retorna 0 si los parámetros no son válidos
        }


        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setDate(1, Date.valueOf(inicio));
            stmt.setDate(2, Date.valueOf(fin));
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) {
                // rs.getDouble puede devolver 0 si el SUM es NULL (no hubo ventas)
                total = rs.getDouble("total_periodo");
            }
        } catch (SQLException e) {
            System.err.println("❌ Error al calcular ventas por rango de fechas: " + e.getMessage());
            e.printStackTrace();
        }
        return total;
    }
    public double getGananciaBrutaPorRangoFechas(LocalDate inicio, LocalDate fin) {
        double gananciaBruta = 0.0;
        String sql = "SELECT SUM(dv.subtotal) AS total_ventas, " +
                "       SUM(dv.cantidad * COALESCE(p.precio_costo, 0)) AS total_costos " +
                "FROM detalle_ventas dv " +
                "JOIN ventas v ON dv.venta_id = v.id " +
                "LEFT JOIN productos p ON dv.producto_codigo = p.codigo " + // LEFT JOIN por si el producto ya no existe pero la venta sí
                "WHERE DATE(v.fecha_hora) BETWEEN ? AND ?";

        if (connection == null || inicio == null || fin == null || fin.isBefore(inicio)) {
            System.err.println("⚠️ Parámetros inválidos para getGananciaBrutaPorRangoFechas.");
            return 0.0;
        }

        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setDate(1, Date.valueOf(inicio));
            stmt.setDate(2, Date.valueOf(fin));
            ResultSet rs = stmt.executeQuery();

            if (rs.next()) {
                double totalVentas = rs.getDouble("total_ventas"); // Será 0 si no hay ventas
                double totalCostos = rs.getDouble("total_costos"); // Será 0 si no hay ventas o costos
                gananciaBruta = totalVentas - totalCostos;
            }
        } catch (SQLException e) {
            System.err.println("❌ Error SQL al calcular ganancia bruta por rango: " + e.getMessage());
            e.printStackTrace();
        }
        return gananciaBruta;
    }
    public Map<String, Double> getVentasPorCategoria(LocalDate inicio, LocalDate fin) {
        Map<String, Double> ventasCategoria = new LinkedHashMap<>(); // Usar LinkedHashMap para mantener orden

        // ----- NUEVA CONSULTA SQL -----
        String sql = "SELECT " +
                "    CASE " +
                "        WHEN dv.producto_codigo = 'MANUAL' THEN 'Otros' " + // Si el código es 'MANUAL', categoría es 'Otros'
                "        ELSE COALESCE(c.nombre, 'Sin Categoría') " + // Si no, usa la categoría del producto o 'Sin Categoría'
                "    END AS categoria_final, " + // Nombre de la columna resultante
                "    SUM(dv.subtotal) AS total_categoria " + // Suma de subtotales
                "FROM " +
                "    detalle_ventas dv " +
                "JOIN " +
                "    ventas v ON dv.venta_id = v.id " +
                // Se sigue necesitando LEFT JOIN para obtener la categoría de productos NO manuales
                "LEFT JOIN " +
                "    productos p ON dv.producto_codigo = p.codigo " +
                "LEFT JOIN " +
                "    categorias c ON p.categoria_id = c.id " +
                "WHERE " +
                "    DATE(v.fecha_hora) BETWEEN ? AND ? " + // Filtro de fecha
                "GROUP BY " +
                "    categoria_final " + // Agrupar por el nombre de categoría calculado
                "ORDER BY " +
                "    categoria_final"; // Ordenar alfabéticamente por categoría

        // ----- FIN NUEVA CONSULTA SQL -----


        if (connection == null || inicio == null || fin == null || fin.isBefore(inicio)) {
            System.err.println("⚠️ Parámetros inválidos para getVentasPorCategoria.");
            return ventasCategoria; // Devuelve mapa vacío
        }

        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setDate(1, Date.valueOf(inicio));
            stmt.setDate(2, Date.valueOf(fin));
            ResultSet rs = stmt.executeQuery();

            while (rs.next()) {
                // Usar el alias 'categoria_final' que definimos en la consulta CASE
                String categoria = rs.getString("categoria_final");
                double total = rs.getDouble("total_categoria");
                ventasCategoria.put(categoria, total);
            }
        } catch (SQLException e) {
            System.err.println("❌ Error SQL al obtener ventas por categoría: " + e.getMessage());
            e.printStackTrace();
        }
        return ventasCategoria;
    }
    public double getStockValorizado() {
        double valorTotal = 0.0;
        // Suma la multiplicación de stock por precio_costo para todos los productos activos
        // COALESCE es importante por si algun precio_costo es NULL, lo trate como 0
        String sql = "SELECT SUM(stock * COALESCE(precio_costo, 0)) AS valor_inventario " +
                "FROM productos " +
                "WHERE activo = TRUE";

        if (connection == null) {
            System.err.println("⚠️ No hay conexión para calcular stock valorizado.");
            return 0.0;
        }

        try (Statement stmt = connection.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {

            if (rs.next()) {
                valorTotal = rs.getDouble("valor_inventario"); // Será 0 si no hay productos activos
            }
        } catch (SQLException e) {
            System.err.println("❌ Error SQL al calcular stock valorizado: " + e.getMessage());
            e.printStackTrace();
        }
        return valorTotal;
    }

    public double getVentasDiarias(LocalDate fecha) {
        String sql = "SELECT SUM(dv.subtotal) AS total_dia " +
                "FROM detalle_ventas dv " +
                "JOIN ventas v ON dv.venta_id = v.id " +
                "WHERE DATE(v.fecha_hora) = ?";
        double total = 0.0;

        if (connection == null) return total;

        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setDate(1, Date.valueOf(fecha));
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) {
                total = rs.getDouble("total_dia");
            }
        } catch (SQLException e) {
            System.err.println("❌ Error al calcular ventas diarias: " + e.getMessage());
            e.printStackTrace();
        }
        return total;
    }

    // Calcula las ventas totales para la semana actual
    public double getVentasSemanales() {
        String sql = "SELECT SUM(dv.subtotal) AS total_semana " +
                "FROM detalle_ventas dv " +
                "JOIN ventas v ON dv.venta_id = v.id " +
                "WHERE YEAR(v.fecha_hora) = YEAR(CURDATE()) AND WEEK(v.fecha_hora, 1) = WEEK(CURDATE(), 1)";
        double total = 0.0;

        if (connection == null) return total;

        try (Statement stmt = connection.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            if (rs.next()) {
                total = rs.getDouble("total_semana");
            }
        } catch (SQLException e) {
            System.err.println("❌ Error al calcular ventas semanales: " + e.getMessage());
            e.printStackTrace();
        }
        return total;
    }
    // Calcula las ventas totales para el mes actual
    public double getVentasMensuales() {
        String sql = "SELECT SUM(dv.subtotal) AS total_mes " +
                "FROM detalle_ventas dv " +
                "JOIN ventas v ON dv.venta_id = v.id " +
                "WHERE YEAR(v.fecha_hora) = YEAR(CURDATE()) AND MONTH(v.fecha_hora) = MONTH(CURDATE())";
        double total = 0.0;

        if (connection == null) return total;

        try (Statement stmt = connection.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            if (rs.next()) {
                total = rs.getDouble("total_mes");
            }
        } catch (SQLException e) {
            System.err.println("❌ Error al calcular ventas mensuales: " + e.getMessage());
            e.printStackTrace();
        }
        return total;
    }

    // Obtiene el producto más vendido (basado en cantidad total vendida)
    // Podría extenderse para filtrar por período
    public Optional<Map.Entry<String, Integer>> getProductoMasVendido() {
        String sql = "SELECT dv.producto_codigo, p.nombre, SUM(dv.cantidad) AS total_cantidad " +
                "FROM detalle_ventas dv " +
                "JOIN productos p ON dv.producto_codigo = p.codigo " +
                "GROUP BY dv.producto_codigo, p.nombre " +
                "ORDER BY total_cantidad DESC " +
                "LIMIT 1";
        if (connection == null) return Optional.empty();

        try (Statement stmt = connection.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            if (rs.next()) {
                String nombreProducto = rs.getString("nombre");
                int cantidad = rs.getInt("total_cantidad");
                Map<String, Integer> resultado = new HashMap<>();
                resultado.put(nombreProducto, cantidad);
                return Optional.of(resultado.entrySet().iterator().next());
            }
        } catch (SQLException e) {
            System.err.println("❌ Error al obtener producto más vendido: " + e.getMessage());
            e.printStackTrace();
        }
        return Optional.empty();
    }

    // Calcula los ingresos totales (histórico)
    public double getIngresosTotales() {
        String sql = "SELECT SUM(subtotal) AS ingresos_totales FROM detalle_ventas";
        double total = 0.0;
        if (connection == null) return total;
        try (Statement stmt = connection.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            if (rs.next()) {
                total = rs.getDouble("ingresos_totales");
            }
        } catch (SQLException e) {
            System.err.println("❌ Error al calcular ingresos totales: " + e.getMessage());
            e.printStackTrace();
        }
        return total;
    }
    public double getGananciaBrutaTotal() {
        double gananciaBruta = 0.0;
        String sql = "SELECT SUM(dv.subtotal) AS total_ventas, " +
                "       SUM(dv.cantidad * COALESCE(p.precio_costo, 0)) AS total_costos " +
                "FROM detalle_ventas dv " +
                "LEFT JOIN productos p ON dv.producto_codigo = p.codigo "; // Sin filtro de fecha

        if (connection == null) return 0.0;

        try (Statement stmt = connection.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            if (rs.next()) {
                double totalVentas = rs.getDouble("total_ventas");
                double totalCostos = rs.getDouble("total_costos");
                gananciaBruta = totalVentas - totalCostos;
            }
        } catch (SQLException e) {
            System.err.println("❌ Error SQL al calcular ganancia bruta total: " + e.getMessage());
            e.printStackTrace();
        }
        return gananciaBruta;
    }
}