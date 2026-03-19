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

/**
 * 通用配置管理器基类
 * <p>
 * 封装了配置文件的加载、保存和验证逻辑，避免代码重复
 * 配置文件统一存放于 config/tfcmaid 目录下
 *
 * @param <T> 配置对象类型
 */
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

    /**
     * 初始化配置管理器
     * <p>
     * 会自动创建配置目录，加载配置文件（不存在时创建默认配置）
     */
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

    /**
     * 从文件加载配置
     * <p>
     * 如果 JSON 格式无效，会自动删除并重建默认配置
     */
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
