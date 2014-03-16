package nl.dykam.dev.pvptimeout;

import com.sk89q.worldguard.bukkit.WorldGuardPlugin;
import com.sk89q.worldguard.protection.ApplicableRegionSet;
import com.sk89q.worldguard.protection.flags.IntegerFlag;
import com.sk89q.worldguard.protection.flags.RegionGroup;
import net.milkbowl.vault.permission.Permission;
import org.bukkit.Bukkit;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.*;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.PotionSplashEvent;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;
import com.mewin.WGCustomFlags.*;

import java.util.*;

public class PvpTimeoutPlugin extends JavaPlugin implements Listener {
    private Permission permission;
    private WGCustomFlagsPlugin customFlagsPlugin;
    Map<String, Data> dataStorage;
    GlobalTick tick;

    public static final IntegerFlag PVP_TIMEOUT = new IntegerFlag("pvp-timeout", RegionGroup.ALL);
    private WorldGuardPlugin worldGuard;

    @Override
    public void onEnable() {
        tick = new GlobalTick(this);
        permission = setupPermission();
        if (permission == null) {
            getLogger().severe("No Vault-compatible permissions plugin found, disabling!");
            Bukkit.getPluginManager().disablePlugin(this);
        }
        customFlagsPlugin = setupCustomFlags();
        customFlagsPlugin.addCustomFlag(PVP_TIMEOUT);

        worldGuard = WorldGuardPlugin.inst();

        dataStorage = new HashMap<>();

        getConfig().options().copyHeader(true);
        getConfig().options().copyDefaults(true);
        Bukkit.getPluginManager().registerEvents(this, this);
    }

    @EventHandler(priority = EventPriority.HIGH)
    private void onEntityDamageByEntity(EntityDamageByEntityEvent event) {
        Entity damager = event.getDamager();

        if(damager instanceof Projectile)
            damager = ((Projectile)damager).getShooter();

        if (!(damager instanceof Player) || !(event.getEntity() instanceof Player))
            return;

        final Player attacker = (Player) damager;
        final Player attacked = (Player) event.getEntity();

        if(handleAttack(attacker, attacked))
            event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGH)
    private void onPotionSplash(PotionSplashEvent event) {
        LivingEntity damager = event.getEntity().getShooter();
        if (!(damager instanceof Player))
            return;
        final Player attacker = (Player) damager;
        List<Player> toCancel = new ArrayList<>();
        for (Iterator<LivingEntity> iterator = event.getAffectedEntities().iterator(); iterator.hasNext(); ) {
            LivingEntity livingEntity = iterator.next();
            if (!(livingEntity instanceof Player))
                return;
            final Player attacked = (Player) livingEntity;

            if (handleAttack(attacker, attacked))
                toCancel.add(attacked);
        }
        for (Player player : toCancel) {
            event.setIntensity(player, 0);
        }


    }

    /**
     * @return Whether cancellable was cancelled.
     */
    private boolean handleAttack(Player attacker, Player attacked) {
        if(attacker.hasPermission("pvptimeout.bypass.attacker"))
            return false;
        if(attacked.hasPermission("pvptimeout.bypass.attacked"))
            return false;

        Integer attackedTimeout = getTimeout(attacked);
        Integer attackerTimeout = getTimeout(attacker);
        @SuppressWarnings("RedundantCast") // Type not redundant, influences the type of the full expression
        Integer timeout = attackedTimeout == null ? attackerTimeout
                : attackerTimeout == null ? attackedTimeout
                : (Integer)Math.max(attackedTimeout, attackerTimeout);

        String attackerName = attacker.getName();
        String attackedName = attacked.getName();
        Long lastAttackTick = this.dataStorage.get(attackedName);
        long currentTick = (long) tick.getTick();
        if (timeout == null) {
            dataStorage.put(attackerName, currentTick);
        } else if (lastAttackTick == null || lastAttackTick <= currentTick - timeout){
            return true;
        }
        return false;
    }

    private Data get(Player player) {
        String name = player.getName();
        Data data = dataStorage.get(name);
        if(data != null) return data;
        data = new Data();
        dataStorage.put(name, data);
        return data;
    }

    private Integer getTimeout(Player attacked) {
        ApplicableRegionSet applicableRegions = worldGuard.getRegionManager(attacked.getWorld()).getApplicableRegions(attacked.getLocation());
        return applicableRegions.getFlag(PVP_TIMEOUT, WorldGuardPlugin.inst().wrapPlayer(attacked));
    }

    public Permission setupPermission() {
        RegisteredServiceProvider<Permission> registration = Bukkit.getServicesManager().getRegistration(Permission.class);
        return registration != null ? registration.getProvider() : null;
    }

    public WGCustomFlagsPlugin setupCustomFlags()
    {
        Plugin plugin = getServer().getPluginManager().getPlugin("WGCustomFlags");

        return plugin instanceof WGCustomFlagsPlugin ? (WGCustomFlagsPlugin) plugin : null;

    }

    private class Data {
    }
}
