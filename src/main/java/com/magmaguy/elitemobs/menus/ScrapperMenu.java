package com.magmaguy.elitemobs.menus;

import com.magmaguy.elitemobs.ChatColorConverter;
import com.magmaguy.elitemobs.api.EliteMobsItemDetector;
import com.magmaguy.elitemobs.config.ConfigValues;
import com.magmaguy.elitemobs.config.TranslationConfig;
import com.magmaguy.elitemobs.config.menus.premade.ScrapperMenuConfig;
import com.magmaguy.elitemobs.config.menus.premade.SellMenuConfig;
import com.magmaguy.elitemobs.items.ItemTierFinder;
import com.magmaguy.elitemobs.items.customenchantments.SoulbindEnchantment;
import com.magmaguy.elitemobs.items.itemconstructor.ItemConstructor;
import com.magmaguy.elitemobs.utils.ItemStackGenerator;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

public class ScrapperMenu extends EliteMenu {
    private static final List<Integer> validSlots = ScrapperMenuConfig.storeSlots;

    public static HashMap<Player, Inventory> inventories = new HashMap<>();

    /**
     * Creates a menu for scrapping elitemobs items. Only special Elite Mob items can be scrapped here.
     *
     * @param player Player for whom the inventory will be created
     */
    public void constructScrapMenu(Player player) {

        Inventory scrapInventory = Bukkit.createInventory(player, 54, ScrapperMenuConfig.shopName);

        for (int i = 0; i < 54; i++) {

            if (i == ScrapperMenuConfig.infoSlot) {
                scrapInventory.setItem(i, ScrapperMenuConfig.infoButton);
                continue;
            }

            if (i == ScrapperMenuConfig.cancelSlot) {
                scrapInventory.setItem(i, ScrapperMenuConfig.cancelButton);
                continue;
            }

            if (i == ScrapperMenuConfig.confirmSlot) {

                ItemStack clonedConfirmButton = ScrapperMenuConfig.confirmButton.clone();

                List<String> lore = new ArrayList<>();
                for (String string : ScrapperMenuConfig.confirmButton.getItemMeta().getLore())
                    lore.add(string);
                ScrapperMenuConfig.confirmButton.getItemMeta().setLore(lore);
                ItemMeta clonedMeta = clonedConfirmButton.getItemMeta();
                clonedMeta.setLore(lore);
                clonedConfirmButton.setItemMeta(clonedMeta);
                scrapInventory.setItem(i, clonedConfirmButton);
                continue;

            }

            if (validSlots.contains(i))
                continue;

            scrapInventory.setItem(i, ItemStackGenerator.generateItemStack(Material.GLASS_PANE));

        }

        player.openInventory(scrapInventory);
        createEliteMenu(player, scrapInventory, inventories);

    }

    public static class ScrapperMenuEvents implements Listener {
        @EventHandler(ignoreCancelled = true)
        public void onInventoryInteract(InventoryClickEvent event) {
            if (!isEliteMenu(event, inventories)) return;
            event.setCancelled(true);

            Player player = (Player) event.getWhoClicked();
            ItemStack currentItem = event.getCurrentItem();
            Inventory shopInventory = event.getView().getTopInventory();
            Inventory playerInventory = event.getView().getBottomInventory();

            if (EliteMenu.isBottomMenu(event)) {
                //CASE: If the player clicked on something in their inventory to put it on the shop

                //Check if it's an elitemobs item. The soulbind check only says if the player would be able to pick it up, and vanilla items can be picked up
                if (!EliteMobsItemDetector.isEliteMobsItem(event.getCurrentItem())) {
                    event.getWhoClicked().sendMessage(ChatColorConverter.convert(ConfigValues.translationConfig.getString(TranslationConfig.SHOP_SALE_INSTRUCTIONS)));
                    return;
                }

                //If the item isn't soulbound to the player, it can't be sold by that player
                if (!SoulbindEnchantment.isValidSoulbindUser(currentItem.getItemMeta(), player)) {
                    player.sendMessage(ChatColorConverter.convert(ConfigValues.translationConfig.getString(TranslationConfig.SHOP_SALE_OTHERS_ITEMS)));
                    return;
                }

                //Do transfer
                for (int slot : ScrapperMenuConfig.storeSlots)
                    if (shopInventory.getItem(slot) == null) {
                        shopInventory.setItem(slot, currentItem);
                        playerInventory.clear(event.getSlot());
                        break;
                    }

            } else if (EliteMenu.isTopMenu(event)) {
                //CASE: Player clicked on the shop

                //Signature item, does nothing
                if (currentItem.equals(SellMenuConfig.infoButton))
                    return;

                //sell items in shop
                if (event.getSlot() == SellMenuConfig.confirmSlot) {

                    for (Integer validSlot : validSlots) {
                        ItemStack itemStack = shopInventory.getItem(validSlot);
                        if (itemStack == null)
                            continue;
                        int tier = ItemTierFinder.findBattleTier(itemStack);
                        for (int i = 0; i < itemStack.getAmount(); i++) {
                            if (ThreadLocalRandom.current().nextDouble() > 0.5) {
                                player.sendMessage(ChatColorConverter.convert("Scrap failed!"));
                                continue;
                            }
                            player.getInventory().addItem(ItemConstructor.constructScrapItem(tier, player, false));
                            player.sendMessage(ChatColorConverter.convert("Scrap succeeded!"));
                        }
                        itemStack.setAmount(0);
                    }

                    return;
                }

                //cancel, transfer items back to player inv and exit
                if (event.getSlot() == SellMenuConfig.cancelSlot) {
                    player.closeInventory();
                    return;
                }

                //If player clicks on a border glass pane, do nothing
                if (!validSlots.contains(event.getSlot())) return;


                //If player clicks on one of the items already in the shop, return to their inventory
                playerInventory.addItem(event.getCurrentItem());
                shopInventory.clear(event.getSlot());

            }

        }

        @EventHandler
        public void onInventoryClose(InventoryCloseEvent event) {
            if (!EliteMenu.onInventoryClose(event, inventories)) return;
            EliteMenu.cancel(event.getView().getTopInventory(), event.getView().getBottomInventory(), validSlots);
        }

    }

}
