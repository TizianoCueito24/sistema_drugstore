package com.example.drugstore2.service;

import com.example.drugstore2.model.Producto;
import com.example.drugstore2.model.ProductoManual;
import com.example.drugstore2.model.VentaTemporal;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

// Servicio simple para mantener el estado compartido de la venta actual
public class VentaStateService {

    private final VentaTemporal ventaTemporal = new VentaTemporal();
    // Lista observable para la UI del ticket
    private final ObservableList<String> ticketItems = FXCollections.observableArrayList();
    private String totalText = "Total: $0.00";

    // Referencias a los componentes de UI compartidos (si es necesario actualizarlos directamente)
    private ListView<String> ticketListViewRef;
    private Label totalLabelRef;

    public VentaTemporal getVentaTemporal() {
        return ventaTemporal;
    }

    public ObservableList<String> getTicketItems() {
        return ticketItems;
    }

    public String getTotalText() {
        return totalText;
    }

    public void setTicketListViewRef(ListView<String> ticketListViewRef) {
        this.ticketListViewRef = ticketListViewRef;
        if (this.ticketListViewRef != null) {
            this.ticketListViewRef.setItems(ticketItems); // Enlazar la lista observable
        }
    }

    public void setTotalLabelRef(Label totalLabelRef) {
        this.totalLabelRef = totalLabelRef;
        if (this.totalLabelRef != null) {
            this.totalLabelRef.setText(totalText); // Establecer texto inicial
        }
    }

    public void agregarProducto(Producto producto) {
        ventaTemporal.agregarProducto(producto);
        actualizarUIVenta();
    }

    public void limpiarVenta() {
        ventaTemporal.limpiar();
        actualizarUIVenta();
    }

    public List<Producto> getProductos() {
        return ventaTemporal.getProductos();
    }

    // Método centralizado para actualizar la UI del ticket y el total
    public void actualizarUIVenta() {
        final double total = ventaTemporal.getTotal();
        final List<Producto> productosActuales = new ArrayList<>(ventaTemporal.getProductos());

        // Agrupar productos para mostrar cantidad
        final Map<String, Integer> conteoProductos = new HashMap<>();
        final Map<String, Producto> productosUnicos = new HashMap<>();

        for (Producto p : productosActuales) {
            if (p == null) continue;
            String codigoKey = p.getCodigo();
            // Usar una clave única para productos manuales basada en nombre y precio
            if (p instanceof ProductoManual) {
                codigoKey = "MANUAL_" + p.getNombre() + "_" + p.getPrecioVenta();
            }
            if (codigoKey == null) continue;
            conteoProductos.put(codigoKey, conteoProductos.getOrDefault(codigoKey, 0) + 1);
            productosUnicos.putIfAbsent(codigoKey, p);
        }

        // Preparar las líneas de texto
        final List<String> itemsToAdd = new ArrayList<>();
        for (Map.Entry<String, Integer> entry : conteoProductos.entrySet()) {
            Producto productoInfo = productosUnicos.get(entry.getKey());
            if (productoInfo != null) {
                itemsToAdd.add(String.format("%d x %s ($%.2f c/u)",
                        entry.getValue(), productoInfo.getNombre(), productoInfo.getPrecioVenta()));
            }
        }

        // Actualizar la Interfaz Gráfica en el Hilo de JavaFX
        Platform.runLater(() -> {
            ticketItems.setAll(itemsToAdd); // Actualiza la lista observable
            totalText = String.format("Total: $%.2f", total);
            if (totalLabelRef != null) {
                totalLabelRef.setText(totalText); // Actualiza el label si la referencia existe
            }
            // El ListView se actualiza automáticamente porque está vinculado a ticketItems
            System.out.println("DEBUG (VentaStateService): UI de venta actualizada. Items: " + ticketItems.size() + ", Total: " + totalText);
        });
    }


}
