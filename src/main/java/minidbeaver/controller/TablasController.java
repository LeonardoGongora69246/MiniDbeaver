package minidbeaver.controller;

import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.ProgressIndicator;
import minidbeaver.model.RemoteDbDao;

import java.util.List;

public class TablasController {

    @FXML private ListView<String> lstTablas;
    @FXML private Button btnDescargar;
    @FXML private Button btnRefrescar;
    @FXML private Label lblEstado;
    @FXML private ProgressIndicator progress;

    private final RemoteDbDao dao = new RemoteDbDao();

    @FXML
    private void initialize() {
        lstTablas.getSelectionModel().selectedItemProperty().addListener(
                (obs, anterior, actual) -> btnDescargar.setDisable(actual == null)
        );

        cargarTablas();
    }

    private void cargarTablas() {
        setCargando(true);
        lblEstado.setText("Cargando tablas...");

        Task<List<String>> tarea = new Task<>() {
            @Override
            protected List<String> call() throws Exception {
                return dao.listarTablas();
            }
        };

        tarea.setOnSucceeded(e -> {
            List<String> tablas = tarea.getValue();
            lstTablas.getItems().setAll(tablas);
            setCargando(false);
            lblEstado.setText("Tablas cargadas: " + tablas.size());
        });

        tarea.setOnFailed(e -> {
            setCargando(false);
            lblEstado.setText("Error al cargar tablas");
            mostrarError("No se pudieron listar las tablas", tarea.getException());
        });

        Thread hilo = new Thread(tarea);
        hilo.setDaemon(true);
        hilo.start();
    }

    @FXML
    private void onRefrescar() {
        cargarTablas();
    }

    @FXML
    private void onDescargar() {
        String tabla = lstTablas.getSelectionModel().getSelectedItem();

        if (tabla == null) {
            mostrarInfo("Selecciona una tabla primero.");
            return;
        }

        /*
         * TODO Integrante 3:
         * Aquí debe abrirse el módulo de descarga y pasarle la tabla seleccionada.
         *
         * Contrato sugerido:
         *   List<RemoteDbDao.ColumnaInfo> columnas = dao.obtenerColumnas(tabla);
         *   RemoteDbDao.ResultadoTabla datos = dao.obtenerRegistros(tabla);
         *   SqliteManager.guardarTabla(tabla, columnas, datos.getFilas());
         */
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle("Descargar tabla");
        alert.setHeaderText("Tabla seleccionada: " + tabla);
        alert.setContentText(
                "Este botón debe abrir el módulo de descarga del Integrante 3.\n\n" +
                "Contrato disponible:\n" +
                "RemoteDbDao.obtenerColumnas(\"" + tabla + "\")\n" +
                "RemoteDbDao.obtenerRegistros(\"" + tabla + "\")\n" +
                "SqliteManager.guardarTabla(\"" + tabla + "\", columnas, filas)"
        );
        alert.showAndWait();
    }

    private void setCargando(boolean cargando) {
        btnRefrescar.setDisable(cargando);
        btnDescargar.setDisable(cargando || lstTablas.getSelectionModel().getSelectedItem() == null);
        progress.setVisible(cargando);
        progress.setManaged(cargando);
    }

    private void mostrarError(String titulo, Throwable ex) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle("Error");
        alert.setHeaderText(titulo);

        String detalle = ex == null ? "Error desconocido" : String.valueOf(ex.getMessage());
        alert.setContentText(detalle);

        alert.showAndWait();
    }

    private void mostrarInfo(String mensaje) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle("Información");
        alert.setHeaderText(null);
        alert.setContentText(mensaje);
        alert.showAndWait();
    }
}