package minidbeaver;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.stage.Stage;
import minidbeaver.model.SesionApp;

public class MainPrueba extends Application {

    @Override
    public void start(Stage stage) throws Exception {
        FXMLLoader loader = new FXMLLoader(getClass().getResource("/minidbeaver/view/Tablas.fxml"));
        stage.setScene(new Scene(loader.load()));
        stage.setTitle("Mini DBeaver");
        stage.setResizable(false);
        stage.show();
    }

    //Cerrar tunel
    @Override
    public void stop() {
        SesionApp.get().cerrar();
    }

    public static void main(String[] args) {
        launch(args);
    }
}
