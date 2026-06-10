package com.pythonburp.bridge;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

final class CryptoBridgeTest {
    @Test
    void sha256HexMatchesKnownValue() {
        CryptoBridge crypto = new CryptoBridge();

        assertEquals(
            "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad",
            crypto.sha256Hex("abc".getBytes())
        );
    }
}
