# ShadowCrypt

**Hide secrets twice: encrypt them, then make them invisible.**

[![Java](https://img.shields.io/badge/Java-17%2B-ED8B00?style=for-the-badge&logo=openjdk&logoColor=white)](https://openjdk.org/)
[![JavaFX](https://img.shields.io/badge/JavaFX-21.0.13-2C2255?style=for-the-badge)](https://openjfx.io/)
[![Maven](https://img.shields.io/badge/Maven-3.9%2B-C71A36?style=for-the-badge&logo=apachemaven&logoColor=white)](https://maven.apache.org/)
[![JUnit 5](https://img.shields.io/badge/JUnit-5.10.2-25A162?style=for-the-badge&logo=junit5&logoColor=white)](https://junit.org/junit5/)
[![Tests](https://img.shields.io/badge/tests-124-2EA043?style=for-the-badge)](#testing)
[![Offline](https://img.shields.io/badge/network-not%20required-2F81F7?style=for-the-badge)](#offline-by-design)

ShadowCrypt is a JavaFX desktop application that encrypts text or files, wraps the one-time
encryption key for a recipient, and embeds the complete encrypted package into the least
significant bits of an ordinary PNG image. The image still looks normal, but only the holder
of the matching private key and passphrase can recover what is inside.

Everything runs locally. There is no server, account, database, or network call involved in
encryption, decryption, key handling, or image analysis.

---

## Table of Contents

- [What It Is For](#what-it-is-for)
- [How It Works](#how-it-works)
- [Features](#features)
- [Cryptographic Design](#cryptographic-design)
- [Package Format](#package-format)
- [Steganography](#steganography)
- [Image Analysis](#image-analysis)
- [Application Tour](#application-tour)
- [Requirements](#requirements)
- [Getting Started](#getting-started)
- [Quick Demo](#quick-demo)
- [Testing](#testing)
- [Project Structure](#project-structure)
- [Security Notes](#security-notes)
- [Scope and Limitations](#scope-and-limitations)

---

## What It Is For

Encryption hides *what* a message says. Steganography hides the fact that a message exists.
ShadowCrypt applies both:

| Layer | What it protects |
| :--- | :--- |
| **AES-256-GCM** | The actual text or file content — confidentiality plus tamper detection |
| **RSA-3072-OAEP** | The one-time AES key, addressed to a specific recipient's public key |
| **PNG LSB steganography** | The encrypted package itself — concealed inside image pixel data |

The result is a single PNG that can be shared normally. Without the recipient's protected
private key and its passphrase, the hidden content cannot be extracted or read.

### Offline by design

All cryptography uses the Java Cryptography Architecture. All image handling uses
`javax.imageio` and JavaFX. Nothing is uploaded, downloaded, or transmitted at runtime.

---

## How It Works

```text
 SENDER                                                      RECIPIENT
 ──────                                                      ─────────
 text or file
     │
     ▼
 serialize inner payload (DATA)
     │
     ▼
 encrypt with fresh AES-256 key ── nonce ──┐
     │                                      │
     ▼                                      ▼
 wrap AES key with recipient's RSA-3072 public key
     │
     ▼
 assemble SHDW package (header + wrapped key + nonce + ciphertext)
     │
     ▼
 embed package bits into PNG RGB LSBs
     │
     ▼
 share stego PNG  ────────────────────────────────────────►  extract package
                                                                  │
                                                                  ▼
                                                        validate header + capacity
                                                                  │
                                                                  ▼
                                                        unlock .scpriv with passphrase
                                                                  │
                                                                  ▼
                                                        unwrap AES key with RSA private key
                                                                  │
                                                                  ▼
                                                        AES-256-GCM authenticate + decrypt
                                                                  │
                                                                  ▼
                                                        recover text or file
```

### Encryption pipeline

1. The payload (UTF-8 text or raw file bytes) is serialized into an inner `DATA` structure
   carrying its type, filename, MIME type, original length, and bytes.
2. A fresh 256-bit AES key and a 12-byte GCM nonce are generated.
3. The AES key is wrapped with the recipient's RSA-3072 public key using OAEP/SHA-256,
   producing a fixed 384-byte wrapped key.
4. The inner payload is encrypted with AES-256-GCM using a 128-bit authentication tag. The
   canonical header, wrapped key, and nonce (413 bytes) are authenticated as additional data.
5. The `SHDW` envelope is assembled and checked against the carrier capacity.
6. The package bits are written into the RGB least significant bits of the carrier PNG.
7. The stego PNG is saved to the chosen output path.

### Decryption pipeline

1. The stego PNG is loaded, normalized to opaque RGB, and its capacity is computed.
2. The first 17 header bytes are extracted to learn the package length, then the declared
   length is validated against the image capacity.
3. The full package is extracted and parsed; malformed fields are rejected before any buffer
   is allocated.
4. The `.scpriv` private key is unlocked with PBKDF2-HMAC-SHA256 and AES-256-GCM.
5. RSA-OAEP unwraps the AES session key.
6. AES-256-GCM verifies the authentication tag and decrypts the inner payload.
7. Text is displayed, or the recovered file is sanitized and written to the chosen folder.
8. SHA-256 of the recovered payload is shown for comparison with the original.

---

## Features

| Feature | Description |
| :--- | :--- |
| **Text encryption** | Encrypt any UTF-8 message, with live feedback on whether it fits the carrier |
| **File encryption** | Encrypt arbitrary files as raw binary, preserving filename and MIME metadata |
| **Recipient-based keys** | RSA-3072 key pairs; only the private-key holder can unwrap the AES key |
| **Protected key storage** | `.scpriv` files secured with PBKDF2-HMAC-SHA256 (600,000 iterations) and AES-256-GCM |
| **Public key sharing** | Export/import `.scpub` files with a SHA-256 fingerprint for verification |
| **PNG steganography** | LSB embedding in RGB channels; alpha is never modified |
| **Capacity checking** | Oversized payloads are rejected, never silently truncated |
| **Image analysis** | Compare carrier and stego images with MSE, PSNR, heatmaps, sizes, and capacity |
| **Stego-only analysis** | Analyze any ShadowCrypt PNG without the private key or an original carrier |
| **Bundled carriers** | Three built-in PNG carriers plus a one-click demo preset |
| **Fully offline** | No backend, cloud service, or network dependency at runtime |

---

## Cryptographic Design

ShadowCrypt uses hybrid encryption: symmetric encryption protects the payload, while public-key
encryption protects the symmetric key.

| Component | Parameters | Purpose |
| :--- | :--- | :--- |
| **AES-256-GCM** | 256-bit key, 12-byte nonce, 128-bit tag | Payload confidentiality and integrity |
| **RSA-3072-OAEP** | OAEP with SHA-256, MGF1-SHA256 | Wrapping the per-package AES key |
| **PBKDF2-HMAC-SHA256** | 600,000 iterations, 16-byte salt | Deriving the private-key encryption key |
| **AES-256-GCM** | Key from PBKDF2, 12-byte nonce | Encrypting `.scpriv` private-key material |
| **SHA-256** | 32-byte digest | Public-key fingerprints and recovered-payload hashes |

### Key details

- A fresh AES key and nonce are generated for **every package**. Keys are never reused.
- The AES key is wrapped, not the whole file; RSA never touches the bulk payload.
- The 413-byte authenticated metadata block (17-byte bootstrap header + 384-byte wrapped key +
  12-byte nonce) binds the package metadata to the ciphertext.
- Private keys are stored as PKCS#8 bytes encrypted inside a versioned `.scpriv` container.
- Public-key fingerprints are uppercase, colon-separated SHA-256 hashes of the X.509 DER
  encoding — the same value on export and import.
- Secret byte and character arrays are cleared after use where the code handles raw key
  material, passphrases, and derived keys.

---

## Package Format

The outer envelope is identified by the ASCII magic value `SHDW` and uses big-endian fields.

### Outer envelope — `SHDW` v1

| Field | Size | Notes |
| :--- | ---: | :--- |
| Magic | 4 bytes | `SHDW` (`53 48 44 57`) |
| Version | 1 byte | `1` |
| Flags | 1 byte | Reserved; only `0x00` is currently accepted |
| Total length | 4 bytes | Includes the header itself |
| Wrapped-key length | 2 bytes | Must be `384` |
| Nonce length | 1 byte | Must be `12` |
| Ciphertext length | 4 bytes | Includes the 16-byte GCM tag |
| Wrapped AES key | 384 bytes | RSA-OAEP output |
| Nonce | 12 bytes | AES-GCM nonce |
| Ciphertext + tag | variable | Authenticated encrypted inner payload |

The fixed bootstrap header is **17 bytes**. Parsing validates magic, version, flags, field
lengths, arithmetic overflow, and declared total length before allocating buffers.

### Inner payload — `DATA` v1

| Field | Size |
| :--- | ---: |
| Magic | 4 bytes (`DATA`) |
| Version | 1 byte |
| Payload type | 1 byte (`0x01` text, `0x02` file) |
| Filename length | 2 bytes (UTF-8 byte count) |
| Filename | variable |
| MIME-type length | 2 bytes (UTF-8 byte count) |
| MIME type | variable |
| Data length | 8 bytes |
| Payload data | variable |

Text payloads use an empty filename and `text/plain; charset=utf-8`. File payloads carry the
original name and a detected MIME type, falling back to `application/octet-stream`.

### Key files

| File | Magic / format | Contents |
| :--- | :--- | :--- |
| `.scpriv` | `SCPR` v1 container | Version, KDF id, iteration count, salt, nonce, encrypted PKCS#8 |
| `.scpub` | PEM-like text | Base64 X.509 `SubjectPublicKeyInfo` between ShadowCrypt markers |

---

## Steganography

ShadowCrypt embeds one package bit per RGB channel LSB.

| Property | Implementation |
| :--- | :--- |
| **Carrier format** | PNG only |
| **Channels used** | Red, green, blue (alpha untouched) |
| **Traversal** | Row-major, left → right, top → bottom |
| **Channel order** | R → G → B within each pixel |
| **Bit order** | Most significant bit first |
| **Capacity** | `floor(width × height × 3 / 8)` bytes |
| **Output image** | New opaque RGB image; the input carrier is not mutated |
| **Transparency** | Composited over white during normalization |

The package is rejected if it exceeds capacity. The decoder is given an explicit byte count,
so buffers are sized from validated metadata rather than untrusted lengths.

---

## Image Analysis

Every encryption run produces real measurements, not estimates:

| Metric | What it shows |
| :--- | :--- |
| **Carrier and stego previews** | Normalized input image and resulting output image |
| **Difference heatmap** | Absolute per-channel differences amplified ×16 |
| **PNG sizes** | Original carrier size, stego size, and the difference |
| **Payload and package sizes** | Plaintext payload size and full embedded package size |
| **Capacity utilization** | Package size as a percentage of carrier capacity |
| **MSE** | Mean squared error across RGB channel values |
| **PSNR** | Peak signal-to-noise ratio in dB (`∞` when images are identical) |
| **Embedding time** | Time taken to embed the package and write the PNG |

### Analyzing an arbitrary stego PNG

Selecting a ShadowCrypt PNG in **Image Analysis** — or selecting one on the **Decryption**
page — reconstructs a carrier estimate by clearing the LSBs the embedder overwrote. The report
then shows the payload footprint, package size read from the header, capacity, and MSE/PSNR
measured against that estimate. Values that only an encryption run can know (original carrier
file size, plaintext payload size, embedding time) are omitted, and the comparison is clearly
labelled as being against the estimate.

---

## Application Tour

| View | What you do there |
| :--- | :--- |
| **Encryption** | Choose text or a file, pick a PNG carrier, import the recipient's public key, and run **Encrypt & Embed** |
| **Decryption** | Select the stego PNG and `.scpriv` file, enter the passphrase, and run **Decrypt & Extract** |
| **Image Analysis** | Inspect carrier/stego previews, the heatmap, MSE, PSNR, capacity, and timing |
| **Key Management** | Generate RSA-3072 key pairs, export/import public keys, and save/load protected private keys |

The Encryption view includes three bundled carriers — **Bundled: Blue**, **Bundled: Texture**,
and **Bundled: Warm** — plus a **Load Demo Preset** button. The status bar always shows
`AES-256-GCM • RSA-3072-OAEP • LSB-PNG`.

---

## Requirements

| Requirement | Version | Notes |
| :--- | :--- | :--- |
| **JDK** | 17 or newer | The project compiles with `--release 17` |
| **Maven** | 3.9 or newer (recommended) | Build and dependency management |
| **JavaFX** | 21.0.13 | Pulled automatically by Maven |
| **OS** | Windows, Linux, or macOS | Any platform with a JavaFX runtime |

The first Maven build needs internet access to download dependencies. The application itself
runs entirely offline afterward.

Verify your tools:

```bash
java -version
mvn -version
```

---

## Getting Started

### 1. Clone the repository

```bash
git clone https://github.com/yashwanth0072/shadowcrypt.git
cd shadowcrypt
```

### 2. Compile

```bash
mvn -q -DskipTests compile
```

### 3. Run the application

```bash
mvn javafx:run
```

This is the recommended way to launch the app during development because Maven resolves the
correct JavaFX native libraries for your platform.

### 4. Build a standalone JAR

```bash
mvn -q package
```

The shaded JAR is written to `target/shadowcrypt.jar`:

```bash
java -jar target/shadowcrypt.jar
```

> If JavaFX native libraries cannot be loaded from the shaded JAR on your platform, use
> `mvn javafx:run` instead.

### Command reference

| Task | Command |
| :--- | :--- |
| Compile only | `mvn -q -DskipTests compile` |
| Run tests | `mvn -q test` |
| Run the app | `mvn javafx:run` |
| Package the JAR | `mvn -q package` |
| Launch the JAR | `java -jar target/shadowcrypt.jar` |

---

## Quick Demo

The bundled demo encrypts sample text into a bundled carrier using the real pipeline.

1. Open **Key Management** and select **Generate RSA-3072 key pair**.
2. Select **Save private key…**, choose a passphrase, and save the `.scpriv` file.
   The default location is `~/.shadowcrypt/keys` (`%USERPROFILE%\.shadowcrypt\keys` on Windows).
3. Select **Export public key…** to save the matching `.scpub` file.
4. Open **Encryption** and select **Load Demo Preset**. This loads `carrier-blue.png` and the
   bundled `welcome.txt` sample, and sets the output to a temporary `shadowcrypt-demo-stego.png`.
5. Select **Encrypt & Embed**.
6. Open **Image Analysis** to inspect the carrier, stego image, heatmap, MSE, PSNR, capacity,
   and embedding time.
7. Open **Decryption**, choose the generated stego PNG and your `.scpriv` file, enter the
   passphrase, and select **Decrypt & Extract**.
8. Compare the recovered text with the original sample and check the displayed SHA-256 hash.

To try file encryption, switch the Encryption payload toggle from **Text** to **File** and
choose any file up to 256 MiB.

---

## Testing

```bash
mvn -q test
```

The suite contains **124 tests across 23 test classes**, covering:

| Area | What is verified |
| :--- | :--- |
| **End-to-end round trips** | Text, random binary, PDF-shaped, empty, and one-byte payloads |
| **Unicode** | Non-ASCII filenames survive encryption and recovery |
| **Failure modes** | Wrong key, wrong passphrase, tampered ciphertext, invalid headers |
| **Envelope validation** | Bad magic, version, flags, lengths, truncation, trailing bytes |
| **Payload validation** | Bad inner magic/version/type, truncated data, path separators |
| **Key handling** | RSA-3072 generation, save/load, passphrase protection, fingerprints |
| **Steganography** | LSB round trips, capacity limits, PNG save/reload survival |
| **Image analysis** | MSE/PSNR values, heatmaps, carrier estimation, stego-only reports |
| **File safety** | Filename sanitization and safe export directories |

---

## Project Structure

```text
shadowcrypt/
├── docs/
│   └── ARCHITECTURE.md
├── src/
│   ├── main/
│   │   ├── java/com/shadowcrypt/
│   │   │   ├── app/              # Launcher and JavaFX application entry point
│   │   │   ├── config/           # Crypto, package, and application constants
│   │   │   ├── crypto/           # AES-GCM, RSA-OAEP, PBKDF2, key storage, SHA-256
│   │   │   ├── format/           # SHDW envelope and DATA payload serialization
│   │   │   ├── image/            # PNG loading, normalization, and writing
│   │   │   ├── steganography/    # LSB encoder, decoder, cursor, capacity
│   │   │   ├── analysis/         # MSE, PSNR, heatmaps, carrier estimation
│   │   │   ├── files/            # Import/export, MIME detection, filename safety
│   │   │   ├── service/          # Key, encryption, decryption, analysis workflows
│   │   │   ├── ui/               # Theme, navigation, views, and controllers
│   │   │   ├── model/            # Immutable requests, results, and reports
│   │   │   ├── exception/        # Domain-specific exceptions
│   │   │   └── util/             # Byte helpers and secret clearing
│   │   └── resources/
│   │       └── images/
│   │           ├── carriers/     # Three bundled PNG carriers
│   │           └── samples/      # Demo text payload
│   └── test/
│       └── java/                 # 23 unit and integration test classes
├── pom.xml
└── README.md
```

The UI depends on the service layer; cryptography, format, image, steganography, files, and
analysis code do not depend on JavaFX. See [`docs/ARCHITECTURE.md`](docs/ARCHITECTURE.md) for
the full layering and data-flow details.

---

## Security Notes

- **Never share** your private key or its passphrase. Share only the stego PNG and your public
  key.
- **Verify fingerprints** through a trusted channel before encrypting. A substituted public key
  would redirect the content to someone else.
- **Use a strong passphrase.** The `.scpriv` file is only as strong as its passphrase.
- **Back up private keys.** Losing the private key or passphrase makes encrypted content
  unrecoverable.
- **Use the original PNG.** Re-encoding, resizing, cropping, or converting the stego image can
  destroy the embedded package.
- **GCM detects tampering, not identity.** Authentication proves the data was not modified; it
  does not prove who created the package.
- **LSB is concealment, not proof of undetectability.** Statistical analysis or image
  transformations may reveal or destroy hidden data.
- **Host security still matters.** The application is offline, but keys and plaintext live on
  your machine while in use.

---

## Scope and Limitations

ShadowCrypt v1 is intentionally scoped:

| Limitation | Detail |
| :--- | :--- |
| **Carrier format** | PNG only |
| **Image output** | Opaque RGB; transparency is composited over white |
| **Input file size** | Guarded at 256 MiB |
| **Capacity** | Packages larger than the carrier capacity are rejected, never truncated |
| **Not included** | JPEG carriers, adaptive LSB, DCT steganography, compression, error correction, digital signatures, SSIM, and network features |

---

**Repository:** [github.com/yashwanth0072/shadowcrypt](https://github.com/yashwanth0072/shadowcrypt)
