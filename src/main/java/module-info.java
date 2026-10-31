module mx.uacam.minidbeaver {
    requires javafx.controls;
    requires javafx.fxml;
    requires java.sql;
    requires com.jcraft.jsch;
    requires org.mariadb.jdbc;
    requires org.xerial.sqlitejdbc;

    opens minidbeaver.controller to javafx.fxml;
    exports minidbeaver;
}
