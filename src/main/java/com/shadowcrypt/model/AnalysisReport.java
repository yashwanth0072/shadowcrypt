package com.shadowcrypt.model;

import com.shadowcrypt.config.AppConstants;

import java.awt.image.BufferedImage;
import java.nio.file.Path;
import java.util.Objects;

/**
 * Image-impact measurements produced from one encryption run or from a stego image
 * analyzed on its own. Values that are only known to an encryption run
 * ({@code originalPngBytes}, {@code payloadBytes}, {@code embeddingNanos}) are
 * reported as {@code -1} for a stego-only report.
 */
public record AnalysisReport(
        AnalysisSource source,
        Path stegoFile,
        int width,
        int height,
        long originalPngBytes,
        long stegoPngBytes,
        long payloadBytes,
        long packageBytes,
        long capacityBytes,
        double mse,
        double psnr,
        long embeddingNanos,
        BufferedImage normalizedCarrier,
        BufferedImage stegoImage,
        BufferedImage differenceHeatmap) {

    public AnalysisReport {
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(stegoFile, "stegoFile");
        Objects.requireNonNull(normalizedCarrier, "normalizedCarrier");
        Objects.requireNonNull(stegoImage, "stegoImage");
        Objects.requireNonNull(differenceHeatmap, "differenceHeatmap");
    }

    public boolean stegoOnly() {
        return source == AnalysisSource.STEGO_IMAGE;
    }

    public boolean hasOriginalPngSize() {
        return originalPngBytes >= 0;
    }

    public boolean hasPayloadSize() {
        return payloadBytes >= 0;
    }

    public boolean hasEmbeddingTime() {
        return embeddingNanos >= 0;
    }

    public long absoluteFileSizeDifference() {
        return hasOriginalPngSize() ? stegoPngBytes - originalPngBytes : 0;
    }

    public double percentageFileSizeDifference() {
        return originalPngBytes <= 0 ? 0.0 : (stegoPngBytes - originalPngBytes) * 100.0 / originalPngBytes;
    }

    /** Number of leading pixels that carry at least one bit of the embedded package. */
    public long payloadFootprintPixels() {
        return footprintPixels(width, height, packageBytes);
    }

    public double payloadFootprintPercent() {
        long total = (long) width * height;
        return total == 0 ? 0.0 : payloadFootprintPixels() * 100.0 / total;
    }

    /** Package bytes to overwritten pixels, in the embedder's row-major RGB order. */
    public static long footprintPixels(int width, int height, long packageBytes) {
        long channels = Math.multiplyExact(packageBytes, 8);
        long pixels = (channels + AppConstants.CHANNELS_PER_PIXEL - 1) / AppConstants.CHANNELS_PER_PIXEL;
        return Math.min((long) width * height, pixels);
    }

    public double capacityUtilizationPercent() {
        return capacityBytes == 0 ? 0.0 : packageBytes * 100.0 / capacityBytes;
    }
}
