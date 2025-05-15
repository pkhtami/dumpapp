module com.example.dumpapp {
    requires javafx.controls;
    requires javafx.fxml;


    opens com.example.dumpapp to javafx.fxml;
    exports com.example.dumpapp;
    exports com.ecuremap;
    opens com.ecuremap to javafx.fxml;
}