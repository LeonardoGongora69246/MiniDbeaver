/*
* UNIVERSIDAD AUTONOMA DE CAMPECHE
* PROGRAMACION AVANZADA
* Prof. Dr. Jose Aguilar Canepa
* Integrantes:
* LEONARDO GONGORA US
* URIEL QUETZ MENDOZA
* ALDAIR CANUL CABRERA
* AARON CHI UC
* */

package minidbeaver;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.stage.Stage;
import minidbeaver.model.SesionApp;

public class Main extends Application {

    @Override
    public void start(Stage stage) throws Exception {
        FXMLLoader loader = new FXMLLoader(getClass().getResource("/minidbeaver/view/Login.fxml"));
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
