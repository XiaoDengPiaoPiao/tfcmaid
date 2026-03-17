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

// 通用配置管理器基类，把TaskConfigManager和FeedConfigManager重复的代码都抽了
// 两个配置管理器写差不多一样的东西，维护起来麻烦
public abstract class BaseConfigManager<T> {
    protected static final Logger LOGGER = LogManager.getLogger();
    protected static final Path CONFIG_DIR = FMLPaths.CONFIGDIR.get().resolve("tfcmaid"); // 配置目录，固定在config/tfcmaid下
    protected static final Gson GSON = new GsonBuilder().setPrettyPrinting().create(); // GSON实例，开启了格式化输出，方便看

    protected T config; // 实际的配置对象
    protected boolean initialized = false; // 是否已初始化，避免重复加载
    private final Path configFile; // 配置文件路径
    private final String configName; // 配置名称，日志里用

    // 构造函数，传文件名和配置名
    protected BaseConfigManager(String fileName, String configName) {
        this.configFile = CONFIG_DIR.resolve(fileName);
        this.configName = configName;
    }

    // 初始化配置，第一次使用时自动调用
    // 会创建目录，检查文件是否存在，不存在就创建默认配置
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

    // 子类实现这个，创建默认配置
    protected abstract void createDefaultConfig() throws IOException;

    // 子类实现这个，返回配置的TypeToken，GSON反序列化要用
    protected abstract TypeToken<T> getConfigType();

    // 加载配置，JSON格式不对会自动重置为默认配置
    protected void loadConfig() throws IOException {
        try (FileReader reader = new FileReader(configFile.toFile())) {
            T loadedConfig;
            try {
                loadedConfig = GSON.fromJson(reader, getConfigType().getType());
            } catch (JsonSyntaxException e) {
                // JSON格式炸了
                LOGGER.warn("Invalid {} config format, recreating default config", configName);
                Files.deleteIfExists(configFile);
                createDefaultConfig();
                loadedConfig = GSON.fromJson(new FileReader(configFile.toFile()), getConfigType().getType());
            }

            if (loadedConfig != null) {
                config = loadedConfig;
                validateConfig(); // 加载完验证一下配置是否合理
            }
        }
    }

    // 验证配置的方法，子类可以覆盖这个来做一些检查
    // 比如检查数值范围之类的，不合理就修正
    protected void validateConfig() {
    }

    // 保存配置到文件
    protected void saveConfig(T configToSave) throws IOException {
        try (FileWriter writer = new FileWriter(configFile.toFile())) {
            GSON.toJson(configToSave, writer);
        }
    }

    // 确保初始化了，没初始化就初始化一下
    // 给获取配置值的方法用，避免忘记初始化
    protected void ensureInitialized() {
        if (!initialized) {
            initialize();
        }
    }
}
