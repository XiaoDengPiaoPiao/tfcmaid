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
import java.util.*;

// 任务配置管理器，用于加载和管理任务屏蔽配置,
// 配置文件格式:
// {
//     "TaskCrossBowAttack": true,
//     "TaskMilk": true,
//     "TaskTridentAttack": true,
//     "TaskNormalFarm": true,
//     "TaskSugarCane": true,
//     "TaskMelon": true,
//     "TaskCocoa": true,
//     "TaskHoney": true,
//     "TaskGrass": true,
//     "TaskTorch": true,
//     "TaskFeedAnimal": true,
//     "TaskFishing": true,
//     "TaskExtinguishing": true
// }
// 输出在config/tfcmaid/task_shield.json
public class TaskConfigManager {
    private static final Logger LOGGER = LogManager.getLogger();
    private static final Path CONFIG_DIR = FMLPaths.CONFIGDIR.get().resolve("tfcmaid");
    private static final Path TASK_SHIELD_FILE = CONFIG_DIR.resolve("task_shield.json");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private static Map<String, Boolean> taskConfigs = new HashMap<>();
    private static boolean initialized = false;

    public static void initialize() {
        try {
            Files.createDirectories(CONFIG_DIR);

            if (!Files.exists(TASK_SHIELD_FILE)) {
                createDefaultConfig();
            }

            loadConfig();
            initialized = true;

            LOGGER.info("Tfcmaid config loaded: {} tasks configured", taskConfigs.size());
        } catch (IOException e) {
            LOGGER.error("Failed to load Tfcmaid config", e);
        }
    }

    private static void createDefaultConfig() throws IOException {
        Map<String, Boolean> defaultConfig = new LinkedHashMap<>();
        defaultConfig.put("TaskCrossBowAttack", true);
        defaultConfig.put("TaskMilk", true);
        defaultConfig.put("TaskTridentAttack", true);
        defaultConfig.put("TaskNormalFarm", true);
        defaultConfig.put("TaskSugarCane", true);
        defaultConfig.put("TaskMelon", true);
        defaultConfig.put("TaskCocoa", true);
        defaultConfig.put("TaskHoney", true);
        defaultConfig.put("TaskGrass", true);
        defaultConfig.put("TaskTorch", true);
        defaultConfig.put("TaskFeedAnimal", true);
        defaultConfig.put("TaskFishing", true);
        defaultConfig.put("TaskExtinguishing", true);

        try (FileWriter writer = new FileWriter(TASK_SHIELD_FILE.toFile())) {
            GSON.toJson(defaultConfig, writer);
        }
    }

    private static void loadConfig() throws IOException {
        try (FileReader reader = new FileReader(TASK_SHIELD_FILE.toFile())) {
            Map<String, Boolean> config;
            try {
                config = GSON.fromJson(reader, new TypeToken<Map<String, Boolean>>(){}.getType());
            } catch (JsonSyntaxException e) {
                LOGGER.warn("Invalid config format, recreating default config");
                Files.deleteIfExists(TASK_SHIELD_FILE);
                createDefaultConfig();
                config = GSON.fromJson(new FileReader(TASK_SHIELD_FILE.toFile()),
                        new TypeToken<Map<String, Boolean>>(){}.getType());
            }

            if (config != null) {
                taskConfigs = config;
            }
        }
    }

    public static boolean isTaskBlocked(String taskName) {
        if (!initialized) {
            initialize();
        }
        return taskConfigs.getOrDefault(taskName, false);
    }

    public static Set<String> getAllBlockedTasks() {
        if (!initialized) {
            initialize();
        }
        Set<String> blockedTasks = new HashSet<>();
        for (Map.Entry<String, Boolean> entry : taskConfigs.entrySet()) {
            if (entry.getValue()) {
                blockedTasks.add(entry.getKey());
            }
        }
        return Collections.unmodifiableSet(blockedTasks);
    }

    public static Map<String, Boolean> getTaskConfigs() {
        if (!initialized) {
            initialize();
        }
        return Collections.unmodifiableMap(taskConfigs);
    }
}