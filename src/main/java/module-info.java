module com.example.stock_d_rogers {
    requires javafx.controls;
    requires javafx.fxml;
    requires java.prefs;


    opens com.example.stock_d_rogers to javafx.fxml;
    exports com.example.stock_d_rogers;
}