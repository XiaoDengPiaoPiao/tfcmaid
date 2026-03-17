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
import java.util.LinkedHashMap;
import java.util.Map;

public class FeedConfigManager {
    private static final Logger LOGGER = LogManager.getLogger();
    private static final Path CONFIG_DIR = FMLPaths.CONFIGDIR.get().resolve("tfcmaid");
    private static final Path FEED_CONFIG_FILE = CONFIG_DIR.resolve("feed_config.json");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private static Map<String, Object> feedConfigs = new LinkedHashMap<>();
    private static boolean initialized = false;

    private static final int DEFAULT_MAX_ANIMAL_COUNT = 30;
    private static final double DEFAULT_FEED_PERCENTAGE_ABOVE_LIMIT = 0.2;

    public static void initialize() {
        try {
            Files.createDirectories(CONFIG_DIR);

            if (!Files.exists(FEED_CONFIG_FILE)) {
                createDefaultConfig();
            }

            loadConfig();
            initialized = true;

            LOGGER.info("Tfcmaid feed config loaded");
        } catch (IOException e) {
            LOGGER.error("Failed to load Tfcmaid feed config", e);
        }
    }

    private static void createDefaultConfig() throws IOException {
        Map<String, Object> defaultConfig = new LinkedHashMap<>();
        defaultConfig.put("maxAnimalCount", DEFAULT_MAX_ANIMAL_COUNT);
        defaultConfig.put("feedPercentageAboveLimit", DEFAULT_FEED_PERCENTAGE_ABOVE_LIMIT);

        try (FileWriter writer = new FileWriter(FEED_CONFIG_FILE.toFile())) {
            GSON.toJson(defaultConfig, writer);
        }
    }

    private static void loadConfig() throws IOException {
        try (FileReader reader = new FileReader(FEED_CONFIG_FILE.toFile())) {
            Map<String, Object> config;
            try {
                config = GSON.fromJson(reader, new TypeToken<Map<String, Object>>(){}.getType());
            } catch (JsonSyntaxException e) {
                LOGGER.warn("Invalid feed config format, recreating default config");
                Files.deleteIfExists(FEED_CONFIG_FILE);
                createDefaultConfig();
                config = GSON.fromJson(new FileReader(FEED_CONFIG_FILE.toFile()),
                        new TypeToken<Map<String, Object>>(){}.getType());
            }

            if (config != null) {
                feedConfigs = config;
                validateConfig();
            }
        }
    }

    private static void validateConfig() {
        if (!feedConfigs.containsKey("maxAnimalCount")) {
            feedConfigs.put("maxAnimalCount", DEFAULT_MAX_ANIMAL_COUNT);
        }
        if (!feedConfigs.containsKey("feedPercentageAboveLimit")) {
            feedConfigs.put("feedPercentageAboveLimit", DEFAULT_FEED_PERCENTAGE_ABOVE_LIMIT);
        }
        
        Object maxCount = feedConfigs.get("maxAnimalCount");
        if (maxCount instanceof Number) {
            feedConfigs.put("maxAnimalCount", Math.max(1, ((Number) maxCount).intValue()));
        } else {
            feedConfigs.put("maxAnimalCount", DEFAULT_MAX_ANIMAL_COUNT);
        }
        
        Object percentage = feedConfigs.get("feedPercentageAboveLimit");
        if (percentage instanceof Number) {
            feedConfigs.put("feedPercentageAboveLimit", Math.max(0.0, Math.min(1.0, ((Number) percentage).doubleValue())));
        } else {
            feedConfigs.put("feedPercentageAboveLimit", DEFAULT_FEED_PERCENTAGE_ABOVE_LIMIT);
        }
    }

    public static int getMaxAnimalCount() {
        if (!initialized) {
            initialize();
        }
        return ((Number) feedConfigs.get("maxAnimalCount")).intValue();
    }

    public static double getFeedPercentageAboveLimit() {
        if (!initialized) {
            initialize();
        }
        return ((Number) feedConfigs.get("feedPercentageAboveLimit")).doubleValue();
    }
}
