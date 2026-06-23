package com.pythonburp;

import burp.api.montoya.MontoyaApi;
import burp.api.montoya.extension.Extension;
import burp.api.montoya.extension.ExtensionUnloadingHandler;
import burp.api.montoya.logging.Logging;
import burp.api.montoya.ui.UserInterface;
import com.pythonburp.core.ExtensionContext;
import com.pythonburp.python.RuntimeProvisioningOutcome;
import org.junit.jupiter.api.Test;

import javax.swing.UIManager;
import javax.swing.SwingUtilities;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.RejectedExecutionException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class BurpPythonIdeExtensionTest {
    @Test
    void unloadHandlerClosesExtensionContext() throws Exception {
        BurpPythonIdeExtension extension = readyExtension();
        StubMontoyaApi stub = new StubMontoyaApi();

        extension.initialize(stub.api());
        ExtensionContext context = contextFrom(extension);

        assertEquals(1, stub.unloadingHandlers.size());

        stub.unloadingHandlers.get(0).extensionUnloaded();

        assertThrows(RejectedExecutionException.class, () -> context.executors().submitScript(() -> 1));
    }

    @Test
    void repeatedInitializeClosesPreviousContext() throws Exception {
        BurpPythonIdeExtension extension = readyExtension();
        StubMontoyaApi firstStub = new StubMontoyaApi();
        StubMontoyaApi secondStub = new StubMontoyaApi();

        extension.initialize(firstStub.api());
        ExtensionContext firstContext = contextFrom(extension);

        extension.initialize(secondStub.api());

        assertThrows(RejectedExecutionException.class, () -> firstContext.executors().submitScript(() -> 1));
        assertEquals(1, secondStub.unloadingHandlers.size());
    }

    @Test
    void initializeDoesNotChangeHostLookAndFeel() throws Exception {
        Object before = UIManager.getLookAndFeel();
        BurpPythonIdeExtension extension = readyExtension();
        StubMontoyaApi stub = new StubMontoyaApi();

        extension.initialize(stub.api());

        assertEquals(before, UIManager.getLookAndFeel());
    }

    @Test
    void createsAndRegistersSuiteTabOnEventDispatchThread() {
        BurpPythonIdeExtension extension = readyExtension();
        StubMontoyaApi stub = new StubMontoyaApi();

        extension.initialize(stub.api());

        assertTrue(stub.suiteTabRegisteredOnEdt);
    }

    @Test
    void doesNotRegisterSuiteTabWhenStartupProvisioningFails() throws Exception {
        BurpPythonIdeExtension extension =
            new BurpPythonIdeExtension(() -> RuntimeProvisioningOutcome.failure("admin declined"));
        StubMontoyaApi stub = new StubMontoyaApi();

        extension.initialize(stub.api());

        assertEquals(0, stub.suiteTabRegistrations);
        assertNull(contextFrom(extension));
        assertTrue(stub.errorLogs.stream().anyMatch(message -> message.contains("admin declined")));
    }

    private static ExtensionContext contextFrom(BurpPythonIdeExtension extension) throws Exception {
        java.lang.reflect.Field field = BurpPythonIdeExtension.class.getDeclaredField("context");
        field.setAccessible(true);
        return (ExtensionContext) field.get(extension);
    }

    private static BurpPythonIdeExtension readyExtension() {
        return new BurpPythonIdeExtension(RuntimeProvisioningOutcome::success);
    }

    private static final class StubMontoyaApi {
        private final List<ExtensionUnloadingHandler> unloadingHandlers = new ArrayList<>();
        private final List<String> errorLogs = new ArrayList<>();
        private final Extension extension = proxy(Extension.class, this::handleExtensionCall);
        private final Logging logging = proxy(Logging.class, this::handleLoggingCall);
        private final UserInterface userInterface = proxy(UserInterface.class, this::handleUserInterfaceCall);
        private final MontoyaApi api = proxy(MontoyaApi.class, this::handleApiCall);
        private boolean suiteTabRegisteredOnEdt;
        private int suiteTabRegistrations;

        MontoyaApi api() {
            return api;
        }

        private Object handleApiCall(Method method, Object[] args) {
            return switch (method.getName()) {
                case "extension" -> extension;
                case "logging" -> logging;
                case "userInterface" -> userInterface;
                default -> defaultValue(method.getReturnType());
            };
        }

        private Object handleExtensionCall(Method method, Object[] args) {
            if (method.getName().equals("registerUnloadingHandler")) {
                unloadingHandlers.add((ExtensionUnloadingHandler) args[0]);
            }
            return defaultValue(method.getReturnType());
        }

        private Object handleLoggingCall(Method method, Object[] args) {
            if (method.getName().equals("logToError")) {
                errorLogs.add(String.valueOf(args[0]));
            }
            return defaultValue(method.getReturnType());
        }

        private Object handleUserInterfaceCall(Method method, Object[] args) {
            if (method.getName().equals("registerSuiteTab")) {
                suiteTabRegisteredOnEdt = SwingUtilities.isEventDispatchThread();
                suiteTabRegistrations++;
            }
            return defaultValue(method.getReturnType());
        }
    }

    private static <T> T proxy(Class<T> type, ProxyMethodHandler handler) {
        InvocationHandler invocationHandler = (ignored, method, args) -> handler.invoke(method, args);
        Object proxy = Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type}, invocationHandler);
        return type.cast(proxy);
    }

    private static Object defaultValue(Class<?> returnType) {
        if (returnType == boolean.class) {
            return false;
        }
        if (returnType == byte.class || returnType == short.class || returnType == int.class || returnType == long.class) {
            return 0;
        }
        if (returnType == float.class || returnType == double.class) {
            return 0.0;
        }
        if (returnType == char.class) {
            return '\0';
        }
        return null;
    }

    @FunctionalInterface
    private interface ProxyMethodHandler {
        Object invoke(Method method, Object[] args);
    }
}
