package org.oddlama.vane.portals.portal;

import static org.oddlama.vane.core.persistent.PersistentSerializer.from_json;
import static org.oddlama.vane.core.persistent.PersistentSerializer.to_json;
import static org.oddlama.vane.util.Util.namespaced_key;

import java.io.IOException;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Predicate;

import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.jetbrains.annotations.NotNull;
import org.oddlama.vane.external.json.JSONObject;

public class Style {

	public static Object serialize(@NotNull final Object o) throws IOException {
		final var style = (Style) o;
		final var json = new JSONObject();
		json.put("key", to_json(NamespacedKey.class, style.key));
		try {
			json.put(
				"active_materials",
				to_json(Style.class.getDeclaredField("active_materials"), style.active_materials)
			);
			json.put(
				"inactive_materials",
				to_json(Style.class.getDeclaredField("inactive_materials"), style.inactive_materials)
			);
		} catch (NoSuchFieldException e) {
			throw new RuntimeException("Invalid field. This is a bug.", e);
		}
		return json;
	}

	@SuppressWarnings("unchecked")
	public static Style deserialize(@NotNull final Object o) throws IOException {
		final var json = (JSONObject) o;
		final var style = new Style(null);
		style.key = from_json(NamespacedKey.class, json.get("key"));
		try {
			style.active_materials =
				(Map<PortalBlock.Type, Material>) from_json(
					Style.class.getDeclaredField("active_materials"),
					json.get("active_materials")
				);
			style.inactive_materials =
				(Map<PortalBlock.Type, Material>) from_json(
					Style.class.getDeclaredField("inactive_materials"),
					json.get("inactive_materials")
				);
		} catch (NoSuchFieldException e) {
			throw new RuntimeException("Invalid field. This is a bug.", e);
		}
		return style;
	}

	private NamespacedKey key;
	private Map<PortalBlock.Type, Material> active_materials = new LinkedHashMap<>();
	private Map<PortalBlock.Type, Material> inactive_materials = new LinkedHashMap<>();

	public Style(final NamespacedKey key) {
		this.key = key;
	}

	public NamespacedKey key() {
		return key;
	}

	public Material material(boolean active, PortalBlock.Type type) {
		if (active) {
			return active_materials.get(type);
		} else {
			return inactive_materials.get(type);
		}
	}

	public static NamespacedKey default_style_key() {
		return namespaced_key("vane_portals", "portal_style_default");
	}

	public void set_material(boolean active, PortalBlock.Type type, Material material) {
		set_material(active, type, material, false);
	}

	public void set_material(boolean active, PortalBlock.Type type, Material material, boolean overwrite) {
		final Map<PortalBlock.Type, Material> map;
		if (active) {
			map = active_materials;
		} else {
			map = inactive_materials;
		}

		if (!overwrite && map.containsKey(type)) {
			throw new RuntimeException(
				"Invalid style definition! PortalBlock.Type." + type + " was specified multiple times."
			);
		}
		map.put(type, material);
	}

	public void check_valid() {
		// Checks if every key contains at least 1 material.
		check_valid(PortalBlock.Type.ORIGIN::equals);
		check_valid(PortalBlock.Type.CONSOLE::equals);
		check_valid(PortalBlock.BOUNDARY.class::isInstance);
		check_valid(PortalBlock.PORTAL.class::isInstance);
	}

	public void check_valid(Predicate<PortalBlock.Type> type) {

		if (!active_materials.keySet().stream().anyMatch(type)) {
			throw new RuntimeException(
					"Invalid style definition! Active state for PortalBlock.Type." + type + " was not specified!"
			);
		}
		if (!inactive_materials.keySet().stream().anyMatch(type)) {
			throw new RuntimeException(
					"Invalid style definition! Inactive state for PortalBlock.Type." + type + " was not specified!"
			);
		}
	}

	public static Style default_style() {
		final var style = new Style(default_style_key());

		final var default_mats = Map.of(
				Material.OBSIDIAN, Material.OBSIDIAN,
				Material.CRYING_OBSIDIAN, Material.CRYING_OBSIDIAN,
				Material.GOLD_BLOCK, Material.GOLD_BLOCK,
				Material.GILDED_BLACKSTONE, Material.GILDED_BLACKSTONE,
				Material.EMERALD_BLOCK, Material.EMERALD_BLOCK
		);

		style.set_boundary(true, default_mats);
		style.set_console(true, Material.ENCHANTING_TABLE);
		style.set_origin(true, Material.OBSIDIAN);
		style.set_portal(true, Material.END_GATEWAY);
		style.set_boundary(false, default_mats);
		style.set_console(false,Material.ENCHANTING_TABLE);
		style.set_origin(false, Material.NETHERITE_BLOCK);
		style.set_portal(false, Material.AIR);

		return style;
	}

	private <K, V> void set_boundary(boolean active, Map<K, V> boundaryStyles) {

	}


	public Style copy(final NamespacedKey new_key) {
		final var copy = new Style(new_key);
		copy.active_materials = new HashMap<>(active_materials);
		copy.inactive_materials = new HashMap<>(inactive_materials);
		return copy;
	}
}
