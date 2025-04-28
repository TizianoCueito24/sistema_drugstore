package com.example.drugstore2.controller;

import com.example.drugstore2.model.TipoMovimientoCaja;
import com.example.drugstore2.service.CajaService;
import com.example.drugstore2.util.AlertUtil;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.layout.GridPane;
import javafx.scene.paint.Color;

import java.text.NumberFormat;
import java.text.ParseException;
import java.util.Locale;
import java.util.Optional;

public class CajaController {

    private CajaService cajaService;
    private final NumberFormat numberFormat = NumberFormat.getNumberInstance(Locale.getDefault());

    @FXML private Label cajaEstadoCajaLabel;
    @FXML private TextField cajaSaldoInicialField;
    @FXML private Button cajaIniciarButton;

    @FXML private GridPane cajaMovGrid;
    @FXML private ComboBox<TipoMovimientoCaja> cajaTipoMovComboBox;
    @FXML private TextField cajaMontoMovField;
    @FXML private TextField cajaDescMovField;
    @FXML private Button cajaRegistrarMovButton;

    @FXML private GridPane cajaCerrarGrid;
    @FXML private Button cajaCalcularSaldoButton;
    @FXML private Label cajaSaldoCalculadoLabel;
    @FXML private TextField cajaSaldoRealField;
    @FXML private Button cajaCerrarButton;

    @FXML
    public void initialize() {
        // Configurar ComboBox de tipos de movimiento manuales
        cajaTipoMovComboBox.getItems().addAll(
                TipoMovimientoCaja.GASTO,
                TipoMovimientoCaja.RETIRO,
                TipoMovimientoCaja.INGRESO_EXTRA
        );
        cajaTipoMovComboBox.setPromptText("Seleccione Tipo");

        // Configurar validadores numéricos
        configurarValidadores();

        // El estado inicial se actualiza cuando se inyecta el servicio
        cajaEstadoCajaLabel.setText("Estado: Verificando servicio...");
        disableUIElements(true); // Deshabilitar todo inicialmente
    }

    public void setCajaService(CajaService cajaService) {
        this.cajaService = cajaService;
        actualizarEstadoCajaUI(); // Actualizar UI una vez que el servicio está listo
    }

    private void configurarValidadores() {
        // Permitir solo números (enteros o decimales con punto o coma)
        String numericPattern = "\\d*([\\.,]\\d*)?";
        cajaSaldoInicialField.textProperty().addListener((obs, ov, nv) -> { if (!nv.matches(numericPattern)) cajaSaldoInicialField.setText(ov); });
        cajaMontoMovField.textProperty().addListener((obs, ov, nv) -> { if (!nv.matches(numericPattern)) cajaMontoMovField.setText(ov); });
        cajaSaldoRealField.textProperty().addListener((obs, ov, nv) -> { if (!nv.matches(numericPattern)) cajaSaldoRealField.setText(ov); });
    }

    // Método público para actualizar desde MainApp al seleccionar pestaña
    public void actualizarEstadoCajaUI() {
        if (cajaService == null) {
            cajaEstadoCajaLabel.setText("Estado: Error - Servicio no disponible");
            cajaEstadoCajaLabel.setTextFill(Color.RED);
            disableUIElements(true);
            return;
        }

        boolean sesionActiva = cajaService.haySesionActiva();
        cajaIniciarButton.setDisable(sesionActiva);
        cajaSaldoInicialField.setDisable(sesionActiva);

        cajaMovGrid.setDisable(!sesionActiva);
        cajaCerrarGrid.setDisable(!sesionActiva);

        if (sesionActiva) {
            cajaEstadoCajaLabel.setText("Estado: CAJA ABIERTA (Sesión ID: " + cajaService.getSesionActivaId() + ")");
            cajaEstadoCajaLabel.setTextFill(Color.DARKGREEN); // Verde oscuro
            actualizarSaldoCalculadoUI(); // Actualizar saldo calculado al mostrar
        } else {
            cajaEstadoCajaLabel.setText("Estado: CAJA CERRADA");
            cajaEstadoCajaLabel.setTextFill(Color.RED);
            cajaSaldoCalculadoLabel.setText("Saldo Calculado: $---.--"); // Resetear saldo calculado
            cajaSaldoRealField.clear(); // Limpiar saldo real
        }
    }

    // Habilita/Deshabilita elementos según si el servicio está listo
    private void disableUIElements(boolean disable) {
        cajaIniciarButton.setDisable(disable);
        cajaSaldoInicialField.setDisable(disable);
        cajaMovGrid.setDisable(disable);
        cajaCerrarGrid.setDisable(disable);
    }

    private void actualizarSaldoCalculadoUI() {
        if (cajaService != null && cajaService.haySesionActiva()) {
            double saldoSistema = cajaService.obtenerSaldoCalculado();
            cajaSaldoCalculadoLabel.setText(String.format("Saldo Calculado: $%.2f", saldoSistema));
        } else {
            cajaSaldoCalculadoLabel.setText("Saldo Calculado: $---.--");
        }
    }


    @FXML
    void handleIniciarCaja(ActionEvent event) {
        String saldoStr = cajaSaldoInicialField.getText().trim();
        if (saldoStr.isEmpty()) {
            AlertUtil.showAlert(Alert.AlertType.WARNING, "Falta Saldo", "Ingrese el saldo inicial en efectivo para abrir la caja.");
            cajaSaldoInicialField.requestFocus();
            return;
        }
        try {
            double saldoInicial = parseDouble(saldoStr);
            if (saldoInicial < 0) {
                AlertUtil.showAlert(Alert.AlertType.ERROR, "Saldo Inválido", "El saldo inicial no puede ser negativo.");
                return;
            }

            boolean exito = cajaService.iniciarSesion(saldoInicial);
            if (exito) {
                AlertUtil.showAlert(Alert.AlertType.INFORMATION, "Caja Iniciada", "Se ha iniciado una nueva sesión de caja con ID: " + cajaService.getSesionActivaId());
                cajaSaldoInicialField.clear();
                actualizarEstadoCajaUI(); // Actualiza labels y deshabilita/habilita controles
            } else {
                // El servicio ya debería haber mostrado un error si falló (ej. sesión ya activa)
                AlertUtil.showAlert(Alert.AlertType.ERROR, "Error al Iniciar", "No se pudo iniciar la sesión de caja. Verifique si ya hay una activa o revise los logs.");
            }

        } catch (NumberFormatException | ParseException e) {
            AlertUtil.showAlert(Alert.AlertType.ERROR, "Formato Incorrecto", "El saldo inicial debe ser un número válido (use '.' o ',' como separador decimal según su configuración).");
        } catch (Exception e) {
            AlertUtil.showAlert(Alert.AlertType.ERROR, "Error Inesperado", "Ocurrió un error al iniciar la caja:\n" + e.getMessage());
            e.printStackTrace();
        }
    }

    @FXML
    void handleRegistrarMovimiento(ActionEvent event) {
        TipoMovimientoCaja tipo = cajaTipoMovComboBox.getValue();
        String montoStr = cajaMontoMovField.getText().trim();
        String desc = cajaDescMovField.getText().trim();

        if (tipo == null || montoStr.isEmpty()) {
            AlertUtil.showAlert(Alert.AlertType.WARNING, "Datos Incompletos", "Seleccione el Tipo de movimiento e ingrese el Monto.");
            return;
        }
        if ((tipo == TipoMovimientoCaja.GASTO || tipo == TipoMovimientoCaja.RETIRO) && desc.isEmpty()) {
            AlertUtil.showAlert(Alert.AlertType.WARNING, "Descripción Requerida", "Ingrese una descripción para los movimientos de Gasto o Retiro.");
            cajaDescMovField.requestFocus();
            return;
        }

        try {
            double monto = parseDouble(montoStr);
            if (monto <= 0) {
                AlertUtil.showAlert(Alert.AlertType.ERROR, "Monto Inválido", "El monto del movimiento debe ser mayor a cero.");
                return;
            }

            // El servicio CajaService se encarga de hacer el monto negativo si es GASTO o RETIRO
            boolean exito = cajaService.registrarMovimiento(tipo, monto, desc, null); // null para ventaId aquí

            if (exito) {
                AlertUtil.showAlert(Alert.AlertType.INFORMATION, "Movimiento Registrado",
                        tipo.getDescripcion() + " por $" + String.format("%.2f", monto) + " registrado correctamente.");
                // Limpiar formulario de movimiento
                cajaTipoMovComboBox.setValue(null);
                cajaMontoMovField.clear();
                cajaDescMovField.clear();
                actualizarSaldoCalculadoUI(); // Actualizar saldo en la UI
            } else {
                AlertUtil.showAlert(Alert.AlertType.ERROR, "Error al Registrar", "No se pudo registrar el movimiento. Verifique que la sesión de caja esté activa.");
            }

        } catch (NumberFormatException | ParseException e) {
            AlertUtil.showAlert(Alert.AlertType.ERROR, "Formato Incorrecto", "El monto debe ser un número válido.");
        } catch (Exception e) {
            AlertUtil.showAlert(Alert.AlertType.ERROR, "Error Inesperado", "Ocurrió un error al registrar el movimiento:\n" + e.getMessage());
            e.printStackTrace();
        }
    }

    @FXML
    void handleCalcularSaldo(ActionEvent event) {
        actualizarSaldoCalculadoUI();
        if (cajaService.haySesionActiva()) {
            AlertUtil.showAlert(Alert.AlertType.INFORMATION, "Saldo Calculado", "Se ha actualizado el saldo calculado según los movimientos registrados.");
        } else {
            AlertUtil.showAlert(Alert.AlertType.WARNING, "Caja Cerrada", "No hay una sesión de caja activa para calcular el saldo.");
        }
    }

    @FXML
    void handleCerrarCaja(ActionEvent event) {
        String saldoRealStr = cajaSaldoRealField.getText().trim();
        if (saldoRealStr.isEmpty()) {
            AlertUtil.showAlert(Alert.AlertType.WARNING, "Falta Saldo Real", "Ingrese el monto de efectivo contado para realizar el arqueo y cierre.");
            cajaSaldoRealField.requestFocus();
            return;
        }

        try {
            double saldoReal = parseDouble(saldoRealStr);
            if (saldoReal < 0) {
                AlertUtil.showAlert(Alert.AlertType.ERROR, "Saldo Inválido", "El saldo real contado no puede ser negativo.");
                return;
            }

            // Obtener saldo calculado ANTES de mostrar confirmación
            double saldoSistema = cajaService.obtenerSaldoCalculado();
            double diferencia = saldoReal - saldoSistema;
            String estadoDiferencia = (diferencia == 0) ? "OK" : (diferencia > 0 ? "SOBRANTE" : "FALTANTE");

            // Confirmación detallada
            Alert confirmacion = new Alert(Alert.AlertType.CONFIRMATION);
            confirmacion.setTitle("Confirmar Cierre de Caja");
            confirmacion.setHeaderText("Va a cerrar la caja con los siguientes valores:");
            confirmacion.setContentText(
                    String.format("Saldo Sistema Calculado: $%.2f%n", saldoSistema) +
                            String.format("Saldo Real Contado:      $%.2f%n", saldoReal) +
                            String.format("Diferencia:              $%.2f (%s)%n%n", Math.abs(diferencia), estadoDiferencia) +
                            "¿Está seguro? Esta acción finalizará la sesión actual y no se puede deshacer."
            );

            Optional<ButtonType> resultado = confirmacion.showAndWait();

            if (resultado.isPresent() && resultado.get() == ButtonType.OK) {
                boolean exito = cajaService.cerrarSesion(saldoReal);
                if (exito) {
                    AlertUtil.showAlert(Alert.AlertType.INFORMATION, "Caja Cerrada", "La sesión de caja (ID: " + cajaService.getSesionActivaId() + " - ahora cerrada) ha sido finalizada exitosamente.");
                    // No limpiar saldo calculado aquí, se hace en actualizarEstadoCajaUI
                    actualizarEstadoCajaUI(); // Actualiza labels y controles
                } else {
                    AlertUtil.showAlert(Alert.AlertType.ERROR, "Error al Cerrar", "No se pudo cerrar la sesión de caja. Verifique los logs.");
                }
            }

        } catch (NumberFormatException | ParseException e) {
            AlertUtil.showAlert(Alert.AlertType.ERROR, "Formato Incorrecto", "El saldo real contado debe ser un número válido.");
        } catch (Exception e) {
            AlertUtil.showAlert(Alert.AlertType.ERROR, "Error Inesperado", "Ocurrió un error al cerrar la caja:\n" + e.getMessage());
            e.printStackTrace();
        }
    }

    // Helper para parsear doubles aceptando coma o punto
    private double parseDouble(String value) throws ParseException {
        // Reemplazar coma por punto para asegurar compatibilidad
        String cleanValue = value.trim().replace(',', '.');
        return numberFormat.parse(cleanValue).doubleValue();
    }
}