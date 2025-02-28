package org.oddlama.vane.enchantments.enchantments;

import com.destroystokyo.paper.MaterialTags;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.block.data.type.Leaves;
import org.bukkit.enchantments.EnchantmentTarget;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.ShapedRecipe;
import org.bukkit.inventory.meta.EnchantmentStorageMeta;
import org.jetbrains.annotations.NotNull;
import org.oddlama.vane.annotation.enchantment.Rarity;
import org.oddlama.vane.annotation.enchantment.VaneEnchantment;
import org.oddlama.vane.core.item.CustomItem;
import org.oddlama.vane.core.module.Context;
import org.oddlama.vane.enchantments.CustomEnchantment;
import org.oddlama.vane.enchantments.Enchantments;
import org.oddlama.vane.enchantments.items.AncientTomeOfKnowledge;
import org.oddlama.vane.enchantments.items.BookVariant;

@VaneEnchantment(name = "leafchopper", rarity = Rarity.COMMON, treasure = true, target = EnchantmentTarget.TOOL)
public class Leafchopper extends CustomEnchantment<Enchantments> {

	public Leafchopper(Context<Enchantments> context) {
		super(context);
	}

	@Override
	public void register_recipes() {
		final var ancient_tome_of_knowledge_enchanted = CustomItem
			.<AncientTomeOfKnowledge.AncientTomeOfKnowledgeVariant>variant_of(
				AncientTomeOfKnowledge.class,
				BookVariant.ENCHANTED_BOOK
			)
			.item();
		final var ancient_tome_of_knowledge = CustomItem
			.<AncientTomeOfKnowledge.AncientTomeOfKnowledgeVariant>variant_of(
				AncientTomeOfKnowledge.class,
				BookVariant.BOOK
			)
			.item();

		final var recipe_key = recipe_key();
		final var item = ancient_tome_of_knowledge_enchanted.clone();
		final var meta = (EnchantmentStorageMeta) item.getItemMeta();
		meta.addStoredEnchant(bukkit(), 1, false);
		item.setItemMeta(meta);
		get_module().update_enchanted_item(item);

		final var recipe = new ShapedRecipe(recipe_key, item)
			.shape(" s ", "sbs", " s ")
			.setIngredient('b', ancient_tome_of_knowledge)
			.setIngredient('s', Material.SHEARS);

		add_recipe(recipe);
	}

	@Override
	public boolean can_enchant(@NotNull ItemStack item_stack) {
		return MaterialTags.AXES.isTagged(item_stack);
	}

	@EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
	public void on_player_left_click_leaves(PlayerInteractEvent event) {
		if (
			!event.hasBlock() || event.getHand() != EquipmentSlot.HAND || event.getAction() != Action.LEFT_CLICK_BLOCK
		) {
			return;
		}

		// Check leaves
		var block = event.getClickedBlock();
		var data = block.getBlockData();
		if (!(data instanceof Leaves)) {
			return;
		}

		// Check non persistent leaves
		var leaves = (Leaves) data;
		if (leaves.isPersistent()) {
			return;
		}

		// Check enchantment level
		final var player = event.getPlayer();
		final var item = player.getEquipment().getItemInMainHand();
		final var level = item.getEnchantmentLevel(this.bukkit());
		if (level == 0) {
			return;
		}

		// Break instantly, for no additional durability cost.
		block.breakNaturally();
		block.getWorld().playSound(block.getLocation(), Sound.BLOCK_GRASS_BREAK, SoundCategory.BLOCKS, 1.0f, 1.0f);
	}
}
