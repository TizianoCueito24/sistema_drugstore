package com.example.drugstore2.model;

public class ProductoManual extends Producto {
    public ProductoManual(String nombre, double precio) {
        // El 4to argumento ahora es precioCosto (double), ponemos 0.0
        // Orden: String, String, double, double, int, String, int
        super("MANUAL-" + nombre.hashCode(), nombre, precio, 0.0, 0, "Sin categoría", 0);
    }
}