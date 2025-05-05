package com.cinema.app;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;

import java.io.IOException;
import java.net.URL;

public class MainApp extends Application {

    private MainController controller; // Keep a reference to the controller

    @Override
    public void start(Stage primaryStage) throws IOException {
        URL fxmlLocation = getClass().getResource("/com/cinema/app/main.fxml");
        if (fxmlLocation == null) {
            System.err.println("Cannot find FXML file. Check the path.");
            return;
        }
        System.out.println("Loading FXML from: " + fxmlLocation);
        FXMLLoader loader = new FXMLLoader(fxmlLocation);
        Parent root = loader.load();

        // Get the controller instance
        controller = loader.getController();

        primaryStage.setTitle("Réservation Cinéma");
        primaryStage.setScene(new Scene(root, 600, 400)); // Adjust size as needed
        primaryStage.show();
    }

    @Override
    public void stop() throws Exception {
        // Called when the application is closed
        if (controller != null) {
            controller.closeConnection(); // Close the database connection
        }
        super.stop();
    }

    public static void main(String[] args) {
        launch(args);
    }
}
