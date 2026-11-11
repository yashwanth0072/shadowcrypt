package com.shadowcrypt.ui.main;

import com.shadowcrypt.service.AnalysisService;
import com.shadowcrypt.service.DecryptionService;
import com.shadowcrypt.service.EncryptionService;
import com.shadowcrypt.service.KeyService;
import com.shadowcrypt.ui.analysis.AnalysisController;
import com.shadowcrypt.ui.analysis.AnalysisView;
import com.shadowcrypt.ui.decryption.DecryptionController;
import com.shadowcrypt.ui.decryption.DecryptionView;
import com.shadowcrypt.ui.encryption.EncryptionController;
import com.shadowcrypt.ui.encryption.EncryptionView;
import com.shadowcrypt.ui.keys.KeyManagementController;
import com.shadowcrypt.ui.keys.KeyManagementView;
import com.shadowcrypt.ui.navigation.NavigationItem;
import com.shadowcrypt.ui.navigation.NavigationPane;
import com.shadowcrypt.ui.theme.Theme;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.control.Label;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;

import java.util.EnumMap;
import java.util.Map;

/** Main application shell: sidebar navigation, content area, and status bar. */
public final class MainWindow {

    private final BorderPane root = new BorderPane();
    private final StackPane contentArea = new StackPane();
    private final Map<NavigationItem, Node> views = new EnumMap<>(NavigationItem.class);

    public MainWindow(AppState state, KeyService keyService, EncryptionService encryptionService,
                      DecryptionService decryptionService, AnalysisService analysisService) {
        NavigationPane navigation = new NavigationPane(this::show);

        EncryptionView encryptionView = new EncryptionView();
        new EncryptionController(encryptionView, state, keyService, encryptionService, analysisService);

        DecryptionView decryptionView = new DecryptionView();
        new DecryptionController(decryptionView, state, decryptionService, analysisService);

        AnalysisView analysisView = new AnalysisView();
        new AnalysisController(analysisView, state, analysisService);

        KeyManagementView keyManagementView = new KeyManagementView();
        new KeyManagementController(keyManagementView, state, keyService);

        views.put(NavigationItem.ENCRYPT, encryptionView);
        views.put(NavigationItem.DECRYPT, decryptionView);
        views.put(NavigationItem.ANALYZE, analysisView);
        views.put(NavigationItem.KEYS, keyManagementView);

        root.getStyleClass().add(Theme.ROOT);
        root.setLeft(navigation);
        root.setCenter(contentArea);
        root.setBottom(buildStatusBar());

        show(NavigationItem.ENCRYPT);
    }

    public Parent root() {
        return root;
    }

    private void show(NavigationItem item) {
        contentArea.getChildren().setAll(views.get(item));
    }

    private Node buildStatusBar() {
        Label algorithms = new Label("AES-256-GCM  •  RSA-3072-OAEP  •  LSB-PNG");
        algorithms.getStyleClass().add(Theme.MUTED);

        Label version = new Label("ShadowCrypt 1.0");
        version.getStyleClass().add(Theme.MUTED);

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        HBox bar = new HBox(12, algorithms, spacer, version);
        bar.setPadding(new Insets(8, 18, 10, 18));
        bar.setStyle("-fx-background-color: #0b1015;"
                + "-fx-border-color: #1c2530 transparent transparent transparent;"
                + "-fx-border-width: 1 0 0 0;");
        return bar;
    }
}
