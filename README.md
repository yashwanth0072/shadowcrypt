# ShadowCrypt

ShadowCrypt is an offline Java desktop application that combines hybrid cryptography with
image steganography. It encrypts text or any file with AES-256-GCM, wraps the one-time AES
key with a recipient's RSA-3072-OAEP public key, and hides the complete package inside an
ordinary PNG image using least-significant-bit substitution. The recipient extracts the
package from the PNG and decrypts it with their passphrase-protected private key.

Everything runs locally. There is no backend, no network access, and no external service.

## Requirements

- JDK 17 or newer (built and tested with JDK 25)
- Maven 3.9 or newer (build tool only)

## Build and run

```bash
mvn -q -DskipTests compile     # compile
mvn -q test                    # 117 unit + integration tests
mvn javafx:run                 # launch the desktop application
mvn -q package                 # build target/shadowcrypt.jar (shaded)
```

`mvn javafx:run` is the supported way to launch during development. The shaded jar starts
with `java -jar target/shadowcrypt.jar`; if a platform refuses to load the JavaFX natives
from the shaded jar, use `mvn javafx:run` instead.

## Five-minute demo

1. **Key Management** → *Generate RSA-3072 key pair* → *Save private key…* (choose a
   passphrase, default location `%USERPROFILE%\.shadowcrypt\keys`) → *Export public key…*.
2. **Encryption** → *Load Demo Preset* (bundled carrier + sample text) → *Encrypt & Embed*.
3. **Image Analysis** opens with the real normalized carrier, stego image, amplified
   difference heatmap, MSE, PSNR, sizes, capacity, and embedding time from that run.
4. **Decryption** → choose the stego PNG, the `.scpriv` file, enter the passphrase, choose an
   output folder → *Decrypt & Extract*. The recovered text and SHA-256 are shown.
5. Switch the Encryption section to **File** to encrypt any file type (PDF, archives,
   images) as raw binary data.

## Features

- **Encryption** — text or arbitrary-file payloads, live capacity feedback, PNG carrier
  picker plus three bundled carriers, recipient public-key import with SHA-256 fingerprint,
  output selection, overwrite confirmation, and real staged progress.
- **Decryption** — stego PNG import with preview, private key + passphrase, staged
  extraction/decryption progress, recovered text or safely exported file, recovered SHA-256,
  and explicit errors for wrong keys, wrong passphrases, tampered data, or malformed packages.
- **Image Analysis** — measurements from the actual encryption: normalized-carrier and stego
  previews, amplified difference heatmap, PNG sizes and percentage difference, payload size,
  package size, theoretical capacity, utilization, MSE, PSNR, and embedding time.
- **Key Management** — RSA-3072 generation, public export/import, SHA-256 fingerprints,
  PBKDF2-protected private-key storage, explicit public/private labeling, and overwrite
  confirmation before replacing key files.

## Security model

- AES-256-GCM with a fresh 256-bit key and 12-byte nonce per package, 128-bit tag, and the
  canonical outer header authenticated as additional data.
- RSA-3072-OAEP with explicit SHA-256 / MGF1-SHA-256 parameters; RSA only wraps the AES key.
- Private keys are protected with PBKDF2-HMAC-SHA256 (600,000 iterations; measured at about
  0.2 s per derivation on the development machine) and AES-256-GCM. Iteration count, salt, and
  nonce are stored per file, so future tuning never breaks existing keys.
- SHA-256 is used for recovered-payload hashing and public-key fingerprints.
- Secrets are never logged; passphrase and encoded key buffers are cleared after use.
- The package format stores no plaintext hash, so the Decryption screen shows the recovered
  SHA-256 together with the AES-GCM authentication result rather than a comparison against a
  stored value. The integration tests prove original/recovered hash equality.

## Binary package (SHDW v1)

Outer envelope (big-endian): `SHDW` magic (4) · version (1) · flags (1) · total length (4) ·
wrapped-key length (2) · nonce length (1) · ciphertext length (4) · wrapped AES key (384) ·
nonce (12) · ciphertext + tag (variable). The 17-byte bootstrap header is validated before any
bounded allocation. AAD is the 413-byte `header || wrapped key || nonce`.

Inner payload (encrypted): `DATA` (4) · version (1) · type (1) · filename length (2) ·
filename UTF-8 · MIME length (2) · MIME UTF-8 · original data length (8) · data.

Private-key file: `SCPR` magic · version · KDF id · iterations · salt · nonce · ciphertext.
Public-key file: PEM-like base64 of the X.509 SubjectPublicKeyInfo.

## Project layout

```
src/main/java/com/shadowcrypt/
├── app/            entry points
├── config/         centralized constants
├── crypto/         AES, RSA, PBKDF2, key storage, hashing
├── format/         envelope + inner payload serialization/parsing
├── image/          PNG loading, RGB normalization, PNG writing
├── steganography/  LSB cursor, encoder, decoder, capacity
├── analysis/       MSE, PSNR, heatmap, comparison
├── files/          importer, exporter, filename sanitization, MIME detection
├── service/        key, encryption, decryption, analysis workflows
├── ui/             theme, navigation, components, four section views/controllers
├── model/          immutable requests/results/reports
├── exception/      domain exceptions
└── util/           byte and secret-clearing helpers
```

UI code contains no cryptography or steganography. Crypto, format, image, analysis, and
steganography packages import no JavaFX. See `docs/ARCHITECTURE.md` for details.

## Testing

`mvn test` covers the full required matrix:

| Requirement | Test |
|---|---|
| Text encryption/decryption | `EndToEndRoundTripTest.textRoundTrip` |
| PDF round trip | `EndToEndRoundTripTest.pdfShapedFileRoundTrip` |
| Arbitrary file round trip | `EndToEndRoundTripTest.arbitraryBinaryFileRoundTripIsByteIdentical` |
| Empty file / one-byte file | `EndToEndRoundTripTest.emptyFileRoundTrip`, `oneByteFileRoundTrip` |
| Unicode filenames | `EndToEndRoundTripTest.unicodeFileNameRoundTrip` |
| Correct / incorrect RSA key | `KeyWorkflowTest`, `FailureModesTest.rejectsIncorrectPrivateKey` |
| Correct / incorrect passphrase | `PrivateKeyStoreTest`, `FailureModesTest.rejectsIncorrectPassphrase` |
| Ciphertext modification | `AesGcmCipherTest.rejectsTamperedCiphertext`, `FailureModesTest.rejectsTamperedCiphertext` |
| Invalid magic / version / header / lengths | `EnvelopeValidationTest`, `FailureModesTest.rejectsInvalidMagicNumber`, `rejectsInvalidLengthField` |
| Payload at capacity / one byte over | `FailureModesTest.acceptsPackageExactlyAtCapacityAndRejectsOneByteOver` |
| Insufficient capacity | `FailureModesTest.rejectsCarrierThatIsTooSmallForThePackage` |
| PNG save/reload | `PngRoundTripTest`, `LsbRoundTripTest.survivesPngWriteAndReload` |
| SHA-256 equality | `EndToEndRoundTripTest` (recovered hash and bytes) |
| Full required integration pipeline | `EndToEndRoundTripTest` (AES → RSA wrap → package → LSB → PNG → reload → extract → unwrap → decrypt → SHA-256) |

## Decisions and limitations

- Carriers must be PNG. Transparency is composited over white and the stego PNG is opaque RGB.
- Input files are guarded at 256 MB, and the package is rejected before embedding if it
  exceeds carrier capacity (no truncation).
- The `files` subpackages are named `importer`/`exporter` because `import` is a reserved Java
  keyword and cannot be a package name.
- SSIM, adaptive LSB, DCT, JPEG carriers, compression, error correction, signatures, and any
  network functionality are intentionally out of scope for v1.

## Status

All eight phases are complete. The application compiles, the complete test suite passes, and
the JavaFX interface launches. See `docs/ARCHITECTURE.md` for module boundaries and formats.
Contributor: Ishitha
