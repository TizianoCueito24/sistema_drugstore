package com.example.drugstore2.model;

public enum TipoMovimientoCaja {
    APERTURA("Apertura de Caja"),
    VENTA_EFECTIVO("Venta en Efectivo"),
    RETIRO("Retiro de Efectivo"),
    GASTO("Gasto"),
    INGRESO_EXTRA("Ingreso Extra"),
    AJUSTE_CIERRE("Ajuste por Cierre"); // Para registrar la diferencia si la hay

    private final String descripcion;

    TipoMovimientoCaja(String descripcion) {
        this.descripcion = descripcion;
    }

    public String getDescripcion() {
        return descripcion;
    }
}