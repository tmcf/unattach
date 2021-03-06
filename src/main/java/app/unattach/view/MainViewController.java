package app.unattach.view;

import app.unattach.controller.Controller;
import app.unattach.controller.ControllerFactory;
import app.unattach.controller.LongTask;
import app.unattach.model.*;
import javafx.application.Platform;
import javafx.beans.Observable;
import javafx.collections.FXCollections;
import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;
import javafx.concurrent.Task;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.VBox;
import javafx.stage.DirectoryChooser;
import javafx.stage.Modality;
import javafx.stage.Stage;
import org.apache.commons.lang3.exception.ExceptionUtils;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class MainViewController {
  private static final Logger LOGGER = Logger.getLogger(MainViewController.class.getName());

  private Controller controller;
  @FXML
  private VBox root;

  // Menu
  @FXML
  private MenuItem emailMenuItem;
  @FXML
  private MenuItem signOutMenuItem;
  @FXML
  private CheckMenuItem addMetadataCheckMenuItem;
  @FXML
  private Menu viewColumnMenu;
  @FXML
  private Menu donationCurrencyMenu;
  @FXML
  private Menu donateMenu;
  @FXML
  private MenuItem donateTwo;
  @FXML
  private MenuItem donateFive;
  @FXML
  private MenuItem donateTen;
  @FXML
  private MenuItem donateTwentyFive;
  @FXML
  private MenuItem donateFifty;
  @FXML
  private MenuItem donateCustom;

  // Search view
  @FXML
  private Tab basicSearchTab;
  @FXML
  private ComboBox<ComboItem<Integer>> emailSizeComboBox;
  @FXML
  private Label labelsListViewLabel;
  @FXML
  private ListView<String> labelsListView;
  @FXML
  private TextField searchQueryTextField;
  @FXML
  private Button searchButton;
  @FXML
  private ProgressBarWithText searchProgressBarWithText;
  @FXML
  private CheckBox backupCheckBox;
  @FXML
  private Button stopSearchButton;
  private boolean stopSearchButtonPressed;

  // Results view
  private static final String DESELECT_ALL_CAPTION = "Deselect all";
  private static final String SELECT_ALL_CAPTION = "Select all";
  @FXML
  private SubView resultsSubView;
  @FXML
  private TableView<Email> resultsTable;
  @FXML
  private TableColumn<Email, CheckBox> selectedTableColumn;
  @FXML
  private CheckBox toggleAllEmailsCheckBox;
  private boolean toggleAllShouldSelect = true;

  // Download view
  @FXML
  private TextField targetDirectoryTextField;
  @FXML
  private Button browseButton;
  @FXML
  private Button downloadButton;
  @FXML
  private Button downloadAndDeleteButton;
  @FXML
  private Button deleteButton;
  @FXML
  private Button stopProcessingButton;
  @FXML
  private ProgressBarWithText processingProgressBarWithText;
  private long bytesProcessed = 0;
  private long allBytesToProcess = 0;
  private boolean stopProcessingButtonPressed = false;

  @FXML
  private void initialize() throws IOException {
    controller = ControllerFactory.getDefaultController();
    emailMenuItem.setText("Signed in as " + controller.getEmailAddress() + ".");
    addMenuForHidingColumns();
    List<CheckMenuItem> currencyMenuItems =
            Arrays.stream(Constants.CURRENCIES).map(CheckMenuItem::new).collect(Collectors.toList());
    currencyMenuItems.forEach(menuItem -> menuItem.setOnAction(this::onDonationCurrencySelected));
    donationCurrencyMenu.getItems().addAll(currencyMenuItems);
    Platform.runLater(() -> {
      currencyMenuItems.stream().filter(menuItem -> menuItem.getText().equals(Constants.DEFAULT_CURRENCY))
              .forEach(menuItem -> {menuItem.setSelected(true); menuItem.fire();});
    });
    donateMenu.setGraphic(new Label()); // This enables the CSS style for the menu.
    emailSizeComboBox.setItems(FXCollections.observableList(getEmailSizeOptions()));
    emailSizeComboBox.getSelectionModel().select(1);
    searchQueryTextField.setText(controller.getSearchQuery());
    searchProgressBarWithText.progressProperty().setValue(0);
    searchProgressBarWithText.textProperty().setValue("(Searching not started yet.)");
    toggleAllEmailsCheckBox.setTooltip(new Tooltip(DESELECT_ALL_CAPTION));
    selectedTableColumn.setComparator((cb1, cb2) -> Boolean.compare(cb1.isSelected(), cb2.isSelected()));
    targetDirectoryTextField.setText(controller.getTargetDirectory());
    processingProgressBarWithText.progressProperty().setValue(0);
    processingProgressBarWithText.textProperty().setValue("(Processing of emails not started yet.)");
    labelsListViewLabel.setText("Email labels:\n(If selecting multiple, results will match any.)");
    labelsListView.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);
    ArrayList<String> labels = new ArrayList<>(controller.getIdToLabel().values());
    Collections.sort(labels);
    labelsListView.setItems(FXCollections.observableList(labels));
  }

  private void addMenuForHidingColumns() {
    resultsTable.getColumns().forEach(column -> {
      CheckMenuItem menuItem = new CheckMenuItem(column.getText());
      menuItem.setSelected(true);
      menuItem.setOnAction(event -> column.setVisible(menuItem.isSelected()));
      viewColumnMenu.getItems().add(menuItem);
    });
  }

  private Vector<ComboItem<Integer>> getEmailSizeOptions() {
    Vector<ComboItem<Integer>> options = new Vector<>();
    for (int minEmailSizeInMb : new int[] {0, 1, 2, 3, 5, 10, 25, 50, 100}) {
      String caption = minEmailSizeInMb == 0 ? "all sizes" : String.format("more than %d MB", minEmailSizeInMb);
      options.add(new ComboItem<>(caption, minEmailSizeInMb));
    }
    return options;
  }

  @FXML
  private void onAboutButtonPressed() {
    controller.openUnattachHomepage();
  }

  @FXML
  private void onFeedbackMenuItemPressed() throws IOException {
    Stage dialog = Scenes.createNewStage("feedback");
    dialog.initOwner(root.getScene().getWindow());
    dialog.initModality(Modality.APPLICATION_MODAL);
    dialog.setScene(Scenes.loadScene("/feedback.view.fxml"));
    Scenes.showAndPreventMakingSmaller(dialog);
  }

  @FXML
  private void onSignOutButtonPressed() throws IOException {
    controller.signOut();
    Scenes.setScene(Scenes.SIGN_IN);
  }

  @FXML
  private void onSearchButtonPressed() {
    disableControls();
    resultsSubView.setText("Results");
    stopSearchButton.setDisable(false);
    stopSearchButtonPressed = false;
    controller.clearPreviousSearch();
    resultsTable.setItems(FXCollections.emptyObservableList());
    AtomicInteger currentBatch = new AtomicInteger();
    AtomicInteger numberOfBatches = new AtomicInteger();

    Task<Void> task = new Task<>() {
      @Override
      protected Void call() throws Exception {
        updateProgress(0, 1);
        updateMessage("Obtaining email metadata ..");
        String query = getQuery();
        LOGGER.info("Obtaining email metadata (query: " + query + ") ..");
        GetEmailMetadataTask longTask = controller.getSearchTask(query);
        currentBatch.set(0);
        numberOfBatches.set(longTask.getNumberOfSteps());
        updateProgress(currentBatch.get(), numberOfBatches.get());
        updateMessage(String.format("Obtaining email metadata (%s) ..", getStatusString()));
        while (!stopSearchButtonPressed && longTask.hasMoreSteps()) {
          GetEmailMetadataTask.Result result = longTask.takeStep();
          currentBatch.set(result.currentBatchNumber);
          updateProgress(currentBatch.get(), numberOfBatches.get());
          updateMessage(String.format("Obtaining email metadata (%s) ..", getStatusString()));
        }
        return null;
      }

      private String getStatusString() {
        if (numberOfBatches.get() == 0) {
          return "no emails matched the query";
        } else {
          return String.format("completed %d of %d batches, %d%%",
              currentBatch.get(), numberOfBatches.get(), 100 * currentBatch.get() / numberOfBatches.get());
        }
      }

      @Override
      protected void succeeded() {
        try {
          updateMessage(String.format("Finished obtaining email metadata (%s).", getStatusString()));
          List<Email> emails = controller.getEmails();
          ObservableList<Email> observableEmails = FXCollections.observableList(emails, email -> new Observable[]{email});
          resultsTable.setItems(observableEmails);
          updateResultsCaption();
          observableEmails.addListener((ListChangeListener<? super Email>) change -> updateResultsCaption());
        } catch (Throwable t) {
          String message = "Failed to process email metadata.";
          updateMessage(message);
          reportError(message, t);
        } finally {
          resetControls();
        }
      }

      @Override
      protected void failed() {
        String message = "Failed to obtain email metadata.";
        updateMessage(message);
        reportError(message, getException());
        resetControls();
      }
    };

    searchProgressBarWithText.progressProperty().bind(task.progressProperty());
    searchProgressBarWithText.textProperty().bind(task.messageProperty());

    new Thread(task).start();
  }

  private void updateResultsCaption() {
    Platform.runLater(() -> {
      int selected = 0, total = 0, selectedSizeInMegaBytes = 0, totalSizeInMegaBytes = 0;
      for (Email email : resultsTable.getItems()) {
        if (email.isSelected()) {
          ++selected;
          selectedSizeInMegaBytes += email.getSizeInMegaBytes();
        }
        ++total;
        totalSizeInMegaBytes += email.getSizeInMegaBytes();
      }
      resultsSubView.setText(String.format("Results: selected %d/%d (%dMB/%dMB)",
              selected, total, selectedSizeInMegaBytes, totalSizeInMegaBytes));
    });
  }

  private void reportError(String message, Throwable t) {
    LOGGER.log(Level.SEVERE, message, t);
    String stackTraceText = ExceptionUtils.getStackTrace(t);
    controller.sendToServer("stack trace", stackTraceText, null);
  }

  @FXML
  private void onStopSearchButtonPressed() {
    stopSearchButtonPressed = true;
  }

  @FXML
  private void onToggleAllEmailsButtonPressed() {
    EmailStatus targetStatus = toggleAllShouldSelect ? EmailStatus.TO_PROCESS : EmailStatus.IGNORED;
    resultsTable.getItems().forEach(email -> {
      if (email.getStatus() == EmailStatus.IGNORED || email.getStatus() == EmailStatus.TO_PROCESS) {
        email.setStatus(targetStatus);
      }
    });
    resultsTable.refresh();
    toggleAllShouldSelect = !toggleAllShouldSelect;
    toggleAllEmailsCheckBox.setTooltip(new Tooltip(toggleAllShouldSelect ? SELECT_ALL_CAPTION : DESELECT_ALL_CAPTION));
  }

  private String getQuery() {
    StringBuilder query = new StringBuilder();
    if (basicSearchTab.isSelected()) {
      int minEmailSizeInMb = emailSizeComboBox.getSelectionModel().getSelectedItem().value;
      query.append(String.format("has:attachment size:%dm", minEmailSizeInMb));
      ObservableList<String> emailLabels = labelsListView.getSelectionModel().getSelectedItems();
      if (!emailLabels.isEmpty()) {
        query.append(" {");
        query.append(emailLabels.stream().map(label -> String.format("label:\"%s\"", label))
                .collect(Collectors.joining(" ")));
        query.append("}");
      }
    } else {
      query = new StringBuilder(searchQueryTextField.getText());
      controller.saveSearchQuery(searchQueryTextField.getText());
    }
    return query.toString();
  }

  @FXML
  private void onBrowseButtonPressed() {
    DirectoryChooser directoryChooser = new DirectoryChooser();
    directoryChooser.setTitle("Set directory for storing attachments");
    File initialDirectory = new File(targetDirectoryTextField.getText());
    if (initialDirectory.exists()) {
      directoryChooser.setInitialDirectory(initialDirectory);
    }
    File newTargetDirectory = directoryChooser.showDialog(targetDirectoryTextField.getScene().getWindow());
    if (newTargetDirectory != null) {
      targetDirectoryTextField.setText(newTargetDirectory.getAbsolutePath());
      controller.saveTargetDirectory(newTargetDirectory.getAbsolutePath());
    }
  }

  @FXML
  private void onOpenButtonPressed() {
    File targetDirectory = getTargetDirectory();
    if (targetDirectory.mkdirs()) {
      LOGGER.info("Created directory \"" + targetDirectory.getAbsolutePath() + "\".");
    }
    controller.openFile(targetDirectory);
  }

  private File getTargetDirectory() {
    return new File(targetDirectoryTextField.getText());
  }

  @FXML
  private void onDownloadButtonPressed() {
    processEmails(new ProcessOption(backupCheckBox.isSelected(), true, false));
  }

  @FXML
  private void onDownloadAndDeleteButtonPressed() {
    String removedLabelId = controller.getOrCreateRemovedLabelId();
    processEmails(new ProcessOption(backupCheckBox.isSelected(), true, true, removedLabelId));
  }

  @FXML
  private void onDeleteButtonPressed() {
    String removedLabelId = controller.getOrCreateRemovedLabelId();
    processEmails(new ProcessOption(backupCheckBox.isSelected(), false, true, removedLabelId));
  }

  private void processEmails(ProcessOption processOption) {
    List<Email> emailsToProcess = getEmailsToProcess();
    if (emailsToProcess.isEmpty()) {
      showNoEmailsAlert();
      return;
    }
    disableControls();
    stopProcessingButton.setDisable(false);
    stopProcessingButtonPressed = false;
    File targetDirectory = getTargetDirectory();
    bytesProcessed = 0;
    allBytesToProcess = emailsToProcess.stream().mapToLong(email -> (long) email.getSizeInBytes()).sum();
    processingProgressBarWithText.progressProperty().setValue(0);
    String filenameSchema = controller.getFilenameSchema();
    ProcessSettings processSettings = new ProcessSettings(processOption, targetDirectory, filenameSchema,
        addMetadataCheckMenuItem.isSelected());
    processEmail(emailsToProcess, 0, 0, processSettings);
  }

  private void showNoEmailsAlert() {
    Alert alert = new Alert(Alert.AlertType.WARNING);
    alert.setTitle("No emails selected");
    alert.setHeaderText(null);
    alert.setContentText("Please select some or all emails in the search results. You can de/select an individual " +
        "email by clicking on the checkbox in its selected row. Alternatively, you can de/select all emails by " +
        "clicking on the checkbox in the table header.");
    alert.showAndWait();
  }

  private void processEmail(List<Email> emailsToProcess, int nextEmailIndex, int failed, ProcessSettings processSettings) {
    if (stopProcessingButtonPressed || nextEmailIndex >= emailsToProcess.size()) {
      processingProgressBarWithText.textProperty().setValue(
          String.format("Processing stopped (%s).", getProcessingStatusString(emailsToProcess, nextEmailIndex, failed)));
      resetControls();
      return;
    }
    Email email = emailsToProcess.get(nextEmailIndex);
    processingProgressBarWithText.textProperty().setValue(
        String.format("Processing selected emails (%s) ..", getProcessingStatusString(emailsToProcess, nextEmailIndex, failed)));

    Task<Void> task = new Task<>() {
      @Override
      protected Void call() throws Exception {
        LongTask<ProcessEmailResult> longTask = controller.getProcessTask(email, processSettings);
        while (!stopProcessingButtonPressed && longTask.hasMoreSteps()) {
          longTask.takeStep();
        }
        return null;
      }

      @Override
      protected void succeeded() {
        bytesProcessed += email.getSizeInBytes();
        processingProgressBarWithText.progressProperty().setValue(1.0 * bytesProcessed / allBytesToProcess);
        resultsTable.refresh();
        processEmail(emailsToProcess, nextEmailIndex + 1, failed, processSettings);
      }

      @Override
      protected void failed() {
        email.setStatus(EmailStatus.FAILED);
        email.setNote(getException().getMessage());
        resultsTable.refresh();
        reportError("Failed to process selected emails.", getException());
        processEmail(emailsToProcess, nextEmailIndex + 1, failed + 1, processSettings);
      }
    };

    new Thread(task).start();
  }

  private String getProcessingStatusString(List<Email> emailsToProcess, int nextEmailIndex, int failed) {
    return String.format("processed %d of %d, %dMB / %dMB, %d%% by size, %d failed",
        nextEmailIndex - failed, emailsToProcess.size(), toMegaBytes(bytesProcessed), toMegaBytes(allBytesToProcess),
        100 * bytesProcessed / allBytesToProcess, failed);
  }

  private static int toMegaBytes(long bytes) {
    return (int) (bytes / Constants.BYTES_IN_MEGABYTE);
  }

  @FXML
  private void onStopProcessingButtonPressed() {
    stopProcessingButton.setDisable(true);
    stopProcessingButtonPressed = true;
  }

  private List<Email> getEmailsToProcess() {
    return resultsTable.getItems().stream()
        .filter(email -> email.getStatus() == EmailStatus.TO_PROCESS).collect(Collectors.toList());
  }

  private void disableControls() {
    signOutMenuItem.setDisable(true);
    searchButton.setDisable(true);
    stopSearchButton.setDisable(true);
    resultsTable.setEditable(false);
    toggleAllEmailsCheckBox.setDisable(true);
    targetDirectoryTextField.setDisable(true);
    browseButton.setDisable(true);
    backupCheckBox.setDisable(true);
    downloadButton.setDisable(true);
    downloadAndDeleteButton.setDisable(true);
    deleteButton.setDisable(true);
    stopProcessingButton.setDisable(true);
  }

  private void resetControls() {
    signOutMenuItem.setDisable(false);
    searchButton.setDisable(false);
    stopSearchButton.setDisable(true);
    resultsTable.setEditable(true);
    toggleAllEmailsCheckBox.setDisable(false);
    toggleAllEmailsCheckBox.setSelected(false);
    targetDirectoryTextField.setDisable(false);
    browseButton.setDisable(false);
    backupCheckBox.setDisable(false);
    downloadButton.setDisable(false);
    downloadAndDeleteButton.setDisable(false);
    deleteButton.setDisable(false);
    stopProcessingButton.setDisable(true);
  }

  @FXML
  private void onDonateMenuItemPressed(ActionEvent event) {
    Object source = event.getSource();
    String currency = getSelectedCurrency();
    if (source == donateTwo) {
      controller.donate("Espresso", 2, currency);
    } else if (source == donateFive) {
      controller.donate("Cappuccino", 5, currency);
    } else if (source == donateTen) {
      controller.donate("Caramel Machiato", 10, currency);
    } else if (source == donateTwentyFive) {
      controller.donate("Bag of Coffee", 25, currency);
    } else if (source == donateFifty) {
      controller.donate("Coffee Machine", 50, currency);
    } else if (source == donateCustom) {
      controller.donate("A Truck of Coffee", 0, currency);
    }
  }

  @FXML
  private void onOpenLogMenuItemPressed() {
    try {
      String home = System.getProperty("user.home");
      File homeFile = new File(home);
      File[] logFiles = homeFile.listFiles(file -> {
        String name = file.getName();
        return name.startsWith(".unattach-") && name.endsWith(".log");
      });
      long latestTimestamp = 0;
      File latest = null;
      if (logFiles == null) {
        return;
      }
      for (File logFile : logFiles) {
        if (logFile.lastModified() > latestTimestamp) {
          latestTimestamp = logFile.lastModified();
          latest = logFile;
        }
      }
      controller.openFile(latest);
    } catch (Throwable t) {
      reportError("Couldn't open the log file.", t);
    }
  }

  @FXML
  private void onQueryButtonPressed() {
    controller.openQueryLanguagePage();
  }

  @FXML
  private void onFilenameSchemaMenuItemPressed() {
    try {
      Stage dialog = Scenes.createNewStage("file name scheme");
      dialog.initOwner(root.getScene().getWindow());
      dialog.initModality(Modality.APPLICATION_MODAL);
      Scene scene = Scenes.loadScene("/filename-schema.view.fxml");
      FilenameSchemaController filenameSchemaController = (FilenameSchemaController) scene.getUserData();
      filenameSchemaController.setSchema(controller.getFilenameSchema());
      dialog.setScene(scene);
      Scenes.showAndPreventMakingSmaller(dialog);
    } catch (IOException e) {
      LOGGER.log(Level.SEVERE, "Failed to open the file name scheme dialog.", e);
    }
  }

  @FXML
  private void onGmailLabelMenuItemPressed() {
    try {
      Stage dialog = Scenes.createNewStage("Gmail label");
      dialog.initOwner(root.getScene().getWindow());
      dialog.initModality(Modality.APPLICATION_MODAL);
      Scene scene = Scenes.loadScene("/gmail-label.view.fxml");
      dialog.setScene(scene);
      Scenes.showAndPreventMakingSmaller(dialog);
    } catch (IOException e) {
      LOGGER.log(Level.SEVERE, "Failed to open the gmail label dialog.", e);
    }
  }

  @FXML
  private void onDonationCurrencySelected(ActionEvent actionEvent) {
    donationCurrencyMenu.getItems().stream().map(CheckMenuItem.class::cast).forEach(e -> e.setSelected(false));
    ((CheckMenuItem) actionEvent.getSource()).setSelected(true);
    String currency = getSelectedCurrency();
    donateMenu.getItems().forEach(e -> {
      String text = e.getText();
      int start = text.indexOf('(');
      String details = text.substring(start + 1, text.length() - 1);
      String[] parts = details.split(" ");
      if (parts.length == 2) {
        String prefix = text.substring(0, start);
        e.setText(prefix + "(" + parts[0] + " " + currency + ")");
      }
    });
  }

  private String getSelectedCurrency() {
    Optional<CheckMenuItem> selectedCurrencyMenu = donationCurrencyMenu.getItems().stream()
            .map(CheckMenuItem.class::cast).filter(CheckMenuItem::isSelected).findFirst();
    return selectedCurrencyMenu.isEmpty() ? null : selectedCurrencyMenu.get().getText();
  }
}
