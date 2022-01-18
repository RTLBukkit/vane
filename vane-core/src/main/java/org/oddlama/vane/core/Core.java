package org.oddlama.vane.core;

import static org.oddlama.vane.core.item.CustomItem.has_custom_model;
import static org.oddlama.vane.util.BlockUtil.drop_naturally;
import static org.oddlama.vane.util.BlockUtil.texture_from_skull;
import static org.oddlama.vane.util.MaterialUtil.is_tillable;
import static org.oddlama.vane.util.Util.ms_to_ticks;
import static org.oddlama.vane.util.Util.read_json_from_url;
import static org.oddlama.vane.util.Util.resolve_skin;

import com.destroystokyo.paper.MaterialTags;
import com.destroystokyo.paper.profile.ProfileProperty;
import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.UUID;
import java.util.logging.Level;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.apache.commons.io.IOUtils;
import org.bukkit.Keyed;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.block.Block;
import org.bukkit.block.Skull;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityTargetEvent;
import org.bukkit.event.inventory.PrepareItemCraftEvent;
import org.bukkit.event.inventory.PrepareSmithingEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.loot.Lootable;
import org.bukkit.permissions.Permission;
import org.bukkit.permissions.PermissionDefault;
import org.bukkit.plugin.messaging.PluginMessageListener;
import org.json.JSONException;
import org.oddlama.vane.annotation.VaneModule;
import org.oddlama.vane.annotation.config.ConfigBoolean;
import org.oddlama.vane.annotation.lang.LangMessage;
import org.oddlama.vane.annotation.persistent.Persistent;
import org.oddlama.vane.core.functional.Consumer1;
import org.oddlama.vane.core.item.CustomItem;
import org.oddlama.vane.core.lang.TranslatedMessage;
import org.oddlama.vane.core.material.HeadMaterialLibrary;
import org.oddlama.vane.core.menu.MenuManager;
import org.oddlama.vane.core.module.Module;
import org.oddlama.vane.core.module.ModuleComponent;

@VaneModule(name = "core", bstats = 8637, config_version = 6, lang_version = 2, storage_version = 1)
public class Core extends Module<Core> implements PluginMessageListener {

	// Channel for proxy messages to multiplex connections
	public static final String CHANNEL_AUTH_MULTIPLEX = "vane_waterfall:auth_multiplex";

	@LangMessage
	public TranslatedMessage lang_command_not_a_player;

	@LangMessage
	public TranslatedMessage lang_command_permission_denied;

	@LangMessage
	public TranslatedMessage lang_invalid_time_format;

	@LangMessage
	public TranslatedMessage lang_break_loot_block_prevented;

	// Module registry
	private SortedSet<Module<?>> vane_modules = new TreeSet<>((a, b) -> a.get_name().compareTo(b.get_name()));

	public final ResourcePackDistributor resource_pack_distributor;

	public void register_module(Module<?> module) {
		vane_modules.add(module);
	}

	public void unregister_module(Module<?> module) {
		vane_modules.remove(module);
	}

	public SortedSet<Module<?>> get_modules() {
		return Collections.unmodifiableSortedSet(vane_modules);
	}

	// Vane global command catch-all permission
	public Permission permission_command_catchall = new Permission(
		"vane.*.commands.*",
		"Allow access to all vane commands (ONLY FOR ADMINS!)",
		PermissionDefault.FALSE
	);

	public MenuManager menu_manager;

	// Persistent storage
	@Persistent
	public Map<UUID, UUID> storage_auth_multiplex = new HashMap<>();

	@Persistent
	public Map<UUID, Integer> storage_auth_multiplexer_id = new HashMap<>();

	// core-config
	@ConfigBoolean(
		def = true,
		desc = "Let the client translate messages using the generated resource pack. This allows every player to select their preferred language, and all plugin messages will also be translated. Disabling this won't allow you to skip generating the resource pack, as it will be needed for custom item textures."
	)
	public boolean config_client_side_translations;

	@ConfigBoolean(def = true, desc = "Send update notices to OPped player when a new version of vane is available.")
	public boolean config_update_notices;

	public String current_version = null;
	public String latest_version = null;

	@ConfigBoolean(
		def = true,
		desc = "Prevent players from breaking blocks with loot-tables (like treasure chests) when they first attempt to destroy it. They still can break it, but must do so within a short timeframe."
	)
	public boolean config_warn_breaking_loot_blocks;

	public Core() {
		// Create global command catch-all permission
		register_permission(permission_command_catchall);

		// Load head material library
		log.info("Loading head library...");
		try {
			HeadMaterialLibrary.load(IOUtils.toString(getResource("head_library.json"), StandardCharsets.UTF_8));
		} catch (IOException e) {
			log.log(Level.SEVERE, "Error while loading head_library.json! Shutting down.", e);
			getServer().shutdown();
		}

		// Components
		new org.oddlama.vane.core.commands.Vane(this);
		new org.oddlama.vane.core.commands.CustomItem(this);
		menu_manager = new MenuManager(this);
		resource_pack_distributor = new ResourcePackDistributor(this);
		new CommandHider(this);
	}

	public void check_for_update() {
		if (current_version == null) {
			try {
				Properties properties = new Properties();
				properties.load(Core.class.getResourceAsStream("/vane-core.properties"));
				current_version = "v" + properties.getProperty("version");
			} catch (IOException e) {
				log.severe("Could not load current version from included properties file: " + e.toString());
				return;
			}
		}

		try {
			final var json = read_json_from_url("https://api.github.com/repos/oddlama/vane/releases/latest");
			latest_version = json.getString("tag_name");
			if (latest_version != null && !latest_version.equals(current_version)) {
				log.warning(
					"A newer version of vane is available online! (current=" +
					current_version +
					", new=" +
					latest_version +
					")"
				);
				log.warning("Please update as soon as possible to get the latest features and fixes.");
				log.warning("Get the latest release here: https://github.com/oddlama/vane/releases/latest");
			}
		} catch (IOException | JSONException e) {
			log.warning("Could not check for updates: " + e.toString());
		}
	}

	@Override
	public void on_enable() {
		getServer().getMessenger().registerIncomingPluginChannel(this, CHANNEL_AUTH_MULTIPLEX, this);
		super.on_enable();

		if (config_update_notices) {
			// Now, and every hour after that check if a new version is available.
			// OPs will get a message about this when they join.
			schedule_task_timer(this::check_for_update, 1l, ms_to_ticks(2 * 60l * 60l * 1000l));
		}
	}

	@Override
	public void on_disable() {
		super.on_disable();
		getServer().getMessenger().unregisterIncomingPluginChannel(this, CHANNEL_AUTH_MULTIPLEX, this);
	}

	public File generate_resource_pack() {
		try {
			var file = new File("vane-resource-pack.zip");
			var pack = new ResourcePackGenerator();
			pack.set_description("Vane plugin resource pack");
			pack.set_icon_png(getResource("pack.png"));

			for (var m : vane_modules) {
				m.generate_resource_pack(pack);
			}

			pack.write(file);
			return file;
		} catch (Exception e) {
			log.log(Level.SEVERE, "Error while generating resourcepack", e);
			return null;
		}
	}

	public void for_all_module_components(final Consumer1<ModuleComponent<?>> f) {
		for (var m : vane_modules) {
			m.for_each_module_component(f);
		}
	}

	// Prevent entity targeting by tempting when the reason is a custom item.
	@EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
	public void on_pathfind(final EntityTargetEvent event) {
		if (event.getReason() != EntityTargetEvent.TargetReason.TEMPT) {
			return;
		}

		if (!(event.getTarget() instanceof Player)) {
			return;
		}

		final var player = (Player) event.getTarget();
		if (!has_custom_model(player.getInventory().getItemInMainHand())) {
			return;
		}
		if (!has_custom_model(player.getInventory().getItemInOffHand())) {
			return;
		}

		// Cancel event as it was induced by a custom item
		event.setCancelled(true);
	}

	// Prevent custom hoe items from tilling blocks
	@EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
	public void on_player_hoe_right_click_block(final PlayerInteractEvent event) {
		if (!event.hasBlock() || event.getAction() != Action.RIGHT_CLICK_BLOCK) {
			return;
		}

		// Only when clicking a tillable block
		if (!is_tillable(event.getClickedBlock().getType())) {
			return;
		}

		// Only when using a custom item that is a hoe
		final var player = event.getPlayer();
		final var item = player.getEquipment().getItem(event.getHand());
		if (has_custom_model(item) && MaterialTags.HOES.isTagged(item)) {
			event.setCancelled(true);
		}
	}

	// Prevent custom items from being used in minecraft's crafting
	// recipes.
	@EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
	public void on_prepare_item_craft(final PrepareItemCraftEvent event) {
		final var recipe = event.getRecipe();
		if (recipe == null) {
			return;
		}

		final NamespacedKey key;
		if (recipe instanceof Keyed) {
			key = ((Keyed) recipe).getKey();
		} else {
			return;
		}

		// Only cancel minecraft's recipes
		if (!key.getNamespace().equals("minecraft")) {
			return;
		}

		for (final var item : event.getInventory().getMatrix()) {
			if (item == null) {
				continue;
			}

			if (has_custom_model(item)) {
				event.getInventory().setResult(null);
				return;
			}
		}
	}

	// Prevent custom items from forming netherite variants, or delegate event to custom item
	@EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
	public void on_prepare_smithing(final PrepareSmithingEvent event) {
		final var item = event.getInventory().getInputEquipment();
		if (item == null) {
			return;
		}

		if (has_custom_model(item)) {
			event.setResult(null);
		}

		// Try to add automatic netherite conversion
		final var item_lookup = CustomItem.from_item(item);
		if (item_lookup == null || !item_lookup.has_netherite_conversion()) {
			return;
		}

		// Check netherite ingot ingredient
		final var mineral = event.getInventory().getInputMineral();
		if (mineral == null || mineral.getType() != Material.NETHERITE_INGOT) {
			return;
		}

		// Check matching input variant
		final var variant_from = item_lookup.netherite_conversion_from();
		if (!item_lookup.variant().equals(variant_from)) {
			return;
		}

		// Create new item
		final var variant_to = item_lookup.netherite_conversion_to();
		event.setResult(item_lookup.modify_variant(item, variant_to));
	}

	// Prevent netherite items from burning, as they are made of netherite
	@EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
	public void on_item_burn(final EntityDamageEvent event) {
		// Only burn damage on dropped items
		if (event.getEntity().getType() != EntityType.DROPPED_ITEM) {
			return;
		}

		switch (event.getCause()) {
			default:
				return;
			case FIRE:
			case FIRE_TICK:
			case LAVA:
				break;
		}

		// Get item variant
		final var entity = event.getEntity();
		if (!(entity instanceof Item)) {
			return;
		}

		final var item = ((Item) entity).getItemStack();
		final var item_lookup = CustomItem.from_item(item);
		if (item_lookup == null || !item_lookup.has_netherite_conversion()) {
			return;
		}

		// Only if we deal with the netherite variant
		if (item_lookup.variant() != item_lookup.netherite_conversion_to()) {
			return;
		}

		event.setCancelled(true);
	}

	// Restore correct head item from head library when broken
	@EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
	public void on_block_break(final BlockBreakEvent event) {
		final var block = event.getBlock();
		if (block.getType() != Material.PLAYER_HEAD && block.getType() != Material.PLAYER_WALL_HEAD) {
			return;
		}

		final var skull = (Skull) block.getState();
		final var texture = texture_from_skull(skull);
		if (texture == null) {
			return;
		}

		final var head_material = HeadMaterialLibrary.from_texture(texture);
		if (head_material == null) {
			return;
		}

		// Set to air and drop item
		block.setType(Material.AIR);
		drop_naturally(block, head_material.item());
	}

	public synchronized String auth_multiplex_player_name(final UUID uuid) {
		final var original_player_id = storage_auth_multiplex.get(uuid);
		final var multiplexer_id = storage_auth_multiplexer_id.get(uuid);
		if (original_player_id == null || multiplexer_id == null) {
			return null;
		}

		final var original_player = getServer().getOfflinePlayer(original_player_id);
		if (original_player != null) {
			return "§7[" + multiplexer_id + "]§r " + original_player.getName();
		}

		return null;
	}

	private void try_init_multiplexed_player_name(final Player player) {
		final var id = player.getUniqueId();
		final var display_name = auth_multiplex_player_name(id);
		if (display_name == null) {
			return;
		}

		log.info(
			"[multiplex] Init player '" +
			display_name +
			"' for registered auth multiplexed player {" +
			id +
			", " +
			player.getName() +
			"}"
		);
		final var display_name_component = LegacyComponentSerializer.legacySection().deserialize(display_name);
		player.displayName(display_name_component);
		player.playerListName(display_name_component);

		final var original_player_id = storage_auth_multiplex.get(id);
		final var skin = resolve_skin(original_player_id);
		final var profile = player.getPlayerProfile();
		profile.setProperty(new ProfileProperty("textures", skin.texture, skin.signature));
		player.setPlayerProfile(profile);
	}

	@EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = false)
	public void on_player_join(PlayerJoinEvent event) {
		try_init_multiplexed_player_name(event.getPlayer());

		if (config_update_notices) {
			// Send update message if new version is available and player is OP.
			if (latest_version != null && !latest_version.equals(current_version) && event.getPlayer().isOp()) {
				// This message is intentionally not translated to ensure it will
				// be displayed correctly and so that everyone understands it.
				event
					.getPlayer()
					.sendMessage(
						Component
							.text("A new version of vane ", NamedTextColor.GREEN)
							.append(Component.text("(" + latest_version + ")", NamedTextColor.AQUA))
							.append(Component.text(" is available!", NamedTextColor.GREEN))
					);
				event
					.getPlayer()
					.sendMessage(
						Component.text("Please update soon to get the latest features.", NamedTextColor.GREEN)
					);
				event
					.getPlayer()
					.sendMessage(
						Component
							.text("Click here to go to the download page", NamedTextColor.AQUA)
							.clickEvent(ClickEvent.openUrl("https://github.com/oddlama/vane/releases/latest"))
					);
			}
		}
	}

	@Override
	public synchronized void onPluginMessageReceived(final String channel, final Player player, byte[] bytes) {
		if (!channel.equals(CHANNEL_AUTH_MULTIPLEX)) {
			return;
		}

		final var stream = new ByteArrayInputStream(bytes);
		final var in = new DataInputStream(stream);

		try {
			final var multiplexer_id = in.readInt();
			final var old_uuid = UUID.fromString(in.readUTF());
			final var old_name = in.readUTF();
			final var new_uuid = UUID.fromString(in.readUTF());
			final var new_name = in.readUTF();

			log.info(
				"[multiplex] Registered auth multiplexed player {" +
				new_uuid +
				", " +
				new_name +
				"} from player {" +
				old_uuid +
				", " +
				old_name +
				"} multiplexer_id " +
				multiplexer_id
			);
			storage_auth_multiplex.put(new_uuid, old_uuid);
			storage_auth_multiplexer_id.put(new_uuid, multiplexer_id);
			mark_persistent_storage_dirty();

			final var multiplexed_player = getServer().getOfflinePlayer(new_uuid);
			if (multiplexed_player != null && multiplexed_player.isOnline()) {
				try_init_multiplexed_player_name(multiplexed_player.getPlayer());
			}
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	// Prevent loot chest destruction
	private final Map<Block, Map<UUID, Long>> loot_break_attempts = new HashMap<>();

	@EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
	public void on_break_loot_chest(final BlockBreakEvent event) {
		if (!config_warn_breaking_loot_blocks) {
			return;
		}

		final var state = event.getBlock().getState(false);
		if (!(state instanceof Lootable)) {
			return;
		}

		final var lootable = (Lootable) state;
		if (!lootable.hasLootTable()) {
			return;
		}

		final var block = event.getBlock();
		final var player = event.getPlayer();
		var block_attempts = loot_break_attempts.get(block);
		final var now = System.currentTimeMillis();
		if (block_attempts != null) {
			final var player_attempt_time = block_attempts.get(player.getUniqueId());
			if (player_attempt_time != null) {
				final var elapsed = now - player_attempt_time;
				if (elapsed > 5000 && elapsed < 30000) {
					// Allow
					return;
				}
			} else {
				block_attempts.put(player.getUniqueId(), now);
			}
		} else {
			block_attempts = new HashMap<UUID, Long>();
			block_attempts.put(player.getUniqueId(), now);
			loot_break_attempts.put(block, block_attempts);
		}

		lang_break_loot_block_prevented.send(player);
		event.setCancelled(true);
	}
}
