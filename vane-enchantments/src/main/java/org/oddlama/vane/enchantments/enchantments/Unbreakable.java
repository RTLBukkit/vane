package org.oddlama.vane.enchantments.enchantments;

import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.player.PlayerItemDamageEvent;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ShapedRecipe;
import org.bukkit.inventory.meta.EnchantmentStorageMeta;
import org.bukkit.loot.LootTables;
import org.oddlama.vane.annotation.enchantment.Rarity;
import org.oddlama.vane.annotation.enchantment.VaneEnchantment;
import org.oddlama.vane.core.LootTable.LootTableEntry;
import org.oddlama.vane.core.item.CustomItem;
import org.oddlama.vane.core.module.Context;
import org.oddlama.vane.enchantments.CustomEnchantment;
import org.oddlama.vane.enchantments.Enchantments;
import org.oddlama.vane.enchantments.items.AncientTomeOfTheGods;
import org.oddlama.vane.enchantments.items.BookVariant;

@VaneEnchantment(name = "unbreakable", rarity = Rarity.RARE, treasure = true, allow_custom = true)
public class Unbreakable extends CustomEnchantment<Enchantments> {

	public Unbreakable(Context<Enchantments> context) {
		super(context);
	}

	@Override
	public void register_superseding() {
		supersedes(Enchantment.DURABILITY);
		supersedes(Enchantment.MENDING);
	}

	@Override
	public void register_recipes() {
		final var ancient_tome_of_the_gods_enchanted = CustomItem
			.<AncientTomeOfTheGods.AncientTomeOfTheGodsVariant>variant_of(
				AncientTomeOfTheGods.class,
				BookVariant.ENCHANTED_BOOK
			)
			.item();
		final var ancient_tome_of_the_gods = CustomItem
			.<AncientTomeOfTheGods.AncientTomeOfTheGodsVariant>variant_of(AncientTomeOfTheGods.class, BookVariant.BOOK)
			.item();

		final var recipe_key = recipe_key();
		final var item = ancient_tome_of_the_gods_enchanted.clone();
		final var meta = (EnchantmentStorageMeta) item.getItemMeta();
		meta.addStoredEnchant(bukkit(), 1, false);
		item.setItemMeta(meta);
		get_module().update_enchanted_item(item);

		final var recipe = new ShapedRecipe(recipe_key, item)
			.shape("waw", "nbn", "tst")
			.setIngredient('b', ancient_tome_of_the_gods)
			.setIngredient('w', Material.WITHER_ROSE)
			.setIngredient('a', Material.ENCHANTED_GOLDEN_APPLE)
			.setIngredient('n', Material.NETHERITE_INGOT)
			.setIngredient('t', Material.TOTEM_OF_UNDYING)
			.setIngredient('s', Material.NETHER_STAR);

		add_recipe(recipe);

		// Loot generation
		get_module().loot_table(LootTables.ABANDONED_MINESHAFT).put(recipe_key, new LootTableEntry(120, item));
		get_module().loot_table(LootTables.BASTION_TREASURE).put(recipe_key, new LootTableEntry(30, item));
	}

	@EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = false)
	public void on_player_item_damage(final PlayerItemDamageEvent event) {
		// Check enchantment
		final var item = event.getItem();
		if (item.getEnchantmentLevel(this.bukkit()) == 0) {
			return;
		}

		// Set item unbreakable to prevent further event calls
		final var meta = item.getItemMeta();
		meta.setUnbreakable(true);
		// Also hide the internal unbreakable tag on the client
		meta.addItemFlags(ItemFlag.HIDE_UNBREAKABLE);
		item.setItemMeta(meta);

		// Prevent damage
		event.setDamage(0);
		event.setCancelled(true);
	}
}
