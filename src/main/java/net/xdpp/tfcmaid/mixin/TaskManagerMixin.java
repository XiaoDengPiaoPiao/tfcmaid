package net.xdpp.tfcmaid.mixin;

import com.github.tartaricacid.touhoulittlemaid.api.task.IMaidTask;
import com.github.tartaricacid.touhoulittlemaid.entity.task.TaskManager;
import net.xdpp.tfcmaid.config.TaskConfigManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.HashMap;
import java.util.Map;

/**
 * 女仆任务管理器 Mixin
 * <p>
 * 用于根据配置屏蔽掉与 TFC 模组不兼容的女仆任务
 */
@Mixin(value = TaskManager.class, remap = false)
public abstract class TaskManagerMixin {
    private static final Map<Class<?>, Boolean> BLOCKED_TASK_CACHE = new HashMap<>();
    private static boolean shouldSkipAdd = false;

    private static boolean isTaskClassBlocked(Class<?> taskClass) {
        if (!BLOCKED_TASK_CACHE.containsKey(taskClass)) {
            boolean blocked = TaskConfigManager.isTaskBlocked(taskClass.getSimpleName());
            BLOCKED_TASK_CACHE.put(taskClass, blocked);
            return blocked;
        }
        return BLOCKED_TASK_CACHE.get(taskClass);
    }

    @Inject(method = "init()V", at = @At("HEAD"), remap = false)
    private static void maid_useful_task$before_init(CallbackInfo ci) {
        shouldSkipAdd = true;
        BLOCKED_TASK_CACHE.clear();
    }

    @Inject(method = "init()V", at = @At("RETURN"), remap = false)
    private static void maid_useful_task$after_init(CallbackInfo ci) {
        shouldSkipAdd = false;
    }

    @Inject(method = "add(Lcom/github/tartaricacid/touhoulittlemaid/api/task/IMaidTask;)V", at = @At("HEAD"), cancellable = true, remap = false)
    private void maid_useful_task$add_task(IMaidTask task, CallbackInfo ci) {
        if (shouldSkipAdd && isTaskClassBlocked(task.getClass())) {
            ci.cancel();
        }
    }
}
