package com.example.drugstore2.model;

import java.time.LocalDateTime;

public class CajaMovimiento {
    private int id;
    private int sesionId;
    private LocalDateTime fechaHora;
    private TipoMovimientoCaja tipoMovimiento;
    private double monto;
    private String descripcion;
    private Integer ventaId; // Usar Integer para permitir null

    // Constructor, getters y setters
    public CajaMovimiento(int sesionId, LocalDateTime fechaHora, TipoMovimientoCaja tipoMovimiento, double monto, String descripcion, Integer ventaId) {
        this.sesionId = sesionId;
        this.fechaHora = fechaHora;
        this.tipoMovimiento = tipoMovimiento;
        this.monto = monto; // Positivo o negativo según el tipo
        this.descripcion = descripcion;
        this.ventaId = ventaId;
    }

    // Getters (y Setters si fueran necesarios)
    public int getId() { return id; }
    public int getSesionId() { return sesionId; }
    public LocalDateTime getFechaHora() { return fechaHora; }
    public TipoMovimientoCaja getTipoMovimiento() { return tipoMovimiento; }
    public double getMonto() { return monto; }
    public String getDescripcion() { return descripcion; }
    public Integer getVentaId() { return ventaId; }
    public void setId(int id) { this.id = id; } // Útil si se recupera de BD
}
