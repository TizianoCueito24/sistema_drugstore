package com.example.drugstore2.model; // Asegúrate que el paquete sea el correcto

import javafx.beans.property.SimpleDoubleProperty;
import javafx.beans.property.SimpleStringProperty;

public class CategoriaVenta {

    private final SimpleStringProperty nombre;
    private final SimpleDoubleProperty total;

    public CategoriaVenta(String nombre, double total) {
        this.nombre = new SimpleStringProperty(nombre);
        this.total = new SimpleDoubleProperty(total);
    }

    // --- Getters ---
    public String getNombre() {
        return nombre.get();
    }

    public double getTotal() {
        return total.get();
    }

    // --- Properties (para TableView) ---
    public SimpleStringProperty nombreProperty() {
        return nombre;
    }

    public SimpleDoubleProperty totalProperty() {
        return total;
    }
}