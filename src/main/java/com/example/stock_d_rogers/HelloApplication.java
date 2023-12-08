package com.example.stock_d_rogers;

import javafx.application.Application;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.DatePicker;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.text.Text;
import javafx.stage.DirectoryChooser;
import javafx.stage.Stage;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.prefs.Preferences;
import java.io.*;

import java.net.URL;
import java.net.URLConnection;

public class HelloApplication extends Application {
    private File currentDirectory;
    private Preferences prefs;

    private static void downloadFile(String fileURL, File saveDir) throws IOException {
        URL url = new URL(fileURL);
        URLConnection connection = url.openConnection();

        // Extract the date from the URL and use it as the file name
        String fileName = fileURL.substring(fileURL.lastIndexOf('/') + 1);
        String saveFilePath = saveDir.getAbsolutePath() + File.separator + fileName;

        try (BufferedInputStream in = new BufferedInputStream(connection.getInputStream());
             FileOutputStream fileOutputStream = new FileOutputStream(saveFilePath)) {
            byte dataBuffer[] = new byte[1024];
            int bytesRead;
            while ((bytesRead = in.read(dataBuffer, 0, 1024)) != -1) {
                fileOutputStream.write(dataBuffer, 0, bytesRead);
            }
        }
        System.out.println("Download completed. File saved as " + saveFilePath);
    }

    private List<LocalDate> getDatesInRange(LocalDate start, LocalDate end) {
        List<LocalDate> dates = new ArrayList<>();
        long daysBetween = ChronoUnit.DAYS.between(start, end);
        for (int i = 0; i <= daysBetween; i++) {
            dates.add(start.plusDays(i));
        }
        return dates;
    }

    @Override
    public void start(Stage stage) {
        prefs = Preferences.userNodeForPackage(HelloApplication.class);
        String defaultDirectory = prefs.get("downloadDirectory", System.getProperty("user.home") + File.separator + "Downloads");
        currentDirectory = new File(defaultDirectory);
        String fileURL = "https://dps.psx.com.pk/download/mkt_summary/2023-12-07.Z";

        VBox root = new VBox();
        root.setPadding(new Insets(20));

        // directory
        HBox directoryHBox = new HBox();
        Text directoryPath = new Text("Download Directory: " + currentDirectory.getAbsolutePath());
        Button changeDirectoryBtn = new Button("Change Directory");
        DirectoryChooser directoryChooser = new DirectoryChooser();
        changeDirectoryBtn.setOnAction((event) -> {
            File selectedDirectory = directoryChooser.showDialog(stage);
            if (selectedDirectory != null) {
                currentDirectory = selectedDirectory;
                directoryPath.setText("Download Directory: " + currentDirectory.getAbsolutePath());
                prefs.put("downloadDirectory", currentDirectory.getAbsolutePath()); // Save to preferences
            }
        });

        directoryHBox.setSpacing(10);
        directoryHBox.setAlignment(Pos.CENTER);
        directoryHBox.getChildren().addAll(directoryPath, changeDirectoryBtn);

        // download
        Text successMessage = new Text();
        Button downloadBtn = new Button("Download");
        downloadBtn.setOnAction((event) -> {
            try {
                downloadFile(fileURL, currentDirectory);
                successMessage.setText("Download successful");
                successMessage.setFill(Color.GREEN);
            } catch (Exception e) {
                successMessage.setText("Download failed: " + e.getMessage());
                successMessage.setFill(Color.RED);
            }
        });

        // date selection
        VBox dateSelectionBox = new VBox();
        dateSelectionBox.setPadding(new Insets(10));
        dateSelectionBox.setSpacing(10);

        DatePicker startDatePicker = new DatePicker();
        DatePicker endDatePicker = new DatePicker();
        Button printDatesButton = new Button("Print Dates");

        printDatesButton.setOnAction(e -> {
            LocalDate startDate = startDatePicker.getValue();
            LocalDate endDate = endDatePicker.getValue();
            if (startDate != null && endDate != null && !startDate.isAfter(endDate)) {
                List<LocalDate> datesInRange = getDatesInRange(startDate, endDate);
                datesInRange.forEach(System.out::println);
            } else {
                System.out.println("Invalid date range");
            }
        });

        dateSelectionBox.getChildren().addAll(startDatePicker, endDatePicker, printDatesButton);


        // assemble javafx components
        root.setSpacing(10);
        root.getChildren().addAll(directoryHBox, successMessage, downloadBtn, dateSelectionBox);

        Scene scene = new Scene(root, 500, 500);
        stage.setTitle("Brokerage House");
        stage.setScene(scene);
        stage.show();
    }

    public static void main(String[] args) {
        launch(args);
    }
}
