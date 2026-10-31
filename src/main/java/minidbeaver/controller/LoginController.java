package minidbeaver.controller;

import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.stage.Stage;
import minidbeaver.model.ConexionConfig;
import minidbeaver.model.SesionApp;

public class LoginController {

    // SSH
    @FXML private TextField txtHost;
    @FXML private TextField txtPuerto;
    @FXML private TextField txtUsuario;
    @FXML private PasswordField txtPassword;

    // Base de datos
    @FXML private TextField txtDbHost;
    @FXML private TextField txtDbNombre;
    @FXML private TextField txtDbUsuario;
    @FXML private PasswordField txtDbPassword;

    @FXML private Button btnConectar;
    @FXML private Label lblEstado;

    @FXML
    private void onConectar() {
        // Validar campos vacíos
        if (vacio(txtHost) || vacio(txtUsuario) || vacio(txtPassword)
                || vacio(txtDbHost) || vacio(txtDbNombre) || vacio(txtDbUsuario)) {
            mostrarMensaje("Llena todos los campos", true);
            return;
        }

        //Validar puerto
        int puerto;
        try {
            String texto = txtPuerto.getText().trim();
            puerto = texto.isEmpty() ? 22 : Integer.parseInt(texto);
        } catch (NumberFormatException e) {
            mostrarMensaje("El puerto debe ser un número", true);
            return;
        }

        ConexionConfig cfg = new ConexionConfig(
                txtHost.getText().trim(), puerto,
                txtUsuario.getText().trim(), txtPassword.getText(),
                txtDbHost.getText().trim(), txtDbNombre.getText().trim(),
                txtDbUsuario.getText().trim(), txtDbPassword.getText());

        // Abrir tunel en otro hilo
        btnConectar.setDisable(true);
        mostrarMensaje("Conectando...", false);

        Task<Void> tarea = new Task<>() {
            @Override
            protected Void call() throws Exception {
                SesionApp.get().iniciar(cfg);
                return null;
            }
        };
        tarea.setOnSucceeded(e -> abrirPantallaTablas());
        tarea.setOnFailed(e -> {
            btnConectar.setDisable(false);
            mostrarMensaje(mensajeError(tarea.getException()), true);
        });

        Thread hilo = new Thread(tarea);
        hilo.setDaemon(true);
        hilo.start();
    }

    private void abrirPantallaTablas() {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/minidbeaver/view/Tablas.fxml"));
            Stage stage = (Stage) btnConectar.getScene().getWindow();
            stage.setScene(new Scene(loader.load()));
            stage.centerOnScreen();
        } catch (Exception ex) {
            btnConectar.setDisable(false);
            mostrarMensaje("No se pudo abrir la siguiente pantalla", true);
            ex.printStackTrace();
        }
    }

    private String mensajeError(Throwable ex) {
        String msg = String.valueOf(ex.getMessage()).toLowerCase();
        if (msg.contains("auth fail")) return "Usuario o contraseña incorrectos";
        if (msg.contains("cannot be bound")) return "El puerto local 3307 ya está en uso";
        return "No se pudo conectar al servidor";
    }

    private void mostrarMensaje(String texto, boolean esError) {
        lblEstado.setStyle(esError ? "-fx-text-fill: #c0392b;" : "-fx-text-fill: #333;");
        lblEstado.setText(texto);
    }

    private boolean vacio(TextField campo) {
        return campo.getText() == null || campo.getText().trim().isEmpty();
    }
}