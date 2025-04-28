package com.example.drugstore2.service;
import com.example.drugstore2.model.Producto;

import java.sql.*; // Para Connection, DriverManager, PreparedStatement, ResultSet, SQLException, Statement
import java.util.*; // O importa Map, List, HashMap, ArrayList individualmente

public class InventarioService {
    private Connection connection;

    // Constructor (sin cambios)
    public InventarioService() {
        // ... (código existente del constructor)
        try {
            Class.forName("com.mysql.cj.jdbc.Driver");
            String url = "jdbc:mysql://localhost:3306/drugstore?useSSL=false&serverTimezone=UTC";
            String usuario = "root";
            String clave = "";
            connection = DriverManager.getConnection(url, usuario, clave);
            System.out.println("✅ Conexión a MySQL establecida.");
        } catch (ClassNotFoundException e) {
            System.out.println("❌ Driver MySQL no encontrado.");
            e.printStackTrace();
        } catch (SQLException e) {
            System.out.println("❌ Error al conectar BD:");
            e.printStackTrace();
        }
    }

    public Connection getConnection() { return connection; }
    public Map<String, Producto> getProductos() {
        Map<String, Producto> productos = new HashMap<>();
        String sql = "SELECT p.*, c.nombre AS categoria, p.stock_minimo, p.precio_costo " + // <-- Añadido p.precio_costo
                "FROM productos p " +
                "LEFT JOIN categorias c ON p.categoria_id = c.id " +
                "WHERE p.activo = TRUE";
        try (Statement stmt = connection.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                String codigo = rs.getString("codigo");
                String nombre = rs.getString("nombre");
                double precioVenta = rs.getDouble("precio_venta");
                double precioCosto = rs.getDouble("precio_costo"); // <-- LEER precio_costo
                int stock = rs.getInt("stock");
                String categoria = rs.getString("categoria");
                int stockMinimo = rs.getInt("stock_minimo");
                // Usar el constructor actualizado de Producto
                productos.put(codigo, new Producto(codigo, nombre, precioVenta, precioCosto, stock, categoria, stockMinimo));
            }
        } catch (SQLException e) {
            System.err.println("❌ Error SQL al obtener productos: " + e.getMessage());
            e.printStackTrace();
        }
        return productos;
    }

    public Producto buscarProductoPorCodigo(String codigo) {
        if (codigo == null || codigo.trim().isEmpty()) return null;
        // Añadir p.precio_costo a la consulta
        String sql = "SELECT p.*, c.nombre AS categoria, p.stock_minimo, p.precio_costo " + // <-- Añadido p.precio_costo
                "FROM productos p " +
                "LEFT JOIN categorias c ON p.categoria_id = c.id " +
                "WHERE p.codigo = ? AND p.activo = TRUE"; // Solo activos
        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setString(1, codigo.trim());
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) {
                return new Producto(
                        rs.getString("codigo"),
                        rs.getString("nombre"),
                        rs.getDouble("precio_venta"),
                        // ... aquí puede estar el problema ...
                        rs.getDouble("precio_costo"), // ¿Está este argumento en la posición correcta?
                        rs.getInt("stock"),
                        rs.getString("categoria"),   // ¿Es este el nombre correcto de la columna/alias?
                        rs.getInt("stock_minimo")
                );
            }
        } catch (SQLException e) {
            System.err.println("❌ Error al buscar producto por código " + codigo + ": " + e.getMessage());
            e.printStackTrace();
        }
        return null;
    }
    public void agregarProducto(String codigo, String nombre, double precioVenta, double precioCosto, int stock, int categoriaId, int stockMinimo) { // <-- Nuevo parámetro precioCosto
        if (buscarProductoPorCodigo(codigo) != null) {
            System.err.println("⚠️ Intento de agregar producto con código duplicado: " + codigo);
            return;
        }

        String sql = "INSERT INTO productos (codigo, nombre, precio_venta, precio_costo, stock, categoria_id, activo, stock_minimo) " + // <-- Añadido precio_costo
                "VALUES (?, ?, ?, ?, ?, ?, TRUE, ?)"; // Añadido ? para precio_costo
        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setString(1, codigo);
            stmt.setString(2, nombre);
            stmt.setDouble(3, precioVenta);
            stmt.setDouble(4, precioCosto); // <-- Establecer valor para precio_costo
            stmt.setInt(5, stock);
            stmt.setInt(6, categoriaId);
            stmt.setInt(7, stockMinimo); // El último ? es stock_minimo
            int filasAfectadas = stmt.executeUpdate();
            if (filasAfectadas > 0) {
                System.out.println("✅ Producto agregado con éxito: " + nombre + " (Costo: " + precioCosto + ")");
                // Ajustar el mensaje de log si es necesario
                registrarEvento("INSERT", "productos", "Agregado: " + nombre + " (Cod: " + codigo + ", Costo: " + precioCosto + ", Stock: " + stock + ", Min: " + stockMinimo + ")");
            } else {
                System.err.println("❌ No se pudo agregar el producto: " + nombre);
            }
        } catch (SQLException e) {
            System.err.println("❌ Error SQL al agregar producto " + codigo + ": " + e.getMessage());
            e.printStackTrace();
        }
    }
    public boolean modificarProducto(String codigo, String nuevoNombre, double nuevoPrecioVenta, double nuevoPrecioCosto, int nuevaCategoriaId, int nuevoStockMinimo) { // <-- Nuevo parámetro nuevoPrecioCosto
        // Añadir precio_costo al UPDATE
        String sql = "UPDATE productos SET nombre = ?, precio_venta = ?, precio_costo = ?, categoria_id = ?, stock_minimo = ? " + // <-- Añadido precio_costo = ?
                "WHERE codigo = ? AND activo = TRUE";

        if (connection == null || codigo == null || codigo.trim().isEmpty() || nuevoNombre == null || nuevoNombre.trim().isEmpty() || nuevoPrecioVenta <= 0 || nuevoPrecioCosto < 0 || nuevoStockMinimo < 0) { // Permitir costo 0
            System.err.println("⚠️ Datos inválidos para modificar producto.");
            return false;
        }

        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setString(1, nuevoNombre.trim());
            stmt.setDouble(2, nuevoPrecioVenta);
            stmt.setDouble(3, nuevoPrecioCosto); // <-- Establecer nuevo precio_costo
            stmt.setInt(4, nuevaCategoriaId);
            stmt.setInt(5, nuevoStockMinimo);
            stmt.setString(6, codigo.trim()); // WHERE

            int filasAfectadas = stmt.executeUpdate();
            if (filasAfectadas > 0) {
                // Ajustar el mensaje de log si es necesario
                registrarEvento("UPDATE", "productos", "Modificado: " + nuevoNombre + " (Cod: " + codigo + ", Costo: " + nuevoPrecioCosto + ", Min: " + nuevoStockMinimo + ")");
                System.out.println("✅ Producto modificado con éxito: " + codigo);
                return true;
            } else {
                System.out.println("⚠️ No se encontró el producto activo a modificar o no hubo cambios: " + codigo);
                return false;
            }
        } catch (SQLException e) {
            System.err.println("❌ Error SQL al modificar producto " + codigo + ": " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }
    public boolean actualizarStock(String codigo, int cantidad) {
        String sql = "UPDATE productos SET stock = stock + ? WHERE codigo = ? AND (stock + ?) >= 0";
        if (connection == null || codigo == null || codigo.trim().isEmpty()) {
            System.err.println("⚠️ Datos inválidos para actualizar stock.");
            return false;
        }

        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setInt(1, cantidad);
            stmt.setString(2, codigo.trim());
            stmt.setInt(3, cantidad); // Para la condición WHERE
            int filasAfectadas = stmt.executeUpdate();
            if (filasAfectadas > 0) {
                System.out.println("   (Stock actualizado para " + codigo + " en: " + cantidad + ")"); // Log interno
                return true;
            } else {
                // Esto puede ocurrir si el producto no existe o si la actualización resultaría en stock negativo
                System.err.println("⚠️ No se pudo actualizar stock para " + codigo + " (quizás stock insuficiente o producto no encontrado). Cantidad: " + cantidad);
                return false; // Indicar fallo
            }
        } catch (SQLException e) {
            System.err.println("❌ Error SQL al actualizar stock para " + codigo + ": " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }

    public boolean ajustarStockManual(String codigo, int cantidadAjuste, String motivo) {
        if (cantidadAjuste == 0) {
            System.out.println("Ajuste manual de stock con cantidad 0, no se realiza acción.");
            return true; // No es un error, pero no hace nada
        }
        boolean exito = actualizarStock(codigo, cantidadAjuste);

        if (exito) {
            String tipoEvento = (cantidadAjuste > 0) ? "STOCK_ADD" : "STOCK_REMOVE";
            String desc = String.format("Ajuste Manual: Cod %s, Cant: %+d, Motivo: %s",
                    codigo, cantidadAjuste, (motivo == null || motivo.trim().isEmpty() ? "No especificado" : motivo.trim()));
            registrarEvento(tipoEvento, "productos", desc);
            System.out.println("✅ Ajuste manual de stock realizado para " + codigo);
        }
        // El mensaje de error ya lo da actualizarStock si falla
        return exito;
    }
    public boolean desactivarProducto(String codigo) {
        // ... (código existente)
        String sql = "UPDATE productos SET activo = FALSE WHERE codigo = ? AND activo = TRUE";
        if (connection == null || codigo == null || codigo.trim().isEmpty()) return false;
        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setString(1, codigo.trim());
            int filasAfectadas = stmt.executeUpdate();
            if (filasAfectadas > 0) {
                registrarEvento("DEACTIVATE", "productos", "Se desactivó el producto con código: " + codigo);
                System.out.println("✅ Producto desactivado con éxito: " + codigo);
                return true;
            } else {
                System.out.println("⚠️ No se encontró el producto activo a desactivar: " + codigo);
                return false;
            }
        } catch (SQLException e) {
            System.err.println("❌ Error SQL al desactivar producto " + codigo + ": " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }

    public List<String> obtenerCategorias() {
        // ... (código existente)
        List<String> c = new ArrayList<>();
        try (Statement s = connection.createStatement(); ResultSet r = s.executeQuery("SELECT nombre FROM categorias ORDER BY nombre")) {
            while (r.next()) c.add(r.getString("nombre"));
        } catch (Exception e) { e.printStackTrace(); }
        return c;
    }
    public int obtenerCategoriaId(String nombre) {
        // ... (código existente)
        try (PreparedStatement s = connection.prepareStatement("SELECT id FROM categorias WHERE nombre = ?")) {
            s.setString(1, nombre);
            ResultSet r = s.executeQuery();
            if (r.next()) return r.getInt("id");
        } catch (Exception e) { e.printStackTrace(); }
        return -1;
    }
    private void registrarEvento(String tipo, String tabla, String descripcion) {
        // ... (código existente)
        try (PreparedStatement s = connection.prepareStatement("INSERT INTO log_eventos (tipo_evento, tabla_afectada, descripcion) VALUES (?, ?, ?)")) {
            s.setString(1, tipo); s.setString(2, tabla); s.setString(3, descripcion); s.executeUpdate();
        } catch (SQLException e) { System.err.println("❌ Error al registrar evento: "+e.getMessage()); }
    }
    public List<Producto> buscarProductosPorNombre(String nombreFragmento) {
        List<Producto> productosEncontrados = new ArrayList<>();
        if (nombreFragmento == null || nombreFragmento.trim().isEmpty() || connection == null) {
            return productosEncontrados;
        }
        String sql = "SELECT p.*, c.nombre AS categoria, p.stock_minimo, p.precio_costo " + // <-- Añadido p.precio_costo
                "FROM productos p " +
                "LEFT JOIN categorias c ON p.categoria_id = c.id " +
                "WHERE LOWER(p.nombre) LIKE LOWER(?) AND p.activo = TRUE";
        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setString(1, "%" + nombreFragmento.trim() + "%");
            ResultSet rs = stmt.executeQuery();
            while (rs.next()) {
                productosEncontrados.add(new Producto(
                        rs.getString("codigo"),
                        rs.getString("nombre"),
                        rs.getDouble("precio_venta"),
                        rs.getDouble("precio_costo"), // <-- LEER precio_costo
                        rs.getInt("stock"),
                        rs.getString("categoria"),
                        rs.getInt("stock_minimo")
                ));
            }
        } catch (SQLException e) {
            System.err.println("❌ Error SQL al buscar productos por nombre '" + nombreFragmento + "': " + e.getMessage());
            e.printStackTrace();
        }
        return productosEncontrados;
    }

}