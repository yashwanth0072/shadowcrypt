package com.shadowcrypt.service;

import com.shadowcrypt.analysis.AnalysisCalculator;
import com.shadowcrypt.config.PackageConstants;
import com.shadowcrypt.exception.FileSafetyException;
import com.shadowcrypt.exception.MalformedPackageException;
import com.shadowcrypt.format.envelope.EnvelopeParser;
import com.shadowcrypt.image.models.CarrierImage;
import com.shadowcrypt.image.normalization.CarrierNormalizer;
import com.shadowcrypt.model.AnalysisReport;
import com.shadowcrypt.model.EncryptionResult;
import com.shadowcrypt.steganography.capacity.CapacityCalculator;
import com.shadowcrypt.steganography.extraction.LsbDecoder;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;

/** Turns a real encryption result or a standalone stego image into the analysis report. */
public final class AnalysisService {

    public AnalysisReport analyze(EncryptionResult encryptionResult) {
        Objects.requireNonNull(encryptionResult, "encryptionResult");
        return AnalysisCalculator.calculate(
                encryptionResult.stegoFile(),
                encryptionResult.normalizedCarrier(),
                encryptionResult.stegoImage(),
                encryptionResult.originalPngBytes(),
                encryptionResult.stegoPngBytes(),
                encryptionResult.payloadBytes(),
                encryptionResult.packageBytes(),
                encryptionResult.capacityBytes(),
                encryptionResult.embeddingNanos());
    }

    /**
     * Analyzes any ShadowCrypt stego PNG without an encryption run and without the
     * private key. Only the readable package header is needed for the sizes.
     */
    public AnalysisReport analyzeStego(Path stegoFile) {
        Objects.requireNonNull(stegoFile, "stegoFile");

        CarrierImage stego = CarrierNormalizer.load(stegoFile);
        long capacity = CapacityCalculator.capacityBytes(stego.width(), stego.height());
        if (capacity < PackageConstants.BOOTSTRAP_HEADER_BYTES) {
            throw new MalformedPackageException("The image is too small to contain a ShadowCrypt package");
        }

        byte[] bootstrapBytes = LsbDecoder.extract(stego.image(), PackageConstants.BOOTSTRAP_HEADER_BYTES);
        EnvelopeParser.Bootstrap bootstrap = EnvelopeParser.parseBootstrap(bootstrapBytes);
        if (bootstrap.totalPackageLength() > capacity) {
            throw new MalformedPackageException("The declared package length exceeds the carrier capacity");
        }

        return AnalysisCalculator.calculateFromStego(
                stegoFile,
                stego.image(),
                fileSize(stegoFile),
                bootstrap.totalPackageLength(),
                capacity);
    }

    private static long fileSize(Path file) {
        try {
            return Files.size(file);
        } catch (IOException e) {
            throw new FileSafetyException("Unable to read file size: " + file, e);
        }
    }
}
