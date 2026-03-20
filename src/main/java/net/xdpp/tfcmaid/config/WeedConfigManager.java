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
import java.util.HashSet;
import java.util.Set;

/**
 * 杂草清理任务配置管理器
 * <p>
 * 用于管理杂草清理任务的黑名单配置
 */
public class WeedConfigManager {
    private static final Logger LOGGER = LogManager.getLogger();
    private static final Path CONFIG_DIR = FMLPaths.CONFIGDIR.get().resolve("tfcmaid");
    private static final Path WEED_CONFIG_FILE = CONFIG_DIR.resolve("weed_config.json");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private static Set<String> blacklistedBlocks = new HashSet<>();
    private static boolean initialized = false;

    public static class WeedConfig {
        public Set<String> blacklistedBlocks = new HashSet<>();
    }

    public static void initialize() {
        try {
            Files.createDirectories(CONFIG_DIR);

            if (!Files.exists(WEED_CONFIG_FILE)) {
                createDefaultConfig();
            }

            loadConfig();
            initialized = true;

            LOGGER.info("Tfcmaid weed config loaded");
        } catch (IOException e) {
            LOGGER.error("Failed to load Tfcmaid weed config", e);
        }
    }

    private static void createDefaultConfig() throws IOException {
        WeedConfig defaultConfig = new WeedConfig();
        try (FileWriter writer = new FileWriter(WEED_CONFIG_FILE.toFile())) {
            GSON.toJson(defaultConfig, writer);
        }
    }

    private static void loadConfig() throws IOException {
        try (FileReader reader = new FileReader(WEED_CONFIG_FILE.toFile())) {
            WeedConfig config;
            try {
                config = GSON.fromJson(reader, new TypeToken<WeedConfig>() {}.getType());
            } catch (JsonSyntaxException e) {
                LOGGER.warn("Invalid weed config format, recreating default config");
                Files.deleteIfExists(WEED_CONFIG_FILE);
                createDefaultConfig();
                config = GSON.fromJson(new FileReader(WEED_CONFIG_FILE.toFile()),
                        new TypeToken<WeedConfig>() {}.getType());
            }

            if (config != null) {
                blacklistedBlocks = config.blacklistedBlocks;
            }
        }
    }

    public static boolean isBlockBlacklisted(String blockId) {
        if (!initialized) {
            initialize();
        }
        return blacklistedBlocks.contains(blockId);
    }

    public static Set<String> getBlacklistedBlocks() {
        if (!initialized) {
            initialize();
        }
        return Set.copyOf(blacklistedBlocks);
    }
}
