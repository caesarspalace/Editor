package com.editor;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;

import java.io.IOException;

public class AppMain extends Application {

    @Override
    public void start(Stage primaryStage) {
        try {
            // Carga la vista FXML desde el classpath
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/com/editor/editor_view.fxml"));
            Parent root = loader.load();

            // Configura la escena y la ventana principal
            Scene scene = new Scene(root, 870, 600);
            primaryStage.setTitle("Couna");
            primaryStage.setScene(scene);
            primaryStage.setMinWidth(600);
            primaryStage.setMinHeight(400);
            
            primaryStage.show();
        } catch (IOException e) {
            System.err.println("Error al cargar la interfaz FXML: " + e.getMessage());
            e.printStackTrace();
        }
    }

    public static void main(String[] args) {
        // Lanza la aplicación JavaFX
        launch(args);
    }
}