package com.pythonburp.nativepack;

import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

final class NativePackManifestLoaderTest {
    @Test
    void loadsBundledManifest() throws Exception {
        NativePackManifest manifest = NativePackManifestLoader.loadBundled();

        assertEquals(0, manifest.resources().size());
    }

    @Test
    void parsesNativePackResources() throws Exception {
        NativePackManifest manifest = NativePackManifestLoader.parse("""
            [
              {
                "packId": "windows-x64-core",
                "os": "windows",
                "arch": "amd64",
                "resourcePath": "native-pack/windows-x64-core/example.pyd",
                "targetPath": "native/windows-x64-core/example.pyd",
                "sha256": "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"
              }
            ]
            """);

        NativePackResource resource = manifest.resources().get(0);
        assertEquals("windows-x64-core", resource.packId());
        assertEquals("native/windows-x64-core/example.pyd", resource.targetPath());
    }

    @Test
    void rejectsUnknownFields() {
        assertThrows(IOException.class, () -> NativePackManifestLoader.parse("""
            [
              {
                "packId": "windows-x64-core",
                "os": "windows",
                "arch": "amd64",
                "resourcePath": "native-pack/windows-x64-core/example.pyd",
                "targetPath": "native/windows-x64-core/example.pyd",
                "sha256": "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",
                "unexpected": "value"
              }
            ]
            """));
    }
}
