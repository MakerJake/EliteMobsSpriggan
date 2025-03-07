package com.magmaguy.elitemobs.mobconstructor.custombosses;

import com.magmaguy.elitemobs.ChatColorConverter;
import com.magmaguy.elitemobs.MetadataHandler;
import com.magmaguy.elitemobs.api.internal.RemovalReason;
import com.magmaguy.elitemobs.entitytracker.EntityTracker;
import com.magmaguy.elitemobs.thirdparty.discordsrv.DiscordSRVAnnouncement;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

public class CustomBossEscapeMechanism {
    public static Integer startEscape(int timeout, CustomBossEntity customBossEntity) {
        if (timeout < 1) return null;
        return Bukkit.getScheduler().scheduleSyncDelayedTask(MetadataHandler.PLUGIN, () -> {
            doEscapeMessage(customBossEntity);
        }, 20L * 60L * timeout);
    }

    public static void doEscapeMessage(CustomBossEntity customBossEntity){
        if (customBossEntity.customBossesConfigFields.getAnnouncementPriority() < 1) return;
        if (customBossEntity.customBossesConfigFields.getEscapeMessage() != null)
            for (Player player : Bukkit.getOnlinePlayers())
                if (player.getWorld().equals(customBossEntity.getLocation().getWorld()))
                    player.sendMessage(ChatColorConverter.convert(customBossEntity.customBossesConfigFields.getEscapeMessage()));
        if (customBossEntity.customBossesConfigFields.getAnnouncementPriority() < 3) return;
        new DiscordSRVAnnouncement(ChatColorConverter.convert(customBossEntity.customBossesConfigFields.getEscapeMessage()));
        if (customBossEntity.getUnsyncedLivingEntity() != null)
            EntityTracker.unregister(customBossEntity.getUnsyncedLivingEntity().getUniqueId(), RemovalReason.BOSS_TIMEOUT);
    }
}
