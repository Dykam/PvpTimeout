package nl.dykam.dev.pvptimeout;

import org.apache.commons.lang.NullArgumentException;
import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;

import java.util.ArrayList;
import java.util.List;

public class GlobalTick {
    private int tick;
    private List<Runnable> tickListeners;
    public GlobalTick(Plugin plugin) {
        Bukkit.getScheduler().scheduleSyncRepeatingTask(plugin, new Ticker(), 1, 1);
        tickListeners = new ArrayList<>();
    }

    public int getTick() {
        return tick;
    }

    private class Ticker implements Runnable {
        @Override
        public void run() {
            tick++;
            for (Runnable tickListener : tickListeners) {
                try {
                    tickListener.run();
                } catch (Throwable throwable) {
                    throwable.printStackTrace();
                }
            }

        }
    }

    public void addTickListener(Runnable tickListener) {
        if(tickListener == null)
            throw new NullArgumentException("tickListener");
        tickListeners.add(tickListener);
    }
}
