package com.shadowcrypt.analysis;

import com.shadowcrypt.analysis.metrics.MseCalculator;
import com.shadowcrypt.analysis.metrics.PsnrCalculator;
import com.shadowcrypt.model.AnalysisReport;
import com.shadowcrypt.model.AnalysisSource;
import org.junit.jupiter.api.Test;

import java.awt.image.BufferedImage;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MetricsTest {

    @Test
    void identicalImagesHaveZeroMseAndInfinitePsnr() {
        BufferedImage first = solid(8, 8, 0x336699);
        BufferedImage second = solid(8, 8, 0x336699);

        double mse = MseCalculator.calculate(first, second);

        assertEquals(0.0, mse);
        assertEquals(Double.POSITIVE_INFINITY, PsnrCalculator.calculate(mse));
    }

    @Test
    void mseMatchesHandComputedValue() {
        BufferedImage original = imageOf(2, 1, 0x000000, 0x000000);
        BufferedImage stego = imageOf(2, 1, 0x010101, 0x020202);

        double mse = MseCalculator.calculate(original, stego);

        // 3 channels * (1^2) + 3 channels * (2^2) = 15 over 6 samples.
        assertEquals(2.5, mse, 1e-12);
        assertEquals(10.0 * Math.log10((255.0 * 255.0) / 2.5),
                PsnrCalculator.calculate(mse), 1e-12);
    }

    @Test
    void rejectsNegativeMse() {
        assertThrows(IllegalArgumentException.class, () -> PsnrCalculator.calculate(-1.0));
    }

    @Test
    void rejectsMismatchedDimensions() {
        BufferedImage first = solid(4, 4, 0x000000);
        BufferedImage second = solid(4, 5, 0x000000);

        assertThrows(IllegalArgumentException.class, () -> MseCalculator.calculate(first, second));
    }

    @Test
    void reportDerivesSizeAndCapacityMetrics() {
        BufferedImage carrier = solid(10, 10, 0x101010);
        BufferedImage stego = solid(10, 10, 0x111111);

        AnalysisReport report = AnalysisCalculator.calculate(
                Path.of("stego.png"), carrier, stego, 1_000, 1_300, 50, 500, 4_000, 12_000_000L);

        assertEquals(AnalysisSource.ENCRYPTION, report.source());
        assertEquals(Path.of("stego.png"), report.stegoFile());
        assertEquals(10, report.width());
        assertEquals(10, report.height());
        assertEquals(300, report.absoluteFileSizeDifference());
        assertEquals(30.0, report.percentageFileSizeDifference(), 1e-12);
        assertEquals(12.5, report.capacityUtilizationPercent(), 1e-12);
        assertEquals(1.0, report.mse(), 1e-12);
        assertEquals(12_000_000L, report.embeddingNanos());
        assertNotNull(report.differenceHeatmap());
        assertTrue(report.psnr() > 0);
    }

    private static BufferedImage solid(int width, int height, int argb) {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        int[] pixels = new int[width * height];
        java.util.Arrays.fill(pixels, argb);
        image.setRGB(0, 0, width, height, pixels, 0, width);
        return image;
    }

    private static BufferedImage imageOf(int width, int height, int... argbPixels) {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        image.setRGB(0, 0, width, height, argbPixels, 0, width);
        return image;
    }
}
