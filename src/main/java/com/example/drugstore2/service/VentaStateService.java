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
import java.util.Comparator; // Para ordenar ticket
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Servicio simple para mantener y compartir el estado de la venta actual (ticket temporal)
 * entre diferentes controladores.
 */
public class VentaStateService {

    private final VentaTemporal ventaTemporal = new VentaTemporal();
    // Lista observable para la UI del ticket (solo una lista compartida)
    private final ObservableList<String> ticketItems = FXCollections.observableArrayList();
    private String totalText = "Total: $0.00";

    // Referencias a los componentes de UI que mostrarán el ticket
    // (Normalmente solo los de la pestaña Venta Escáner)
    private ListView<String> ticketListViewRef;
    private Label totalLabelRef;

    /**
     * Obtiene el objeto VentaTemporal que contiene los productos.
     * @return La instancia de VentaTemporal.
     */
    public VentaTemporal getVentaTemporal() {
        return ventaTemporal;
    }

    /**
     * Obtiene la lista observable de strings formateados para mostrar en el ListView del ticket.
     * @return La ObservableList de items del ticket.
     */
    public ObservableList<String> getTicketItems() {
        return ticketItems;
    }

    /**
     * Obtiene el texto actual del total formateado.
     * @return String del total (ej. "Total: $123.45").
     */
    public String getTotalText() {
        return totalText;
    }

    /**
     * Establece la referencia al ListView que mostrará el ticket.
     * Automáticamente vincula la lista observable interna a este ListView.
     * @param ticketListViewRef El ListView de la UI.
     */
    public void setTicketListViewRef(ListView<String> ticketListViewRef) {
        this.ticketListViewRef = ticketListViewRef;
        if (this.ticketListViewRef != null) {
            // Asegurarse que el ListView use la lista observable de este servicio
            this.ticketListViewRef.setItems(ticketItems);
            System.out.println("DEBUG (VentaStateService): Referencia a ticketListView establecida.");
            // Actualizar por si ya había items al establecer la referencia
            actualizarUIVenta();
        } else {
            System.err.println("WARN (VentaStateService): setTicketListViewRef recibió null.");
        }
    }

    /**
     * Establece la referencia al Label que mostrará el total.
     * Actualiza el texto del Label con el total actual.
     * @param totalLabelRef El Label de la UI.
     */
    public void setTotalLabelRef(Label totalLabelRef) {
        this.totalLabelRef = totalLabelRef;
        if (this.totalLabelRef != null) {
            this.totalLabelRef.setText(totalText); // Establecer texto actual
            System.out.println("DEBUG (VentaStateService): Referencia a totalLabel establecida.");
        } else {
            System.err.println("WARN (VentaStateService): setTotalLabelRef recibió null.");
        }
    }

    /**
     * Agrega un producto a la venta temporal compartida y actualiza la UI.
     * @param producto El producto a agregar.
     */
    public void agregarProducto(Producto producto) {
        if (producto != null) {
            ventaTemporal.agregarProducto(producto);
            actualizarUIVenta(); // Actualiza la UI después de agregar
        }
    }

    /**
     * Limpia la venta temporal compartida (quita todos los productos) y actualiza la UI.
     */
    public void limpiarVenta() {
        ventaTemporal.limpiar();
        actualizarUIVenta(); // Actualiza la UI después de limpiar
    }

    /**
     * Obtiene la lista de productos actualmente en la venta temporal.
     * @return Lista de Productos.
     */
    public List<Producto> getProductos() {
        return ventaTemporal.getProductos();
    }

    /**
     * Método centralizado para actualizar la UI del ticket (ListView y Label)
     * basándose en el estado actual de ventaTemporal. Debe ser llamado
     * después de cualquier cambio en ventaTemporal.
     */
    public void actualizarUIVenta() {
        final double total = ventaTemporal.getTotal();
        final List<Producto> productosActuales = new ArrayList<>(ventaTemporal.getProductos());

        // --- Agrupar productos para mostrar cantidad ---
        final Map<String, Integer> conteoProductos = new HashMap<>();
        final Map<String, Producto> productosUnicos = new HashMap<>();

        for (Producto p : productosActuales) {
            if (p == null) continue;
            String codigoKey = p.getCodigo();
            // Usar una clave única para productos manuales basada en nombre y precio
            if (p instanceof ProductoManual) {
                codigoKey = "MANUAL_" + p.getNombre() + "_" + p.getPrecioVenta();
            }
            if (codigoKey == null) continue; // Evitar errores si el código es null
            conteoProductos.put(codigoKey, conteoProductos.getOrDefault(codigoKey, 0) + 1);
            productosUnicos.putIfAbsent(codigoKey, p);
        }

        // --- Preparar las líneas de texto a añadir (ordenadas por nombre) ---
        final List<String> itemsToAdd = new ArrayList<>();
        // Crear lista de entradas para ordenar
        List<Map.Entry<String, Integer>> sortedEntries = new ArrayList<>(conteoProductos.entrySet());
        // Ordenar usando el nombre del producto asociado a la clave única
        sortedEntries.sort(Comparator.comparing(entry -> productosUnicos.get(entry.getKey()).getNombre()));

        System.out.println("DEBUG (VentaStateService): Preparando items para UI. Items agrupados: " + sortedEntries.size());

        for (Map.Entry<String, Integer> entry : sortedEntries) {
            Producto productoInfo = productosUnicos.get(entry.getKey());
            if (productoInfo != null) {
                String linea = String.format("%d x %s ($%.2f c/u)",
                        entry.getValue(), productoInfo.getNombre(), productoInfo.getPrecioVenta());
                itemsToAdd.add(linea);
                System.out.println("  -> Item a añadir: " + linea);
            } else {
                System.err.println("WARN (VentaStateService): No se encontró info para la clave: " + entry.getKey());
            }
        }

        // --- Actualizar la Interfaz Gráfica en el Hilo de JavaFX ---
        Platform.runLater(() -> {
            try {
                // Actualizar la lista observable (esto refresca el ListView vinculado)
                ticketItems.setAll(itemsToAdd);

                // Actualizar el texto del total
                totalText = String.format("Total: $%.2f", total);
                if (totalLabelRef != null) {
                    totalLabelRef.setText(totalText); // Actualiza el label si la referencia existe
                } else {
                    // Advertir si el label no está seteado, pero no es crítico si no se necesita mostrar ahí
                    // System.err.println("WARN (VentaStateService): totalLabelRef es NULL al actualizar UI.");
                }
                // El ListView (ticketListViewRef) se actualiza automáticamente porque está vinculado a ticketItems

                System.out.println("DEBUG (VentaStateService): UI de venta actualizada en Platform.runLater. Items: " + ticketItems.size() + ", Total: " + totalText);

            } catch (Exception e) {
                System.err.println("ERROR CRITICO durante Platform.runLater en VentaStateService.actualizarUIVenta: " + e.getMessage());
                e.printStackTrace();
            }
        });
    } // Fin del método actualizarUIVenta

} // Fin de la clase VentaStateService