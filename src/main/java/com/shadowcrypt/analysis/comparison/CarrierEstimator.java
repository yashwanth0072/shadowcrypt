package com.shadowcrypt.analysis.comparison;

import com.shadowcrypt.config.AppConstants;

import java.awt.image.BufferedImage;
import java.util.Objects;

/**
 * Reconstructs an approximate carrier from a stego image. LSB embedding only ever
 * overwrites the first {@code payloadChannels} RGB channels, so clearing exactly those
 * least-significant bits yields an estimate that matches the real carrier everywhere the
 * embedder never touched.
 */
public final class CarrierEstimator {

    private CarrierEstimator() {
    }

    /** @param payloadChannels number of RGB channels the embedder overwrote */
    public static BufferedImage estimateCarrier(BufferedImage stegoImage, long payloadChannels) {
        Objects.requireNonNull(stegoImage, "stegoImage");
        if (payloadChannels < 0) {
            throw new IllegalArgumentException("Payload channels must not be negative");
        }

        int width = stegoImage.getWidth();
        int height = stegoImage.getHeight();
        long totalChannels = (long) width * height * AppConstants.CHANNELS_PER_PIXEL;
        if (payloadChannels > totalChannels) {
            throw new IllegalArgumentException("Payload channels exceed the image capacity");
        }

        int[] pixels = ImageComparison.pixelRgb(stegoImage);
        for (long channel = 0; channel < payloadChannels; channel++) {
            int pixelIndex = (int) (channel / AppConstants.CHANNELS_PER_PIXEL);
            int channelInPixel = (int) (channel % AppConstants.CHANNELS_PER_PIXEL);
            int shift = 16 - 8 * channelInPixel;
            pixels[pixelIndex] &= ~(1 << shift);
        }

        BufferedImage estimate = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        estimate.setRGB(0, 0, width, height, pixels, 0, width);
        return estimate;
    }
}
