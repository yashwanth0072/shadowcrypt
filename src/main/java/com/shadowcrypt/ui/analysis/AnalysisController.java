package com.shadowcrypt.ui.analysis;

import com.shadowcrypt.model.AnalysisReport;
import com.shadowcrypt.service.AnalysisService;
import com.shadowcrypt.ui.components.ErrorText;
import com.shadowcrypt.ui.components.Formatters;
import com.shadowcrypt.ui.main.AppState;
import javafx.stage.FileChooser;
import javafx.stage.Window;

import java.io.File;
import java.nio.file.Path;
import java.util.Locale;

/** Renders the analysis of the most recent encryption or of a selected stego image. */
public final class AnalysisController {

    private static final String ENCRYPTION_CARRIER_CAPTION = "Normalized carrier";
    private static final String ENCRYPTION_HEATMAP_CAPTION = "Amplified absolute difference (×16)";
    private static final String STEGO_CARRIER_CAPTION = "Carrier estimate (embedded LSBs cleared)";
    private static final String STEGO_HEATMAP_CAPTION = "Payload footprint (white = embedded pixels)";

    private final AnalysisView view;
    private final AppState state;
    private final AnalysisService analysisService;

    public AnalysisController(AnalysisView view, AppState state, AnalysisService analysisService) {
        this.view = view;
        this.state = state;
        this.analysisService = analysisService;

        view.stegoPicker().setOnBrowse(this::chooseStegoImage);
        state.lastAnalysisProperty().addListener((observable, oldValue, newValue) -> {
            if (newValue == null) {
                view.setHasAnalysis(false);
            } else {
                render(newValue);
            }
        });
        if (state.lastAnalysisProperty().get() != null) {
            render(state.lastAnalysisProperty().get());
        }
    }

    private void chooseStegoImage() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Choose the stego PNG image to analyze");
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("PNG images", "*.png"));
        File file = chooser.showOpenDialog(window());
        if (file == null) {
            return;
        }

        Path path = file.toPath();
        view.stegoPicker().setPath(path);
        view.stegoPicker().setBrowseDisabled(true);
        view.statusBanner().hide();
        StegoAnalysisRunner.run(analysisService, path, state,
                () -> view.stegoPicker().setBrowseDisabled(false),
                error -> view.statusBanner().error(ErrorText.describe(error)));
    }

    private void render(AnalysisReport report) {
        boolean stegoOnly = report.stegoOnly();
        String dimensions = report.width() + "×" + report.height();

        view.setHasAnalysis(true);
        view.setContext(stegoOnly
                ? "Analyzed from the stego image alone: " + report.stegoFile()
                + "\nThe carrier is reconstructed by clearing the LSBs the embedder overwrote, "
                + "so MSE and PSNR describe that estimate rather than the original cover image."
                : "Stego image: " + report.stegoFile());
        view.setPreviews(report.normalizedCarrier(), report.stegoImage(), report.differenceHeatmap(),
                (stegoOnly ? STEGO_CARRIER_CAPTION : ENCRYPTION_CARRIER_CAPTION) + " • " + dimensions,
                "Stego image • " + dimensions,
                (stegoOnly ? STEGO_HEATMAP_CAPTION : ENCRYPTION_HEATMAP_CAPTION) + " • " + dimensions);

        view.metricGrid().clearMetrics();
        view.metricGrid().addMetric("Dimensions", dimensions);
        if (report.hasOriginalPngSize()) {
            view.metricGrid().addMetric("Original PNG size",
                    Formatters.formatBytes(report.originalPngBytes()));
        }
        view.metricGrid().addMetric("Stego PNG size", Formatters.formatBytes(report.stegoPngBytes()));
        if (report.hasOriginalPngSize()) {
            view.metricGrid().addMetric("Absolute size difference",
                    Formatters.formatBytes(report.absoluteFileSizeDifference()));
            view.metricGrid().addMetric("Percentage size difference",
                    Formatters.formatPercent(report.percentageFileSizeDifference()));
        }
        if (report.hasPayloadSize()) {
            view.metricGrid().addMetric("Original payload size",
                    Formatters.formatBytes(report.payloadBytes()));
        }
        view.metricGrid().addMetric("Complete package size", Formatters.formatBytes(report.packageBytes()));
        view.metricGrid().addMetric("Theoretical capacity", Formatters.formatBytes(report.capacityBytes()));
        view.metricGrid().addMetric("Capacity utilization",
                Formatters.formatPercent(report.capacityUtilizationPercent()));
        if (stegoOnly) {
            view.metricGrid().addMetric("Payload footprint",
                    report.payloadFootprintPixels() + " pixels ("
                            + Formatters.formatPercent(report.payloadFootprintPercent())
                            + " of the image)");
            view.metricGrid().addMetric("Measured against", "Reconstructed carrier estimate");
        }
        view.metricGrid().addMetric(stegoOnly ? "MSE (vs carrier estimate)" : "MSE",
                String.format(Locale.ROOT, "%.6f", report.mse()));
        String psnrText;
        if (Double.isInfinite(report.psnr())) {
            psnrText = stegoOnly ? "∞ (estimate matches)" : "∞ (identical images)";
        } else {
            psnrText = String.format(Locale.ROOT, "%.2f dB", report.psnr());
        }
        view.metricGrid().addMetric(stegoOnly ? "PSNR (vs carrier estimate)" : "PSNR", psnrText);
        if (report.hasEmbeddingTime()) {
            view.metricGrid().addMetric("Embedding time",
                    Formatters.formatDurationMillis(report.embeddingNanos()));
        }
    }

    private Window window() {
        return view.getScene() == null ? null : view.getScene().getWindow();
    }
}
