package com.example.drugstore2.model;

public class ProductoManual extends Producto {
    // Define el código fijo que DEBE existir en tu tabla 'productos'
    private static final String MANUAL_PRODUCT_CODE = "MANUAL";

    public ProductoManual(String nombre, double precio) {
        // Llama al constructor padre usando SIEMPRE el código fijo "MANUAL"
        super(MANUAL_PRODUCT_CODE, // Código fijo
                nombre,             // Nombre real introducido
                precio,             // Precio real introducido
                0.0,                // Costo por defecto
                0,                  // Stock por defecto
                "Sin categoría",    // Nombre categoría por defecto (o el de la fila 'MANUAL')
                0);                 // Stock mínimo por defecto
    }
}