package nl.dykam.dev.pvptimeout;

import com.sk89q.worldguard.bukkit.WorldGuardPlugin;
import com.sk89q.worldguard.protection.ApplicableRegionSet;
import com.sk89q.worldguard.protection.flags.IntegerFlag;
import com.sk89q.worldguard.protection.flags.RegionGroup;
import org.bukkit.*;
import org.bukkit.entity.*;
import org.bukkit.event.*;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.PotionSplashEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;
import com.mewin.WGCustomFlags.*;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Score;
import org.bukkit.scoreboard.Scoreboard;

import java.lang.ref.WeakReference;
import java.util.*;

public class PvpTimeoutPlugin extends JavaPlugin implements Listener {
    public OfflinePlayer timeLeftPlayer;
    Map<String, Data> dataStorage;
    GlobalTick tick;
    Set<PotionEffectType> damagingEffects;

    public static final IntegerFlag PVP_TIMEOUT = new IntegerFlag("pvp-timeout", RegionGroup.ALL);
    private WorldGuardPlugin worldGuard;

    public PvpTimeoutPlugin() {
        damagingEffects = new HashSet<>();
        damagingEffects.add(PotionEffectType.BLINDNESS);
        damagingEffects.add(PotionEffectType.CONFUSION);
        damagingEffects.add(PotionEffectType.HARM);
        damagingEffects.add(PotionEffectType.HUNGER);
        damagingEffects.add(PotionEffectType.POISON);
        damagingEffects.add(PotionEffectType.SLOW);
        damagingEffects.add(PotionEffectType.SLOW_DIGGING);
        damagingEffects.add(PotionEffectType.WEAKNESS);
        damagingEffects.add(PotionEffectType.WITHER);
    }

    @Override
    public void onEnable() {
        tick = new GlobalTick(this);
        tick.addTickListener(new Ticker());
        WGCustomFlagsPlugin customFlagsPlugin = setupCustomFlags();
        customFlagsPlugin.addCustomFlag(PVP_TIMEOUT);

        worldGuard = WorldGuardPlugin.inst();

        dataStorage = new HashMap<>();

        reloadConfig();
        getConfig().options().copyHeader(true);
        getConfig().options().copyDefaults(true);
        saveConfig();
        Bukkit.getPluginManager().registerEvents(this, this);

        String string = getConfig().getString("text.prefix");
        timeLeftPlayer = Bukkit.getOfflinePlayer(ChatColor.translateAlternateColorCodes('&', string));
    }

    @Override
    public void onDisable() {
        for (Data data : dataStorage.values()) {
            data.objective.setDisplaySlot(null);
        }
        dataStorage.clear();
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
        ThrownPotion potion = event.getPotion();
        LivingEntity damager = potion.getShooter();
        if (!(damager instanceof Player)) return;
        boolean damaging = isDamaging(potion);

        final Player attacker = (Player) damager;
        List<Player> toCancel = new ArrayList<>();
        for (LivingEntity livingEntity : event.getAffectedEntities()) {
            if (!(livingEntity instanceof Player))
                return;
            final Player attacked = (Player) livingEntity;

            Situation situation = determineSituation(attacker, attacked);
            if (handleAttack(attacker, attacked))
                toCancel.add(attacked);
        }
        for (Player player : toCancel) {
            event.setIntensity(player, 0);
        }
    }

    private boolean isDamaging(ThrownPotion potion) {
        for (PotionEffect potionEffect : potion.getEffects()) {
            if(this.damagingEffects.contains(potionEffect.getType())) {
                return true;
            }
        }
        return false;
    }

    @EventHandler
    private void onQuit(PlayerQuitEvent event) {
        dataStorage.remove(event.getPlayer().getName());
    }

    private void handleUpdateScoreboard(Player player) {
        Data data = get(player);
        Integer timeout = getTimeout(player);
        if (timeout == null || data.lastAttackTick == null) {
            data.objective.setDisplaySlot(null);
            return;
        }
        int timeLeft = (int)(timeout - tick.getTick() + data.lastAttackTick);
        if(timeLeft <= 0) {
            if(timeLeft == 0) {
                player.playSound(player.getLocation(), Sound.NOTE_PIANO, 1f, 2.0f);
            }
            data.objective.setDisplaySlot(null);
            data.lastAttackTick = null;
            return;
        }
        if(timeLeft % 20 == 0) {
            player.playSound(player.getLocation(), Sound.NOTE_PIANO, 1f, 1.3333334f);
        }
        data.score.setScore((int) Math.ceil(timeLeft / 20f));
        data.objective.setDisplaySlot(DisplaySlot.SIDEBAR);
    }

    /**
     * @return Whether the person is attackable or not
     */
    private boolean handleAttack(Player attacker, Player attacked) {
        long currentTick = (long) tick.getTick();
        switch (determineSituation(attacker, attacked)) {
            case TAG:
                Data attackerData = get(attacker);
                attackerData.lastAttackTick = currentTick;
            case NONE:
                return false;
            case TAGGED:
                return true;
        }
        return false;
    }

    private Situation determineSituation(Player attacker, Player attacked) {
        if(attacker.hasPermission("pvptimeout.bypass.attacker"))
            return Situation.NONE;
        if(attacked.hasPermission("pvptimeout.bypass.attacked"))
            return Situation.NONE;

        Integer attackedTimeout = getTimeout(attacked);
        Integer attackerTimeout = getTimeout(attacker);
        int attackerTimeoutInt = attackerTimeout == null ? 0 : attackerTimeout;

        Data attackedData = get(attacked);
        Long lastAttackTick = attackedData.lastAttackTick;
        long currentTick = (long) tick.getTick();
        if (attackedTimeout == null && attackerTimeout == null)
            return Situation.TAG;
        else if (lastAttackTick == null || lastAttackTick <= currentTick - attackerTimeoutInt)
            return Situation.TAGGED;
        return Situation.NONE;
    }

    private Data get(Player player) {
        String name = player.getName();
        Data data = dataStorage.get(name);
        if(data != null)
            return data;
        data = new Data();
        data.scoreboard = Bukkit.getScoreboardManager().getNewScoreboard();
        player.setScoreboard(data.scoreboard);
        Objective objective = data.scoreboard.registerNewObjective("timeleft", "dummy");
        objective.setDisplayName(ChatColor.translateAlternateColorCodes('&', getConfig().getString("text.title")));
        objective.setDisplaySlot(DisplaySlot.SIDEBAR);
        data.objective = objective;
        data.score = objective.getScore(timeLeftPlayer);
        data.player = new WeakReference<>(player);
        dataStorage.put(name, data);
        return data;
    }

    private Integer getTimeout(Player player) {
        ApplicableRegionSet applicableRegions = worldGuard.getRegionManager(player.getWorld()).getApplicableRegions(player.getLocation());
        return applicableRegions.getFlag(PVP_TIMEOUT, WorldGuardPlugin.inst().wrapPlayer(player));
    }

    public WGCustomFlagsPlugin setupCustomFlags()
    {
        Plugin plugin = getServer().getPluginManager().getPlugin("WGCustomFlags");

        return plugin instanceof WGCustomFlagsPlugin ? (WGCustomFlagsPlugin) plugin : null;

    }

    private class Data {
        public Long lastAttackTick;
        public Scoreboard scoreboard;
        public Objective objective;
        public Score score;
        public WeakReference<Player> player;
    }

    private class Ticker implements Runnable {
        @Override
        public void run() {
            for (Data data : dataStorage.values()) {
                if(data.lastAttackTick == null)
                    continue;
                handleUpdateScoreboard(data.player.get());
            }

        }
    }
}
