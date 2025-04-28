package com.example.drugstore2.service;

import com.example.drugstore2.model.CajaMovimiento;
import com.example.drugstore2.model.TipoMovimientoCaja;

import java.sql.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

public class CajaService {

    private Connection connection;
    private int sesionActivaId = -1; // Guarda el ID de la sesión activa, -1 si no hay

    public CajaService(Connection connection) {
        this.connection = connection;
        if (this.connection == null) {
            System.err.println("❌ Error: La conexión a la base de datos es nula en CajaService.");
            // Considerar lanzar una excepción
        }
        cargarSesionActiva(); // Intenta cargar una sesión activa al iniciar
    }

    // Intenta encontrar y cargar el ID de una sesión activa al inicio
    private void cargarSesionActiva() {
        String sql = "SELECT id FROM caja_sesiones WHERE activa = TRUE ORDER BY fecha_apertura DESC LIMIT 1";
        try (Statement stmt = connection.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            if (rs.next()) {
                this.sesionActivaId = rs.getInt("id");
                System.out.println("ℹ️ Sesión de caja activa encontrada (ID: " + this.sesionActivaId + ")");
            } else {
                this.sesionActivaId = -1;
                System.out.println("ℹ️ No hay sesión de caja activa.");
            }
        } catch (SQLException e) {
            System.err.println("❌ Error al buscar sesión activa: " + e.getMessage());
            this.sesionActivaId = -1;
        }
    }

    public boolean haySesionActiva() {
        return this.sesionActivaId != -1;
    }

    public int getSesionActivaId() {
        return this.sesionActivaId;
    }

    // Inicia una nueva sesión de caja
    public boolean iniciarSesion(double saldoInicial) {
        if (haySesionActiva()) {
            System.err.println("⚠️ Ya existe una sesión de caja activa (ID: " + sesionActivaId + "). Ciérrela primero.");
            return false;
        }
        if (saldoInicial < 0) {
            System.err.println("⚠️ Saldo inicial no puede ser negativo.");
            return false;
        }

        String sqlSesion = "INSERT INTO caja_sesiones (fecha_apertura, saldo_inicial, activa) VALUES (?, ?, TRUE)";
        String sqlMovimiento = "INSERT INTO caja_movimientos (sesion_id, fecha_hora, tipo_movimiento, monto, descripcion) VALUES (?, ?, ?, ?, ?)";
        LocalDateTime ahora = LocalDateTime.now();
        ResultSet generatedKeys = null;

        try {
            connection.setAutoCommit(false); // Iniciar transacción

            // 1. Crear la sesión
            PreparedStatement stmtSesion = connection.prepareStatement(sqlSesion, Statement.RETURN_GENERATED_KEYS);
            stmtSesion.setTimestamp(1, Timestamp.valueOf(ahora));
            stmtSesion.setDouble(2, saldoInicial);
            stmtSesion.executeUpdate();

            // Obtener el ID de la sesión creada
            generatedKeys = stmtSesion.getGeneratedKeys();
            if (generatedKeys.next()) {
                this.sesionActivaId = generatedKeys.getInt(1);
            } else {
                throw new SQLException("No se pudo obtener el ID de la sesión generada.");
            }
            stmtSesion.close();

            // 2. Registrar el movimiento de apertura
            PreparedStatement stmtMov = connection.prepareStatement(sqlMovimiento);
            stmtMov.setInt(1, this.sesionActivaId);
            stmtMov.setTimestamp(2, Timestamp.valueOf(ahora));
            stmtMov.setString(3, TipoMovimientoCaja.APERTURA.name()); // Guarda el nombre del enum
            stmtMov.setDouble(4, saldoInicial); // El monto de apertura es el saldo inicial
            stmtMov.setString(5, TipoMovimientoCaja.APERTURA.getDescripcion());
            stmtMov.executeUpdate();
            stmtMov.close();

            connection.commit(); // Confirmar transacción
            System.out.println("✅ Sesión de caja iniciada (ID: " + this.sesionActivaId + ") con saldo inicial: " + saldoInicial);
            return true;

        } catch (SQLException e) {
            System.err.println("❌ Error SQL al iniciar sesión de caja: " + e.getMessage());
            try {
                connection.rollback(); // Revertir en caso de error
            } catch (SQLException ex) {
                System.err.println("❌ Error CRÍTICO al hacer rollback: " + ex.getMessage());
            }
            this.sesionActivaId = -1; // Resetear ID si falla
            return false;
        } finally {
            try { if (generatedKeys != null) generatedKeys.close(); } catch (SQLException e) { e.printStackTrace(); }
            try { connection.setAutoCommit(true); } catch (SQLException e) { e.printStackTrace(); } // Restaurar auto-commit
        }
    }

    // Registra un movimiento de caja (Gasto, Retiro, Ingreso Extra, Venta Efectivo)
    public boolean registrarMovimiento(TipoMovimientoCaja tipo, double monto, String descripcion, Integer ventaId) {
        if (!haySesionActiva()) {
            System.err.println("⚠️ No hay sesión de caja activa para registrar el movimiento.");
            return false;
        }
        // Validar monto según tipo
        if ((tipo == TipoMovimientoCaja.RETIRO || tipo == TipoMovimientoCaja.GASTO) && monto > 0) {
            monto = -monto; // Asegurar que retiros/gastos sean negativos
            System.out.println("ℹ️ Convirtiendo monto a negativo para " + tipo.name());
        }
        if ((tipo == TipoMovimientoCaja.VENTA_EFECTIVO || tipo == TipoMovimientoCaja.INGRESO_EXTRA) && monto < 0) {
            System.err.println("⚠️ El monto para " + tipo.name() + " no puede ser negativo.");
            return false;
        }
        if (tipo == TipoMovimientoCaja.APERTURA || tipo == TipoMovimientoCaja.AJUSTE_CIERRE) {
            System.err.println("⚠️ El tipo de movimiento " + tipo.name() + " no se registra manualmente aquí.");
            return false;
        }


        String sql = "INSERT INTO caja_movimientos (sesion_id, fecha_hora, tipo_movimiento, monto, descripcion, venta_id) VALUES (?, ?, ?, ?, ?, ?)";
        LocalDateTime ahora = LocalDateTime.now();

        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setInt(1, this.sesionActivaId);
            stmt.setTimestamp(2, Timestamp.valueOf(ahora));
            stmt.setString(3, tipo.name());
            stmt.setDouble(4, monto);
            stmt.setString(5, descripcion);
            if (ventaId != null) {
                stmt.setInt(6, ventaId);
            } else {
                stmt.setNull(6, Types.INTEGER);
            }
            stmt.executeUpdate();
            System.out.println("✅ Movimiento registrado: " + tipo.name() + ", Monto: " + monto);
            return true;
        } catch (SQLException e) {
            System.err.println("❌ Error SQL al registrar movimiento: " + e.getMessage());
            return false;
        }
    }

    // Calcula el saldo que debería haber en caja según los movimientos registrados
    public double obtenerSaldoCalculado() {
        if (!haySesionActiva()) {
            System.err.println("⚠️ No hay sesión activa para calcular saldo.");
            return 0.0; // O lanzar excepción
        }

        String sql = "SELECT SUM(monto) FROM caja_movimientos WHERE sesion_id = ?";
        double saldoCalculado = 0.0;

        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setInt(1, this.sesionActivaId);
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) {
                saldoCalculado = rs.getDouble(1); // Obtiene la suma total de montos (positivos y negativos)
            }
        } catch (SQLException e) {
            System.err.println("❌ Error SQL al calcular saldo de caja: " + e.getMessage());
            return 0.0; // O manejar el error de otra forma
        }
        return saldoCalculado;
    }

    // Cierra la sesión activa, realizando el arqueo
    public boolean cerrarSesion(double saldoRealContado) {
        if (!haySesionActiva()) {
            System.err.println("⚠️ No hay sesión de caja activa para cerrar.");
            return false;
        }
        if (saldoRealContado < 0) {
            System.err.println("⚠️ Saldo real contado no puede ser negativo.");
            return false;
        }

        double saldoCalculado = obtenerSaldoCalculado();
        double diferencia = saldoRealContado - saldoCalculado;
        LocalDateTime ahora = LocalDateTime.now();

        String sqlUpdateSesion = "UPDATE caja_sesiones SET fecha_cierre = ?, saldo_final_calculado = ?, saldo_final_real = ?, diferencia = ?, activa = FALSE WHERE id = ? AND activa = TRUE";
        // Opcional: Registrar la diferencia como un movimiento de ajuste
        String sqlMovAjuste = "INSERT INTO caja_movimientos (sesion_id, fecha_hora, tipo_movimiento, monto, descripcion) VALUES (?, ?, ?, ?, ?)";


        try {
            connection.setAutoCommit(false); // Transacción

            // 1. Actualizar la sesión
            PreparedStatement stmtUpdate = connection.prepareStatement(sqlUpdateSesion);
            stmtUpdate.setTimestamp(1, Timestamp.valueOf(ahora));
            stmtUpdate.setDouble(2, saldoCalculado);
            stmtUpdate.setDouble(3, saldoRealContado);
            stmtUpdate.setDouble(4, diferencia);
            stmtUpdate.setInt(5, this.sesionActivaId);

            int filasAfectadas = stmtUpdate.executeUpdate();
            stmtUpdate.close();

            if (filasAfectadas == 0) {
                throw new SQLException("No se pudo actualizar la sesión activa (quizás ya estaba cerrada).");
            }

            // 2. Opcional: Registrar movimiento de ajuste si hay diferencia
            if (diferencia != 0) {
                PreparedStatement stmtAjuste = connection.prepareStatement(sqlMovAjuste);
                stmtAjuste.setInt(1, this.sesionActivaId);
                stmtAjuste.setTimestamp(2, Timestamp.valueOf(ahora));
                stmtAjuste.setString(3, TipoMovimientoCaja.AJUSTE_CIERRE.name());
                stmtAjuste.setDouble(4, diferencia); // Registrar la diferencia exacta (puede ser +/-)
                stmtAjuste.setString(5, (diferencia > 0 ? "Sobrante" : "Faltante") + " detectado al cierre");
                stmtAjuste.executeUpdate();
                stmtAjuste.close();
                System.out.println("ℹ️ Registrado ajuste por diferencia de: " + diferencia);
            }


            connection.commit(); // Confirmar
            System.out.println("✅ Sesión de caja cerrada (ID: " + this.sesionActivaId + ")");
            System.out.println("   Saldo Calculado: " + saldoCalculado);
            System.out.println("   Saldo Real: " + saldoRealContado);
            System.out.println("   Diferencia: " + diferencia);
            this.sesionActivaId = -1; // Marcar que no hay sesión activa
            return true;

        } catch (SQLException e) {
            System.err.println("❌ Error SQL al cerrar sesión de caja: " + e.getMessage());
            try { connection.rollback(); } catch (SQLException ex) { System.err.println("❌ Error CRÍTICO al hacer rollback: " + ex.getMessage()); }
            return false;
        } finally {
            try { connection.setAutoCommit(true); } catch (SQLException e) { e.printStackTrace(); }
        }
    }

    // Podrías añadir métodos para obtener todos los movimientos de una sesión, etc.
    public List<CajaMovimiento> getMovimientosSesionActual() {
        List<CajaMovimiento> movimientos = new ArrayList<>();
        if (!haySesionActiva()) return movimientos;

        String sql = "SELECT * FROM caja_movimientos WHERE sesion_id = ? ORDER BY fecha_hora ASC";
        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setInt(1, this.sesionActivaId);
            ResultSet rs = stmt.executeQuery();
            while(rs.next()) {
                Integer ventaId = rs.getObject("venta_id", Integer.class); // Para manejar NULLs
                movimientos.add(new CajaMovimiento(
                        rs.getInt("sesion_id"),
                        rs.getTimestamp("fecha_hora").toLocalDateTime(),
                        TipoMovimientoCaja.valueOf(rs.getString("tipo_movimiento")), // Convierte String a Enum
                        rs.getDouble("monto"),
                        rs.getString("descripcion"),
                        ventaId
                ));
            }
        } catch (SQLException e) {
            System.err.println("❌ Error SQL obteniendo movimientos: " + e.getMessage());
        } catch (IllegalArgumentException e) {
            System.err.println("❌ Error: Tipo de movimiento inválido en BD: " + e.getMessage());
        }
        return movimientos;
    }
}