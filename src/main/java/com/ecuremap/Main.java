package com.ecuremap;

import javafx.application.Application;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.collections.transformation.SortedList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.*;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.util.Duration;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import javafx.beans.property.SimpleStringProperty;

public class Main extends Application {

    private static final String DATA_DIR = "ecuremap_data";
    private static final String CUSTOMERS_FILE = DATA_DIR + "/customers.dat";
    private static final String CARS_FILE = DATA_DIR + "/cars.dat";
    private static final String APPOINTMENTS_FILE = DATA_DIR + "/appointments.dat";
    private static final String DUMPS_DIR = DATA_DIR + "/dumps";

    private static final String[] PERSIAN_WEEK_DAYS = {"یکشنبه", "دوشنبه", "سه‌شنبه", "چهارشنبه", "پنج‌شنبه", "جمعه", "شنبه"};
    private static final String[] PERSIAN_MONTHS = {
            "فروردین", "اردیبهشت", "خرداد", "تیر", "مرداد", "شهریور",
            "مهر", "آبان", "آذر", "دی", "بهمن", "اسفند"
    };

    private ObservableList<Customer> customers = FXCollections.observableArrayList();
    private ObservableList<Car> cars = FXCollections.observableArrayList();
    private ObservableList<Appointment> appointments = FXCollections.observableArrayList();

    private FilteredList<Customer> filteredCustomers = new FilteredList<>(customers);
    private SortedList<Customer> sortedCustomers = new SortedList<>(filteredCustomers);

    private TableView<Customer> tableView = new TableView<>();
    private TextField searchField = new TextField();
    private Button backButton = new Button("← بازگشت");
    private StackPane mainPane = new StackPane();

    public static void main(String[] args) {
        launch(args);
    }

    @Override
    public void start(Stage primaryStage) {
        createDataDirectories();
        loadData();

        primaryStage.setTitle("سیستم مدیریت مشتریان ریمپ ایسیو");
        setupMainMenu();

        Scene scene = new Scene(mainPane, 1200, 800);
        applyStyles(scene);
        primaryStage.setScene(scene);
        primaryStage.setMinWidth(900);
        primaryStage.setMinHeight(650);
        primaryStage.show();
    }

    private void setupMainMenu() {
        VBox mainMenu = new VBox(20);
        mainMenu.setPadding(new Insets(25));
        mainMenu.setStyle("-fx-background-color: #ffffff; -fx-background-radius: 15;");

        // Header with title and clock
        HBox headerBox = new HBox();
        headerBox.setAlignment(Pos.CENTER_LEFT);

        Label titleLabel = new Label("مدیریت مشتریان ریمپ ایسیو");
        titleLabel.setStyle("-fx-font-size: 28px; -fx-font-weight: bold; -fx-text-fill: #2c3e50;");

        HBox spacer = new HBox();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        // Clock and date display
        HBox clockBox = createClockBox();

        Button appointmentsButton = createStyledButton("📅 نوبت‌های آینده", "#17a2b8", "#27b2c8");
        appointmentsButton.setOnAction(e -> showUpcomingAppointments());

        headerBox.getChildren().addAll(titleLabel, spacer, clockBox, new HBox(12), appointmentsButton);

        // Search box
        HBox searchBox = new HBox(15);
        setupSearchField();
        searchBox.getChildren().add(searchField);

        // Customer table
        setupCustomerTable();
        tableView.setStyle("-fx-font-size: 14px; -fx-table-cell-border-color: transparent; " +
                "-fx-selection-bar: #4a6baf; -fx-pref-height: 500; " +
                "-fx-background-color: white;");

        // Buttons
        Button addButton = createStyledButton("➕ مشتری جدید", "#4a6baf", "#5a7bbf");
        addButton.setOnAction(e -> showCustomerForm(null));

        HBox buttonBox = new HBox(15);
        buttonBox.setAlignment(Pos.CENTER);
        buttonBox.getChildren().add(addButton);

        mainMenu.getChildren().addAll(headerBox, searchBox, tableView, buttonBox);
        mainPane.getChildren().clear();
        mainPane.getChildren().add(mainMenu);
    }

    private HBox createClockBox() {
        HBox clockBox = new HBox(10);
        clockBox.setAlignment(Pos.CENTER);

        Label timeLabel = new Label();
        timeLabel.setStyle("-fx-font-size: 18px; -fx-font-weight: bold; -fx-text-fill: #2c3e50;");

        Label dateLabel = new Label();
        dateLabel.setStyle("-fx-font-size: 16px; -fx-text-fill: #2c3e50;");

        // Update clock and date
        Timeline clock = new Timeline(new KeyFrame(Duration.ZERO, e -> {
            LocalDateTime now = LocalDateTime.now();
            timeLabel.setText(String.format("%02d:%02d:%02d", now.getHour(), now.getMinute(), now.getSecond()));

            // Fixed day of week calculation
            int dayOfWeek = now.getDayOfWeek().getValue() % 7;
            String dayName = PERSIAN_WEEK_DAYS[dayOfWeek];
            String persianDate = PersianDateConverter.toPersianDate(now.toLocalDate());
            dateLabel.setText(dayName + " " + persianDate);
        }), new KeyFrame(Duration.seconds(1)));
        clock.setCycleCount(Timeline.INDEFINITE);
        clock.play();

        clockBox.getChildren().addAll(timeLabel, dateLabel);
        return clockBox;
    }

    private void setupSearchField() {
        searchField.setPromptText("جستجو بر اساس نام مشتری...");
        searchField.setStyle("-fx-font-size: 16px; -fx-pref-width: 600; -fx-pref-height: 45; -fx-padding: 10 20; " +
                "-fx-background-radius: 25; -fx-border-radius: 25; -fx-border-color: #ced4da; " +
                "-fx-background-color: #ffffff; " +
                "-fx-text-fill: #2c3e50;");
        searchField.textProperty().addListener((obs, oldVal, newVal) -> filterCustomers());
    }

    private void showCustomerForm(Customer customer) {
        VBox form = new VBox(20);
        form.setPadding(new Insets(25));
        form.setStyle("-fx-background-color: #ffffff; -fx-background-radius: 15;");

        Label titleLabel = new Label(customer == null ? "➕ ثبت مشتری جدید" : "✏️ ویرایش مشتری");
        titleLabel.setStyle("-fx-font-size: 24px; -fx-font-weight: bold; -fx-text-fill: #2c3e50;");

        GridPane formGrid = new GridPane();
        formGrid.setHgap(20);
        formGrid.setVgap(20);
        formGrid.setPadding(new Insets(20));

        TextField nameField = createStyledTextField();
        TextField phoneField = createStyledTextField();
        TextArea descriptionArea = createStyledTextArea();

        if (customer != null) {
            nameField.setText(customer.getName());
            phoneField.setText(customer.getPhone());
            descriptionArea.setText(customer.getDescription());
        }

        formGrid.addRow(0, createStyledLabel("نام مشتری"), new Label(":"), nameField);
        formGrid.addRow(1, createStyledLabel("شماره تماس"), new Label(":"), phoneField);
        formGrid.addRow(2, createStyledLabel("توضیحات"), new Label(":"), descriptionArea);

        Button saveButton = createStyledButton("💾 ذخیره", "#28a745", "#38b755");
        saveButton.setOnAction(e -> {
            if (validateCustomer(nameField.getText(), phoneField.getText())) {
                if (customer == null) {
                    Customer newCustomer = new Customer(nameField.getText(), phoneField.getText());
                    newCustomer.setDescription(descriptionArea.getText());
                    customers.add(newCustomer);
                } else {
                    customer.setName(nameField.getText());
                    customer.setPhone(phoneField.getText());
                    customer.setDescription(descriptionArea.getText());
                }
                saveData();
                setupMainMenu();
            }
        });

        Button cancelButton = createStyledButton("❌ انصراف", "#dc3545", "#ec4555");
        cancelButton.setOnAction(e -> setupMainMenu());

        HBox buttonBox = new HBox(20, saveButton, cancelButton);
        buttonBox.setAlignment(Pos.CENTER);

        form.getChildren().addAll(titleLabel, formGrid, buttonBox);
        showInMainPane(form);
    }

    private void showCustomerDetails(Customer customer) {
        VBox details = new VBox(20);
        details.setPadding(new Insets(25));
        details.setStyle("-fx-background-color: #ffffff; -fx-background-radius: 15;");

        // Header with customer name
        Label titleLabel = new Label("📋 مشخصات مشتری: " + customer.getName());
        titleLabel.setStyle("-fx-font-size: 24px; -fx-font-weight: bold; -fx-text-fill: #2c3e50;");

        // Customer info card
        VBox infoCard = new VBox(15);
        infoCard.setStyle("-fx-background-color: #f8f9fa; -fx-background-radius: 10; " +
                "-fx-border-color: #dee2e6; -fx-border-width: 1; -fx-border-radius: 10;");

        GridPane infoGrid = new GridPane();
        infoGrid.setHgap(20);
        infoGrid.setVgap(15);
        infoGrid.setPadding(new Insets(10));

        infoGrid.addRow(0, createStyledLabel("نام"), new Label(":"), createValueLabel(customer.getName()));
        infoGrid.addRow(1, createStyledLabel("شماره تماس"), new Label(":"), createValueLabel(customer.getPhone()));
        infoGrid.addRow(2, createStyledLabel("تاریخ ثبت"), new Label(":"), createValueLabel(customer.getPersianCreationDate()));
        infoGrid.addRow(3, createStyledLabel("توضیحات"), new Label(":"),
                createValueLabel(customer.getDescription().isEmpty() ? "بدون توضیحات" : customer.getDescription()));

        infoCard.getChildren().add(infoGrid);

        // Action buttons
        HBox buttonBox = new HBox(15);
        buttonBox.setAlignment(Pos.CENTER);

        Button editButton = createStyledButton("✏️ ویرایش", "#4a6baf", "#5a7bbf");
        editButton.setOnAction(e -> showCustomerForm(customer));

        Button deleteButton = createStyledButton("🗑️ حذف", "#dc3545", "#ec4555");
        deleteButton.setOnAction(e -> confirmAndDeleteCustomer(customer));

        Button carsButton = createStyledButton("🚗 مدیریت خودروها", "#28a745", "#38b755");
        carsButton.setOnAction(e -> showCustomerCars(customer));

        Button appointmentsButton = createStyledButton("📅 مدیریت نوبت‌ها", "#17a2b8", "#27b2c8");
        appointmentsButton.setOnAction(e -> showCustomerAppointments(customer));

        buttonBox.getChildren().addAll(editButton, deleteButton, carsButton, appointmentsButton);

        // Back button
        backButton = createStyledButton("← بازگشت", "#6c757d", "#7c858d");
        backButton.setOnAction(e -> setupMainMenu());

        details.getChildren().addAll(
                new HBox(backButton),
                new HBox(titleLabel),
                infoCard,
                buttonBox
        );
        showInMainPane(details);
    }

    private void confirmAndDeleteCustomer(Customer customer) {
        Alert confirmation = createAlert(Alert.AlertType.CONFIRMATION, "تأیید حذف",
                "آیا از حذف این مشتری مطمئن هستید؟", "این عمل قابل بازگشت نیست!");

        if (confirmation.showAndWait().get() == ButtonType.OK) {
            customers.remove(customer);
            saveData();
            setupMainMenu();
        }
    }

    private void showCustomerCars(Customer customer) {
        VBox carsView = new VBox(20);
        carsView.setPadding(new Insets(25));
        carsView.setStyle("-fx-background-color: #ffffff; -fx-background-radius: 15;");

        Label titleLabel = new Label("🚗 خودروهای مشتری: " + customer.getName());
        titleLabel.setStyle("-fx-font-size: 24px; -fx-font-weight: bold; -fx-text-fill: #2c3e50;");

        // Cars table
        TableView<Car> carsTable = createCarsTable(customer);

        // Car management buttons
        HBox carButtons = new HBox(15);
        carButtons.setAlignment(Pos.CENTER);

        Button addCarButton = createStyledButton("➕ افزودن خودرو", "#4a6baf", "#5a7bbf");
        addCarButton.setOnAction(e -> showCarForm(customer, null));

        Button editCarButton = createStyledButton("✏️ ویرایش خودرو", "#4a6baf", "#5a7bbf");
        editCarButton.setOnAction(e -> {
            Car selectedCar = carsTable.getSelectionModel().getSelectedItem();
            if (selectedCar != null) {
                showCarForm(customer, selectedCar);
            } else {
                showAlert("خطا", "لطفاً یک خودرو را انتخاب کنید");
            }
        });

        Button deleteCarButton = createStyledButton("🗑️ حذف خودرو", "#dc3545", "#ec4555");
        deleteCarButton.setOnAction(e -> {
            Car selectedCar = carsTable.getSelectionModel().getSelectedItem();
            if (selectedCar != null) {
                confirmAndDeleteCar(customer, selectedCar);
            } else {
                showAlert("خطا", "لطفاً یک خودرو را انتخاب کنید");
            }
        });

        carButtons.getChildren().addAll(addCarButton, editCarButton, deleteCarButton);

        // Back button
        backButton = createStyledButton("← بازگشت", "#6c757d", "#7c858d");
        backButton.setOnAction(e -> showCustomerDetails(customer));

        carsView.getChildren().addAll(
                new HBox(backButton),
                titleLabel,
                carsTable,
                carButtons
        );
        showInMainPane(carsView);
    }

    private TableView<Car> createCarsTable(Customer customer) {
        TableView<Car> carsTable = new TableView<>();
        carsTable.setStyle("-fx-font-size: 14px; -fx-table-cell-border-color: transparent; " +
                "-fx-selection-bar: #4a6baf; -fx-pref-height: 400; " +
                "-fx-background-color: white;");

        ObservableList<Car> customerCars = FXCollections.observableArrayList(
                cars.filtered(car -> car.getCustomerId().equals(customer.getId()))
        );

        TableColumn<Car, String> modelCol = new TableColumn<>("مدل خودرو");
        modelCol.setCellValueFactory(new PropertyValueFactory<>("model"));
        modelCol.setStyle("-fx-font-size: 14px; -fx-font-weight: bold;");

        TableColumn<Car, String> yearCol = new TableColumn<>("سال تولید");
        yearCol.setCellValueFactory(new PropertyValueFactory<>("year"));
        yearCol.setStyle("-fx-font-size: 14px;");

        TableColumn<Car, String> ecuCol = new TableColumn<>("مدل ایسیو");
        ecuCol.setCellValueFactory(new PropertyValueFactory<>("ecuModel"));
        ecuCol.setStyle("-fx-font-size: 14px;");

        TableColumn<Car, String> dateCol = new TableColumn<>("تاریخ ثبت");
        dateCol.setCellValueFactory(new PropertyValueFactory<>("persianCreationDate"));
        dateCol.setStyle("-fx-font-size: 14px;");

        carsTable.getColumns().addAll(modelCol, yearCol, ecuCol, dateCol);
        carsTable.setItems(customerCars);
        carsTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);

        carsTable.setRowFactory(tv -> {
            TableRow<Car> row = new TableRow<>();
            row.setOnMouseClicked(event -> {
                if (event.getClickCount() == 2 && !row.isEmpty()) {
                    showDumpManagement(customer, row.getItem());
                }
            });
            return row;
        });

        return carsTable;
    }

    private void confirmAndDeleteCar(Customer customer, Car car) {
        Alert confirmation = createAlert(Alert.AlertType.CONFIRMATION, "تأیید حذف",
                "آیا از حذف این خودرو مطمئن هستید؟", "این عمل قابل بازگشت نیست!");

        if (confirmation.showAndWait().get() == ButtonType.OK) {
            cars.remove(car);
            saveData();
            showCustomerCars(customer);
        }
    }

    private void showCarForm(Customer customer, Car car) {
        VBox form = new VBox(20);
        form.setPadding(new Insets(25));
        form.setStyle("-fx-background-color: #ffffff; -fx-background-radius: 15;");

        Label titleLabel = new Label(car == null ? "➕ افزودن خودرو جدید" : "✏️ ویرایش خودرو");
        titleLabel.setStyle("-fx-font-size: 24px; -fx-font-weight: bold; -fx-text-fill: #2c3e50;");

        GridPane formGrid = new GridPane();
        formGrid.setHgap(20);
        formGrid.setVgap(20);
        formGrid.setPadding(new Insets(20));

        TextField modelField = createStyledTextField();
        TextField yearField = createStyledTextField();
        TextField ecuField = createStyledTextField();
        TextArea descriptionArea = createStyledTextArea();

        if (car != null) {
            modelField.setText(car.getModel());
            yearField.setText(car.getYear());
            ecuField.setText(car.getEcuModel());
            descriptionArea.setText(car.getDescription());
        }

        formGrid.addRow(0, createStyledLabel("مدل خودرو"), new Label(":"), modelField);
        formGrid.addRow(1, createStyledLabel("سال تولید"), new Label(":"), yearField);
        formGrid.addRow(2, createStyledLabel("مدل ایسیو"), new Label(":"), ecuField);
        formGrid.addRow(3, createStyledLabel("توضیحات"), new Label(":"), descriptionArea);

        Button saveButton = createStyledButton("💾 ذخیره", "#28a745", "#38b755");
        saveButton.setOnAction(e -> {
            if (validateCar(modelField.getText(), yearField.getText(), ecuField.getText())) {
                if (car == null) {
                    Car newCar = new Car(customer.getId(), modelField.getText(), yearField.getText(), ecuField.getText());
                    newCar.setDescription(descriptionArea.getText());
                    cars.add(newCar);
                } else {
                    car.setModel(modelField.getText());
                    car.setYear(yearField.getText());
                    car.setEcuModel(ecuField.getText());
                    car.setDescription(descriptionArea.getText());
                }
                saveData();
                showCustomerCars(customer);
            }
        });

        Button cancelButton = createStyledButton("❌ انصراف", "#dc3545", "#ec4555");
        cancelButton.setOnAction(e -> showCustomerCars(customer));

        HBox buttonBox = new HBox(20, saveButton, cancelButton);
        buttonBox.setAlignment(Pos.CENTER);

        form.getChildren().addAll(titleLabel, formGrid, buttonBox);
        showInMainPane(form);
    }

    private void showDumpManagement(Customer customer, Car car) {
        VBox dumpManagement = new VBox(20);
        dumpManagement.setPadding(new Insets(25));
        dumpManagement.setStyle("-fx-background-color: #ffffff; -fx-background-radius: 15;");

        Label titleLabel = new Label("💾 مدیریت فایل‌های دامپ برای خودرو: " + car.getModel());
        titleLabel.setStyle("-fx-font-size: 24px; -fx-font-weight: bold; -fx-text-fill: #2c3e50;");

        // Dump files list with search
        TextField searchDumpField = createStyledTextField();
        searchDumpField.setPromptText("جستجو در فایل‌های دامپ...");

        TableView<DumpFile> dumpTable = createDumpFilesTable(car, searchDumpField);

        // File action buttons
        HBox fileActionButtons = createDumpFileActionButtons(customer, car, dumpTable);

        // New file upload form
        VBox uploadForm = createUploadForm(customer, car);

        // Back button
        backButton = createStyledButton("← بازگشت", "#6c757d", "#7c858d");
        backButton.setOnAction(e -> showCustomerCars(customer));

        dumpManagement.getChildren().addAll(
                new HBox(backButton),
                titleLabel,
                uploadForm,
                new VBox(15,
                        new Label("فایل‌های ذخیره شده:"),
                        searchDumpField,
                        dumpTable,
                        fileActionButtons
                )
        );
        showInMainPane(dumpManagement);
    }

    private TableView<DumpFile> createDumpFilesTable(Car car, TextField searchField) {
        TableView<DumpFile> dumpTable = new TableView<>();
        dumpTable.setStyle("-fx-font-size: 14px; -fx-border-color: #ced4da; " +
                "-fx-background-radius: 5; -fx-pref-height: 300; " +
                "-fx-background-color: #ffffff;");

        ObservableList<DumpFile> dumpFiles = FXCollections.observableArrayList(car.getDumpFiles());
        FilteredList<DumpFile> filteredDumps = new FilteredList<>(dumpFiles);
        dumpTable.setItems(filteredDumps);
        dumpTable.setPlaceholder(new Label("هنوز فایلی اضافه نشده است"));

        TableColumn<DumpFile, String> fileNameCol = new TableColumn<>("نام فایل");
        fileNameCol.setCellValueFactory(new PropertyValueFactory<>("fileName"));
        fileNameCol.setStyle("-fx-font-size: 14px;");

        TableColumn<DumpFile, String> dateCol = new TableColumn<>("تاریخ ثبت");
        dateCol.setCellValueFactory(new PropertyValueFactory<>("persianCreationDate"));
        dateCol.setStyle("-fx-font-size: 14px;");

        dumpTable.getColumns().addAll(fileNameCol, dateCol);
        dumpTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);

        searchField.textProperty().addListener((obs, oldVal, newVal) -> {
            filteredDumps.setPredicate(dump -> {
                if (newVal == null || newVal.isEmpty()) return true;
                return dump.getFileName().toLowerCase().contains(newVal.toLowerCase());
            });
        });

        return dumpTable;
    }

    private HBox createDumpFileActionButtons(Customer customer, Car car, TableView<DumpFile> dumpTable) {
        HBox fileActionButtons = new HBox(10);
        fileActionButtons.setAlignment(Pos.CENTER);

        Button renameButton = createStyledButton("✏️ تغییر نام", "#17a2b8", "#27b2c8");
        renameButton.setOnAction(e -> {
            DumpFile selectedFile = dumpTable.getSelectionModel().getSelectedItem();
            if (selectedFile != null) {
                renameDumpFile(customer, car, selectedFile.getFileName());
            } else {
                showAlert("خطا", "لطفاً یک فایل را انتخاب کنید");
            }
        });

        Button deleteButton = createStyledButton("🗑️ حذف", "#dc3545", "#ec4555");
        deleteButton.setOnAction(e -> {
            DumpFile selectedFile = dumpTable.getSelectionModel().getSelectedItem();
            if (selectedFile != null) {
                deleteDumpFile(customer, car, selectedFile.getFileName());
            } else {
                showAlert("خطا", "لطفاً یک فایل را انتخاب کنید");
            }
        });

        Button downloadButton = createStyledButton("⬇️ دانلود", "#4a6baf", "#5a7bbf");
        downloadButton.setOnAction(e -> {
            DumpFile selectedFile = dumpTable.getSelectionModel().getSelectedItem();
            if (selectedFile != null) {
                downloadDumpFile(customer, car, selectedFile.getFileName());
            } else {
                showAlert("خطا", "لطفاً یک فایل را انتخاب کنید");
            }
        });

        fileActionButtons.getChildren().addAll(renameButton, deleteButton, downloadButton);
        return fileActionButtons;
    }

    private VBox createUploadForm(Customer customer, Car car) {
        TextField dumpNameField = createStyledTextField();
        dumpNameField.setPromptText("نام فایل دامپ...");

        Button uploadButton = createStyledButton("📁 انتخاب فایل", "#4a6baf", "#5a7bbf");

        Label fileLabel = new Label("هیچ فایلی انتخاب نشده");
        fileLabel.setStyle("-fx-font-size: 14px; -fx-text-fill: #6c757d;");

        uploadButton.setOnAction(e -> {
            FileChooser fileChooser = new FileChooser();
            fileChooser.setTitle("انتخاب فایل دامپ ایسیو");
            fileChooser.getExtensionFilters().addAll(
                    new FileChooser.ExtensionFilter("فایل‌های دامپ", "*.bin", "*.ols", "*.hex", "*.dam"),
                    new FileChooser.ExtensionFilter("همه فایل‌ها", "*.*")
            );
            File file = fileChooser.showOpenDialog(null);
            if (file != null) {
                fileLabel.setText(file.getAbsolutePath());
                String defaultName = file.getName().replaceAll("\\.[^.]+$", "");
                dumpNameField.setText(defaultName);
            }
        });

        Button saveButton = createStyledButton("💾 ذخیره فایل", "#28a745", "#38b755");
        saveButton.setOnAction(e -> saveUploadedFile(customer, car, dumpNameField, fileLabel));

        return new VBox(15,
                new Label("فایل جدید:"),
                new HBox(15, dumpNameField, uploadButton),
                fileLabel,
                saveButton
        );
    }

    private void saveUploadedFile(Customer customer, Car car, TextField dumpNameField, Label fileLabel) {
        try {
            String fileName = dumpNameField.getText().trim();
            String sourcePath = fileLabel.getText();

            if (fileName.isEmpty() || fileLabel.getText().equals("هیچ فایلی انتخاب نشده")) {
                showAlert("خطا", "لطفاً نام فایل و فایل را انتخاب کنید");
                return;
            }

            String fileExt = sourcePath.substring(sourcePath.lastIndexOf("."));
            String destFileName = fileName + fileExt;

            // Create directory path with car and customer details
            String carFolderName = String.format("%s_%s_%s_%s",
                    car.getEcuModel().replaceAll("[^a-zA-Z0-9]", "_"),
                    car.getModel().replaceAll("[^a-zA-Z0-9]", "_"),
                    car.getYear().replaceAll("[^a-zA-Z0-9]", "_"),
                    customer.getName().replaceAll("[^a-zA-Z0-9]", "_"));

            Path carDir = Paths.get(DUMPS_DIR, carFolderName);
            Files.createDirectories(carDir);

            Path destPath = carDir.resolve(destFileName);
            Files.copy(Paths.get(sourcePath), destPath, StandardCopyOption.REPLACE_EXISTING);

            car.addDumpFile(destFileName);
            saveData();
            showAlert("موفق", "فایل با موفقیت ذخیره شد");

            // Reset form
            dumpNameField.clear();
            fileLabel.setText("هیچ فایلی انتخاب نشده");

            // Refresh the dump management view to show the new file
            showDumpManagement(customer, car);
        } catch (Exception ex) {
            showAlert("خطا", "مشکل در ذخیره فایل: " + ex.getMessage());
        }
    }

    private void renameDumpFile(Customer customer, Car car, String oldFileName) {
        TextInputDialog dialog = new TextInputDialog(oldFileName.replaceAll("\\.[^.]+$", ""));
        dialog.setTitle("تغییر نام فایل");
        dialog.setHeaderText("نام جدید را وارد کنید");
        dialog.setContentText("نام فایل (بدون پسوند):");

        Optional<String> result = dialog.showAndWait();
        result.ifPresent(newName -> {
            try {
                String fileExt = oldFileName.substring(oldFileName.lastIndexOf("."));
                String newFileName = newName + fileExt;

                // Create directory path with car and customer details
                String carFolderName = String.format("%s_%s_%s_%s",
                        car.getEcuModel().replaceAll("[^a-zA-Z0-9]", "_"),
                        car.getModel().replaceAll("[^a-zA-Z0-9]", "_"),
                        car.getYear().replaceAll("[^a-zA-Z0-9]", "_"),
                        customer.getName().replaceAll("[^a-zA-Z0-9]", "_"));

                Path oldPath = Paths.get(DUMPS_DIR, carFolderName, oldFileName);
                Path newPath = Paths.get(DUMPS_DIR, carFolderName, newFileName);

                if (Files.exists(newPath)) {
                    showAlert("خطا", "فایلی با این نام از قبل وجود دارد");
                    return;
                }

                Files.move(oldPath, newPath);
                car.renameDumpFile(oldFileName, newFileName);
                saveData();
                showDumpManagement(customer, car);
                showAlert("موفق", "نام فایل با موفقیت تغییر یافت");
            } catch (Exception ex) {
                showAlert("خطا", "مشکل در تغییر نام فایل: " + ex.getMessage());
            }
        });
    }

    private void deleteDumpFile(Customer customer, Car car, String fileName) {
        Alert confirmation = createAlert(Alert.AlertType.CONFIRMATION, "تأیید حذف",
                "آیا از حذف این فایل مطمئن هستید؟", "این عمل قابل بازگشت نیست!");

        if (confirmation.showAndWait().get() == ButtonType.OK) {
            try {
                // Create directory path with car and customer details
                String carFolderName = String.format("%s_%s_%s_%s",
                        car.getEcuModel().replaceAll("[^a-zA-Z0-9]", "_"),
                        car.getModel().replaceAll("[^a-zA-Z0-9]", "_"),
                        car.getYear().replaceAll("[^a-zA-Z0-9]", "_"),
                        customer.getName().replaceAll("[^a-zA-Z0-9]", "_"));

                Path filePath = Paths.get(DUMPS_DIR, carFolderName, fileName);
                Files.deleteIfExists(filePath);
                car.removeDumpFile(fileName);
                saveData();
                showDumpManagement(customer, car);
                showAlert("موفق", "فایل با موفقیت حذف شد");
            } catch (Exception ex) {
                showAlert("خطا", "مشکل در حذف فایل: " + ex.getMessage());
            }
        }
    }

    private void downloadDumpFile(Customer customer, Car car, String fileName) {
        try {
            FileChooser fileChooser = new FileChooser();
            fileChooser.setTitle("ذخیره فایل دامپ");
            fileChooser.setInitialFileName(fileName);
            
            // Set initial directory to desktop
            String userHome = System.getProperty("user.home");
            File desktop = new File(userHome, "Desktop");
            fileChooser.setInitialDirectory(desktop);
            
            File destFile = fileChooser.showSaveDialog(null);
            if (destFile != null) {
                // Create directory path with car and customer details
                String carFolderName = String.format("%s_%s_%s_%s",
                        car.getEcuModel().replaceAll("[^a-zA-Z0-9]", "_"),
                        car.getModel().replaceAll("[^a-zA-Z0-9]", "_"),
                        car.getYear().replaceAll("[^a-zA-Z0-9]", "_"),
                        customer.getName().replaceAll("[^a-zA-Z0-9]", "_"));

                Path sourcePath = Paths.get(DUMPS_DIR, carFolderName, fileName);
                Files.copy(sourcePath, destFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
                showAlert("موفق", "فایل با موفقیت دانلود شد");
            }
        } catch (Exception ex) {
            showAlert("خطا", "مشکل در دانلود فایل: " + ex.getMessage());
        }
    }

    private void setupCustomerTable() {
        TableColumn<Customer, String> nameCol = new TableColumn<>("نام مشتری");
        nameCol.setCellValueFactory(new PropertyValueFactory<>("name"));
        nameCol.setStyle("-fx-font-size: 14px; -fx-font-weight: bold;");

        TableColumn<Customer, String> phoneCol = new TableColumn<>("شماره تماس");
        phoneCol.setCellValueFactory(new PropertyValueFactory<>("phone"));
        phoneCol.setStyle("-fx-font-size: 14px;");

        TableColumn<Customer, String> creationDateCol = new TableColumn<>("تاریخ ثبت");
        creationDateCol.setCellValueFactory(new PropertyValueFactory<>("persianCreationDate"));
        creationDateCol.setStyle("-fx-font-size: 14px;");

        tableView.getColumns().setAll(nameCol, phoneCol, creationDateCol);
        tableView.setItems(sortedCustomers);
        tableView.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);

        tableView.setRowFactory(tv -> {
            TableRow<Customer> row = new TableRow<>();
            row.setOnMouseClicked(event -> {
                if (event.getClickCount() == 2 && !row.isEmpty()) {
                    showCustomerDetails(row.getItem());
                }
            });
            return row;
        });
    }

    private boolean validateCustomer(String name, String phone) {
        if (name == null || name.trim().isEmpty()) {
            showAlert("خطا", "نام مشتری نمی‌تواند خالی باشد");
            return false;
        }

        if (phone == null || phone.trim().isEmpty()) {
            showAlert("خطا", "شماره تماس نمی‌تواند خالی باشد");
            return false;
        }

        // Check for duplicate name and phone combination
        for (Customer c : customers) {
            if (c.getName().equalsIgnoreCase(name.trim()) &&
                    c.getPhone().equals(phone.trim())) {
                showAlert("خطا", "مشتری با این نام و شماره تماس قبلاً ثبت شده است");
                return false;
            }
        }

        return true;
    }

    private boolean validateCar(String model, String year, String ecuModel) {
        if (model == null || model.trim().isEmpty()) {
            showAlert("خطا", "مدل خودرو نمی‌تواند خالی باشد");
            return false;
        }

        if (year == null || year.trim().isEmpty()) {
            showAlert("خطا", "سال تولید نمی‌تواند خالی باشد");
            return false;
        }

        if (ecuModel == null || ecuModel.trim().isEmpty()) {
            showAlert("خطا", "مدل ایسیو نمی‌تواند خالی باشد");
            return false;
        }

        return true;
    }

    private void filterCustomers() {
        String searchText = searchField.getText().toLowerCase();
        filteredCustomers.setPredicate(customer -> {
            if (searchText.isEmpty()) return true;
            return customer.getName().toLowerCase().contains(searchText);
        });
    }

    private void createDataDirectories() {
        try {
            Files.createDirectories(Paths.get(DATA_DIR));
            Files.createDirectories(Paths.get(DUMPS_DIR));
        } catch (IOException e) {
            System.out.println("خطا در ایجاد دایرکتوری‌های داده: " + e.getMessage());
        }
    }

    @SuppressWarnings("unchecked")
    private void loadData() {
        // Load customers
        loadFromFile(CUSTOMERS_FILE, customers, "مشتریان");

        // Load cars
        loadFromFile(CARS_FILE, cars, "خودروها");

        // Load appointments
        loadFromFile(APPOINTMENTS_FILE, appointments, "نوبت‌ها");
    }

    private <T> void loadFromFile(String filePath, ObservableList<T> targetList, String dataTypeName) {
        Path path = Paths.get(filePath);
        if (Files.exists(path)) {
            try (ObjectInputStream ois = new ObjectInputStream(new FileInputStream(path.toFile()))) {
                List<T> loadedData = (List<T>) ois.readObject();
                targetList.setAll(loadedData);
            } catch (Exception e) {
                System.out.println("خطا در بارگذاری اطلاعات " + dataTypeName + ": " + e.getMessage());
            }
        }
    }

    private void saveData() {
        // Save customers
        saveToFile(CUSTOMERS_FILE, customers, "مشتریان");

        // Save cars
        saveToFile(CARS_FILE, cars, "خودروها");

        // Save appointments
        saveToFile(APPOINTMENTS_FILE, appointments, "نوبت‌ها");
    }

    private <T> void saveToFile(String filePath, List<T> data, String dataTypeName) {
        try (ObjectOutputStream oos = new ObjectOutputStream(new FileOutputStream(filePath))) {
            oos.writeObject(new ArrayList<>(data));
        } catch (Exception e) {
            System.out.println("خطا در ذخیره اطلاعات " + dataTypeName + ": " + e.getMessage());
        }
    }

    private Alert createAlert(Alert.AlertType type, String title, String header, String content) {
        Alert alert = new Alert(type);
        alert.setTitle(title);
        alert.setHeaderText(header);
        alert.setContentText(content);
        alert.getDialogPane().setStyle("-fx-font-size: 14px;");
        return alert;
    }

    private void showAlert(String title, String message) {
        Alert alert = createAlert(Alert.AlertType.INFORMATION, title, null, message);
        alert.showAndWait();
    }

    private Button createStyledButton(String text, String baseColor, String hoverColor) {
        Button button = new Button(text);
        button.setStyle("-fx-background-color: " + baseColor + "; " +
                "-fx-text-fill: white; -fx-font-weight: bold; " +
                "-fx-padding: 10 20; -fx-background-radius: 20; " +
                "-fx-font-size: 16px; -fx-cursor: hand;");
        button.setOnMouseEntered(e -> button.setStyle("-fx-background-color: " + hoverColor + "; " +
                "-fx-text-fill: white; -fx-font-weight: bold; " +
                "-fx-padding: 10 20; -fx-background-radius: 20; " +
                "-fx-font-size: 16px; -fx-cursor: hand;"));
        button.setOnMouseExited(e -> button.setStyle("-fx-background-color: " + baseColor + "; " +
                "-fx-text-fill: white; -fx-font-weight: bold; " +
                "-fx-padding: 10 20; -fx-background-radius: 20; " +
                "-fx-font-size: 16px; -fx-cursor: hand;"));
        return button;
    }

    private TextField createStyledTextField() {
        TextField field = new TextField();
        field.setStyle("-fx-font-size: 16px; -fx-pref-height: 40; -fx-padding: 8 15; " +
                "-fx-background-radius: 5; -fx-border-radius: 5; " +
                "-fx-border-color: #ced4da; " +
                "-fx-background-color: #ffffff; " +
                "-fx-text-fill: #2c3e50;");
        return field;
    }

    private TextArea createStyledTextArea() {
        TextArea area = new TextArea();
        area.setStyle("-fx-font-size: 16px; -fx-pref-height: 100; -fx-padding: 8 15; " +
                "-fx-background-radius: 5; -fx-border-radius: 5; " +
                "-fx-border-color: #ced4da; " +
                "-fx-background-color: #ffffff; " +
                "-fx-text-fill: #2c3e50;");
        return area;
    }

    private Label createStyledLabel(String text) {
        Label label = new Label(text);
        label.setStyle("-fx-font-size: 16px; -fx-text-fill: #343a40; -fx-font-weight: bold;");
        return label;
    }

    private Label createValueLabel(String text) {
        Label label = new Label(text);
        label.setStyle("-fx-font-size: 16px; -fx-text-fill: #212529;");
        return label;
    }

    private void applyStyles(Scene scene) {
        scene.getRoot().setStyle(
                "-fx-base: #f8f9fa; " +
                        "-fx-font-family: 'Tahoma'; " +
                        "-fx-background-color: #f5f7fa;"
        );

        // General button style
        String buttonStyle = "-fx-background-color: #4a6baf; " +
                "-fx-text-fill: white; -fx-font-weight: bold; " +
                "-fx-padding: 8 16; " +
                "-fx-background-radius: 20; " +
                "-fx-font-size: 14px; " +
                "-fx-effect: dropshadow(three-pass-box, rgba(0,0,0,0.2), 5, 0, 0, 1); " +
                "-fx-cursor: hand;";

        String buttonHoverStyle = "-fx-background-color: #5a7bbf; " +
                "-fx-effect: dropshadow(three-pass-box, rgba(0,0,0,0.3), 8, 0, 0, 2);";

        scene.getRoot().lookupAll(".button").forEach(node -> {
            node.setStyle(buttonStyle);
            node.setOnMouseEntered(e -> node.setStyle(buttonStyle + buttonHoverStyle));
            node.setOnMouseExited(e -> node.setStyle(buttonStyle));
        });

        // Table style
        String tableStyle =
                "-fx-font-size: 14px; " +
                        "-fx-selection-bar: #4a6baf; " +
                        "-fx-selection-bar-non-focused: #d3d3d3; " +
                        "-fx-table-cell-border-color: transparent; " +
                        "-fx-table-header-border-color: #dee2e6; " +
                        "-fx-padding: 5; " +
                        "-fx-background-color: white;";

        scene.getRoot().lookupAll(".table-view").forEach(node -> node.setStyle(tableStyle));

        // Table header style
        String tableHeaderStyle =
                "-fx-background-color: #4a6baf; " +
                        "-fx-text-fill: white; " +
                        "-fx-font-weight: bold; " +
                        "-fx-font-size: 14px;";

        scene.getRoot().lookupAll(".column-header").forEach(node -> node.setStyle(tableHeaderStyle));

        // Input fields style
        String fieldStyle =
                "-fx-font-size: 14px; " +
                        "-fx-padding: 8 15; " +
                        "-fx-pref-height: 40; " +
                        "-fx-background-radius: 5; " +
                        "-fx-border-radius: 5; " +
                        "-fx-border-color: #ced4da; " +
                        "-fx-border-width: 1; " +
                        "-fx-background-insets: 0; " +
                        "-fx-background-color: #ffffff; " +
                        "-fx-text-fill: #2c3e50;";

        scene.getRoot().lookupAll(".text-field, .combo-box").forEach(node -> node.setStyle(fieldStyle));

        // Labels style
        String labelStyle =
                "-fx-text-fill: #343a40; " +
                        "-fx-font-size: 14px; " +
                        "-fx-font-weight: normal;";

        scene.getRoot().lookupAll(".label").forEach(node -> node.setStyle(labelStyle));

        // Titles style
        String titleStyle =
                "-fx-font-size: 24px; " +
                        "-fx-font-weight: bold; " +
                        "-fx-text-fill: #2c3e50; " +
                        "-fx-padding: 0 0 10 0;";

        scene.getRoot().lookupAll(".title-label").forEach(node -> node.setStyle(titleStyle));

        // ListView style
        String listViewStyle =
                "-fx-font-size: 14px; " +
                        "-fx-border-color: #ced4da; " +
                        "-fx-border-width: 1; " +
                        "-fx-background-radius: 5; " +
                        "-fx-background-color: #ffffff;";

        scene.getRoot().lookupAll(".list-view").forEach(node -> node.setStyle(listViewStyle));
    }

    private void showCustomerAppointments(Customer customer) {
        VBox appointmentsView = new VBox(20);
        appointmentsView.setPadding(new Insets(25));
        appointmentsView.setStyle("-fx-background-color: #ffffff; -fx-background-radius: 15;");

        Label titleLabel = new Label("📅 نوبت‌های مشتری: " + customer.getName());
        titleLabel.setStyle("-fx-font-size: 24px; -fx-font-weight: bold; -fx-text-fill: #2c3e50;");

        // Appointments table
        TableView<Appointment> appointmentsTable = createAppointmentsTable(customer);

        // Appointment management buttons
        HBox appointmentButtons = new HBox(15);
        appointmentButtons.setAlignment(Pos.CENTER);

        Button addButton = createStyledButton("➕ نوبت جدید", "#4a6baf", "#5a7bbf");
        addButton.setOnAction(e -> showAppointmentForm(customer, null));

        Button editButton = createStyledButton("✏️ ویرایش نوبت", "#4a6baf", "#5a7bbf");
        editButton.setOnAction(e -> {
            Appointment selectedAppointment = appointmentsTable.getSelectionModel().getSelectedItem();
            if (selectedAppointment != null) {
                showAppointmentForm(customer, selectedAppointment);
            } else {
                showAlert("خطا", "لطفاً یک نوبت را انتخاب کنید");
            }
        });

        Button deleteButton = createStyledButton("🗑️ حذف نوبت", "#dc3545", "#ec4555");
        deleteButton.setOnAction(e -> {
            Appointment selectedAppointment = appointmentsTable.getSelectionModel().getSelectedItem();
            if (selectedAppointment != null) {
                confirmAndDeleteAppointment(customer, selectedAppointment);
            } else {
                showAlert("خطا", "لطفاً یک نوبت را انتخاب کنید");
            }
        });

        Button toggleStatusButton = createStyledButton("🔄 تغییر وضعیت", "#28a745", "#38b755");
        toggleStatusButton.setOnAction(e -> {
            Appointment selectedAppointment = appointmentsTable.getSelectionModel().getSelectedItem();
            if (selectedAppointment != null) {
                selectedAppointment.setCompleted(!selectedAppointment.isCompleted());
                saveData();
                showCustomerAppointments(customer);
            } else {
                showAlert("خطا", "لطفاً یک نوبت را انتخاب کنید");
            }
        });

        appointmentButtons.getChildren().addAll(addButton, editButton, deleteButton, toggleStatusButton);

        // Back button
        backButton = createStyledButton("← بازگشت", "#6c757d", "#7c858d");
        backButton.setOnAction(e -> showCustomerDetails(customer));

        appointmentsView.getChildren().addAll(
                new HBox(backButton),
                titleLabel,
                appointmentsTable,
                appointmentButtons
        );
        showInMainPane(appointmentsView);
    }

    private TableView<Appointment> createAppointmentsTable(Customer customer) {
        TableView<Appointment> appointmentsTable = new TableView<>();
        appointmentsTable.setStyle("-fx-font-size: 14px; -fx-table-cell-border-color: transparent; " +
                "-fx-selection-bar: #4a6baf; -fx-pref-height: 400; " +
                "-fx-background-color: white;");

        ObservableList<Appointment> customerAppointments = FXCollections.observableArrayList(
                appointments.filtered(apt -> apt.getCustomerId().equals(customer.getId()))
        );

        TableColumn<Appointment, String> customerCol = new TableColumn<>("مشتری");
        customerCol.setCellValueFactory(cellData -> {
            Appointment apt = cellData.getValue();
            Customer foundCustomer = findCustomerById(apt.getCustomerId());
            return new SimpleStringProperty(foundCustomer != null ? foundCustomer.getName() : "نامشخص");
        });
        customerCol.setStyle("-fx-font-size: 14px;");

        TableColumn<Appointment, String> phoneCol = new TableColumn<>("شماره تماس");
        phoneCol.setCellValueFactory(cellData -> {
            Appointment apt = cellData.getValue();
            Customer foundCustomer = findCustomerById(apt.getCustomerId());
            return new SimpleStringProperty(foundCustomer != null ? foundCustomer.getPhone() : "نامشخص");
        });
        phoneCol.setStyle("-fx-font-size: 14px;");

        TableColumn<Appointment, String> dayCol = new TableColumn<>("روز");
        dayCol.setCellValueFactory(cellData -> {
            Appointment apt = cellData.getValue();
            int dayOfWeek = apt.getAppointmentDateTime().getDayOfWeek().getValue() % 7;
            return new SimpleStringProperty(PERSIAN_WEEK_DAYS[dayOfWeek]);
        });
        dayCol.setStyle("-fx-font-size: 14px;");

        TableColumn<Appointment, String> timeCol = new TableColumn<>("ساعت");
        timeCol.setCellValueFactory(cellData -> {
            Appointment apt = cellData.getValue();
            LocalTime time = apt.getAppointmentDateTime().toLocalTime();
            return new SimpleStringProperty(String.format("%02d:%02d", time.getHour(), time.getMinute()));
        });
        timeCol.setStyle("-fx-font-size: 14px;");

        TableColumn<Appointment, String> dateCol = new TableColumn<>("تاریخ");
        dateCol.setCellValueFactory(cellData -> {
            Appointment apt = cellData.getValue();
            return new SimpleStringProperty(PersianDateConverter.toPersianDate(apt.getAppointmentDateTime().toLocalDate()));
        });
        dateCol.setStyle("-fx-font-size: 14px;");

        TableColumn<Appointment, String> notesCol = new TableColumn<>("توضیحات");
        notesCol.setCellValueFactory(new PropertyValueFactory<>("notes"));
        notesCol.setStyle("-fx-font-size: 14px;");

        TableColumn<Appointment, String> statusCol = new TableColumn<>("وضعیت");
        statusCol.setCellValueFactory(cellData -> {
            Appointment apt = cellData.getValue();
            String status = apt.isCompleted() ? "✅ انجام شده" : "⏳ در انتظار";
            return new SimpleStringProperty(status);
        });
        statusCol.setStyle("-fx-font-size: 14px;");

        appointmentsTable.getColumns().addAll(customerCol, phoneCol, dayCol, timeCol, dateCol, notesCol, statusCol);
        appointmentsTable.setItems(customerAppointments);
        appointmentsTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);

        return appointmentsTable;
    }

    private Customer findCustomerById(String customerId) {
        return customers.stream()
                .filter(c -> c.getId().equals(customerId))
                .findFirst()
                .orElse(null);
    }

    private void confirmAndDeleteAppointment(Customer customer, Appointment appointment) {
        Alert confirmation = createAlert(Alert.AlertType.CONFIRMATION, "تأیید حذف",
                "آیا از حذف این نوبت مطمئن هستید؟", "این عمل قابل بازگشت نیست!");

        if (confirmation.showAndWait().get() == ButtonType.OK) {
            appointments.remove(appointment);
            saveData();
            showCustomerAppointments(customer);
        }
    }

    private void showAppointmentForm(Customer customer, Appointment appointment) {
        VBox form = new VBox(20);
        form.setPadding(new Insets(25));
        form.setStyle("-fx-background-color: #ffffff; -fx-background-radius: 15;");

        Label titleLabel = new Label(appointment == null ? "➕ ثبت نوبت جدید" : "✏️ ویرایش نوبت");
        titleLabel.setStyle("-fx-font-size: 24px; -fx-font-weight: bold; -fx-text-fill: #2c3e50;");

        GridPane formGrid = new GridPane();
        formGrid.setHgap(20);
        formGrid.setVgap(20);
        formGrid.setPadding(new Insets(20));

        // Day selection buttons
        ToggleGroup dayGroup = new ToggleGroup();
        VBox daysContainer = createDaySelectionButtons(dayGroup, appointment);

        // Time selection
        ComboBox<Integer> hourPicker = createHourPicker();
        ComboBox<Integer> minutePicker = createMinutePicker();

        // Notes
        TextArea notesArea = createStyledTextArea();
        if (appointment != null) {
            notesArea.setText(appointment.getNotes());
        }

        // Time selection box
        HBox timeBox = new HBox(10);
        timeBox.getChildren().addAll(hourPicker, new Label(":"), minutePicker);

        formGrid.addRow(0, createStyledLabel("انتخاب روز"), new Label(":"), daysContainer);
        formGrid.addRow(1, createStyledLabel("ساعت"), new Label(":"), timeBox);
        formGrid.addRow(2, createStyledLabel("توضیحات"), new Label(":"), notesArea);

        Button saveButton = createStyledButton("💾 ذخیره", "#28a745", "#38b755");
        saveButton.setOnAction(e -> saveAppointment(customer, appointment, dayGroup, hourPicker, minutePicker, notesArea));

        Button cancelButton = createStyledButton("❌ انصراف", "#dc3545", "#ec4555");
        cancelButton.setOnAction(e -> showCustomerAppointments(customer));

        HBox buttonBox = new HBox(20, saveButton, cancelButton);
        buttonBox.setAlignment(Pos.CENTER);

        form.getChildren().addAll(titleLabel, formGrid, buttonBox);
        showInMainPane(form);
    }

    private VBox createDaySelectionButtons(ToggleGroup dayGroup, Appointment appointment) {
        VBox daysContainer = new VBox(10);
        LocalDate today = LocalDate.now();

        // Show today and next 9 days (total 10 days)
        for (int i = 0; i < 10; i++) {
            LocalDate date = today.plusDays(i);
            int dayOfWeek = date.getDayOfWeek().getValue() % 7;
            RadioButton dayButton = new RadioButton(PERSIAN_WEEK_DAYS[dayOfWeek] + " " +
                    PersianDateConverter.toPersianDate(date));
            dayButton.setToggleGroup(dayGroup);
            dayButton.setUserData(date);
            dayButton.setStyle("-fx-font-size: 14px; -fx-text-fill: #2c3e50;");
            daysContainer.getChildren().add(dayButton);

            // Select appointment date if editing
            if (appointment != null && date.equals(appointment.getAppointmentDateTime().toLocalDate())) {
                dayButton.setSelected(true);
            }
        }

        // Select today by default for new appointments
        if (appointment == null && !daysContainer.getChildren().isEmpty()) {
            ((RadioButton)daysContainer.getChildren().get(0)).setSelected(true);
        }

        return daysContainer;
    }

    private ComboBox<Integer> createHourPicker() {
        ComboBox<Integer> hourPicker = new ComboBox<>();
        for (int i = 0; i <= 23; i++) {
            hourPicker.getItems().add(i);
        }
        hourPicker.setStyle("-fx-font-size: 14px; -fx-pref-height: 40; -fx-padding: 8 15; " +
                "-fx-background-radius: 5; -fx-border-radius: 5; " +
                "-fx-border-color: #ced4da; " +
                "-fx-background-color: #ffffff; " +
                "-fx-text-fill: #2c3e50;");
        hourPicker.setValue(LocalTime.now().getHour());
        return hourPicker;
    }

    private ComboBox<Integer> createMinutePicker() {
        ComboBox<Integer> minutePicker = new ComboBox<>();
        minutePicker.getItems().addAll(0, 15, 30, 45);
        minutePicker.setStyle("-fx-font-size: 14px; -fx-pref-height: 40; -fx-padding: 8 15; " +
                "-fx-background-radius: 5; -fx-border-radius: 5; " +
                "-fx-border-color: #ced4da; " +
                "-fx-background-color: #ffffff; " +
                "-fx-text-fill: #2c3e50;");
        minutePicker.setValue(0);
        return minutePicker;
    }

    private void saveAppointment(Customer customer, Appointment appointment,
                                 ToggleGroup dayGroup, ComboBox<Integer> hourPicker,
                                 ComboBox<Integer> minutePicker, TextArea notesArea) {

        if (hourPicker.getValue() == null || minutePicker.getValue() == null) {
            showAlert("خطا", "لطفاً ساعت را انتخاب کنید");
            return;
        }

        RadioButton selectedDay = (RadioButton) dayGroup.getSelectedToggle();
        if (selectedDay == null) {
            showAlert("خطا", "لطفاً یک روز را انتخاب کنید");
            return;
        }

        LocalDate selectedDate = (LocalDate) selectedDay.getUserData();
        LocalDateTime appointmentDateTime = LocalDateTime.of(
                selectedDate,
                LocalTime.of(hourPicker.getValue(), minutePicker.getValue())
        );

        if (appointment == null) {
            Appointment newAppointment = new Appointment(
                    customer.getId(),
                    appointmentDateTime,
                    notesArea.getText()
            );
            appointments.add(newAppointment);
        } else {
            appointment.setNotes(notesArea.getText());
        }
        saveData();
        showCustomerAppointments(customer);
    }

    private void showUpcomingAppointments() {
        VBox appointmentsView = new VBox(20);
        appointmentsView.setPadding(new Insets(25));
        appointmentsView.setStyle("-fx-background-color: #ffffff; -fx-background-radius: 15;");

        Label titleLabel = new Label("📅 نوبت‌های آینده");
        titleLabel.setStyle("-fx-font-size: 24px; -fx-font-weight: bold; -fx-text-fill: #2c3e50;");

        // Appointments table
        TableView<Appointment> appointmentsTable = createUpcomingAppointmentsTable();

        // Appointment action buttons
        HBox appointmentButtons = new HBox(15);
        appointmentButtons.setAlignment(Pos.CENTER);

        Button toggleStatusButton = createStyledButton("✅ انجام شده", "#28a745", "#38b755");
        toggleStatusButton.setOnAction(e -> {
            Appointment selectedAppointment = appointmentsTable.getSelectionModel().getSelectedItem();
            if (selectedAppointment != null) {
                selectedAppointment.setCompleted(true);
                saveData();
                showUpcomingAppointments();
            } else {
                showAlert("خطا", "لطفاً یک نوبت را انتخاب کنید");
            }
        });

        Button deleteButton = createStyledButton("🗑️ حذف نوبت", "#dc3545", "#ec4555");
        deleteButton.setOnAction(e -> {
            Appointment selectedAppointment = appointmentsTable.getSelectionModel().getSelectedItem();
            if (selectedAppointment != null) {
                confirmAndDeleteAppointment(null, selectedAppointment);
                showUpcomingAppointments();
            } else {
                showAlert("خطا", "لطفاً یک نوبت را انتخاب کنید");
            }
        });

        appointmentButtons.getChildren().addAll(toggleStatusButton, deleteButton);

        // Back button
        backButton = createStyledButton("← بازگشت", "#6c757d", "#7c858d");
        backButton.setOnAction(e -> setupMainMenu());

        appointmentsView.getChildren().addAll(
                new HBox(backButton),
                titleLabel,
                appointmentsTable,
                appointmentButtons
        );
        showInMainPane(appointmentsView);
    }

    private TableView<Appointment> createUpcomingAppointmentsTable() {
        TableView<Appointment> appointmentsTable = new TableView<>();
        appointmentsTable.setStyle("-fx-font-size: 14px; -fx-table-cell-border-color: transparent; " +
                "-fx-selection-bar: #4a6baf; -fx-pref-height: 500; " +
                "-fx-background-color: white;");

        // Filter upcoming and incomplete appointments
        ObservableList<Appointment> upcomingAppointments = FXCollections.observableArrayList();
        LocalDateTime now = LocalDateTime.now();
        appointments.stream()
                .filter(apt -> !apt.isCompleted() && apt.getAppointmentDateTime().isAfter(now))
                .sorted((a1, a2) -> a1.getAppointmentDateTime().compareTo(a2.getAppointmentDateTime()))
                .forEach(upcomingAppointments::add);

        TableColumn<Appointment, String> customerCol = new TableColumn<>("مشتری");
        customerCol.setCellValueFactory(cellData -> {
            Appointment apt = cellData.getValue();
            Customer foundCustomer = findCustomerById(apt.getCustomerId());
            return new SimpleStringProperty(foundCustomer != null ? foundCustomer.getName() : "نامشخص");
        });
        customerCol.setStyle("-fx-font-size: 14px;");

        TableColumn<Appointment, String> phoneCol = new TableColumn<>("شماره تماس");
        phoneCol.setCellValueFactory(cellData -> {
            Appointment apt = cellData.getValue();
            Customer foundCustomer = findCustomerById(apt.getCustomerId());
            return new SimpleStringProperty(foundCustomer != null ? foundCustomer.getPhone() : "نامشخص");
        });
        phoneCol.setStyle("-fx-font-size: 14px;");

        TableColumn<Appointment, String> dayCol = new TableColumn<>("روز");
        dayCol.setCellValueFactory(cellData -> {
            Appointment apt = cellData.getValue();
            int dayOfWeek = apt.getAppointmentDateTime().getDayOfWeek().getValue() % 7;
            return new SimpleStringProperty(PERSIAN_WEEK_DAYS[dayOfWeek]);
        });
        dayCol.setStyle("-fx-font-size: 14px;");

        TableColumn<Appointment, String> timeCol = new TableColumn<>("ساعت");
        timeCol.setCellValueFactory(cellData -> {
            Appointment apt = cellData.getValue();
            LocalTime time = apt.getAppointmentDateTime().toLocalTime();
            return new SimpleStringProperty(String.format("%02d:%02d", time.getHour(), time.getMinute()));
        });
        timeCol.setStyle("-fx-font-size: 14px;");

        TableColumn<Appointment, String> dateCol = new TableColumn<>("تاریخ");
        dateCol.setCellValueFactory(cellData -> {
            Appointment apt = cellData.getValue();
            return new SimpleStringProperty(PersianDateConverter.toPersianDate(apt.getAppointmentDateTime().toLocalDate()));
        });
        dateCol.setStyle("-fx-font-size: 14px;");

        TableColumn<Appointment, String> notesCol = new TableColumn<>("توضیحات");
        notesCol.setCellValueFactory(new PropertyValueFactory<>("notes"));
        notesCol.setStyle("-fx-font-size: 14px;");

        TableColumn<Appointment, String> statusCol = new TableColumn<>("وضعیت");
        statusCol.setCellValueFactory(cellData -> {
            Appointment apt = cellData.getValue();
            String status = apt.isCompleted() ? "✅ انجام شده" : "⏳ در انتظار";
            return new SimpleStringProperty(status);
        });
        statusCol.setStyle("-fx-font-size: 14px;");

        appointmentsTable.getColumns().addAll(customerCol, phoneCol, dayCol, timeCol, dateCol, notesCol, statusCol);
        appointmentsTable.setItems(upcomingAppointments);
        appointmentsTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);

        return appointmentsTable;
    }

    private void showInMainPane(javafx.scene.Node node) {
        mainPane.getChildren().clear();
        mainPane.getChildren().add(node);
    }

    // Data model classes
    public static class Customer implements Serializable {
        private static final long serialVersionUID = 1L;
        private final String id;
        private String name;
        private String phone;
        private String description;
        private final LocalDateTime creationDate;

        public Customer(String name, String phone) {
            this.id = "CUST-" + System.currentTimeMillis();
            this.name = name.trim();
            this.phone = phone.trim();
            this.description = "";
            this.creationDate = LocalDateTime.now();
        }

        // Getters
        public String getId() { return id; }
        public String getName() { return name; }
        public String getPhone() { return phone; }
        public String getDescription() { return description; }
        public LocalDateTime getCreationDate() { return creationDate; }
        public String getPersianCreationDate() { return PersianDateConverter.toPersianDateTime(creationDate); }

        // Setters
        public void setName(String name) { this.name = name; }
        public void setPhone(String phone) { this.phone = phone; }
        public void setDescription(String description) { this.description = description; }
    }

    public static class Car implements Serializable {
        private static final long serialVersionUID = 1L;
        private final String id;
        private final String customerId;
        private String model;
        private String year;
        private String ecuModel;
        private String description;
        private final LocalDateTime creationDate;
        private final List<DumpFile> dumpFiles = new ArrayList<>();

        public Car(String customerId, String model, String year, String ecuModel) {
            this.id = "CAR-" + System.currentTimeMillis();
            this.customerId = customerId;
            this.model = model.trim();
            this.year = year.trim();
            this.ecuModel = ecuModel.trim();
            this.description = "";
            this.creationDate = LocalDateTime.now();
        }

        // Getters
        public String getId() { return id; }
        public String getCustomerId() { return customerId; }
        public String getModel() { return model; }
        public String getYear() { return year; }
        public String getEcuModel() { return ecuModel; }
        public String getDescription() { return description; }
        public LocalDateTime getCreationDate() { return creationDate; }
        public List<DumpFile> getDumpFiles() { return new ArrayList<>(dumpFiles); }
        public String getPersianCreationDate() { return PersianDateConverter.toPersianDateTime(creationDate); }

        // Setters
        public void setModel(String model) { this.model = model; }
        public void setYear(String year) { this.year = year; }
        public void setEcuModel(String ecuModel) { this.ecuModel = ecuModel; }
        public void setDescription(String description) { this.description = description; }

        // Dump file management
        public void addDumpFile(String fileName) { dumpFiles.add(new DumpFile(fileName, LocalDateTime.now())); }
        public void removeDumpFile(String fileName) { dumpFiles.removeIf(df -> df.getFileName().equals(fileName)); }
        public void renameDumpFile(String oldName, String newName) {
            dumpFiles.stream()
                    .filter(df -> df.getFileName().equals(oldName))
                    .findFirst()
                    .ifPresent(df -> df.setFileName(newName));
        }
    }

    public static class DumpFile implements Serializable {
        private static final long serialVersionUID = 1L;
        private String fileName;
        private final LocalDateTime creationDate;

        public DumpFile(String fileName, LocalDateTime creationDate) {
            this.fileName = fileName;
            this.creationDate = creationDate;
        }

        public String getFileName() { return fileName; }
        public LocalDateTime getCreationDate() { return creationDate; }
        public String getPersianCreationDate() { return PersianDateConverter.toPersianDateTime(creationDate); }
        public void setFileName(String fileName) { this.fileName = fileName; }
    }

    public static class Appointment implements Serializable {
        private static final long serialVersionUID = 1L;
        private final String id;
        private final String customerId;
        private final LocalDateTime appointmentDateTime;
        private String notes;
        private boolean isCompleted;
        private final LocalDateTime creationDate;

        public Appointment(String customerId, LocalDateTime appointmentDateTime, String notes) {
            this.id = "APT-" + System.currentTimeMillis();
            this.customerId = customerId;
            this.appointmentDateTime = appointmentDateTime;
            this.notes = notes;
            this.isCompleted = false;
            this.creationDate = LocalDateTime.now();
        }

        // Getters
        public String getId() { return id; }
        public String getCustomerId() { return customerId; }
        public LocalDateTime getAppointmentDateTime() { return appointmentDateTime; }
        public String getNotes() { return notes; }
        public boolean isCompleted() { return isCompleted; }
        public LocalDateTime getCreationDate() { return creationDate; }
        public String getPersianAppointmentDateTime() { return PersianDateConverter.toPersianDateTime(appointmentDateTime); }
        public String getPersianCreationDate() { return PersianDateConverter.toPersianDateTime(creationDate); }

        // Setters
        public void setNotes(String notes) { this.notes = notes; }
        public void setCompleted(boolean completed) { isCompleted = completed; }
    }

    public static class PersianDateConverter {
        private static final int[] MONTH_DAYS = {31, 31, 31, 31, 31, 31, 30, 30, 30, 30, 30, 29};

        public static String toPersianDate(LocalDate gregorianDate) {
            int[] pDate = gregorianToPersian(
                    gregorianDate.getYear(),
                    gregorianDate.getMonthValue(),
                    gregorianDate.getDayOfMonth()
            );
            return String.format("%d %s %d",
                    pDate[2],
                    PERSIAN_MONTHS[pDate[1] - 1],
                    pDate[0]);
        }

        public static String toPersianDateTime(LocalDateTime dateTime) {
            String date = toPersianDate(dateTime.toLocalDate());
            String time = String.format("%02d:%02d", dateTime.getHour(), dateTime.getMinute());
            return date + " - " + time;
        }

        private static int[] gregorianToPersian(int gYear, int gMonth, int gDay) {
            int gy = gYear - 1600;
            int gm = gMonth - 1;
            int gd = gDay - 1;

            int gDayNo = 365 * gy + (gy + 3) / 4 - (gy + 99) / 100 + (gy + 399) / 400;

            for (int i = 0; i < gm; ++i) {
                gDayNo += exactGregorianMonthDays(i, gYear);
            }

            gDayNo += gd;

            int pDayNo = gDayNo - 79;
            int pYear = 979 + 33 * (pDayNo / 12053);
            pDayNo %= 12053;

            pYear += 4 * (pDayNo / 1461);
            pDayNo %= 1461;

            if (pDayNo >= 366) {
                pYear += (pDayNo - 1) / 365;
                pDayNo = (pDayNo - 1) % 365;
            }

            int i;
            for (i = 0; i < 11 && pDayNo >= MONTH_DAYS[i]; ++i) {
                pDayNo -= MONTH_DAYS[i];
            }

            return new int[]{pYear, i + 1, pDayNo + 1};
        }

        private static int exactGregorianMonthDays(int month, int year) {
            switch (month) {
                case 0: return 31;
                case 1: return isGregorianLeap(year) ? 29 : 28;
                case 2: return 31;
                case 3: return 30;
                case 4: return 31;
                case 5: return 30;
                case 6: return 31;
                case 7: return 31;
                case 8: return 30;
                case 9: return 31;
                case 10: return 30;
                case 11: return 31;
                default: return 0;
            }
        }

        private static boolean isGregorianLeap(int year) {
            return (year % 4 == 0 && year % 100 != 0) || (year % 400 == 0);
        }
    }
}