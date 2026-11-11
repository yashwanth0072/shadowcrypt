package com.shadowcrypt.ui.analysis;

import com.shadowcrypt.ui.components.Card;
import com.shadowcrypt.ui.components.FilePickerField;
import com.shadowcrypt.ui.components.ImagePreviewPane;
import com.shadowcrypt.ui.components.MetricGrid;
import com.shadowcrypt.ui.components.SectionHeader;
import com.shadowcrypt.ui.components.StatusBanner;
import com.shadowcrypt.ui.theme.Theme;
import javafx.geometry.Insets;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.VBox;

import java.awt.image.BufferedImage;

/** Image-impact analysis of the most recent encryption run or of a standalone stego PNG. */
public final class AnalysisView extends ScrollPane {

    private final Label emptyState = new Label(
            "No analysis yet. Run an encryption or choose a stego PNG to analyze.");
    private final Label contextLabel = new Label();
    private final Card contentCard = new Card(null);

    private final FilePickerField stegoPicker = new FilePickerField("Choose a stego PNG to analyze");
    private final StatusBanner statusBanner = new StatusBanner();

    private final ImagePreviewPane carrierPreview = new ImagePreviewPane("Carrier");
    private final ImagePreviewPane stegoPreview = new ImagePreviewPane("Stego image");
    private final ImagePreviewPane heatmapPreview = new ImagePreviewPane("Difference heatmap");
    private final MetricGrid metricGrid = new MetricGrid();

    public AnalysisView() {
        emptyState.getStyleClass().add(Theme.MUTED);
        emptyState.setWrapText(true);
        emptyState.setPadding(new Insets(4, 0, 0, 0));

        contextLabel.getStyleClass().add(Theme.MUTED);
        contextLabel.setWrapText(true);

        Label stegoHint = new Label(
                "Analysis normally comes from an encryption run. Any ShadowCrypt stego PNG can "
                        + "also be analyzed on its own — the carrier is reconstructed by clearing "
                        + "the LSB plane.");
        stegoHint.getStyleClass().add(Theme.MUTED);
        stegoHint.setWrapText(true);

        Card stegoCard = new Card("Analyze a stego image");
        stegoCard.add(stegoPicker, stegoHint);

        FlowPane previews = new FlowPane(18, 18,
                carrierPreview, stegoPreview, heatmapPreview);

        Card metricsCard = new Card("Metrics");
        metricsCard.add(metricGrid);

        contentCard.add(contextLabel, previews, metricsCard);

        VBox content = new VBox(16);
        content.setPadding(new Insets(24, 24, 32, 24));
        content.getChildren().addAll(
                new SectionHeader("Image Analysis",
                        "Compare the normalized carrier with the produced stego image using "
                                + "measurements from the actual encryption operation."),
                stegoCard,
                statusBanner,
                emptyState,
                contentCard);

        setContent(content);
        setFitToWidth(true);
        setHbarPolicy(ScrollBarPolicy.NEVER);
        setHasAnalysis(false);
    }

    public void setHasAnalysis(boolean hasAnalysis) {
        emptyState.setVisible(!hasAnalysis);
        emptyState.setManaged(!hasAnalysis);
        contentCard.setVisible(hasAnalysis);
        contentCard.setManaged(hasAnalysis);
    }

    public void setContext(String text) {
        contextLabel.setText(text);
    }

    public void setPreviews(BufferedImage carrier, BufferedImage stego, BufferedImage heatmap,
                            String carrierCaption, String stegoCaption, String heatmapCaption) {
        carrierPreview.setImage(carrier, carrierCaption);
        stegoPreview.setImage(stego, stegoCaption);
        heatmapPreview.setImage(heatmap, heatmapCaption);
    }

    public FilePickerField stegoPicker() {
        return stegoPicker;
    }

    public StatusBanner statusBanner() {
        return statusBanner;
    }

    public MetricGrid metricGrid() {
        return metricGrid;
    }
}
