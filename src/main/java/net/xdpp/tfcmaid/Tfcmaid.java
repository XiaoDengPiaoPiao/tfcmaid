package net.xdpp.tfcmaid;

import com.mojang.logging.LogUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.server.ServerStartingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;
import net.xdpp.tfcmaid.config.FeedConfigManager;
import net.xdpp.tfcmaid.config.TaskConfigManager;
import net.xdpp.tfcmaid.config.WeedConfigManager;
import net.xdpp.tfcmaid.item.FarmDebugWand;
import org.slf4j.Logger;

// The value here should match an entry in the META-INF/mods.toml file
@Mod(Tfcmaid.MODID)
public class Tfcmaid {

    // Define mod id in a common place for everything to reference
    public static final String MODID = "tfcmaid";
    // Directly reference a slf4j logger
    private static final Logger LOGGER = LogUtils.getLogger();
    // 创建一个延迟注册器来管理方块，所有方块都将在"tfcmaid"命名空间下注册
    public static final DeferredRegister<Block> BLOCKS = DeferredRegister.create(ForgeRegistries.BLOCKS, MODID);
    // 创建一个延迟注册器来管理物品，所有物品都将在"tfcmaid"命名空间下注册
    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, MODID);
    // 创建一个延迟注册器来管理创造模式标签页，所有标签页都将在"tfcmaid"命名空间下注册
    public static final DeferredRegister<CreativeModeTab> CREATIVE_MODE_TABS = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, MODID);

    // 注册调试工具物品
    public static final RegistryObject<Item> FARM_DEBUG_WAND = ITEMS.register("farm_debug_wand", () ->
            new FarmDebugWand(new Item.Properties().stacksTo(1)));

    public Tfcmaid() {
        // 初始化任务配置管理器
        TaskConfigManager.initialize();
        // 初始化喂养配置管理器
        FeedConfigManager.initialize();
        // 初始化杂草配置管理器
        WeedConfigManager.initialize();
        // 注册延迟注册器
        var modEventBus = FMLJavaModLoadingContext.get().getModEventBus();
        BLOCKS.register(modEventBus);
        ITEMS.register(modEventBus);
        CREATIVE_MODE_TABS.register(modEventBus);
    }

    // SubscribeEvent 注解，让事件总线自动发现并调用方法
    @SubscribeEvent
    public void onServerStarting(ServerStartingEvent event) {
        // 在服务器启动时执行操作
        LOGGER.info("HELLO from server starting");
    }

    // 使用 EventBusSubscriber 自动注册类中所有使用 @SubscribeEvent 注解的静态方法
    @Mod.EventBusSubscriber(modid = MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
    public static class ClientModEvents {

        @SubscribeEvent
        public static void onClientSetup(FMLClientSetupEvent event) {
            // 客户端设置，在客户端初始化时执行
            LOGGER.info("HELLO FROM CLIENT SETUP");
            LOGGER.info("你好，群峦");
            LOGGER.info("MINECRAFT NAME >> {}", Minecraft.getInstance().getUser().getName());
        }
    }
}
