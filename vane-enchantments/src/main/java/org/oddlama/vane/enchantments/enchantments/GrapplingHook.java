package org.oddlama.vane.enchantments.enchantments;

import java.util.List;
import org.bukkit.Material;
import org.bukkit.enchantments.EnchantmentTarget;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.player.PlayerFishEvent;
import org.bukkit.inventory.ShapedRecipe;
import org.bukkit.inventory.meta.EnchantmentStorageMeta;
import org.bukkit.util.Vector;
import org.oddlama.vane.annotation.config.ConfigDouble;
import org.oddlama.vane.annotation.config.ConfigDoubleList;
import org.oddlama.vane.annotation.enchantment.Rarity;
import org.oddlama.vane.annotation.enchantment.VaneEnchantment;
import org.oddlama.vane.core.item.CustomItem;
import org.oddlama.vane.core.module.Context;
import org.oddlama.vane.enchantments.CustomEnchantment;
import org.oddlama.vane.enchantments.Enchantments;
import org.oddlama.vane.enchantments.items.AncientTomeOfKnowledge;
import org.oddlama.vane.enchantments.items.BookVariant;

@VaneEnchantment(
	name = "grappling_hook",
	max_level = 3,
	rarity = Rarity.UNCOMMON,
	treasure = true,
	target = EnchantmentTarget.FISHING_ROD
)
public class GrapplingHook extends CustomEnchantment<Enchantments> {

	// Constant offset to the added velocity, so the player will always move up a little.
	private static final Vector CONSTANT_OFFSET = new Vector(0.0, 0.2, 0.0);

	@ConfigDouble(
		def = 16.0,
		min = 2.0,
		max = 50.0,
		desc = "Ideal grappling distance for maximum grapple strength. Strength increases rapidly before, and falls of slowly after."
	)
	private double config_ideal_distance;

	@ConfigDoubleList(def = { 1.6, 2.1, 2.7 }, min = 0.0, desc = "Grappling strength for each enchantment level.")
	private List<Double> config_strength;

	public GrapplingHook(Context<Enchantments> context) {
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
			.shape(" h ", " l ", " b ")
			.setIngredient('b', ancient_tome_of_knowledge)
			.setIngredient('l', Material.LEAD)
			.setIngredient('h', Material.TRIPWIRE_HOOK);

		add_recipe(recipe);
	}

	private double get_strength(int level) {
		if (level > 0 && level <= config_strength.size()) {
			return config_strength.get(level - 1);
		}
		return config_strength.get(0);
	}

	@EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
	public void on_player_fish_event(final PlayerFishEvent event) {
		// Get enchantment level
		final var player = event.getPlayer();
		var item = player.getEquipment().getItemInMainHand();
		var level = item.getEnchantmentLevel(this.bukkit());
		if (level == 0) {
			item = player.getEquipment().getItemInOffHand();
			level = item.getEnchantmentLevel(this.bukkit());
			if (level == 0) {
				return;
			}
		}

		// Grapple when stuck in ground
		switch (event.getState()) {
			case FAILED_ATTEMPT:
				// Assume stuck in ground if velocity is < 0.01
				if (event.getHook().getVelocity().length() >= 0.01) {
					return;
				}

				// Block must be solid to be hooked
				if (!event.getHook().getLocation().getBlock().getType().isSolid()) {
					return;
				}
				break;
			case IN_GROUND:
				break;
			default:
				return;
		}

		var direction = event.getHook().getLocation().subtract(player.getLocation()).toVector();
		var distance = direction.length();
		var attenuation = distance / config_ideal_distance;

		// Reset fall distance
		player.setFallDistance(0.0f);

		// Set player velocity
		player.setVelocity(
			player
				.getVelocity()
				.add(
					direction
						.normalize()
						.multiply(get_strength(level) * Math.exp(1.0 - attenuation) * attenuation)
						.add(CONSTANT_OFFSET)
				)
		);
	}
}
