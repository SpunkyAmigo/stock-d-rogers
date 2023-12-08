package com.example.stock_d_rogers;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.concurrent.Task;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.DatePicker;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.text.Text;
import javafx.stage.DirectoryChooser;
import javafx.stage.Stage;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.prefs.Preferences;
import java.io.*;

import java.net.URL;
import java.net.URLConnection;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public class HelloApplication extends Application {
    private File currentDirectory;
    private Preferences prefs;

    private static void downloadFileAndExtractLis(String fileURL, File saveDir, String formattedDate) throws IOException {
        File tempZipFile = null;

        try {
            tempZipFile = File.createTempFile("tempZip", ".zip", saveDir);
            URL url = new URL(fileURL);
            URLConnection connection = url.openConnection();

            try (BufferedInputStream in = new BufferedInputStream(connection.getInputStream());
                 FileOutputStream fileOutputStream = new FileOutputStream(tempZipFile)) {
                byte dataBuffer[] = new byte[1024];
                int bytesRead;
                while ((bytesRead = in.read(dataBuffer, 0, 1024)) != -1) {
                    fileOutputStream.write(dataBuffer, 0, bytesRead);
                }
            }

            // Extract .lis file
            try (ZipInputStream zipInputStream = new ZipInputStream(new FileInputStream(tempZipFile))) {
                ZipEntry entry;
                while ((entry = zipInputStream.getNextEntry()) != null) {
                    if (entry.getName().endsWith(".lis")) {
                        File extractedFile = new File(saveDir, formattedDate + ".lis");
                        extractFile(zipInputStream, extractedFile);
                        break; // Assuming only one .lis file in the ZIP
                    }
                }
            }
        } finally {
            // Ensure the temporary ZIP file is deleted
            if (tempZipFile != null && !tempZipFile.delete()) {
                System.err.println("Could not delete temporary ZIP file: " + tempZipFile.getAbsolutePath());
            }
        }
    }


    private static void extractFile(ZipInputStream zipIn, File file) throws IOException {
        try (BufferedOutputStream bos = new BufferedOutputStream(new FileOutputStream(file))) {
            byte[] bytesIn = new byte[1024];
            int read;
            while ((read = zipIn.read(bytesIn)) != -1) {
                bos.write(bytesIn, 0, read);
            }
        }
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

        VBox root = new VBox();

        // display success messages
        VBox messageBox = new VBox();
        messageBox.setSpacing(5);

        Button clearMessagesBtn = new Button("Clear Messages");
        clearMessagesBtn.setOnAction(e -> messageBox.getChildren().clear());

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

        // date selection
        VBox dateSelectionBox = new VBox();
        dateSelectionBox.setSpacing(10);

        DatePicker startDatePicker = new DatePicker(LocalDate.now());
        DatePicker endDatePicker = new DatePicker(LocalDate.now());

        // download
        Text successMessage = new Text();
        Button downloadBtn = new Button("Download");
        downloadBtn.setOnAction((event) -> {
            Task<Void> downloadTask = new Task<>() {
                @Override
                protected Void call() {
                    LocalDate startDate = startDatePicker.getValue();
                    LocalDate endDate = endDatePicker.getValue();
                    if (startDate != null && endDate != null && !startDate.isAfter(endDate)) {
                        List<LocalDate> datesInRange = getDatesInRange(startDate, endDate);
                        for (LocalDate date : datesInRange) {
                            if (isCancelled()) {
                                break;
                            }
                            try {
                                String formattedDate = date.format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));
                                String dynamicURL = "https://dps.psx.com.pk/download/mkt_summary/" + formattedDate + ".Z";
                                downloadFileAndExtractLis(dynamicURL, currentDirectory, formattedDate);

                                Platform.runLater(() -> {
                                    Text message = new Text("Download successful for " + formattedDate);
                                    message.setFill(Color.GREEN);
                                    messageBox.getChildren().add(message);
                                });
                            } catch (Exception e) {
                                Platform.runLater(() -> {
                                    Text message = new Text("Download failed for " + date + ": " + e.getMessage());
                                    message.setFill(Color.RED);
                                    messageBox.getChildren().add(message);
                                });
                            }
                        }
                    } else {
                        Platform.runLater(() -> {
                            Text message = new Text("Invalid date range");
                            message.setFill(Color.RED);
                            messageBox.getChildren().add(message);
                        });
                    }
                    return null;
                }
            };

            new Thread(downloadTask).start();
        });


        dateSelectionBox.getChildren().addAll(startDatePicker, endDatePicker);

        Pane spacer = new Pane();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        HBox hBox = new HBox();
        hBox.getChildren().addAll(downloadBtn, spacer, clearMessagesBtn);

        // assemble javafx components
        root.setSpacing(10);
        root.setPadding(new Insets(20));
        root.getChildren().addAll(directoryHBox, dateSelectionBox, hBox, messageBox);

        Scene scene = new Scene(root, 500, 500);
        stage.setTitle("Brokerage House");
        stage.setScene(scene);
        stage.show();
    }

    public static void main(String[] args) {
        launch(args);
    }
}
