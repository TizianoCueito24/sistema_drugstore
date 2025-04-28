package com.example.drugstore2.model;

import java.time.LocalDateTime;

public class CajaSesion {
    private int id;
    private LocalDateTime fechaApertura;
    private double saldoInicial;
    private LocalDateTime fechaCierre; // Null si está activa
    private Double saldoFinalCalculado; // Null si está activa
    private Double saldoFinalReal; // Null si está activa
    private Double diferencia; // Null si está activa
    private boolean activa;

    // Constructor para iniciar sesión
    public CajaSesion(LocalDateTime fechaApertura, double saldoInicial) {
        this.fechaApertura = fechaApertura;
        this.saldoInicial = saldoInicial;
        this.activa = true;
        // Los demás campos son null o se calculan al cerrar
    }

    // Constructor completo (para leer de BD)
    public CajaSesion(int id, LocalDateTime fechaApertura, double saldoInicial, LocalDateTime fechaCierre, Double saldoFinalCalculado, Double saldoFinalReal, Double diferencia, boolean activa) {
        this.id = id;
        this.fechaApertura = fechaApertura;
        this.saldoInicial = saldoInicial;
        this.fechaCierre = fechaCierre;
        this.saldoFinalCalculado = saldoFinalCalculado;
        this.saldoFinalReal = saldoFinalReal;
        this.diferencia = diferencia;
        this.activa = activa;
    }


    // Getters y Setters
    public int getId() { return id; }
    public LocalDateTime getFechaApertura() { return fechaApertura; }
    public double getSaldoInicial() { return saldoInicial; }
    public LocalDateTime getFechaCierre() { return fechaCierre; }
    public Double getSaldoFinalCalculado() { return saldoFinalCalculado; }
    public Double getSaldoFinalReal() { return saldoFinalReal; }
    public Double getDiferencia() { return diferencia; }
    public boolean isActiva() { return activa; }

    public void setId(int id) { this.id = id; }
    public void setFechaCierre(LocalDateTime fechaCierre) { this.fechaCierre = fechaCierre; }
    public void setSaldoFinalCalculado(Double saldoFinalCalculado) { this.saldoFinalCalculado = saldoFinalCalculado; }
    public void setSaldoFinalReal(Double saldoFinalReal) { this.saldoFinalReal = saldoFinalReal; }
    public void setDiferencia(Double diferencia) { this.diferencia = diferencia; }
    public void setActiva(boolean activa) { this.activa = activa; }
}