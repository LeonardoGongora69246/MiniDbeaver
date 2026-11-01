module mx.uacam.minidbeaver {
    requires javafx.controls;
    requires javafx.fxml;
    requires java.sql;
    requires com.jcraft.jsch;
    requires org.mariadb.jdbc;
    requires org.xerial.sqlitejdbc;

    // Exportar para que el código pueda usarlos
    exports minidbeaver;
    exports minidbeaver.controller;
    exports minidbeaver.model;

    // Abrir para que JavaFX pueda instanciar los controladores por reflexión
    opens minidbeaver.controller to javafx.fxml;
    opens minidbeaver.model to javafx.base, javafx.fxml;
}