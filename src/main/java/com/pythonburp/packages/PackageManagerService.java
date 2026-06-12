package com.pythonburp.packages;

import com.pythonburp.concurrency.RuntimeActivityCoordinator;
import com.pythonburp.storage.ExtensionDataCleaner;
import com.pythonburp.storage.ExtensionDataPaths;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Supplier;

public final class PackageManagerService {
    private final ExtensionDataPaths paths;
    private final RuntimeActivityCoordinator coordinator;
    private final SharedPackageEnvironment environment;
    private final PackageRequestStore requestStore;
    private final PackageSettingsStore settingsStore;
    private final PackageInventoryReader inventoryReader;
    private final ExtensionDataCleaner cleaner;
    private final EmbeddedPipRunner pipRunner;
    private final Supplier<Path> pythonSupplier;
    private volatile boolean reset;

    public PackageManagerService(
        ExtensionDataPaths paths,
        RuntimeActivityCoordinator coordinator,
        SharedPackageEnvironment environment,
        PackageRequestStore requestStore,
        PackageSettingsStore settingsStore,
        PackageInventoryReader inventoryReader,
        ExtensionDataCleaner cleaner,
        EmbeddedPipRunner pipRunner,
        Path python
    ) {
        this(paths, coordinator, environment, requestStore, settingsStore, inventoryReader,
            cleaner, pipRunner, () -> python);
    }

    public PackageManagerService(
        ExtensionDataPaths paths,
        RuntimeActivityCoordinator coordinator,
        SharedPackageEnvironment environment,
        PackageRequestStore requestStore,
        PackageSettingsStore settingsStore,
        PackageInventoryReader inventoryReader,
        ExtensionDataCleaner cleaner,
        EmbeddedPipRunner pipRunner,
        Supplier<Path> pythonSupplier
    ) {
        this.paths = paths;
        this.coordinator = coordinator;
        this.environment = environment;
        this.requestStore = requestStore;
        this.settingsStore = settingsStore;
        this.inventoryReader = inventoryReader;
        this.cleaner = cleaner;
        this.pipRunner = pipRunner;
        this.pythonSupplier = pythonSupplier;
    }

    public PackageOperationResult installRequirement(String requirement, Consumer<String> output) {
        String value = requirement == null ? "" : requirement.trim();
        if (value.isBlank()) return failure("Package requirement is empty");
        return mutate(replace(requests(), PackageRequest.pypi(requirementId(value), value)), output);
    }

    public PackageOperationResult installWheel(Path wheel, Consumer<String> output) {
        try {
            Path managed = copySource(wheel, ".whl");
            String file = wheel.getFileName().toString();
            String id = file.contains("-") ? file.substring(0, file.indexOf('-')) : file.replaceFirst("\\.whl$", "");
            return mutate(replace(requests(), PackageRequest.wheel(id, managed)), output);
        } catch (IOException e) {
            return failure(e.getMessage());
        }
    }

    public PackageOperationResult installRequirements(Path requirements, Consumer<String> output) {
        try {
            Path managed = copySource(requirements, ".txt");
            return mutate(replace(requests(), PackageRequest.requirements("requirements-" + UUID.randomUUID(), managed)), output);
        } catch (IOException e) {
            return failure(e.getMessage());
        }
    }

    public PackageOperationResult uninstall(String name, Consumer<String> output) {
        String id = PackageRequest.normalizeId(name);
        List<PackageRequest> next = requests().stream().filter(request -> !request.id().equals(id)).toList();
        return mutate(next, output);
    }

    public PackageOperationResult repair(Consumer<String> output) {
        return mutate(requests(), output);
    }

    public List<PackageInventoryEntry> inventory() throws IOException {
        return inventoryReader.read(paths.userPackages());
    }

    public PackageOperationResult clearUserPackages() {
        try (var ignored = coordinator.beginPackageMutation()) {
            cleaner.clearUserPackages();
            requestStore.save(List.of());
            return success("User packages cleared");
        } catch (Exception e) {
            return failure(e.getMessage());
        }
    }

    public PackageOperationResult clearPipCache() {
        try (var ignored = coordinator.beginPackageMutation()) {
            cleaner.clearPipCache();
            return success("pip cache cleared");
        } catch (Exception e) {
            return failure(e.getMessage());
        }
    }

    public PackageOperationResult resetAllExtensionData() {
        try (var ignored = coordinator.beginPackageMutation()) {
            pipRunner.cancel();
            cleaner.resetAll();
            reset = true;
            return new PackageOperationResult(true, "All extension data reset; reload the extension", List.of());
        } catch (Exception e) {
            return failure(e.getMessage());
        }
    }

    public PackageManagerSettings settings() throws IOException { return settingsStore.load(); }
    public void saveSettings(PackageManagerSettings settings) throws IOException { settingsStore.save(settings); }
    public boolean resetRequired() { return reset; }

    private PackageOperationResult mutate(List<PackageRequest> next, Consumer<String> output) {
        if (reset) return failure("Extension reload is required after reset");
        try (var ignored = coordinator.beginPackageMutation()) {
            PackageManagerSettings settings = settingsStore.load();
            PipCommandFactory commands = new PipCommandFactory(pythonSupplier.get(), paths);
            environment.replaceWith(staging -> {
                for (PackageRequest request : next) {
                    List<String> command = switch (request.type()) {
                        case PYPI -> commands.installRequirement(request.value(), staging, settings);
                        case WHEEL -> commands.installWheel(request.path(), staging, settings);
                        case REQUIREMENTS -> commands.installRequirements(request.path(), staging, settings);
                    };
                    try {
                        PipRunResult result = pipRunner.run(command, output);
                        if (!result.succeeded()) throw new IOException("pip failed with exit code " + result.exitCode());
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        throw new IOException("pip operation interrupted", e);
                    }
                }
                inventoryReader.read(staging);
            });
            requestStore.save(next);
            return success("Package environment updated");
        } catch (Exception e) {
            return failure(e.getMessage());
        }
    }

    private Path copySource(Path source, String extension) throws IOException {
        if (source == null || !Files.isRegularFile(source) || !source.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(extension)) {
            throw new IOException("Invalid package source: " + source);
        }
        Files.createDirectories(paths.packageSources());
        Path sourceDirectory = paths.requireOwned(paths.packageSources().resolve(UUID.randomUUID().toString()));
        Files.createDirectories(sourceDirectory);
        Path destination = paths.requireOwned(sourceDirectory.resolve(source.getFileName()));
        Files.copy(source, destination, StandardCopyOption.REPLACE_EXISTING);
        return destination;
    }

    private List<PackageRequest> requests() {
        try { return requestStore.load(); } catch (IOException e) { return List.of(); }
    }

    private static List<PackageRequest> replace(List<PackageRequest> existing, PackageRequest replacement) {
        List<PackageRequest> result = new ArrayList<>(existing.stream()
            .filter(request -> !request.id().equals(replacement.id())).toList());
        result.add(replacement);
        return List.copyOf(result);
    }

    private static String requirementId(String requirement) {
        String value = requirement.split("[<>=!~\\[]", 2)[0].trim();
        return PackageRequest.normalizeId(value);
    }

    private PackageOperationResult success(String message) {
        try { return new PackageOperationResult(true, message, inventory()); }
        catch (IOException e) { return new PackageOperationResult(true, message, List.of()); }
    }

    private static PackageOperationResult failure(String message) {
        return new PackageOperationResult(false, message == null ? "Package operation failed" : message, List.of());
    }
}
