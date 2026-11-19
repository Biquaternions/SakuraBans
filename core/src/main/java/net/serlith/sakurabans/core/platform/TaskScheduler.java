package net.serlith.sakurabans.core.platform;

import java.util.UUID;

public interface TaskScheduler {

    void runGlobal(Runnable task);

    void runForPlayer(UUID player, Runnable task);

}
