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
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.prefs.Preferences;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import org.apache.poi.hssf.usermodel.HSSFWorkbook;
import org.apache.poi.hssf.usermodel.HSSFSheet;
import org.apache.poi.hssf.usermodel.HSSFRow;
import org.apache.poi.hssf.usermodel.HSSFCell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.CreationHelper;

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
        File tempZipFile = null;
        File lisFile = null;

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

            // Extract .lis file and immediately convert to .xls
            try (ZipInputStream zipInputStream = new ZipInputStream(new FileInputStream(tempZipFile))) {
                ZipEntry entry;
                while ((entry = zipInputStream.getNextEntry()) != null) {
                    if (entry.getName().endsWith(".lis")) {
                        // Construct the .lis file path
                        String formattedDate = date.format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));
                        lisFile = new File(saveDir, formattedDate + ".lis");
                        // Extract the .lis file
                        extractFile(zipInputStream, lisFile);
                        // Convert the .lis file to .xls
                        File xlsFile = new File(saveDir, formattedDate + ".xls");
                        convertLisToXls(lisFile, xlsFile);
                        break; // Assuming only one .lis file in the ZIP
                    }
                }
            }
        } finally {
            // Ensure the temporary ZIP file is deleted
            if (tempZipFile != null && !tempZipFile.delete()) {
                System.err.println("Could not delete temporary ZIP file: " + tempZipFile.getAbsolutePath());
            }
            // Delete the .lis file if it was created
            if (lisFile != null && !lisFile.delete()) {
                System.err.println("Could not delete temporary .lis file: " + lisFile.getAbsolutePath());
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

    private void convertLisToXls(File lisFile, File xlsFile) throws IOException {
        // Create a workbook and a sheet
        HSSFWorkbook workbook = new HSSFWorkbook();
        HSSFSheet sheet = workbook.createSheet("Stock Data");

        // Define header row
        String[] headers = {"Date", "Ticker", "Open", "High", "Low", "Close", "Vol", ""};

        // Create header row
        HSSFRow headerRow = sheet.createRow(0);
        for (int i = 0; i < headers.length; i++) {
            headerRow.createCell(i).setCellValue(headers[i]);
        }

        // Reading .lis file
        try (BufferedReader reader = new BufferedReader(new FileReader(lisFile))) {
            String line;
            int rownum = 1;
            while ((line = reader.readLine()) != null) {
                // Assuming the data is separated by "|"
                String[] values = line.split("\\|");

                // Assuming the format of the data is consistent with your provided example
                // Skip if the line does not contain expected data
                if (values.length < 9) continue;

                // Create a row and fill the cells with data
                HSSFRow row = sheet.createRow(rownum++);
                row.createCell(0).setCellValue(convertDate(values[0])); // Date
                row.createCell(1).setCellValue(values[1]); // Ticker
                row.createCell(2).setCellValue(Double.parseDouble(values[4])); // Open
                row.createCell(3).setCellValue(Double.parseDouble(values[5])); // High
                row.createCell(4).setCellValue(Double.parseDouble(values[6])); // Low
                row.createCell(5).setCellValue(Double.parseDouble(values[7])); // Close
                row.createCell(6).setCellValue(Double.parseDouble(values[8])); // Vol
                row.createCell(7).setCellValue(Double.parseDouble(values[9])); // Blank
                // Add more cells if there are more columns in your .lis file
            }
        }

        // Write the workbook to the output file
        try (FileOutputStream out = new FileOutputStream(xlsFile)) {
            workbook.write(out);
        }
        workbook.close();
    }

    private String convertDate(String dateStr) {
        // Extract day, month, and year from the date string
        int day = Integer.parseInt(dateStr.substring(0, 2));
        String monthStr = dateStr.substring(2, 5);
        int year = Integer.parseInt(dateStr.substring(5));

        // Convert month string to integer
        try {
            int month = new SimpleDateFormat("MMM").parse(monthStr).getMonth() + 1;
            Date date = new Date(year - 1900, month - 1, day);
            SimpleDateFormat formatter = new SimpleDateFormat("dd-MMM-yy");
            return formatter.format(date);
        } catch (ParseException e) {
            e.printStackTrace();
        }

        return dateStr;
    }
}
