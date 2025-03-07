package com.magmaguy.elitemobs.treasurechest;

import com.google.common.collect.ArrayListMultimap;
import com.magmaguy.elitemobs.ChatColorConverter;
import com.magmaguy.elitemobs.MetadataHandler;
import com.magmaguy.elitemobs.adventurersguild.GuildRank;
import com.magmaguy.elitemobs.api.internal.RemovalReason;
import com.magmaguy.elitemobs.config.customtreasurechests.CustomTreasureChestConfigFields;
import com.magmaguy.elitemobs.entitytracker.EntityTracker;
import com.magmaguy.elitemobs.items.customitems.CustomItem;
import com.magmaguy.elitemobs.mobconstructor.custombosses.CustomBossEntity;
import com.magmaguy.elitemobs.powerstances.VisualItemInitializer;
import com.magmaguy.elitemobs.utils.*;
import lombok.Getter;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.block.data.type.Chest;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.event.world.ChunkUnloadEvent;
import org.bukkit.event.world.WorldLoadEvent;
import org.bukkit.event.world.WorldUnloadEvent;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.time.Instant;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

public class TreasureChest {

    @Getter
    private static ArrayListMultimap<String, TreasureChest> unloadedChests = ArrayListMultimap.create();
    private static HashMap<Location, TreasureChest> treasureChestHashMap = new HashMap<>();
    private final CustomTreasureChestConfigFields customTreasureChestConfigFields;
    public boolean chunkIsLoaded = true;
    public boolean effectIsOn = true;
    private long restockTime;
    private Location location;

    public TreasureChest(CustomTreasureChestConfigFields customTreasureChestConfigFields, Location location, long restockTime) {
        this.customTreasureChestConfigFields = customTreasureChestConfigFields;
        this.location = location;
        this.restockTime = restockTime;
        if (!customTreasureChestConfigFields.isEnabled())
            return;

        if (customTreasureChestConfigFields.getChestMaterial() == null)
            return;

        if (location == null)
            return;

        if (location.getWorld() == null) {
            unloadedChests.put(customTreasureChestConfigFields.getWorldName(), this);
            return;
        } else
            try {
                location.getChunk().load();
            } catch (Exception ex) {
                new InfoMessage("Location for treasure chest " + customTreasureChestConfigFields.getFilename() + " is not loaded, so a treasure chest will not be placed!");
                new WarningMessage(ex.getMessage());
                return;
            }

        generateChest();
        treasureChestHashMap.put(location, this);

    }

    public static HashMap<Location, TreasureChest> getTreasureChestHashMap() {
        return treasureChestHashMap;
    }

    public static TreasureChest getTreasureChest(Location location) {
        return getTreasureChestHashMap().get(location);
    }

    private void generateChest() {
        try {
            if (!location.getWorld()
                    .getBlockAt(location).getType().equals(customTreasureChestConfigFields.getChestMaterial()))
                location.getWorld().getBlockAt(location).setType(customTreasureChestConfigFields.getChestMaterial());
        } catch (Exception ex) {
            new WarningMessage("Custom Treasure Chest " + customTreasureChestConfigFields.getFilename() + " has an invalid location and can not be placed.");
            return;
        }
        //todo: this doesn't support non- chest block types like the ender chest
        Chest chest = (Chest) location.getBlock().getBlockData();
        chest.setFacing(customTreasureChestConfigFields.getFacing());
        location.getBlock().setBlockData(chest);
        location.getBlock().getState().update();

        startEffects();
    }

    public void doInteraction(Player player) {

        if (customTreasureChestConfigFields.getDropStyle().equals(DropStyle.GROUP))
            if (playerIsInCooldown(player)) {
                groupTimerCooldownMessage(player, getPlayerCooldown(player));
                return;
            }

        if (ThreadLocalRandom.current().nextDouble() < customTreasureChestConfigFields.getMimicChance())
            doMimic();
        else
            doTreasure(player);

        if (customTreasureChestConfigFields.getDropStyle().equals(DropStyle.GROUP)) {
            customTreasureChestConfigFields.getRestockTimers().add(cooldownStringConstructor(player));
            new BukkitRunnable() {
                @Override
                public void run() {
                    customTreasureChestConfigFields.getRestockTimers().removeIf(restockTime -> restockTime.split(":")[0].equals(player.getUniqueId().toString()));
                }
            }.runTaskLater(MetadataHandler.PLUGIN, 20L * 60 * customTreasureChestConfigFields.getRestockTimer());
            return;
        }

        location.getBlock().setType(Material.AIR);

        restockTime = cooldownTime();
        customTreasureChestConfigFields.setRestockTime(location, restockTime);
        effectIsOn = false;
        new BukkitRunnable() {
            @Override
            public void run() {
                generateChest();
                effectIsOn = true;
                startEffects();
            }
        }.runTaskLater(MetadataHandler.PLUGIN, 20L * 60 * this.customTreasureChestConfigFields.getRestockTimer());

    }

    private void doMimic() {
        HashMap<String, Double> weighedValues = new HashMap<>();
        for (String string : this.customTreasureChestConfigFields.getMimicCustomBossesList()) {
            String filename = string.split(":")[0];
            double weight = 1;
            try {
                weight = Double.valueOf(string.split(":")[1]);
            } catch (Exception ex) {
                weight = 1;
            }
            weighedValues.put(filename, weight);
        }
        CustomBossEntity customBossEntity = CustomBossEntity.createCustomBossEntity(WeightedProbability.pickWeighedProbability(weighedValues));
        customBossEntity.spawn(location, randomizeTier(), false);
    }

    private void doTreasure(Player player) {
        for (String string : this.customTreasureChestConfigFields.getLootList())
            try {
                String filename = string.split(":")[0];
                double odds;
                if (customTreasureChestConfigFields.getSpecialLootList().get(string) != null) {
                    odds = customTreasureChestConfigFields.getSpecialLootList().get(string).getChance();
                } else
                    odds = Double.valueOf(string.split(":")[1]);
                if (ThreadLocalRandom.current().nextDouble() < odds)
                    if (customTreasureChestConfigFields.getSpecialLootList().get(string) != null) {
                        location.getWorld().dropItem(location, customTreasureChestConfigFields.getSpecialLootList().
                                get(string).generateItemStack(player, customTreasureChestConfigFields.getChestTier() * 10, (customTreasureChestConfigFields.getChestTier() + 1) * 10));
                    } else
                        CustomItem.dropPlayerLoot(player, randomizeTier(), filename, location);
            } catch (Exception ex) {
                new WarningMessage("Malformed loot entry for " + this.customTreasureChestConfigFields.getFile() + " !");
                new WarningMessage("Entry: " + string);
                new WarningMessage("Correct format: filename.yml:odds");
                ex.printStackTrace();
            }
    }

    private int randomizeTier() {
        return customTreasureChestConfigFields.getChestTier() * 10 + ThreadLocalRandom.current().nextInt(11);
    }

    private void lowRankMessage(Player player) {
        //todo: fix treasure chests to incorporate prestige ranks into them
        player.sendMessage(ChatColorConverter.convert("&7[EM] &cYour guild rank needs to be " + GuildRank.getRankName(0, customTreasureChestConfigFields.getChestTier())
                + " &cin order to open this chest!"));
    }

    private void groupTimerCooldownMessage(Player player, long targetTime) {
        player.sendMessage(ChatColorConverter.convert("&7[EM] &cYou've already opened this chest recently! Wait "
                + timeConverter(targetTime - Instant.now().getEpochSecond()) + "!"));
    }

    private boolean playerIsInCooldown(Player player) {
        for (String string : customTreasureChestConfigFields.getRestockTimers())
            if (string.split(":")[0].equals(player.getUniqueId().toString()))
                return true;
        return false;
    }

    private long getPlayerCooldown(Player player) {
        for (String string : customTreasureChestConfigFields.getRestockTimers())
            if (string.split(":")[0].equals(player.getUniqueId().toString()))
                return Long.parseLong(string.split(":")[1]);
        return 0;
    }

    private String cooldownStringConstructor(Player player) {
        return player.getUniqueId() + ":" + cooldownTime();
    }

    private long cooldownTime() {
        return Instant.now().getEpochSecond() + 60L * this.customTreasureChestConfigFields.getRestockTimer();
    }

    private String timeConverter(long seconds) {
        if (seconds < 60 * 2)
            return seconds + " seconds";
        if (seconds < 60 * 60 * 2)
            return Round.twoDecimalPlaces(seconds / 60) + "minutes";
        if (seconds < 60 * 60 * 48)
            return Round.twoDecimalPlaces(seconds / 60 / 60) + "hours";
        else
            return Round.twoDecimalPlaces(seconds / 60 / 60 / 48) + "days";
    }

    public void startEffects() {
        for (String string : this.customTreasureChestConfigFields.getEffects()) {
            try {
                Particle particle = Particle.valueOf(string);
                doParticleTrail(particle);
            } catch (Exception ex) {
            }
            try {
                Material material = Material.valueOf(string);
                doItemTrail(material);
            } catch (Exception ex) {
            }
        }
    }

    private void doParticleTrail(Particle particle) {
        effectIsOn = true;
        new BukkitRunnable() {
            @Override
            public void run() {
                //In case of chunk unload, stop the effect
                if (!chunkIsLoaded || !effectIsOn) {
                    cancel();
                    effectIsOn = false;
                    return;
                }
                //All conditions cleared, do the particle effect
                location.getWorld().spawnParticle(particle, location.clone().add(new Vector(0.5, 0.5, 0.5)), 1, 0.1, 0.1, 0.1, 0.05);
            }
        }.runTaskTimer(MetadataHandler.PLUGIN, 0, 1);
    }

    private void doItemTrail(Material material) {
        effectIsOn = true;
        new BukkitRunnable() {

            @Override
            public void run() {
                //In case of chunk unload, stop the effect
                if (!chunkIsLoaded || !effectIsOn) {
                    cancel();
                    effectIsOn = false;
                    return;
                }

                //All conditions cleared, do the boss flair effect
                Item item = VisualItemInitializer.initializeItem(ItemStackGenerator.generateItemStack
                        (material, "visualItem", Arrays.asList(ThreadLocalRandom.current().nextDouble() + "")), location.clone().add(new Vector(0.5, 0.5, 0.5)));
                item.setVelocity(new Vector(
                        ThreadLocalRandom.current().nextDouble() / 5 - 0.10,
                        ThreadLocalRandom.current().nextDouble() / 5 - 0.10,
                        ThreadLocalRandom.current().nextDouble() / 5 - 0.10));
                new BukkitRunnable() {
                    @Override
                    public void run() {
                        item.remove();
                        EntityTracker.wipeEntity(item, RemovalReason.EFFECT_TIMEOUT);
                    }
                }.runTaskLater(MetadataHandler.PLUGIN, 20);

            }
        }.runTaskTimer(MetadataHandler.PLUGIN, 0, 1);
    }

    public enum DropStyle {
        SINGLE,
        GROUP
    }

    public static class TreasureChestEvents implements Listener {
        @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
        public void onPlayerInteract(PlayerInteractEvent event) {
            if (event.getClickedBlock() == null) return;
            TreasureChest treasureChest = getTreasureChest(event.getClickedBlock().getLocation());
            if (treasureChest == null) return;
            event.setCancelled(true);
            if (GuildRank.getMaxGuildRank(event.getPlayer()) < treasureChest.customTreasureChestConfigFields.getChestTier())
                treasureChest.lowRankMessage(event.getPlayer());
            else
                treasureChest.doInteraction(event.getPlayer());
        }

        @EventHandler
        public void onChunkUnload(ChunkUnloadEvent event) {
            for (TreasureChest treasureChest : getTreasureChestHashMap().values())
                if (ChunkLocationChecker.chunkLocationCheck(treasureChest.location, event.getChunk()))
                    treasureChest.effectIsOn = false;
        }

        @EventHandler
        public void onChunkLoad(ChunkLoadEvent event) {
            for (TreasureChest treasureChest : getTreasureChestHashMap().values())
                if (ChunkLocationChecker.chunkLocationCheck(treasureChest.location, event.getChunk())) {
                    treasureChest.effectIsOn = true;
                    treasureChest.startEffects();
                }
        }

        @EventHandler
        public void onWorldLoad(WorldLoadEvent event) {
            for (TreasureChest treasureChest : getUnloadedChests().get(event.getWorld().getName())) {
                treasureChest.location.setWorld(event.getWorld());
                treasureChest.generateChest();
                treasureChestHashMap.put(treasureChest.location, treasureChest);
            }
            getUnloadedChests().removeAll(event.getWorld().getName());
        }

        @EventHandler
        public void onWorldUnload(WorldUnloadEvent event) {
            for (Iterator<Map.Entry<Location, TreasureChest>> iterator = getTreasureChestHashMap().entrySet().iterator(); iterator.hasNext(); ) {
                Map.Entry<Location, TreasureChest> entry = iterator.next();
                TreasureChest treasureChest = entry.getValue();
                if (treasureChest.customTreasureChestConfigFields.getWorldName().equals(event.getWorld().getName()))
                    treasureChest.effectIsOn = false;
                getUnloadedChests().put(event.getWorld().getName(), treasureChest);
                iterator.remove();
            }
        }

        @EventHandler
        public void onBreak(BlockBreakEvent event) {
            for (TreasureChest treasureChest : treasureChestHashMap.values())
                if (event.getBlock().getLocation().equals(treasureChest.location.getBlock().getLocation()))
                    event.setCancelled(true);
        }

    }

}
