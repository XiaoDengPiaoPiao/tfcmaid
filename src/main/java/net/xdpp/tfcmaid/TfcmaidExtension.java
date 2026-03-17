package net.xdpp.tfcmaid;

import com.github.tartaricacid.touhoulittlemaid.api.ILittleMaid;
import com.github.tartaricacid.touhoulittlemaid.api.LittleMaidExtension;
import com.github.tartaricacid.touhoulittlemaid.entity.task.TaskManager;
import net.xdpp.tfcmaid.task.TaskTFCFeather;
import net.xdpp.tfcmaid.task.TaskTFCFeed;
import net.xdpp.tfcmaid.task.TaskTFCKillOld;
import net.xdpp.tfcmaid.task.TaskTFCMilk;
import net.xdpp.tfcmaid.task.TaskTFCShears;

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
    }
}
