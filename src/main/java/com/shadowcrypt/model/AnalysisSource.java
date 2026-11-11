package com.shadowcrypt.model;

/** Where an {@link AnalysisReport} got its comparison images from. */
public enum AnalysisSource {

    /** Measured during an encryption run: the real normalized carrier is available. */
    ENCRYPTION,

    /** Measured from a stego image alone: the carrier is a reconstructed estimate. */
    STEGO_IMAGE
}
