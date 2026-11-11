package com.shadowcrypt.analysis;

import com.shadowcrypt.analysis.comparison.CarrierEstimator;
import com.shadowcrypt.analysis.heatmap.DifferenceHeatmapGenerator;
import com.shadowcrypt.analysis.heatmap.PayloadFootprintGenerator;
import com.shadowcrypt.analysis.metrics.MseCalculator;
import com.shadowcrypt.analysis.metrics.PsnrCalculator;
import com.shadowcrypt.config.AppConstants;
import com.shadowcrypt.model.AnalysisReport;
import com.shadowcrypt.model.AnalysisSource;

import java.awt.image.BufferedImage;
import java.nio.file.Path;
import java.util.Objects;

/** Computes the complete image-impact report from real encryption outputs or a stego image. */
public final class AnalysisCalculator {

    private AnalysisCalculator() {
    }

    public static AnalysisReport calculate(
            Path stegoFile,
            BufferedImage normalizedCarrier,
            BufferedImage stegoImage,
            long originalPngBytes,
            long stegoPngBytes,
            long payloadBytes,
            long packageBytes,
            long capacityBytes,
            long embeddingNanos) {

        Objects.requireNonNull(stegoFile, "stegoFile");
        Objects.requireNonNull(normalizedCarrier, "normalizedCarrier");
        Objects.requireNonNull(stegoImage, "stegoImage");

        double mse = MseCalculator.calculate(normalizedCarrier, stegoImage);
        double psnr = PsnrCalculator.calculate(mse);
        BufferedImage differenceHeatmap = DifferenceHeatmapGenerator.generate(
                normalizedCarrier, stegoImage, AppConstants.HEATMAP_AMPLIFICATION);

        return new AnalysisReport(
                AnalysisSource.ENCRYPTION,
                stegoFile,
                normalizedCarrier.getWidth(),
                normalizedCarrier.getHeight(),
                originalPngBytes,
                stegoPngBytes,
                payloadBytes,
                packageBytes,
                capacityBytes,
                mse,
                psnr,
                embeddingNanos,
                normalizedCarrier,
                stegoImage,
                differenceHeatmap);
    }

    /**
     * Measures a stego image on its own. The carrier is reconstructed by clearing the
     * LSBs of the channels the embedder overwrote, so the size and timing values only
     * an encryption run can know are reported as {@code -1}.
     */
    public static AnalysisReport calculateFromStego(
            Path stegoFile,
            BufferedImage stegoImage,
            long stegoPngBytes,
            long packageBytes,
            long capacityBytes) {

        Objects.requireNonNull(stegoFile, "stegoFile");
        Objects.requireNonNull(stegoImage, "stegoImage");

        BufferedImage carrierEstimate = CarrierEstimator.estimateCarrier(stegoImage, packageBytes * 8);
        double mse = MseCalculator.calculate(carrierEstimate, stegoImage);
        double psnr = PsnrCalculator.calculate(mse);
        BufferedImage differenceHeatmap = PayloadFootprintGenerator.generate(
                stegoImage.getWidth(), stegoImage.getHeight(),
                AnalysisReport.footprintPixels(
                        stegoImage.getWidth(), stegoImage.getHeight(), packageBytes));

        return new AnalysisReport(
                AnalysisSource.STEGO_IMAGE,
                stegoFile,
                stegoImage.getWidth(),
                stegoImage.getHeight(),
                -1,
                stegoPngBytes,
                -1,
                packageBytes,
                capacityBytes,
                mse,
                psnr,
                -1,
                carrierEstimate,
                stegoImage,
                differenceHeatmap);
    }
}
