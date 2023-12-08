package com.example.stock_d_rogers;

import javafx.application.Platform;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.scene.control.DatePicker;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.text.Text;
import javafx.stage.DirectoryChooser;
import javafx.stage.Stage;

import java.io.*;
import java.net.URL;
import java.net.URLConnection;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.prefs.Preferences;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public class DashboardController {
    private File currentDirectory;
    private final Preferences prefs;
    private Stage stage;

    @FXML
    private DatePicker endDatePicker;
    @FXML
    private DatePicker startDatePicker;
    @FXML
    private Text directoryPathText;
    @FXML
    private VBox messageBox;

    public DashboardController() {
        prefs = Preferences.userNodeForPackage(App.class);
        String defaultDirectory = prefs.get("downloadDirectory", System.getProperty("user.home") + File.separator + "Downloads");
        currentDirectory = new File(defaultDirectory);
    }

    @FXML
    private void initialize() {
        startDatePicker.setValue(LocalDate.now());
        endDatePicker.setValue(LocalDate.now());
        directoryPathText.setText("Download Directory: " + currentDirectory.getAbsolutePath());
    }

    @FXML
    private void onClear() {
        messageBox.getChildren().clear();
    }
    @FXML
    private void onChangeDirectory() {
        if (stage != null) {
            DirectoryChooser directoryChooser = new DirectoryChooser();
            File selectedDirectory = directoryChooser.showDialog(stage);
            if (selectedDirectory != null) {
                currentDirectory = selectedDirectory;
                directoryPathText.setText("Download Directory: " + currentDirectory.getAbsolutePath());
                prefs.put("downloadDirectory", currentDirectory.getAbsolutePath());
            }
        }
    }
    @FXML
    private void onDownload() {
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
    }

    private void downloadFileAndExtractLis(String fileURL, File saveDir, String formattedDate) throws IOException {
        File tempZipFile = null;

        try {
            tempZipFile = File.createTempFile("tempZip", ".zip", saveDir);
            URL url = new URL(fileURL);
            URLConnection connection = url.openConnection();

            try (BufferedInputStream in = new BufferedInputStream(connection.getInputStream());
                 FileOutputStream fileOutputStream = new FileOutputStream(tempZipFile)) {
                byte[] dataBuffer = new byte[1024];
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


    private void extractFile(ZipInputStream zipIn, File file) throws IOException {
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

    public void setStage(Stage stage) {
        this.stage = stage;
    }
}
