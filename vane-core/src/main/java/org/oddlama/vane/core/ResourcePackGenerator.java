package org.oddlama.vane.core;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.bukkit.NamespacedKey;
import org.json.JSONArray;
import org.json.JSONObject;

public class ResourcePackGenerator {

	private String description = "";
	private byte[] icon_png_content = null;
	private Map<String, Map<String, JSONObject>> translations = new HashMap<>();
	private Map<NamespacedKey, List<JSONObject>> item_overrides = new HashMap<>();
	private Map<NamespacedKey, byte[]> item_textures = new HashMap<>();

	public void set_description(String description) {
		this.description = description;
	}

	public void set_icon_png(File file) throws IOException {
		this.icon_png_content = Files.readAllBytes(file.toPath());
	}

	public void set_icon_png(InputStream data) throws IOException {
		this.icon_png_content = data.readAllBytes();
	}

	public JSONObject translations(String namespace, String lang_code) {
		var ns = translations.get(namespace);
		if (ns == null) {
			ns = new HashMap<String, JSONObject>();
			translations.put(namespace, ns);
		}
		var lang_map = ns.get(lang_code);
		if (lang_map == null) {
			lang_map = new JSONObject();
			ns.put(lang_code, lang_map);
		}
		return lang_map;
	}

	public void add_item_model(NamespacedKey key, InputStream texture_png) throws IOException {
		item_textures.put(key, texture_png.readAllBytes());
	}

	public void add_item_override(
		NamespacedKey base_item_key,
		NamespacedKey new_item_key,
		Consumer<JSONObject> create_predicate
	) {
		var overrides = item_overrides.get(base_item_key);
		if (overrides == null) {
			overrides = new ArrayList<JSONObject>();
			item_overrides.put(base_item_key, overrides);
		}

		final var predicate = new JSONObject();
		create_predicate.accept(predicate);

		final var override = new JSONObject();
		override.put("predicate", predicate);
		override.put("model", new_item_key.getNamespace() + ":item/" + new_item_key.getKey());
		overrides.add(override);
	}

	private String generate_pack_mcmeta() {
		final var pack = new JSONObject();
		pack.put("pack_format", 8);
		pack.put("description", description);

		final var root = new JSONObject();
		root.put("pack", pack);

		return root.toString();
	}

	private void write_translations(final ZipOutputStream zip) throws IOException {
		for (var t : translations.entrySet()) {
			var namespace = t.getKey();
			for (var ns : t.getValue().entrySet()) {
				var lang_code = ns.getKey();
				var lang_map = ns.getValue();
				zip.putNextEntry(new ZipEntry("assets/" + namespace + "/lang/" + lang_code + ".json"));
				zip.write(lang_map.toString().getBytes(StandardCharsets.UTF_8));
				zip.closeEntry();
			}
		}
	}

	private JSONObject create_item_model_handheld(NamespacedKey texture) {
		// Create model json
		final var textures = new JSONObject();
		textures.put("layer0", texture.getNamespace() + ":item/" + texture.getKey());

		final var model = new JSONObject();
		model.put("parent", "minecraft:item/handheld");
		model.put("textures", textures);

		return model;
	}

	private void write_item_models(final ZipOutputStream zip) throws IOException {
		for (var entry : item_textures.entrySet()) {
			final var key = entry.getKey();
			final var texture = entry.getValue();

			// Write texture
			zip.putNextEntry(new ZipEntry("assets/" + key.getNamespace() + "/textures/item/" + key.getKey() + ".png"));
			zip.write(texture);
			zip.closeEntry();

			// Write model json
			final var model = create_item_model_handheld(key);
			zip.putNextEntry(new ZipEntry("assets/" + key.getNamespace() + "/models/item/" + key.getKey() + ".json"));
			zip.write(model.toString().getBytes(StandardCharsets.UTF_8));
			zip.closeEntry();
		}
	}

	private static int compare_item_overrides(JSONObject o1, JSONObject o2) {
		if (o1.has("custom_model_data")) {
			if (o2.has("custom_model_data")) {
				return o1.getInt("custom_model_data") - o2.getInt("custom_model_data");
			}
			return -1;
		} else {
			if (o2.has("custom_model_data")) {
				return 1;
			}
			return 0;
		}
	}

	private void write_item_overrides(final ZipOutputStream zip) throws IOException {
		for (var entry : item_overrides.entrySet()) {
			final var key = entry.getKey();

			// Create sorted JSONArray
			Collections.sort(entry.getValue(), ResourcePackGenerator::compare_item_overrides);
			final var overrides = new JSONArray();
			entry.getValue().forEach(o -> overrides.put(o));

			// Create model json
			final var model = create_item_model_handheld(key);
			model.put("overrides", overrides);

			// Write item model override
			zip.putNextEntry(new ZipEntry("assets/" + key.getNamespace() + "/models/item/" + key.getKey() + ".json"));
			zip.write(model.toString().getBytes(StandardCharsets.UTF_8));
			zip.closeEntry();
		}
	}

	public void write(File file) throws IOException {
		try (var zip = new ZipOutputStream(new FileOutputStream(file))) {
			zip.putNextEntry(new ZipEntry("pack.mcmeta"));
			zip.write(generate_pack_mcmeta().getBytes(StandardCharsets.UTF_8));
			zip.closeEntry();

			if (icon_png_content != null) {
				zip.putNextEntry(new ZipEntry("pack.png"));
				zip.write(icon_png_content);
				zip.closeEntry();
			}

			write_translations(zip);
			write_item_models(zip);
			write_item_overrides(zip);
		} catch (IOException e) {
			throw e;
		}
	}
}
