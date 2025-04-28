module com.example.drugstore2 {

    requires javafx.controls;
    requires javafx.fxml;
    requires javafx.graphics; // Ya no se necesita javafx.web según tu pom

    // Optional UI Libraries (descomenta si las usas)
    requires org.controlsfx.controls;
    requires com.dlsc.formsfx;
    requires net.synedra.validatorfx;
    requires org.kordamp.ikonli.javafx;
    requires org.kordamp.bootstrapfx.core;
    requires eu.hansolo.tilesfx;
    requires com.almasb.fxgl.all;

    // Database
    requires java.sql;

    // CSV Handling
    requires org.apache.commons.csv;

    // MySQL Driver (Automático por nombre)
    requires mysql.connector.j; // Nombre del módulo automático

    // Abrir paquetes a JavaFX FXML para reflexión
    opens com.example.drugstore2 to javafx.fxml;
    opens com.example.drugstore2.controller to javafx.fxml;
    opens com.example.drugstore2.model to javafx.base; // Para PropertyValueFactory en modelos

    // Exportar paquete principal (y otros si es necesario)
    exports com.example.drugstore2;
    exports com.example.drugstore2.controller;
    exports com.example.drugstore2.model;
    exports com.example.drugstore2.service;
    exports com.example.drugstore2.util;
}