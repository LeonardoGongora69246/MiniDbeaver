package minidbeaver.controller;

import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.ProgressIndicator;
import minidbeaver.model.RemoteDbDao;
import javafx.scene.control.ButtonType;
import minidbeaver.model.SqliteManager;

import java.util.List;

public class TablasController {

    @FXML private ListView<String> lstTablas;
    @FXML private Button btnAbrirCrud;
    @FXML private Button btnDescargar;
    @FXML private Button btnRefrescar;
    @FXML private Label lblEstado;
    @FXML private ProgressIndicator progress;

    private final RemoteDbDao dao = new RemoteDbDao();
    private final DescargaController descargaController = new DescargaController();
    private final SqliteManager sqlite = new SqliteManager();
    @FXML
    private void initialize() {
        lstTablas.getSelectionModel().selectedItemProperty().addListener(
                (obs, anterior, actual) ->
                        btnDescargar.setDisable(progress.isVisible() || actual == null)
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
    private void onAbrirCrud() {
        String tabla = lstTablas.getSelectionModel().getSelectedItem();
        if (tabla == null) { mostrarInfo("Selecciona una tabla primero."); return; }
        try {
            if (!sqlite.tablaExiste(tabla)) {
                mostrarInfo("Primero descarga la tabla " + tabla + ".");
                return;
            }
            CrudController.abrir((javafx.stage.Stage) lstTablas.getScene().getWindow(), tabla);
        } catch (Exception ex) {
            mostrarError("No se pudo abrir el CRUD", ex);
        }
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

        boolean sobrescribir = false;

        // Comprobar si ya existe localmente
        try {
            if (sqlite.tablaExiste(tabla)) {

                Alert confirmacion =
                        new Alert(Alert.AlertType.CONFIRMATION);

                confirmacion.setTitle("Tabla existente");
                confirmacion.setHeaderText(
                        "La tabla " + tabla + " ya existe");

                confirmacion.setContentText(
                        "¿Deseas reemplazar los datos descargados?");

                var respuesta = confirmacion.showAndWait();

                if (respuesta.isEmpty()
                        || respuesta.get() != ButtonType.OK) {
                    return;
                }

                sobrescribir = true;
            }

        } catch (Exception ex) {
            mostrarError("Error al comprobar la tabla", ex);
            return;
        }

        // Crear tarea de descarga
        Task<Void> tarea = descargaController.crearTareaDescarga(
                tabla, sobrescribir);

        // Mostrar indicador de progreso
        setCargando(true);
        lblEstado.textProperty().bind(tarea.messageProperty());

        tarea.setOnSucceeded(e -> {

            lblEstado.textProperty().unbind();
            setCargando(false);

            lblEstado.setText("Descarga completada: " + tabla);

            mostrarInfo(
                    "La tabla " + tabla +
                            " se descargó correctamente en SQLite.");
        });

        tarea.setOnFailed(e -> {

            lblEstado.textProperty().unbind();
            setCargando(false);

            lblEstado.setText("Error al descargar " + tabla);

            mostrarError(
                    "No se pudo descargar la tabla",
                    tarea.getException());
        });

        // Ejecutar sin bloquear JavaFX
        Thread hilo = new Thread(tarea);
        hilo.setDaemon(true);
        hilo.start();
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