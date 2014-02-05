package nl.dykam.dev.pvpgroup;

import net.milkbowl.vault.Vault;
import net.milkbowl.vault.permission.Permission;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.HashMap;
import java.util.Map;

public class PVPGroupPlugin extends JavaPlugin implements Listener {
    Permission perms;
    private Permission permission;
    Map<String, BukkitTask> timers;

    @Override
    public void onEnable() {
        perms = setupPermission();
        if(perms == null) {
            getLogger().severe("No Vault-compatible permissions plugin found, disabling!");
            Bukkit.getPluginManager().disablePlugin(this);
        }

        timers = new HashMap<String, BukkitTask>();

        getConfig().options().copyHeader(true);
        getConfig().options().copyDefaults(true);
        Bukkit.getPluginManager().registerEvents(this, this);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        for (BukkitTask bukkitTask : timers.values()) {
            bukkitTask.cancel();
        }
        timers.clear();

        reloadConfig();
        return true;
    }

    @EventHandler
    private void onPlayerHitPlayer(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Player) || !(event.getEntity() instanceof Player))
            return;

        final Player damager = (Player) event.getDamager();
        if(damager.hasPermission("pvpgroup.bypass"))
            return;

        if(timers.containsKey(damager.getName()))
            timers.get(damager.getName()).cancel();

        perms.playerAddGroup(damager, getConfig().getString("group"));

        timers.put(damager.getName(), Bukkit.getScheduler().runTaskLater(this, new Runnable() {
            @Override
            public void run() {
                perms.playerRemoveGroup(damager, getConfig().getString("group"));
                timers.remove(damager.getName());
            }
        }, getConfig().getInt("time")));
    }

    public Permission setupPermission() {
        RegisteredServiceProvider<Permission> registration = Bukkit.getServicesManager().getRegistration(Permission.class);
        if(registration == null)
            return null;
        return registration.getProvider();
    }
}
