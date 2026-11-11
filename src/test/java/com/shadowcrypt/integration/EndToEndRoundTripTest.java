package com.shadowcrypt.integration;

import com.shadowcrypt.config.AppConstants;
import com.shadowcrypt.crypto.hashing.Sha256;
import com.shadowcrypt.model.AnalysisReport;
import com.shadowcrypt.model.AnalysisSource;
import com.shadowcrypt.model.DecryptionRequest;
import com.shadowcrypt.model.DecryptionResult;
import com.shadowcrypt.model.EncryptionRequest;
import com.shadowcrypt.model.EncryptionResult;
import com.shadowcrypt.model.PayloadType;
import com.shadowcrypt.service.AnalysisService;
import com.shadowcrypt.service.DecryptionService;
import com.shadowcrypt.service.EncryptionService;
import com.shadowcrypt.service.KeyService;
import com.shadowcrypt.support.CryptoFixtures;
import com.shadowcrypt.support.TestCarrierFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPair;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EndToEndRoundTripTest {

    private static final char[] PASSPHRASE = "integration-test-passphrase".toCharArray();
    private static final String PDF_HEADER = "%PDF-1.7\n";

    @TempDir
    Path tempDirectory;

    private final KeyService keyService = new KeyService();
    private final EncryptionService encryptionService = new EncryptionService();
    private final DecryptionService decryptionService = new DecryptionService();

    private KeyPair recipient;
    private Path carrierFile;
    private Path privateKeyFile;
    private Path outputDirectory;

    @BeforeEach
    void setUp() throws IOException {
        recipient = CryptoFixtures.rsaKeyPair();
        carrierFile = TestCarrierFactory.writePng(
                TestCarrierFactory.randomCarrier(320, 240, 21L), tempDirectory, "carrier.png");
        privateKeyFile = tempDirectory.resolve("recipient.scpriv");
        keyService.savePrivateKey(privateKeyFile, recipient.getPrivate(), PASSPHRASE, false);
        outputDirectory = tempDirectory.resolve("recovered");
        Files.createDirectories(outputDirectory);
    }

    @Test
    void textRoundTrip() {
        String message = "ShadowCrypt end-to-end text ✓";

        EncryptionResult encrypted = encryptionService.encrypt(new EncryptionRequest(
                PayloadType.TEXT, message, null, carrierFile, recipient.getPublic(),
                tempDirectory.resolve("text-stego.png"), false));

        DecryptionResult decrypted = decryptionService.decrypt(new DecryptionRequest(
                encrypted.stegoFile(), privateKeyFile, PASSPHRASE, outputDirectory, false));

        assertEquals(PayloadType.TEXT, decrypted.type());
        assertEquals(message, decrypted.text());
        assertNull(decrypted.recoveredFile());
        assertEquals(Sha256.hex(message.getBytes(StandardCharsets.UTF_8)), decrypted.recoveredSha256());
        assertEquals(encrypted.packageBytes(), decrypted.packageBytes());
    }

    @Test
    void arbitraryBinaryFileRoundTripIsByteIdentical() throws IOException {
        byte[] original = new byte[12_000];
        new Random(22L).nextBytes(original);
        Path source = tempDirectory.resolve("archive.bin");
        Files.write(source, original);

        Path recovered = roundTripFile(source, "archive.bin");

        assertArrayEquals(original, Files.readAllBytes(recovered));
    }

    @Test
    void pdfShapedFileRoundTrip() throws IOException {
        byte[] original = new byte[4_000];
        new Random(23L).nextBytes(original);
        byte[] pdf = new byte[PDF_HEADER.length() + original.length];
        System.arraycopy(PDF_HEADER.getBytes(StandardCharsets.US_ASCII), 0, pdf, 0, PDF_HEADER.length());
        System.arraycopy(original, 0, pdf, PDF_HEADER.length(), original.length);
        Path source = tempDirectory.resolve("document.pdf");
        Files.write(source, pdf);

        Path recovered = roundTripFile(source, "document.pdf");

        assertArrayEquals(pdf, Files.readAllBytes(recovered));
    }

    @Test
    void emptyFileRoundTrip() throws IOException {
        Path source = tempDirectory.resolve("empty.bin");
        Files.write(source, new byte[0]);

        Path recovered = roundTripFile(source, "empty.bin");

        assertEquals(0, Files.size(recovered));
    }

    @Test
    void oneByteFileRoundTrip() throws IOException {
        Path source = tempDirectory.resolve("one.bin");
        Files.write(source, new byte[]{0x7F});

        Path recovered = roundTripFile(source, "one.bin");

        assertArrayEquals(new byte[]{0x7F}, Files.readAllBytes(recovered));
    }

    @Test
    void unicodeFileNameRoundTrip() throws IOException {
        byte[] original = "unicode file contents".getBytes(StandardCharsets.UTF_8);
        Path source = tempDirectory.resolve("résumé-日本語.txt");
        Files.write(source, original);

        Path recovered = roundTripFile(source, "résumé-日本語.txt");

        assertArrayEquals(original, Files.readAllBytes(recovered));
    }

    @Test
    void bundledDemoPresetRunsTheRealPipeline() throws IOException {
        Path demoCarrier = tempDirectory.resolve("demo-carrier.png");
        try (var input = getClass().getResourceAsStream(AppConstants.DEMO_CARRIER_RESOURCE)) {
            assertNotNull(input, "bundled demo carrier must exist");
            Files.copy(input, demoCarrier);
        }
        String sampleText;
        try (var input = getClass().getResourceAsStream(AppConstants.DEMO_SAMPLE_RESOURCE)) {
            assertNotNull(input, "bundled demo sample must exist");
            sampleText = new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }

        Path stegoFile = tempDirectory.resolve("demo-stego.png");
        EncryptionResult encrypted = encryptionService.encrypt(new EncryptionRequest(
                PayloadType.TEXT, sampleText, null, demoCarrier, recipient.getPublic(), stegoFile, false));
        DecryptionResult decrypted = decryptionService.decrypt(new DecryptionRequest(
                stegoFile, privateKeyFile, PASSPHRASE, outputDirectory, false));

        assertEquals(sampleText, decrypted.text());
    }

    @Test
    void analysisReportUsesTheRealEncryptionResult() {
        EncryptionResult encrypted = encryptionService.encrypt(new EncryptionRequest(
                PayloadType.TEXT, "analysis wiring", null, carrierFile, recipient.getPublic(),
                tempDirectory.resolve("analysis-stego.png"), false));

        AnalysisReport report = new AnalysisService().analyze(encrypted);

        assertEquals(AnalysisSource.ENCRYPTION, report.source());
        assertEquals(encrypted.stegoFile(), report.stegoFile());
        assertEquals(encrypted.width(), report.width());
        assertEquals(encrypted.height(), report.height());
        assertEquals(encrypted.packageBytes(), report.packageBytes());
        assertEquals(encrypted.payloadBytes(), report.payloadBytes());
        assertEquals(encrypted.originalPngBytes(), report.originalPngBytes());
        assertEquals(encrypted.stegoPngBytes(), report.stegoPngBytes());
        assertEquals(encrypted.embeddingNanos(), report.embeddingNanos());
        assertTrue(report.mse() >= 0);
        assertTrue(report.psnr() > 0);
        assertEquals(encrypted.width(), report.differenceHeatmap().getWidth());
        assertEquals(encrypted.height(), report.differenceHeatmap().getHeight());
    }

    private Path roundTripFile(Path source, String expectedFileName) {
        Path stegoFile = tempDirectory.resolve("file-stego.png");

        EncryptionResult encrypted = encryptionService.encrypt(new EncryptionRequest(
                PayloadType.FILE, null, source, carrierFile, recipient.getPublic(), stegoFile, false));

        DecryptionResult decrypted = decryptionService.decrypt(new DecryptionRequest(
                stegoFile, privateKeyFile, PASSPHRASE, outputDirectory, false));

        assertEquals(PayloadType.FILE, decrypted.type());
        assertEquals(expectedFileName, decrypted.fileName());
        assertEquals(Sha256.hex(source), decrypted.recoveredSha256(),
                "recovered SHA-256 must match the original file");
        assertTrue(Files.isRegularFile(decrypted.recoveredFile()));
        assertEquals(encrypted.packageBytes(), decrypted.packageBytes());
        return decrypted.recoveredFile();
    }
}
