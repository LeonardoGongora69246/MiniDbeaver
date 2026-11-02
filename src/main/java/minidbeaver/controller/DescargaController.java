package minidbeaver.controller;

import javafx.concurrent.Task;
import minidbeaver.model.SqliteManager;

public class DescargaController {

    private final SqliteManager sqlite = new SqliteManager();

    public Task<Void> crearTareaDescarga(
            String nombreTabla,
            boolean sobrescribir) {

        return new Task<>() {

            @Override
            protected Void call() throws Exception {

                updateMessage("Descargando tabla: " + nombreTabla);

                sqlite.guardarTabla(nombreTabla, sobrescribir);

                updateMessage("Descarga completada");

                return null;
            }
        };
    }
}