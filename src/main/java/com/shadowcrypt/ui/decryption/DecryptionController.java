package com.shadowcrypt.ui.decryption;

import com.shadowcrypt.exception.FileSafetyException;
import com.shadowcrypt.exception.ShadowCryptException;
import com.shadowcrypt.image.models.CarrierImage;
import com.shadowcrypt.image.normalization.CarrierNormalizer;
import com.shadowcrypt.model.DecryptionRequest;
import com.shadowcrypt.model.DecryptionResult;
import com.shadowcrypt.model.PayloadType;
import com.shadowcrypt.service.AnalysisService;
import com.shadowcrypt.service.DecryptionService;
import com.shadowcrypt.ui.analysis.StegoAnalysisRunner;
import com.shadowcrypt.ui.components.Dialogs;
import com.shadowcrypt.ui.components.ErrorText;
import com.shadowcrypt.ui.components.Formatters;
import com.shadowcrypt.ui.main.AppState;
import com.shadowcrypt.ui.main.BackgroundTaskRunner;
import com.shadowcrypt.util.SecureWipe;
import javafx.application.Platform;
import javafx.concurrent.Task;
import javafx.stage.DirectoryChooser;
import javafx.stage.FileChooser;
import javafx.stage.Window;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/** Wires the decryption screen: previews, validation, progress, and result rendering. */
public final class DecryptionController {

    private final DecryptionView view;
    private final AppState state;
    private final DecryptionService decryptionService;
    private final AnalysisService analysisService;

    public DecryptionController(DecryptionView view, AppState state,
                                DecryptionService decryptionService, AnalysisService analysisService) {
        this.view = view;
        this.state = state;
        this.decryptionService = decryptionService;
        this.analysisService = analysisService;

        view.stegoPicker().setOnBrowse(this::chooseStegoImage);
        view.privateKeyPicker().setOnBrowse(this::choosePrivateKey);
        view.outputDirectoryPicker().setOnBrowse(this::chooseOutputDirectory);
        view.decryptButton().setOnAction(event -> startDecryption(false));

        state.lastPrivateKeyFileProperty().addListener((observable, oldValue, newValue) -> {
            if (newValue != null) {
                view.privateKeyPicker().setPath(newValue);
            }
        });
        if (state.lastPrivateKeyFile() != null) {
            view.privateKeyPicker().setPath(state.lastPrivateKeyFile());
        }
    }

    private void chooseStegoImage() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Choose the stego PNG image");
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("PNG images", "*.png"));
        File file = chooser.showOpenDialog(window());
        if (file != null) {
            applyStegoImage(file.toPath());
        }
    }

    private void applyStegoImage(Path path) {
        try {
            CarrierImage image = CarrierNormalizer.load(path);
            view.stegoPicker().setPath(path);
            view.stegoPreview().setImage(image.image(),
                    path.getFileName() + " • " + image.width() + "×" + image.height());
            view.statusBanner().hide();
            StegoAnalysisRunner.run(analysisService, path, state, null, null);
        } catch (ShadowCryptException e) {
            view.stegoPicker().setPath(path);
            view.stegoPreview().clear("Unsupported image");
            view.statusBanner().error(e.getMessage());
        }
    }

    private void choosePrivateKey() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Choose the protected private-key file");
        chooser.getExtensionFilters()
                .add(new FileChooser.ExtensionFilter("ShadowCrypt private key", "*.scpriv"));
        File file = chooser.showOpenDialog(window());
        if (file != null) {
            view.privateKeyPicker().setPath(file.toPath());
            view.statusBanner().hide();
        }
    }

    private void chooseOutputDirectory() {
        DirectoryChooser chooser = new DirectoryChooser();
        chooser.setTitle("Choose the output folder");
        Path current = view.outputDirectoryPicker().getPath();
        if (current != null && Files.isDirectory(current)) {
            chooser.setInitialDirectory(current.toFile());
        }
        File directory = chooser.showDialog(window());
        if (directory != null) {
            view.outputDirectoryPicker().setPath(directory.toPath());
        }
    }

    private void startDecryption(boolean replaceExisting) {
        Path stego = view.stegoPicker().getPath();
        Path privateKey = view.privateKeyPicker().getPath();
        Path outputDirectory = view.outputDirectoryPicker().getPath();
        String passphraseText = view.passphrase();

        if (stego == null) {
            view.statusBanner().warning("Choose the stego PNG image.");
            return;
        }
        if (privateKey == null) {
            view.statusBanner().warning("Choose the private-key file.");
            return;
        }
        if (outputDirectory == null) {
            view.statusBanner().warning("Choose the output folder for recovered files.");
            return;
        }
        if (passphraseText.isEmpty()) {
            view.statusBanner().warning("Enter the private-key passphrase.");
            return;
        }

        char[] passphrase = passphraseText.toCharArray();
        DecryptionRequest request;
        try {
            request = new DecryptionRequest(stego, privateKey, passphrase, outputDirectory, replaceExisting);
        } finally {
            SecureWipe.wipe(passphrase);
        }
        runDecryption(request);
    }

    private void runDecryption(DecryptionRequest request) {
        view.decryptButton().setDisable(true);
        view.statusBanner().hide();
        view.busyPane().showIndeterminate("Extracting and decrypting…");

        Task<DecryptionResult> task = new Task<>() {
            @Override
            protected DecryptionResult call() {
                return decryptionService.decrypt(request, (progress, message) ->
                        Platform.runLater(() -> view.busyPane().showProgress(progress, message)));
            }
        };

        BackgroundTaskRunner.run(task,
                result -> {
                    view.busyPane().hideBusy();
                    view.decryptButton().setDisable(false);
                    view.clearPassphrase();
                    state.setLastPrivateKeyFile(request.privateKeyFile());
                    renderResult(result);
                },
                error -> {
                    view.busyPane().hideBusy();
                    view.decryptButton().setDisable(false);
                    if (retryWithReplacementIfNeeded(error, request)) {
                        return;
                    }
                    view.statusBanner().error(ErrorText.describe(error));
                });
    }

    private boolean retryWithReplacementIfNeeded(Throwable error, DecryptionRequest request) {
        if (request.replaceExisting() || !(error instanceof FileSafetyException)) {
            return false;
        }
        boolean confirmed = Dialogs.confirm(window(), "Replace recovered file?",
                "A file with the recovered name already exists.",
                error.getMessage() + "\n\nReplace the existing file?");
        if (!confirmed) {
            return false;
        }
        startDecryption(true);
        return true;
    }

    private void renderResult(DecryptionResult result) {
        if (result.type() == PayloadType.TEXT) {
            view.showResultCard("Recovered text");
            view.setRecoveredTextVisible(true);
            view.recoveredText().setText(result.text());
            view.resultMetrics().addMetric("Text length", result.text().length() + " characters");
        } else {
            view.showResultCard("Recovered file");
            view.setRecoveredTextVisible(false);
            view.recoveredText().clear();
            view.resultMetrics().addMetric("File name", result.fileName());
            view.resultMetrics().addMetric("File size", fileSize(result.recoveredFile()));
            view.resultMetrics().addMetric("Saved to", result.recoveredFile().toString());
        }
        view.resultMetrics().addMetric("Package size", Formatters.formatBytes(result.packageBytes()));
        view.resultMetrics().addMetric("Extraction time",
                Formatters.formatDurationMillis(result.extractionNanos()));
        view.resultMetrics().addMetric("Decryption time",
                Formatters.formatDurationMillis(result.decryptionNanos()));
        view.setSha(result.recoveredSha256());
        view.setAuthentication("AES-256-GCM authentication passed — payload integrity verified", true);

        String message = result.type() == PayloadType.TEXT
                ? "Decryption complete. The recovered text is shown below."
                : "Decryption complete. Recovered file written to " + result.recoveredFile() + ".";
        view.statusBanner().success(message);
    }

    private static String fileSize(Path file) {
        try {
            return Formatters.formatBytes(Files.size(file));
        } catch (IOException e) {
            return "—";
        }
    }

    private Window window() {
        return view.getScene() == null ? null : view.getScene().getWindow();
    }
}
