package com.example.drugstore2.model;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public class Venta {
    private LocalDateTime fechaHora;
    private DetalleVenta detalle;


    public Venta(LocalDateTime fechaHora, DetalleVenta detalle) {
        this.fechaHora = fechaHora;
        this.detalle = detalle;
    }

    public LocalDateTime getFechaHora() {
        return fechaHora;
    }

    // Para mostrarlo como String en el TableView
    public String getFechaHoraString() {
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss");
        return fechaHora.format(formatter);
    }

    public DetalleVenta getDetalle() {
        return detalle;
    }

    // Devuelve un String con información del producto y cantidad
    public String getDetalleString() {
        return detalle.getProducto().getNombre() + " x" + detalle.getCantidad();
    }

    // Total de la venta
    public double getTotal() {
        return detalle.getSubtotal();
    }
}
