package com.cinema.app;

import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.control.CheckBox; // Ajouté
import javafx.scene.control.Alert;
import javafx.scene.control.Alert.AlertType;
import javafx.scene.layout.BorderPane; // Ajouté
import javafx.scene.layout.GridPane;
import javafx.collections.ObservableList;
import javafx.scene.effect.GaussianBlur; // Ajouté
import javafx.scene.effect.ColorAdjust; // Ajouté
import javafx.animation.ScaleTransition; // Ajouté
import javafx.util.Duration; // Ajouté
import javafx.application.Platform; // Ajouté pour runLater

import java.net.URL;
import java.sql.*;
import java.util.ResourceBundle;

public class MainController implements Initializable {

    @FXML private BorderPane rootPane; // Injecter le conteneur racine
    @FXML private GridPane seatGridPane;
    @FXML private Label statusLabel;
    @FXML private TextField newSeatRowField;
    @FXML private TextField newSeatNumberField;
    @FXML private Button addSeatButton;
    @FXML private CheckBox deleteModeCheckBox; // Remplacement du bouton

    // --- Database Configuration --- 
    private static final String DB_URL = "jdbc:mysql://localhost:3306/cinema_db?useSSL=false&serverTimezone=UTC"; // Added timezone
    private static final String DB_USER = "root"; // Replace with your DB username
    private static final String DB_PASSWORD = ""; // Replace with your DB password
    // -----------------------------

    private Connection connection;
    // isDeleteMode est maintenant déterminé par la CheckBox

    // --- CSS Style Class Constants ---
    private static final String CSS_SEAT_BUTTON = "seat-button";
    private static final String CSS_SEAT_AVAILABLE = "seat-available";
    private static final String CSS_SEAT_RESERVED = "seat-reserved";
    private static final String CSS_SEAT_DELETE_MODE = "seat-delete-mode";

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        statusLabel.setText("Bienvenue ! Sélectionnez un siège.");
        // Appliquer les effets après que la scène soit potentiellement prête
        Platform.runLater(this::applyBackgroundEffects);
        if (connectToDB()) {
            loadSeatsFromDB();
        } else {
            // Désactiver les contrôles si la connexion échoue
            seatGridPane.setDisable(true);
            addSeatButton.setDisable(true);
            deleteModeCheckBox.setDisable(true);
            newSeatRowField.setDisable(true);
            newSeatNumberField.setDisable(true);
        }
    }

    private void applyBackgroundEffects() {
        if (rootPane == null) {
            System.err.println("rootPane is null, cannot apply effects.");
            return;
        }
        // Appliquer un effet de flou gaussien
        GaussianBlur blur = new GaussianBlur(10); // Ajustez le rayon du flou si nécessaire

        // Appliquer un ajustement de couleur pour désaturer
        ColorAdjust desaturate = new ColorAdjust();
        desaturate.setSaturation(-0.50); // Réduire la saturation de 50%
        // desaturate.setBrightness(-0.1);

        // Chaîner les effets
        blur.setInput(desaturate);

        // Appliquer l'effet au conteneur racine
        rootPane.setEffect(blur);
    }

    private boolean connectToDB() {
        try {
            // Optional: Explicitly load the driver (needed in some environments)
            // Class.forName("com.mysql.cj.jdbc.Driver");
            connection = DriverManager.getConnection(DB_URL, DB_USER, DB_PASSWORD);
            System.out.println("Database connection successful.");
            return true;
        } catch (SQLException e) {
            showError("Database Connection Error", "Could not connect to the database: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
        // catch (ClassNotFoundException e) {
        //     showError("Database Driver Error", "MySQL JDBC Driver not found: " + e.getMessage());
        //     e.printStackTrace();
        //     return false;
        // }
    }

    private void loadSeatsFromDB() {
        String query = "SELECT id, row, number, is_reserved FROM seats ORDER BY row, number";
        try (Statement stmt = connection.createStatement();
             ResultSet rs = stmt.executeQuery(query)) {

            seatGridPane.getChildren().clear();
            int gridRowIndex = 0;
            int gridColIndex = 0;
            String currentRow = "";

            while (rs.next()) {
                int id = rs.getInt("id");
                String seatRow = rs.getString("row");
                int seatNumber = rs.getInt("number");
                boolean isReserved = rs.getBoolean("is_reserved");

                if (!seatRow.equals(currentRow)) {
                    currentRow = seatRow;
                    gridRowIndex++; // Start a new row in the grid
                    gridColIndex = 0; // Reset column index for the new row
                }

                Button seatButton = createSeatButton(id, seatRow, seatNumber, isReserved);
                seatGridPane.add(seatButton, gridColIndex++, gridRowIndex);
            }

        } catch (SQLException e) {
            showError("Load Seats Error", "Error loading seats from database: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private Button createSeatButton(int id, String seatRow, int seatNumber, boolean isReserved) {
        Button seatButton = new Button(seatRow + seatNumber);
        seatButton.setUserData(new SeatInfo(id, isReserved)); // Store ID and reservation status

        // Apply base CSS class
        seatButton.getStyleClass().add(CSS_SEAT_BUTTON);

        // Apply state-specific CSS class
        updateButtonStyles(seatButton, isReserved);

        // Set the action handler
        seatButton.setOnAction(event -> handleSeatClick(seatButton));

        return seatButton;
    }

    private void updateButtonStyles(Button button, boolean isReserved) {
        ObservableList<String> styleClasses = button.getStyleClass();
        // Remove previous state classes
        styleClasses.removeAll(CSS_SEAT_AVAILABLE, CSS_SEAT_RESERVED, CSS_SEAT_DELETE_MODE);

        // Utiliser l'état de la CheckBox pour déterminer le mode
        if (deleteModeCheckBox.isSelected()) {
            styleClasses.add(CSS_SEAT_DELETE_MODE);
        } else if (isReserved) {
            styleClasses.add(CSS_SEAT_RESERVED);
        } else {
            styleClasses.add(CSS_SEAT_AVAILABLE);
        }
    }

    private void handleSeatClick(Button seatButton) {
        SeatInfo seatInfo = (SeatInfo) seatButton.getUserData();
        int seatId = seatInfo.getId();

        // Animation de clic (Bonus)
        playClickAnimation(seatButton);

        if (deleteModeCheckBox.isSelected()) {
            // --- Delete Mode --- 
            if (deleteSeatFromDB(seatId)) {
                statusLabel.setText("Siège " + seatButton.getText() + " supprimé avec succès.");
                seatGridPane.getChildren().remove(seatButton); // Remove button from grid
                // Optional: Reload all seats for perfect grid alignment after deletion
                // loadSeatsFromDB();
            } else {
                statusLabel.setText("Erreur lors de la suppression du siège " + seatButton.getText());
            }
        } else {
            // --- Reservation/Cancellation Mode --- 
            boolean currentReservationStatus = seatInfo.isReserved();
            boolean newReservationStatus = !currentReservationStatus; // Toggle status

            if (updateSeatStateInDB(seatId, newReservationStatus)) {
                // Update button state and style in UI
                seatInfo.setReserved(newReservationStatus);
                updateButtonStyles(seatButton, newReservationStatus);

                // Update status label
                String action = newReservationStatus ? "réservé" : "libéré";
                statusLabel.setText("Siège " + seatButton.getText() + " " + action + " avec succès.");
                System.out.println("Seat " + seatButton.getText() + " (ID: " + seatId + ") status updated to: " + action);
            } else {
                // Error occurred during DB update, status label might show DB error
                statusLabel.setText("Erreur lors de la mise à jour du siège " + seatButton.getText());
                // Optional: Reload seats to ensure consistency after error
                // loadSeatsFromDB();
            }
        }
    }

    // Animation de clic (Bonus)
    private void playClickAnimation(Button button) {
        ScaleTransition st = new ScaleTransition(Duration.millis(100), button);
        st.setFromX(1.0);
        st.setFromY(1.0);
        st.setToX(0.9);
        st.setToY(0.9);
        st.setAutoReverse(true);
        st.setCycleCount(2); // Rétrécit puis revient à la normale
        st.play();
    }

    private boolean updateSeatStateInDB(int seatId, boolean isReserved) {
        String updateQuery = "UPDATE seats SET is_reserved = ? WHERE id = ?";
        try (PreparedStatement pstmt = connection.prepareStatement(updateQuery)) {
            pstmt.setBoolean(1, isReserved);
            pstmt.setInt(2, seatId);
            int affectedRows = pstmt.executeUpdate();
            return affectedRows > 0;
        } catch (SQLException e) {
            showError("Database Update Error", "Error updating seat (ID: " + seatId + ") reservation status: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }

    @FXML
    private void handleAddSeatButton() {
        String row = newSeatRowField.getText().trim().toUpperCase();
        String numberStr = newSeatNumberField.getText().trim();

        if (row.isEmpty() || numberStr.isEmpty()) {
            showError("Input Error", "La ligne et le numéro ne peuvent pas être vides.");
            return;
        }

        int number;
        try {
            number = Integer.parseInt(numberStr);
            if (number <= 0) {
                 showError("Input Error", "Le numéro du siège doit être un entier positif.");
                 return;
            }
        } catch (NumberFormatException e) {
            showError("Input Error", "Le numéro du siège doit être un nombre entier valide.");
            return;
        }

        if (addSeatToDB(row, number)) {
            statusLabel.setText("Siège " + row + number + " ajouté avec succès.");
            loadSeatsFromDB(); // Refresh the grid
            newSeatRowField.clear();
            newSeatNumberField.clear();
        } else {
            // Error message is shown by addSeatToDB
            statusLabel.setText("Erreur lors de l'ajout du siège " + row + number);
        }
    }

    private boolean addSeatToDB(String row, int number) {
        String insertQuery = "INSERT INTO seats (row, number, is_reserved) VALUES (?, ?, ?)";
        try (PreparedStatement pstmt = connection.prepareStatement(insertQuery)) {
            pstmt.setString(1, row);
            pstmt.setInt(2, number);
            pstmt.setBoolean(3, false); // New seats are initially available
            int affectedRows = pstmt.executeUpdate();
            return affectedRows > 0;
        } catch (SQLIntegrityConstraintViolationException e) {
             showError("Database Error", "Le siège " + row + number + " existe déjà.");
             return false;
        } catch (SQLException e) {
            showError("Database Insert Error", "Error adding seat " + row + number + ": " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }

    // Nouvelle méthode pour gérer le changement d'état de la CheckBox
    @FXML
    private void handleDeleteModeToggle() {
        boolean isDelete = deleteModeCheckBox.isSelected();
        if (isDelete) {
            statusLabel.setText("Mode Suppression ACTIF. Cliquez sur un siège pour le supprimer.");
        } else {
            statusLabel.setText("Mode Suppression DÉSACTIVÉ.");
        }
        // Mettre à jour le style de tous les sièges pour refléter le changement de mode
        seatGridPane.getChildren().forEach(node -> {
            if (node instanceof Button) {
                Button seatButton = (Button) node;
                SeatInfo info = (SeatInfo) seatButton.getUserData();
                if (info != null) { // Ensure it's a seat button with data
                   updateButtonStyles(seatButton, info.isReserved());
                }
            }
        });
    }

    private boolean deleteSeatFromDB(int seatId) {
        String deleteQuery = "DELETE FROM seats WHERE id = ?";
        try (PreparedStatement pstmt = connection.prepareStatement(deleteQuery)) {
            pstmt.setInt(1, seatId);
            int affectedRows = pstmt.executeUpdate();
            return affectedRows > 0;
        } catch (SQLException e) {
            showError("Database Delete Error", "Error deleting seat (ID: " + seatId + "): " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }

    private void showError(String title, String message) {
        Alert alert = new Alert(AlertType.ERROR);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }

    // Helper class to store seat info in button's user data
    private static class SeatInfo {
        private final int id;
        private boolean isReserved;

        public SeatInfo(int id, boolean isReserved) {
            this.id = id;
            this.isReserved = isReserved;
        }

        public int getId() {
            return id;
        }

        public boolean isReserved() {
            return isReserved;
        }

        public void setReserved(boolean reserved) {
            isReserved = reserved;
        }
    }

    // Method to be called when the application stops to close the connection
    public void closeConnection() {
        try {
            if (connection != null && !connection.isClosed()) {
                connection.close();
                System.out.println("Database connection closed.");
            }
        } catch (SQLException e) {
            System.err.println("Error closing database connection: " + e.getMessage());
            e.printStackTrace();
        }
    }

    // Ensure connection is closed when the window is closed
    // This requires modifying MainApp to call this method
}
