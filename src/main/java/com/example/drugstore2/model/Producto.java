package com.example.drugstore2.model;

import javafx.beans.property.SimpleDoubleProperty;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.property.SimpleStringProperty;

public class Producto {
    private final SimpleIntegerProperty stockMinimo;
    private SimpleStringProperty codigo;
    private SimpleStringProperty nombre;
    private SimpleDoubleProperty precioVenta;
    private SimpleDoubleProperty precioCosto;
    private SimpleIntegerProperty stock;
    private String categoriaNombre;

    public Producto(String codigo, String nombre, double precioVenta, double precioCosto, int stock, String categoriaNombre, int stockMinimo) {
        this.codigo = new SimpleStringProperty(codigo);
        this.nombre = new SimpleStringProperty(nombre);
        this.precioVenta = new SimpleDoubleProperty(precioVenta);
        this.precioCosto = new SimpleDoubleProperty(precioCosto); // <-- Ahora SÍ recibe el parámetro
        this.stock = new SimpleIntegerProperty(stock);
        this.categoriaNombre = categoriaNombre;
        this.stockMinimo = new SimpleIntegerProperty(stockMinimo);
    }


    public Producto(String codigo, String nombre, double precioVenta, int stock) {
        // Llama al constructor principal con un costo por defecto (ej. 0) o calcúlalo si es posible
        this(codigo, nombre, precioVenta, 0.0, stock, null, 5);
    }

    public String getCategoriaNombre() {
        return categoriaNombre;
    }
    public void setCategoriaNombre(String categoriaNombre) {
        this.categoriaNombre = categoriaNombre;
    }

    public String getCodigo() {
        return codigo.get();
    }

    public String getNombre() {
        return nombre.get();
    }

    public double getPrecioVenta() {
        return precioVenta.get();
    }

    public int getStockMinimo() {return stockMinimo.get();}

    public int getStock() {
        return stock.get();
    }
    public double getPrecioCosto() { // <-- NUEVO GETTER
        return precioCosto.get();
    }

    public void setStock(int stock) {
        this.stock.set(stock);
    }
    public void setStockMinimo(int stockMinimo) {
        this.stockMinimo.set(stockMinimo);
    }
    public void setPrecioCosto(double precioCosto) { // <-- NUEVO SETTER (si es necesario modificarlo después de crearlo)
        this.precioCosto.set(precioCosto);
    }


    public SimpleStringProperty codigoProperty() {
        return codigo;
    }

    public SimpleStringProperty nombreProperty() {
        return nombre;
    }

    public SimpleDoubleProperty precioVentaProperty() {
        return precioVenta;
    }

    public SimpleIntegerProperty stockProperty() {
        return stock;
    }

    public SimpleIntegerProperty stockMinimoProperty() {return stockMinimo;}

    public SimpleDoubleProperty precioCostoProperty() { // <-- NUEVO PROPERTY
        return precioCosto;
    }
}

