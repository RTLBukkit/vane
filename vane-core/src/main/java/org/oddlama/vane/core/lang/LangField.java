package org.oddlama.vane.core.lang;

import java.lang.reflect.Field;
import java.util.function.Function;
import org.bukkit.configuration.file.YamlConfiguration;
import org.oddlama.vane.core.ResourcePackGenerator;
import org.oddlama.vane.core.YamlLoadException;
import org.oddlama.vane.core.module.Module;

public abstract class LangField<T> {

	private Module<?> module;
	protected Object owner;
	protected Field field;
	protected String name;
	private final String namespace;
	private final String key;

	public LangField(Module<?> module, Object owner, Field field, Function<String, String> map_name) {
		this.module = module;
		this.owner = owner;
		this.field = field;
		this.name = map_name.apply(field.getName().substring("lang_".length()));
		this.namespace = module.namespace();
		this.key = namespace + "." + yaml_path();

		field.setAccessible(true);
	}

	public String get_name() {
		return name;
	}

	public String yaml_path() {
		return name;
	}

	protected void check_yaml_path(YamlConfiguration yaml) throws YamlLoadException {
		if (!yaml.contains(name, true)) {
			throw new YamlLoadException("yaml is missing entry with path '" + name + "'");
		}
	}

	public Module<?> module() {
		return module;
	}

	public String namespace() {
		return namespace;
	}

	public String key() {
		return key;
	}

	public abstract void check_loadable(YamlConfiguration yaml) throws YamlLoadException;

	public abstract void load(final String namespace, final YamlConfiguration yaml);

	public abstract void add_translations(
		final ResourcePackGenerator pack,
		final YamlConfiguration yaml,
		String lang_code
	) throws YamlLoadException;

	@SuppressWarnings("unchecked")
	public T get() {
		try {
			return (T) field.get(owner);
		} catch (IllegalAccessException e) {
			throw new RuntimeException("Invalid field access on '" + field.getName() + "'. This is a bug.");
		}
	}
}
