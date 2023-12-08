package com.example.stock_d_rogers;

import javafx.application.Platform;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.scene.control.DatePicker;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
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
    @FXML
    private TextField formatTextField;

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
        formatTextField.textProperty().addListener((observable, oldValue, newValue) -> {
            saveFormatPattern();
        });
        String savedFormatPattern = prefs.get("formatPattern", "yyyy-MM-dd"); // Default format
        formatTextField.setText(savedFormatPattern);
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

                        String formattedDate = date.format(DateTimeFormatter.ofPattern("yyyy-MM-dd EEEE"));
                        Text loadingMessage = new Text("Downloading for " + formattedDate + "...");
                        ProgressIndicator progressIndicator = new ProgressIndicator();
                        progressIndicator.setPrefSize(13, 13);
                        final HBox[] hbox = new HBox[1];
                        Platform.runLater(() -> {
                            hbox[0] = new HBox(5, loadingMessage, progressIndicator);
                            messageBox.getChildren().add(hbox[0]);
                        });

                        try {
                            String dynamicURL = "https://dps.psx.com.pk/download/mkt_summary/" + date.format(DateTimeFormatter.ofPattern("yyyy-MM-dd")) + ".Z";
                            downloadFileAndExtractLis(dynamicURL, currentDirectory, date);

                            Platform.runLater(() -> {
                                messageBox.getChildren().remove(hbox[0]);
                                Text message = new Text("Download successful for " + formattedDate);
                                message.setFill(Color.GREEN);
                                messageBox.getChildren().add(message);
                            });
                        } catch (Exception e) {
                            Platform.runLater(() -> {
                                messageBox.getChildren().remove(hbox[0]);
                                Text message = new Text("Download failed for " + date.format(DateTimeFormatter.ofPattern("yyyy-MM-dd EEEE")) + ": " + e.getMessage());
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


    private void downloadFileAndExtractLis(String fileURL, File saveDir, LocalDate date) throws IOException {
        String formatPattern = formatTextField.getText().isEmpty() ? "yyyy-MM-dd" : formatTextField.getText();
        DateTimeFormatter formatter;
        try {
            formatter = DateTimeFormatter.ofPattern(formatPattern);
        } catch (IllegalArgumentException e) {
            formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd"); // Fallback to default on invalid format
            Platform.runLater(() -> {
                Text message = new Text("Invalid format pattern. Using default: yyyy-MM-dd");
                message.setFill(Color.ORANGE);
                messageBox.getChildren().add(message);
            });
        }
        String formattedDate = date.format(formatter);
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

    private void saveFormatPattern() {
        String formatPattern = formatTextField.getText();
        prefs.put("formatPattern", formatPattern);
    }
}
