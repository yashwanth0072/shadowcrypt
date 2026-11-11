# ShadowCrypt Architecture

This document records the module boundaries, binary formats, and data flow.

## Layering

```
ui -> service -> crypto | format | image | steganography | files | analysis
```

- `ui` may depend on `service`, `model`, `exception`, `util`, and capacity helpers for live
  feedback. It contains no cryptographic or embedding logic.
- `crypto`, `format`, `image`, `steganography`, and `analysis` do not import JavaFX.
- Serialization is independent of image embedding; image embedding is independent of
  cryptography.
- Services are the only place modules are composed.

## Modules

| Package | Responsibility |
|---|---|
| `app` | JavaFX application entry points |
| `config` | Cryptographic, package, and application constants |
| `model` | Immutable requests, results, and reports |
| `exception` | Domain-specific unchecked exceptions |
| `crypto.aes` | AES-256-GCM encryption/decryption, session key and nonce creation |
| `crypto.rsa` | RSA-3072 key generation and explicit OAEP wrapping/unwrapping |
| `crypto.keyderivation` | PBKDF2-HMAC-SHA256 key derivation |
| `crypto.keystorage` | Versioned protected private-key files, public-key codec |
| `crypto.hashing` | SHA-256 hashing and fingerprints |
| `format.serialization` | Bounded big-endian primitive reader/writer |
| `format.payload` | Encrypted inner `DATA` payload |
| `format.envelope` | Outer `SHDW` envelope, canonical AAD, strict parsing |
| `image` | PNG loading/normalization, PNG writing |
| `steganography` | LSB capacity, shared traversal cursor, encoder, decoder |
| `analysis` | MSE, PSNR, difference heatmap, image comparison, report assembly |
| `files.importer` | Safe input reading with size guards and MIME detection |
| `files.exporter` | Safe, atomic export under a user-selected directory |
| `files.validation` | Filename sanitization and MIME detection |
| `service` | Encryption, decryption, key management, analysis, progress events |
| `ui` | Theme, navigation, reusable components, four section views/controllers |

`files.importer`/`files.exporter` are deliberately not named `import`/`export`; `import` is a
reserved Java keyword and cannot appear in a package name.

## Outer envelope (SHDW v1)

All integers are big-endian.

| Field | Size (bytes) |
|---|---:|
| Magic `SHDW` | 4 |
| Version | 1 |
| Flags | 1 |
| Total package length | 4 |
| Wrapped-key length | 2 |
| Nonce length | 1 |
| Ciphertext length (includes 16-byte GCM tag) | 4 |
| Wrapped AES key | 384 |
| GCM nonce | 12 |
| Ciphertext + tag | variable |

The bootstrap header is 17 bytes. AAD is the canonical 413-byte sequence
`header || wrapped key || nonce`; ciphertext is never part of the AAD. Encryption builds the
header from the known plaintext length (`ciphertext length = inner length + 16`), so the AAD
exists before GCM encryption. Decryption reuses the exact extracted prefix as AAD.

## Inner payload (encrypted)

`DATA` (4) · version (1) · type (1) · filename length (2) · filename UTF-8 · MIME length (2) ·
MIME UTF-8 · original data length (8) · data.

- Type `0x01` = text, `0x02` = file.
- Lengths count UTF-8 bytes.
- Filename and MIME stay inside the encrypted payload.
- Path separators are rejected on both serialization and parsing.

## Protected private-key file (`.scpriv`)

`SCPR` (4) · version (1) · KDF id (1) · iterations (4) · salt length (1) · salt (16) ·
nonce length (1) · nonce (12) · ciphertext length (4) · AES-256-GCM(PKCS#8 key) + tag.

The bytes preceding the ciphertext are used as GCM AAD, binding the KDF parameters to the
ciphertext.

## Public-key file (`.scpub`)

PEM-like text containing base64 of the X.509 SubjectPublicKeyInfo. The fingerprint is
SHA-256 over the DER encoding, displayed as colon-separated hex.

## LSB embedding order

Row-major; left to right; top to bottom; red, green, blue channels; most-significant bit
first within each payload byte; one payload bit per channel LSB. The encoder and decoder
share a single channel cursor so the traversal cannot drift.

Capacity in bytes = `floor(width * height * 3 / 8)`.

## Data flow

Encryption:
`payload` → `PayloadSerializer` → fresh AES key/nonce → RSA-OAEP wrap → envelope header + AAD
→ AES-GCM encrypt → complete package → capacity validation → normalized carrier → LSB embed →
PNG write → `EncryptionResult` (real sizes and embedding time).

Decryption:
normalized stego PNG → 17-byte bootstrap header → strict validation → bounded full
extraction → envelope parse → actual-prefix AAD → RSA-OAEP unwrap → AES-GCM decrypt/authenticate
→ payload parse → text or sanitized export → SHA-256.

Analysis:
`EncryptionResult` → `AnalysisCalculator` (MSE/PSNR/heatmap) → `AnalysisReport` →
`AnalysisView` (no recomputation in the UI). Without an encryption run, a stego PNG goes
`CarrierNormalizer` → bootstrap header (`LsbDecoder` + `EnvelopeParser`) →
`CarrierEstimator` (LSBs of the overwritten channels cleared) + `PayloadFootprintGenerator`
→ MSE/PSNR/footprint; values only an encryption run knows are stored as `-1` and omitted
from the UI.

## UI threading

Every long operation runs through `BackgroundTaskRunner` on a daemon worker. Services report
stages through `ProgressListener`; controllers marshal progress to the FX thread and render
results or user-safe errors. Passphrases are wiped after use.

## UI structure

- `ui/main/MainWindow` — sidebar + content shell + status bar, keeps all four views alive.
- `ui/main/AppState` — observable active public key, private-key path, last encryption result,
  and last analysis report shared between sections.
- `ui/components` — cards, headers, file pickers, preview panes, banners, metric grids, busy
  overlay, dialogs, formatting helpers.
- `ui/theme/Theme` — dark neutral theme generated in Java and applied as a data-URI
  stylesheet, so the project contains no CSS files or other languages.
- One view + one controller per section: encryption, decryption, analysis, keys.

## Status

Implemented and tested. See the README for the test-to-requirement mapping.
