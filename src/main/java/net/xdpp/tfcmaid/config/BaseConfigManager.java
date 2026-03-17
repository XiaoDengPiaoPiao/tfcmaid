package net.xdpp.tfcmaid.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonSyntaxException;
import com.google.gson.reflect.TypeToken;
import net.minecraftforge.fml.loading.FMLPaths;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public abstract class BaseConfigManager<T> {
    protected static final Logger LOGGER = LogManager.getLogger();
    protected static final Path CONFIG_DIR = FMLPaths.CONFIGDIR.get().resolve("tfcmaid");
    protected static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    protected T config;
    protected boolean initialized = false;
    private final Path configFile;
    private final String configName;

    protected BaseConfigManager(String fileName, String configName) {
        this.configFile = CONFIG_DIR.resolve(fileName);
        this.configName = configName;
    }

    public void initialize() {
        try {
            Files.createDirectories(CONFIG_DIR);

            if (!Files.exists(configFile)) {
                createDefaultConfig();
            }

            loadConfig();
            initialized = true;

            LOGGER.info("Tfcmaid {} config loaded", configName);
        } catch (IOException e) {
            LOGGER.error("Failed to load Tfcmaid {} config", configName, e);
        }
    }

    protected abstract void createDefaultConfig() throws IOException;

    protected abstract TypeToken<T> getConfigType();

    protected void loadConfig() throws IOException {
        try (FileReader reader = new FileReader(configFile.toFile())) {
            T loadedConfig;
            try {
                loadedConfig = GSON.fromJson(reader, getConfigType().getType());
            } catch (JsonSyntaxException e) {
                LOGGER.warn("Invalid {} config format, recreating default config", configName);
                Files.deleteIfExists(configFile);
                createDefaultConfig();
                loadedConfig = GSON.fromJson(new FileReader(configFile.toFile()), getConfigType().getType());
            }

            if (loadedConfig != null) {
                config = loadedConfig;
                validateConfig();
            }
        }
    }

    protected void validateConfig() {
    }

    protected void saveConfig(T configToSave) throws IOException {
        try (FileWriter writer = new FileWriter(configFile.toFile())) {
            GSON.toJson(configToSave, writer);
        }
    }

    protected void ensureInitialized() {
        if (!initialized) {
            initialize();
        }
    }
}
