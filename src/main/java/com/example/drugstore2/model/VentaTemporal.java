package com.example.drugstore2.model;

import java.util.ArrayList;
import java.util.List;

public class VentaTemporal {

    // Lista que mantiene todos los productos escaneados o agregados manualmente
    private List<Producto> productos = new ArrayList<>();

    // Método para agregar un producto al ticket
    public void agregarProducto(Producto producto) {
        productos.add(producto);
    }

    // Método para obtener el total de la venta
    public double getTotal() {
        double total = 0.0;
        for (Producto producto : productos) {
            total += producto.getPrecioVenta(); // Cambiado de getPrecio() a getPrecioVenta()
        }
        return total;
    }

    // Método para obtener la lista de productos en el ticket
    public List<Producto> getProductos() {
        return productos;
    }

    // Método para limpiar el ticket después de confirmar la venta
    public void limpiar() {
        productos.clear(); // Limpia la lista de productos
    }
}