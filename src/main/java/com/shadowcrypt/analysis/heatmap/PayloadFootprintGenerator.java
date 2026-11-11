package com.shadowcrypt.analysis.heatmap;

import java.awt.image.BufferedImage;

/**
 * Paints the contiguous region of pixels the LSB embedder overwrote: white where the
 * package lives, black everywhere else. The region is exact, because embedding starts at
 * the first channel and advances in row-major RGB order.
 */
public final class PayloadFootprintGenerator {

    private static final int EMBEDDED = 0xFFFFFFFF;
    private static final int UNTOUCHED = 0xFF000000;

    private PayloadFootprintGenerator() {
    }

    /** @param payloadPixels number of leading pixels that carry at least one payload bit */
    public static BufferedImage generate(int width, int height, long payloadPixels) {
        if (width <= 0 || height <= 0) {
            throw new IllegalArgumentException("Image dimensions must be positive");
        }
        long totalPixels = (long) width * height;
        if (payloadPixels < 0 || payloadPixels > totalPixels) {
            throw new IllegalArgumentException("Payload pixels must fit inside the image");
        }

        int[] pixels = new int[(int) totalPixels];
        int embedded = (int) payloadPixels;
        for (int pixel = 0; pixel < embedded; pixel++) {
            pixels[pixel] = EMBEDDED;
        }
        for (int pixel = embedded; pixel < pixels.length; pixel++) {
            pixels[pixel] = UNTOUCHED;
        }

        BufferedImage footprint = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        footprint.setRGB(0, 0, width, height, pixels, 0, width);
        return footprint;
    }
}
