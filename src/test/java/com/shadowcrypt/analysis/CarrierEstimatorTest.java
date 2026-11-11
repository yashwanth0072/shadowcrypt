package com.shadowcrypt.analysis;

import com.shadowcrypt.analysis.comparison.CarrierEstimator;
import org.junit.jupiter.api.Test;

import java.awt.image.BufferedImage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class CarrierEstimatorTest {

    @Test
    void clearsLeastSignificantBitsInsideThePayloadChannels() {
        BufferedImage stego = imageOf(1, 1, 0xFF3F5B7B);

        BufferedImage estimate = CarrierEstimator.estimateCarrier(stego, 3);

        assertEquals(0xFF3E5A7A, estimate.getRGB(0, 0));
    }

    @Test
    void clearsOnlyTheChannelsActuallyOverwritten() {
        BufferedImage stego = imageOf(1, 1, 0xFF3F5B7B);

        BufferedImage estimate = CarrierEstimator.estimateCarrier(stego, 1);

        assertEquals(0xFF3E5B7B, estimate.getRGB(0, 0));
    }

    @Test
    void leavesPixelsBeyondThePayloadByteIdentical() {
        BufferedImage stego = imageOf(2, 1, 0xFF3F5B7B, 0xFF0A0B0C);

        BufferedImage estimate = CarrierEstimator.estimateCarrier(stego, 3);

        assertEquals(0xFF3E5A7A, estimate.getRGB(0, 0));
        assertEquals(0xFF0A0B0C, estimate.getRGB(1, 0));
    }

    @Test
    void preservesDimensionsAndProducesRgbImage() {
        BufferedImage stego = imageOf(3, 2, 0xFF010203, 0xFF040506, 0xFF070809,
                0xFF0A0B0C, 0xFF0D0E0F, 0xFF101112);

        BufferedImage estimate = CarrierEstimator.estimateCarrier(stego, 0);

        assertEquals(3, estimate.getWidth());
        assertEquals(2, estimate.getHeight());
        assertEquals(BufferedImage.TYPE_INT_RGB, estimate.getType());
        assertEquals(0xFF010203, estimate.getRGB(0, 0));
    }

    @Test
    void rejectsChannelsBeyondCapacity() {
        BufferedImage stego = imageOf(1, 1, 0xFF010203);

        assertThrows(IllegalArgumentException.class,
                () -> CarrierEstimator.estimateCarrier(stego, 4));
        assertThrows(IllegalArgumentException.class,
                () -> CarrierEstimator.estimateCarrier(stego, -1));
    }

    private static BufferedImage imageOf(int width, int height, int... argbPixels) {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        image.setRGB(0, 0, width, height, argbPixels, 0, width);
        return image;
    }
}
