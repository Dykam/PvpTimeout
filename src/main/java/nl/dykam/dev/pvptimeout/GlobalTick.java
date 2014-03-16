package nl.dykam.dev.pvptimeout;

import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;

public class GlobalTick {
    private int tick;
    public GlobalTick(Plugin plugin) {
        Bukkit.getScheduler().scheduleSyncRepeatingTask(plugin, new Ticker(), 1, 1);
    }

    public int getTick() {
        return tick;
    }

    private class Ticker implements Runnable {
        @Override
        public void run() {
            tick++;
        }
    }
}
