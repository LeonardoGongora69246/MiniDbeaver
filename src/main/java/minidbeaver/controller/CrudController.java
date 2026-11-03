package minidbeaver.controller;

import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Priority;
import javafx.stage.Stage;
import minidbeaver.model.RegistroDao;
import minidbeaver.model.RegistroDao.Columna;
import minidbeaver.model.RegistroDao.Fila;
import minidbeaver.model.RegistroDao.Resultado;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.function.Consumer;
import java.util.stream.Collectors;

public class CrudController {

    @FXML private Label lblTitulo;
    @FXML private Label lblEstado;
    @FXML private TableView<Fila> tablaRegistros;
    @FXML private GridPane formulario;
    @FXML private Button btnAgregar;
    @FXML private Button btnEditar;
    @FXML private Button btnEliminar;
    @FXML private Button btnLimpiar;
    @FXML private Button btnRefrescar;
    @FXML private Button btnVolver;
    @FXML private Button btnBuscar;
    @FXML private Button btnQuitarFiltro;
    @FXML private ComboBox<String> cmbColumna;
    @FXML private TextField txtBuscar;
    @FXML private ProgressIndicator progress;

    private final RegistroDao dao = new RegistroDao();

    private String nombreTabla;
    private List<Columna> columnas;
    private final List<TextField> campos = new ArrayList<>();

    @FXML
    private void initialize() {
        actualizarBotones();

        tablaRegistros.getSelectionModel().selectedItemProperty().addListener(
                (obs, anterior, actual) -> {
                    if (actual != null) {
                        llenarFormulario(actual);
                    }
                    actualizarBotones();
                });
    }

    // ---------------------------------------------------------------
    // Navegación
    // ---------------------------------------------------------------

    /** Abre la pantalla CRUD de una tabla ya descargada. */
    public static void abrir(Stage stage, String tabla) throws Exception {
        FXMLLoader loader = new FXMLLoader(
                CrudController.class.getResource("/minidbeaver/view/crud.fxml"));
        Scene escena = new Scene(loader.load());

        CrudController controlador = loader.getController();
        controlador.setTabla(tabla);

        stage.setResizable(true);
        stage.setScene(escena);
        stage.sizeToScene();
        stage.centerOnScreen();
    }

    /** Lo llama quien abre la pantalla, indicando qué tabla editar. */
    public void setTabla(String tabla) {
        this.nombreTabla = tabla;
        lblTitulo.setText("Tabla: " + tabla);
        cargar();
    }

    @FXML
    private void onVolver() {
        try {
            FXMLLoader loader = new FXMLLoader(
                    getClass().getResource("/minidbeaver/view/Tablas.fxml"));
            Stage stage = (Stage) tablaRegistros.getScene().getWindow();
            stage.setScene(new Scene(loader.load()));
            stage.setResizable(false);
            stage.sizeToScene();
            stage.centerOnScreen();
        } catch (Exception ex) {
            mostrarError("No se pudo volver a la pantalla de tablas", ex);
        }
    }

    // ---------------------------------------------------------------
    // Carga de datos
    // ---------------------------------------------------------------

    private void cargar() {
        lblEstado.setText("Cargando registros...");
        correr(() -> dao.consultar(nombreTabla), this::mostrarResultado,
                "No se pudieron cargar los registros");
    }

    private void mostrarResultado(Resultado resultado) {
        if (columnas == null) {
            columnas = resultado.getColumnas();
            construirColumnasTabla();
            construirFormulario();

            cmbColumna.getItems().setAll(columnas.stream()
                    .map(Columna::getNombre)
                    .collect(Collectors.toList()));
            cmbColumna.getSelectionModel().selectFirst();
        }

        tablaRegistros.setItems(FXCollections.observableArrayList(resultado.getFilas()));
        tablaRegistros.getSelectionModel().clearSelection();
        actualizarBotones();

        lblEstado.getStyleClass().remove("estado-error");
        lblEstado.setText("Registros: " + resultado.getFilas().size());
    }

    private void construirColumnasTabla() {
        tablaRegistros.getColumns().clear();

        for (int i = 0; i < columnas.size(); i++) {
            final int indice = i;
            Columna col = columnas.get(i);

            String titulo = col.getNombre() + (col.isPrimaryKey() ? " 🔑" : "");
            TableColumn<Fila, String> tc = new TableColumn<>(titulo);
            tc.setCellValueFactory(celda -> new SimpleStringProperty(
                    comoTexto(celda.getValue().getValores().get(indice), true)));
            tc.setPrefWidth(130);
            tablaRegistros.getColumns().add(tc);
        }
    }

    private void construirFormulario() {
        formulario.getChildren().clear();
        campos.clear();

        for (int i = 0; i < columnas.size(); i++) {
            Columna col = columnas.get(i);

            String etiqueta = col.getNombre() + (col.isObligatoria() ? " *" : "");
            Label lbl = new Label(etiqueta);
            lbl.getStyleClass().add("etiqueta-campo");

            Label tipo = new Label(col.getTipo() + (col.isPrimaryKey() ? " · PK" : ""));
            tipo.getStyleClass().add("etiqueta-tipo");

            TextField campo = new TextField();
            campo.setPrefWidth(180);
            GridPane.setHgrow(campo, Priority.ALWAYS);

            if (col.isBlob()) {
                campo.setDisable(true);
                campo.setPromptText("[BLOB] no editable");
            }

            formulario.add(lbl, 0, i * 2);
            formulario.add(tipo, 0, i * 2 + 1);
            formulario.add(campo, 1, i * 2, 1, 2);
            campos.add(campo);
        }
    }

    // ---------------------------------------------------------------
    // Acciones CRUD
    // ---------------------------------------------------------------

    @FXML
    private void onAgregar() {
        if (!validar(true)) {
            return;
        }

        List<String> nombres = new ArrayList<>();
        List<String> textos = new ArrayList<>();
        recolectar(nombres, textos, true);

        ejecutar(() -> dao.insertar(nombreTabla, nombres, textos), "Registro agregado");
    }

    @FXML
    private void onEditar() {
        Fila seleccionada = tablaRegistros.getSelectionModel().getSelectedItem();
        if (seleccionada == null) {
            mostrarInfo("Selecciona un registro de la tabla para editarlo.");
            return;
        }
        if (!validar(false)) {
            return;
        }

        List<String> nombres = new ArrayList<>();
        List<String> textos = new ArrayList<>();
        recolectar(nombres, textos, false);

        if (nombres.isEmpty()) {
            mostrarInfo("No hay columnas editables en esta tabla.");
            return;
        }

        long rowid = seleccionada.getRowid();
        ejecutar(() -> dao.actualizar(nombreTabla, rowid, nombres, textos), "Registro actualizado");
    }

    @FXML
    private void onEliminar() {
        Fila seleccionada = tablaRegistros.getSelectionModel().getSelectedItem();
        if (seleccionada == null) {
            mostrarInfo("Selecciona un registro de la tabla para eliminarlo.");
            return;
        }

        Alert confirmacion = new Alert(Alert.AlertType.CONFIRMATION);
        confirmacion.setTitle("Eliminar registro");
        confirmacion.setHeaderText("¿Eliminar el registro seleccionado?");
        confirmacion.setContentText("Esta acción no se puede deshacer.");

        var respuesta = confirmacion.showAndWait();
        if (respuesta.isEmpty() || respuesta.get() != ButtonType.OK) {
            return;
        }

        long rowid = seleccionada.getRowid();
        ejecutar(() -> dao.eliminar(nombreTabla, rowid), "Registro eliminado");
    }

    @FXML
    private void onLimpiar() {
        tablaRegistros.getSelectionModel().clearSelection();
        for (TextField c : campos) {
            if (!c.isDisabled()) {
                c.clear();
            }
        }
        actualizarBotones();
    }

    @FXML
    private void onRefrescar() {
        txtBuscar.clear();
        cargar();
    }

    // ---------------------------------------------------------------
    // Búsqueda (Read con filtro)
    // ---------------------------------------------------------------

    @FXML
    private void onBuscar() {
        String texto = txtBuscar.getText();
        String columna = cmbColumna.getValue();

        if (texto == null || texto.isBlank() || columna == null) {
            cargar();   // sin texto, muestra todo
            return;
        }

        correr(() -> dao.buscar(nombreTabla, columna, texto), resultado -> {
            mostrarResultado(resultado);
            lblEstado.setText("Resultados de la búsqueda: " + resultado.getFilas().size());
        }, "No se pudo realizar la búsqueda");
    }

    @FXML
    private void onQuitarFiltro() {
        txtBuscar.clear();
        cargar();
    }

    // ---------------------------------------------------------------
    // Validación y formulario
    // ---------------------------------------------------------------

    /**
     * Valida los campos del formulario.
     * Un entero que sea clave primaria puede quedar vacío al agregar
     * (SQLite le asigna el siguiente número automáticamente).
     */
    private boolean validar(boolean esNuevo) {
        for (int i = 0; i < columnas.size(); i++) {
            Columna col = columnas.get(i);
            TextField campo = campos.get(i);

            if (col.isBlob()) {
                continue;
            }

            boolean vacio = campo.getText() == null || campo.getText().trim().isEmpty();
            if (!vacio) {
                continue;
            }

            boolean pkAutomatica = esNuevo && col.isPrimaryKey() && col.isEntero();

            if ((col.isObligatoria() || col.isPrimaryKey()) && !pkAutomatica) {
                mostrarInfo("La columna \"" + col.getNombre() + "\" es obligatoria.");
                campo.requestFocus();
                return false;
            }
        }
        return true;
    }

    /** Junta nombres y textos de las columnas no BLOB. */
    private void recolectar(List<String> nombres, List<String> textos, boolean esNuevo) {
        for (int i = 0; i < columnas.size(); i++) {
            Columna col = columnas.get(i);
            if (col.isBlob()) {
                continue;
            }

            String texto = campos.get(i).getText();

            // Al agregar, una PK entera vacía se omite para que sea automática
            if (esNuevo && col.isPrimaryKey() && col.isEntero()
                    && (texto == null || texto.trim().isEmpty())) {
                continue;
            }

            nombres.add(col.getNombre());
            textos.add(texto);
        }
    }

    private void llenarFormulario(Fila fila) {
        for (int i = 0; i < campos.size(); i++) {
            campos.get(i).setText(comoTexto(fila.getValores().get(i), false));
        }
    }

    private void actualizarBotones() {
        boolean haySeleccion = tablaRegistros.getSelectionModel().getSelectedItem() != null;
        boolean ocupado = progress.isVisible();

        btnAgregar.setDisable(ocupado);
        btnEditar.setDisable(ocupado || !haySeleccion);
        btnEliminar.setDisable(ocupado || !haySeleccion);
        btnLimpiar.setDisable(ocupado);
        btnRefrescar.setDisable(ocupado);
        btnVolver.setDisable(ocupado);
        btnBuscar.setDisable(ocupado);
        btnQuitarFiltro.setDisable(ocupado);
    }

    private String comoTexto(Object valor, boolean paraTabla) {
        if (valor == null) {
            return paraTabla ? "NULL" : "";
        }
        if (valor instanceof byte[] bytes) {
            return "[BLOB " + bytes.length + " bytes]";
        }
        return String.valueOf(valor);
    }

    // ---------------------------------------------------------------
    // Ejecución en segundo plano
    // ---------------------------------------------------------------

    private interface AccionSql {
        void ejecutar() throws Exception;
    }

    /** Ejecuta una operación de escritura y después recarga la tabla. */
    private void ejecutar(AccionSql accion, String mensajeOk) {
        correr(() -> {
            accion.ejecutar();
            return dao.consultar(nombreTabla);
        }, resultado -> {
            txtBuscar.clear();   // la tabla se recarga completa
            mostrarResultado(resultado);
            onLimpiar();
            lblEstado.setText(mensajeOk + " · Registros: " + resultado.getFilas().size());
        }, "No se pudo completar la operación");
    }

    private <T> void correr(Callable<T> trabajo, Consumer<T> alExito, String tituloError) {
        Task<T> tarea = new Task<>() {
            @Override
            protected T call() throws Exception {
                return trabajo.call();
            }
        };

        setOcupado(true);

        tarea.setOnSucceeded(e -> {
            setOcupado(false);
            alExito.accept(tarea.getValue());
        });

        tarea.setOnFailed(e -> {
            setOcupado(false);
            lblEstado.setText("Error");
            mostrarError(tituloError, tarea.getException());
        });

        Thread hilo = new Thread(tarea);
        hilo.setDaemon(true);
        hilo.start();
    }

    private void setOcupado(boolean ocupado) {
        progress.setVisible(ocupado);
        progress.setManaged(ocupado);
        actualizarBotones();
    }

    // ---------------------------------------------------------------
    // Mensajes
    // ---------------------------------------------------------------

    private void mostrarError(String titulo, Throwable ex) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle("Error");
        alert.setHeaderText(titulo);

        Throwable causa = ex;
        while (causa != null && causa.getCause() != null) {
            causa = causa.getCause();
        }
        alert.setContentText(causa == null ? "Error desconocido" : String.valueOf(causa.getMessage()));
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