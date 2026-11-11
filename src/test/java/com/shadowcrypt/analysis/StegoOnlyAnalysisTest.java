package com.shadowcrypt.analysis;

import com.shadowcrypt.exception.MalformedPackageException;
import com.shadowcrypt.model.AnalysisReport;
import com.shadowcrypt.model.AnalysisSource;
import com.shadowcrypt.model.EncryptionRequest;
import com.shadowcrypt.model.EncryptionResult;
import com.shadowcrypt.model.PayloadType;
import com.shadowcrypt.service.AnalysisService;
import com.shadowcrypt.service.EncryptionService;
import com.shadowcrypt.support.CryptoFixtures;
import com.shadowcrypt.support.TestCarrierFactory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPair;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StegoOnlyAnalysisTest {

    @TempDir
    Path tempDirectory;

    @Test
    void analyzesStegoImageWithoutAnEncryptionRun() throws IOException {
        KeyPair recipient = CryptoFixtures.rsaKeyPair();
        Path carrierFile = TestCarrierFactory.writePng(
                TestCarrierFactory.randomCarrier(320, 240, 41L), tempDirectory, "carrier.png");
        Path stegoFile = tempDirectory.resolve("stego.png");

        EncryptionResult encrypted = new EncryptionService().encrypt(new EncryptionRequest(
                PayloadType.TEXT, "stego-only analysis", null, carrierFile,
                recipient.getPublic(), stegoFile, false));

        AnalysisReport report = new AnalysisService().analyzeStego(stegoFile);

        assertEquals(AnalysisSource.STEGO_IMAGE, report.source());
        assertTrue(report.stegoOnly());
        assertEquals(stegoFile, report.stegoFile());
        assertEquals(encrypted.width(), report.width());
        assertEquals(encrypted.height(), report.height());
        assertEquals(encrypted.packageBytes(), report.packageBytes());
        assertEquals(encrypted.capacityBytes(), report.capacityBytes());
        assertEquals(Files.size(stegoFile), report.stegoPngBytes());

        assertEquals(-1, report.originalPngBytes());
        assertEquals(-1, report.payloadBytes());
        assertEquals(-1, report.embeddingNanos());
        assertFalse(report.hasOriginalPngSize());
        assertFalse(report.hasPayloadSize());
        assertFalse(report.hasEmbeddingTime());
        assertEquals(0.0, report.percentageFileSizeDifference());

        assertTrue(report.mse() >= 0);
        assertTrue(report.psnr() > 0);
        assertTrue(report.capacityUtilizationPercent() > 0);
        assertEquals(encrypted.width(), report.differenceHeatmap().getWidth());
        assertEquals(encrypted.height(), report.differenceHeatmap().getHeight());

        assertCarrierEstimateOnlyClearsTheEmbeddedChannels(report);
        assertFootprintMarksTheEmbeddedPixels(report);
    }

    private static void assertCarrierEstimateOnlyClearsTheEmbeddedChannels(AnalysisReport report) {
        int[] stego = rgb(report.stegoImage());
        int[] estimate = rgb(report.normalizedCarrier());
        int payloadPixels = (int) report.payloadFootprintPixels();

        for (int pixel = payloadPixels; pixel < stego.length; pixel++) {
            assertEquals(stego[pixel], estimate[pixel],
                    "pixels beyond the payload must match the stego image exactly");
        }
        for (int pixel = 0; pixel < payloadPixels - 1; pixel++) {
            for (int shift = 16; shift >= 0; shift -= 8) {
                assertEquals(0, (estimate[pixel] >>> shift) & 1,
                        "embedded channel LSBs must be cleared");
            }
        }
    }

    private static void assertFootprintMarksTheEmbeddedPixels(AnalysisReport report) {
        int payloadPixels = (int) report.payloadFootprintPixels();
        int width = report.width();

        assertEquals(0xFFFFFFFF, report.differenceHeatmap().getRGB(0, 0));
        assertEquals(0xFFFFFFFF, report.differenceHeatmap().getRGB(
                (payloadPixels - 1) % width, (payloadPixels - 1) / width));
        assertEquals(0xFF000000, report.differenceHeatmap().getRGB(
                payloadPixels % width, payloadPixels / width));
        assertTrue(report.payloadFootprintPercent() > 0);
        assertTrue(report.payloadFootprintPercent() < 100);
    }

    private static int[] rgb(BufferedImage image) {
        return image.getRGB(0, 0, image.getWidth(), image.getHeight(), null, 0, image.getWidth());
    }

    @Test
    void rejectsPngWithoutShadowCryptPackage() throws IOException {
        Path plainFile = TestCarrierFactory.writePng(
                TestCarrierFactory.randomCarrier(64, 64, 7L), tempDirectory, "plain.png");

        assertThrows(MalformedPackageException.class,
                () -> new AnalysisService().analyzeStego(plainFile));
    }
}
