package com.pythonburp.python;

import org.junit.jupiter.api.Test;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;

final class PythonRuntimeCompatibilityTest {
    @Test
    void acceptsExactWindowsX64MsvcPython3143Fingerprint() throws Exception {
        Method validate = validator();

        assertDoesNotThrow(() -> validate.invoke(null, 3, 14, 3, "Windows", "AMD64", "Python 3.14.3 [MSC v.1944 64 bit (AMD64)]"));
    }

    @Test
    void rejectsDifferentPatchVersion() throws Exception {
        Method validate = validator();

        InvocationTargetException error = assertThrows(InvocationTargetException.class,
            () -> validate.invoke(null, 3, 14, 6, "Windows", "AMD64", "Python 3.14.6 [MSC v.1944 64 bit (AMD64)]"));

        assertTrue(error.getCause().getMessage().contains("3.14.3"));
    }

    @Test
    void rejectsNonMsvcRuntime() throws Exception {
        Method validate = validator();

        InvocationTargetException error = assertThrows(InvocationTargetException.class,
            () -> validate.invoke(null, 3, 14, 3, "Windows", "AMD64", "Python 3.14.3 [GCC 15.2.0]"));

        assertTrue(error.getCause().getMessage().contains("MSVC"));
    }

    private static Method validator() throws Exception {
        Class<?> compatibility = Class.forName("com.pythonburp.python.PythonRuntimeCompatibility");
        Method validate = compatibility.getDeclaredMethod(
            "validate", int.class, int.class, int.class, String.class, String.class, String.class);
        validate.setAccessible(true);
        return validate;
    }
}
