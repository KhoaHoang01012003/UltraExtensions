package com.pythonburp.bridge;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

final class EncoderBridgeTest {
    @Test
    void base64RoundTripsBytes() {
        EncoderBridge encoder = new EncoderBridge();

        String encoded = encoder.base64Encode("abc".getBytes());

        assertEquals("YWJj", encoded);
        assertArrayEquals("abc".getBytes(), encoder.base64Decode(encoded));
    }
}
