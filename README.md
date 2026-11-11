# ShadowCrypt

> **Secure file encryption meets image steganography --- entirely
> offline.**

ShadowCrypt is a Java desktop application that protects text and files
using hybrid cryptography, then conceals the encrypted package inside an
ordinary PNG image through Least Significant Bit (LSB) steganography.

The application combines **AES-256-GCM**, **RSA-3072-OAEP**,
password-protected private-key storage, and PNG-based data hiding. The
complete workflow runs locally on the user's machine: ShadowCrypt does
not require a backend, cloud service, or network connection.

------------------------------------------------------------------------

## Table of Contents

-   [Overview](#overview)
-   [Problem Statement](#problem-statement)
-   [Objectives](#objectives)
-   [How ShadowCrypt Works](#how-shadowcrypt-works)
-   [Key Features](#key-features)
-   [Technology Stack](#technology-stack)
-   [Cryptographic Design](#cryptographic-design)
-   [Steganography and Image
    Analysis](#steganography-and-image-analysis)
-   [Application Modules](#application-modules)
-   [Package Format](#package-format)
-   [Requirements](#requirements)
-   [Installation and Setup](#installation-and-setup)
-   [Running the Application](#running-the-application)
-   [Quick Demo](#quick-demo)
-   [Running Tests](#running-tests)
-   [Project Structure](#project-structure)
-   [Security Considerations](#security-considerations)
-   [Current Limitations](#current-limitations)
-   [Possible Future Enhancements](#possible-future-enhancements)
-   [Contributing](#contributing)
-   [License](#license)
-   [Acknowledgements](#acknowledgements)

------------------------------------------------------------------------

## Overview

Traditional encryption protects the contents of a message, but the
presence of an encrypted file can still attract attention. Steganography
addresses a different part of the problem: it hides the existence of the
message within another file.

ShadowCrypt combines these two ideas:

1.  **Encryption** makes the original content unreadable without the
    correct cryptographic key.
2.  **Steganography** embeds the encrypted package into a PNG carrier
    image.
3.  **Recipient-based key protection** ensures that only the holder of
    the corresponding RSA private key can recover the AES key and
    decrypt the content.

ShadowCrypt supports both text messages and arbitrary file payloads.
File content is handled as binary data, so the workflow is not limited
to text documents.

## Problem Statement

Sending sensitive information as a visibly encrypted file may reveal
that a confidential exchange is taking place. At the same time, relying
on a single encryption mechanism can make key distribution and secure
sharing difficult.

ShadowCrypt aims to provide a local desktop workflow that:

-   encrypts content before hiding it;
-   uses public-key cryptography to protect the symmetric encryption
    key;
-   conceals the resulting encrypted package inside a PNG image;
-   allows the intended recipient to extract and decrypt the package;
-   provides image-quality measurements so users can inspect the
    embedding result.

## Objectives

-   Provide a simple desktop interface for encryption, decryption, key
    management, and image analysis.
-   Protect text and file data with authenticated AES-256-GCM
    encryption.
-   Use RSA-3072-OAEP to wrap the one-time AES key for the intended
    recipient.
-   Store private keys in passphrase-protected encrypted files.
-   Embed and extract encrypted packages using PNG LSB steganography.
-   Validate package metadata and reject malformed, oversized, or
    tampered input.
-   Keep all processing local to the user's computer.

## How ShadowCrypt Works

### High-level workflow

``` text
SENDER
  |
  | 1. Select text or a file
  v
Create a fresh AES-256 key and nonce
  |
  v
Encrypt payload with AES-256-GCM
  |
  | 2. Protect the AES key
  v
Wrap AES key using recipient RSA-3072 public key
  |
  v
Build the ShadowCrypt encrypted package
  |
  | 3. Hide package
  v
Embed package bits into PNG carrier using LSB
  |
  v
Share the resulting stego PNG with the recipient
  |
  v
RECIPIENT
  |
  | 4. Select stego PNG and protected private key
  v
Extract and validate the package
  |
  v
Unlock private key with passphrase
  |
  v
Unwrap AES key using RSA private key
  |
  v
Authenticate and decrypt with AES-256-GCM
  |
  v
Recover original text or file
```

### Encryption workflow

1.  The sender selects a text message or a file.
2.  The sender selects a PNG carrier image and imports the recipient's
    public key.
3.  ShadowCrypt creates a fresh 256-bit AES key and a 12-byte nonce for
    the package.
4.  The payload is encrypted using AES-256-GCM.
5.  The AES key is wrapped using the recipient's RSA-3072 public key
    with OAEP and SHA-256.
6.  The encrypted content and wrapped key are serialized into the
    ShadowCrypt package.
7.  The package is embedded into the PNG carrier using LSB substitution.
8.  The resulting stego PNG can be shared with the recipient.

### Decryption workflow

1.  The recipient selects the stego PNG.
2.  The recipient selects their `.scpriv` private-key file and enters
    its passphrase.
3.  ShadowCrypt extracts and validates the embedded package.
4.  The private key is decrypted locally.
5.  RSA-OAEP unwraps the AES key.
6.  AES-256-GCM verifies the authentication tag and decrypts the
    payload.
7.  The recovered text is displayed or the recovered file is exported.
8.  ShadowCrypt displays the recovered payload's SHA-256 hash.

------------------------------------------------------------------------

## Key Features

### 1. Text Encryption

-   Encrypts text messages before embedding them in an image.
-   Uses authenticated AES-256-GCM encryption.
-   Provides live carrier-capacity feedback.
-   Supports recipient public-key import.

### 2. File Encryption

-   Supports arbitrary file payloads, including documents, PDFs,
    archives, and images.
-   Processes file content as raw binary data.
-   Preserves the original file data during a successful round trip.
-   Includes filename and MIME metadata in the encrypted inner payload.
-   Applies a 256 MB input-file guard.

### 3. Image Steganography

-   Uses PNG images as carrier files.
-   Embeds the encrypted package through Least Significant Bit
    substitution.
-   Extracts the package from a compatible stego PNG.
-   Checks available carrier capacity before embedding.
-   Rejects packages that exceed the carrier's capacity instead of
    silently truncating data.

### 4. Key Management

-   Generates RSA-3072 public/private key pairs.
-   Exports and imports public keys.
-   Displays SHA-256 public-key fingerprints.
-   Protects private-key files with PBKDF2-HMAC-SHA256 and AES-256-GCM.
-   Uses explicit public/private key labeling and overwrite
    confirmation.

### 5. Decryption and Recovery

-   Extracts and decrypts text or file payloads.
-   Requires the recipient's private key and passphrase.
-   Reports failures such as incorrect keys, incorrect passphrases,
    malformed packages, and authentication failures.
-   Displays the recovered payload's SHA-256 hash.
-   Allows recovered files to be exported to a selected location.

### 6. Image Analysis

The Image Analysis section presents measurements from the actual
encryption run, including:

-   normalized carrier image and stego image previews;
-   amplified difference heatmap;
-   carrier and stego PNG file sizes;
-   payload and package sizes;
-   theoretical embedding capacity and capacity utilization;
-   mean squared error (MSE);
-   peak signal-to-noise ratio (PSNR);
-   embedding time.

### 7. Offline Operation

-   No backend server is required.
-   No cloud storage or external service is required.
-   Cryptographic and steganographic operations run locally.
-   Sensitive content and keys are not intentionally sent over a
    network.

------------------------------------------------------------------------

## Technology Stack

  Technology                             Role
  -------------------------------------- ---------------------------------------------
  Java 17+                               Core application language
  JavaFX 21                              Desktop user interface
  Maven 3.9+                             Dependency management and build automation
  Java Cryptography Architecture (JCA)   Cryptographic operations
  AES-256-GCM                            Authenticated symmetric encryption
  RSA-3072-OAEP                          Secure wrapping of the AES key
  PBKDF2-HMAC-SHA256                     Passphrase-based private-key protection
  SHA-256                                Payload hashing and public-key fingerprints
  PNG                                    Image carrier format
  LSB substitution                       Steganographic embedding and extraction
  JUnit 5                                Unit and integration testing

### Main dependencies

The Maven project uses JavaFX Controls, JavaFX Swing, and JUnit Jupiter
for testing. Cryptographic operations use Java's built-in cryptography
APIs.

------------------------------------------------------------------------

## Cryptographic Design

ShadowCrypt uses a hybrid encryption model: symmetric encryption
protects the payload, while asymmetric encryption protects the symmetric
key.

### AES-256-GCM

AES-GCM is used to encrypt the inner payload.

-   Key size: 256 bits
-   Nonce size: 12 bytes
-   Authentication tag: 128 bits
-   A fresh AES key and nonce are generated for each package.
-   The package header is authenticated as additional authenticated data
    (AAD).

GCM provides confidentiality and integrity/authenticity of the encrypted
package. If ciphertext or authenticated metadata is modified, decryption
should fail authentication.

### RSA-3072-OAEP

RSA is used only to wrap the AES key, not to encrypt the complete file.

-   RSA key size: 3072 bits
-   Padding: OAEP
-   OAEP digest: SHA-256
-   MGF1 digest: SHA-256

This keeps large-payload encryption efficient while allowing the AES key
to be delivered securely to the intended recipient.

### Private-key protection

Private-key files are protected using:

-   PBKDF2-HMAC-SHA256;
-   600,000 derivation iterations in the current format;
-   a per-file salt;
-   AES-256-GCM encryption;
-   a per-file nonce.

The KDF iteration count, salt, and nonce are stored with the protected
key data so that the file can be interpreted independently of future
tuning.

### SHA-256

SHA-256 is used for:

-   public-key fingerprints;
-   hashing the recovered payload.

The current package format does not store a plaintext payload hash.
Therefore, the application displays the recovered SHA-256 value and
relies on AES-GCM authentication for package integrity. Round-trip tests
verify that recovered bytes and hashes match the original data.

------------------------------------------------------------------------

## Steganography and Image Analysis

### Least Significant Bit (LSB) embedding

LSB steganography stores data in the least significant bits of image
color-channel values. Changing a least significant bit generally causes
a small numerical change in a channel value.

ShadowCrypt embeds the already-encrypted package into a PNG carrier. The
package is extracted from the corresponding stego PNG during decryption.

### Carrier image handling

-   PNG is the supported carrier format.
-   Images are normalized for embedding.
-   Transparency is composited over white.
-   The generated stego PNG is opaque RGB.
-   Capacity is checked before embedding.

### Image-quality measurements

**Mean Squared Error (MSE)** measures average squared pixel differences
between the normalized carrier and the stego image.

\[ MSE = `\frac{1}{N}`{=tex}`\sum`{=tex}\_{i=1}^{N}(I_i-K_i)^2 \]

Here, (I) represents the carrier image, (K) represents the stego image,
and (N) is the number of compared pixel-channel values.

**Peak Signal-to-Noise Ratio (PSNR)** expresses the difference on a
logarithmic scale.

\[ PSNR =
10`\log`{=tex}\_{10}`\left`{=tex}(`\frac{MAX^2}{MSE}`{=tex}`\right`{=tex})
\]

For 8-bit image channels, (MAX=255). A higher PSNR generally indicates a
smaller pixel-level difference. If MSE is zero, PSNR is mathematically
infinite.

The difference heatmap amplifies visual changes to make embedding
differences easier to inspect.

------------------------------------------------------------------------

## Application Modules

  -----------------------------------------------------------------------
  Module                              Responsibility
  ----------------------------------- -----------------------------------
  Encryption                          Accepts text or file input,
                                      recipient public key, and carrier
                                      image; coordinates encryption and
                                      embedding

  Decryption                          Extracts the package, unlocks the
                                      private key, decrypts content, and
                                      exports recovered files

  Key Management                      Generates, imports, exports,
                                      fingerprints, and stores
                                      cryptographic keys

  Image Analysis                      Displays carrier/stego comparisons,
                                      capacity, MSE, PSNR, and embedding
                                      time
  -----------------------------------------------------------------------

The interface is separated from the core processing logic. Cryptography,
package-format, image, analysis, and steganography packages do not
depend on JavaFX.

------------------------------------------------------------------------

## Package Format

ShadowCrypt uses a versioned binary package format identified by the
`SHDW` magic value.

### Outer envelope --- SHDW v1

The outer envelope uses big-endian fields and contains:

  -----------------------------------------------------------------------
  Field                               Description
  ----------------------------------- -----------------------------------
  Magic                               Four-byte `SHDW` identifier

  Version                             Package format version

  Flags                               Reserved package flags

  Total length                        Total envelope length

  Wrapped-key length                  Length of the RSA-wrapped AES key

  Nonce length                        AES-GCM nonce length

  Ciphertext length                   Encrypted payload length

  Wrapped AES key                     AES key encrypted with the
                                      recipient's RSA public key

  Nonce                               AES-GCM nonce

  Ciphertext + tag                    Authenticated encrypted inner
                                      payload
  -----------------------------------------------------------------------

The 17-byte bootstrap header is validated before bounded allocation. The
canonical header, wrapped key, and nonce form the authenticated
additional data (AAD), with a total AAD length of 413 bytes for the
current format.

### Inner encrypted payload

The inner payload is encrypted before being placed in the outer
envelope. It contains:

-   `DATA` magic identifier;
-   payload version and type;
-   filename length and UTF-8 filename;
-   MIME-type length and UTF-8 MIME type;
-   original data length;
-   original payload bytes.

### Key file formats

-   **Private key:** `.scpriv` protected file using the `SCPR` magic
    identifier, version, KDF identifier, iteration count, salt, nonce,
    and encrypted key material.
-   **Public key:** PEM-like Base64 representation of the X.509
    SubjectPublicKeyInfo encoding.

------------------------------------------------------------------------

## Requirements

Before running ShadowCrypt, install:

-   **JDK 17 or newer**
-   **Maven 3.9 or newer**

The project is configured for Java release 17 and JavaFX 21. The
repository notes that it has also been built and tested with JDK 25.

Verify your installations:

``` bash
java -version
mvn -version
```

------------------------------------------------------------------------

## Installation and Setup

### 1. Clone the repository

``` bash
git clone https://github.com/yashwanth0072/shadowcrypt.git
```

### 2. Open the project directory

``` bash
cd shadowcrypt
```

### 3. Compile the application

``` bash
mvn -q -DskipTests compile
```

### 4. Run the test suite

``` bash
mvn -q test
```

### 5. Launch the desktop application

``` bash
mvn javafx:run
```

Maven downloads the required dependencies on the first build, so an
internet connection may be needed during initial setup. The application
workflow itself is designed to run offline.

------------------------------------------------------------------------

## Running the Application

### Recommended development launch

``` bash
mvn javafx:run
```

This is the supported launch method during development and is
recommended if JavaFX native libraries cannot be loaded from the
packaged JAR on a particular platform.

### Build a packaged JAR

``` bash
mvn -q package
```

The shaded JAR is generated at:

``` text
target/shadowcrypt.jar
```

Try launching it with:

``` bash
java -jar target/shadowcrypt.jar
```

If the platform cannot load JavaFX native libraries from the shaded JAR,
use `mvn javafx:run`.

------------------------------------------------------------------------

## Quick Demo

You can explore the main workflow using the bundled demo preset.

1.  Open **Key Management**.
2.  Generate an RSA-3072 key pair.
3.  Save the private key with a strong passphrase. The default key
    directory is `%USERPROFILE%\.shadowcrypt\keys` on Windows.
4.  Export the public key.
5.  Open **Encryption** and select **Load Demo Preset** to load the
    bundled carrier and sample text.
6.  Select **Encrypt & Embed**.
7.  Open **Image Analysis** to inspect the carrier, stego image,
    difference heatmap, MSE, PSNR, capacity, sizes, and embedding time.
8.  Open **Decryption**.
9.  Select the generated stego PNG and the matching `.scpriv` file.
10. Enter the private-key passphrase, choose an output folder, and
    select **Decrypt & Extract**.
11. Inspect the recovered content and SHA-256 hash.

To test file encryption, switch the Encryption section to **File** and
select a file as the payload.

------------------------------------------------------------------------

## Running Tests

Run all unit and integration tests:

``` bash
mvn -q test
```

The repository README reports 117 unit and integration tests. The test
coverage includes:

  -----------------------------------------------------------------------
  Test area                           What is checked
  ----------------------------------- -----------------------------------
  Text round trip                     Text can be encrypted, embedded,
                                      extracted, and decrypted

  File round trip                     PDF-shaped and arbitrary binary
                                      payloads are recovered

  Empty and one-byte files            Boundary-size payload handling

  Unicode filenames                   Non-ASCII filename preservation

  Key handling                        Correct and incorrect RSA private
                                      keys

  Passphrase handling                 Correct and incorrect private-key
                                      passphrases

  Tamper detection                    Modified ciphertext is rejected

  Envelope validation                 Invalid magic, version, header, and
                                      length fields are rejected

  Capacity validation                 Exact-capacity package accepted;
                                      oversized package rejected

  Carrier capacity                    Insufficient carrier capacity is
                                      reported

  PNG round trip                      Embedded data survives PNG save and
                                      reload

  Hash verification                   Recovered bytes and SHA-256 match
                                      the original in round-trip tests

  Integration pipeline                AES encryption, RSA key wrapping,
                                      package serialization, LSB
                                      embedding, extraction, key
                                      unwrapping, and decryption work
                                      together
  -----------------------------------------------------------------------

------------------------------------------------------------------------

## Project Structure

The main source code is organized under
`src/main/java/com/shadowcrypt/`.

``` text
shadowcrypt/
├── docs/
│   └── ARCHITECTURE.md
├── src/
│   ├── main/
│   │   └── java/
│   │       └── com/
│   │           └── shadowcrypt/
│   │               ├── app/             # Application entry points
│   │               ├── config/          # Centralized configuration
│   │               ├── crypto/          # AES, RSA, PBKDF2, key storage, hashing
│   │               ├── format/          # Envelope and payload serialization/parsing
│   │               ├── image/           # PNG loading, normalization, and writing
│   │               ├── steganography/   # LSB encoder, decoder, cursor, capacity
│   │               ├── analysis/        # MSE, PSNR, heatmap, image comparison
│   │               ├── files/           # File import/export, MIME and filename handling
│   │               ├── service/         # Key, encryption, decryption, analysis workflows
│   │               ├── ui/              # JavaFX theme, navigation, views, controllers
│   │               ├── model/           # Immutable requests, results, and reports
│   │               ├── exception/       # Domain-specific exceptions
│   │               └── util/            # Byte and secret-clearing helpers
│   └── test/
│       └── java/                        # Unit and integration tests
├── .gitignore
├── pom.xml
└── README.md
```

For more detail about module boundaries and design decisions, see
[`docs/ARCHITECTURE.md`](docs/ARCHITECTURE.md).

------------------------------------------------------------------------

## Security Considerations

ShadowCrypt is designed to provide encryption and concealment as
complementary protections. Keep the following points in mind:

-   Share the stego PNG and recipient public key through an appropriate
    channel, but never share the recipient's private key or its
    passphrase.
-   Use a strong, unique passphrase to protect the private-key file.
-   Verify the recipient's public-key fingerprint through a trusted
    channel before encrypting sensitive information. A substituted
    public key could redirect encrypted content to someone else.
-   Keep secure backups of private keys. Losing the private key or
    passphrase can make encrypted content unrecoverable.
-   AES-GCM authentication detects modification of authenticated
    encrypted data; it does not prove who sent the message.
-   LSB steganography is not a guarantee of undetectability. Statistical
    analysis or image transformations may reveal or destroy hidden data.
-   Use the original generated PNG. Re-encoding, resizing, cropping, or
    converting the image may damage the embedded package.
-   The application is offline by design, but the security of the host
    computer, operating system, and stored files remains important.
-   Do not treat the project as a formally audited or certified
    cryptographic product.

------------------------------------------------------------------------

## Current Limitations

The current v1 implementation intentionally has a defined scope:

-   PNG is the supported carrier format.
-   Transparency is composited over white; the generated stego image is
    opaque RGB.
-   Input files are guarded at 256 MB.
-   A package larger than the carrier capacity is rejected; data is not
    truncated.
-   JPEG carriers, adaptive LSB, DCT-based steganography, compression,
    error correction, digital signatures, and network functionality are
    out of scope for v1.
-   SSIM is not currently included in the image-analysis metrics.

------------------------------------------------------------------------

## Possible Future Enhancements

Potential directions for future versions include:

1.  **Adaptive LSB embedding** --- select embedding locations based on
    image characteristics.
2.  **DCT-based steganography** --- explore frequency-domain embedding
    for different robustness and quality trade-offs.
3.  **Error correction** --- investigate recovery from limited bit
    errors.
4.  **Compression** --- reduce package size before embedding where
    appropriate.
5.  **Digital signatures** --- add sender authentication and
    message-origin verification.
6.  **Additional image metrics** --- include SSIM and other perceptual
    quality measures.
7.  **More carrier formats** --- evaluate additional formats while
    accounting for lossy transformations.
8.  **Usability improvements** --- add clearer key-backup guidance and
    more detailed operation reports.

These are possible extensions, not claims about functionality currently
included in v1.

------------------------------------------------------------------------

## Contributing

Contributions, bug reports, and suggestions are welcome.

1.  Fork the repository.

2.  Create a feature branch:

    ``` bash
    git checkout -b feature/your-feature-name
    ```

3.  Make your changes and add or update tests.

4.  Run the test suite:

    ``` bash
    mvn test
    ```

5.  Commit your changes:

    ``` bash
    git commit -m "Describe your changes"
    ```

6.  Push the branch and open a Pull Request.

For security-sensitive changes, include a clear explanation of the
threat model and tests covering both valid and invalid inputs. Do not
submit real private keys, passphrases, or confidential payloads.

------------------------------------------------------------------------

## License

No license information was visible in the repository details reviewed
for this README. Unless a license is added to the repository, reuse and
redistribution permissions are not explicitly granted. Add a `LICENSE`
file before presenting the project as open source under a particular
license.

------------------------------------------------------------------------

## Acknowledgements

ShadowCrypt brings together concepts from applied cryptography,
information security, image processing, and desktop application
development.

**Repository:**
[github.com/yashwanth0072/shadowcrypt](https://github.com/yashwanth0072/shadowcrypt)
