package net.xdpp.tfcmaid;

import com.github.tartaricacid.touhoulittlemaid.api.ILittleMaid;
import com.github.tartaricacid.touhoulittlemaid.api.LittleMaidExtension;
import com.github.tartaricacid.touhoulittlemaid.entity.ai.edible.MaidEdibleBlockManager;
import com.github.tartaricacid.touhoulittlemaid.entity.task.TaskManager;
import net.xdpp.tfcmaid.task.TaskTFCFeather;
import net.xdpp.tfcmaid.task.TaskTFCFeed;
import net.xdpp.tfcmaid.task.TaskTFCKillOld;
import net.xdpp.tfcmaid.task.TaskTFCMilk;
import net.xdpp.tfcmaid.task.TaskTFCShears;
import net.xdpp.tfcmaid.task.TaskTFCNormalCrop;
import net.xdpp.tfcmaid.task.TaskTFCWaterCrop;
import net.xdpp.tfcmaid.task.TaskTFCPickableCrop;
import net.xdpp.tfcmaid.task.TaskTFCSpreadingCrop;
import net.xdpp.tfcmaid.util.TfcCakeEdible;

@LittleMaidExtension
public class TfcmaidExtension implements ILittleMaid {
    @Override
    public void addMaidTask(TaskManager manager) {
        ILittleMaid.super.addMaidTask(manager);
        manager.add(new TaskTFCShears());
        manager.add(new TaskTFCFeather());
        manager.add(new TaskTFCMilk());
        manager.add(new TaskTFCFeed());
        manager.add(new TaskTFCKillOld());
        manager.add(new TaskTFCNormalCrop());
        manager.add(new TaskTFCWaterCrop());
        manager.add(new TaskTFCPickableCrop());
        manager.add(new TaskTFCSpreadingCrop());
    }

    @Override
    public void registerMaidEdibleBlock(MaidEdibleBlockManager manager) {
        ILittleMaid.super.registerMaidEdibleBlock(manager);
        manager.add(new TfcCakeEdible());
    }
}
