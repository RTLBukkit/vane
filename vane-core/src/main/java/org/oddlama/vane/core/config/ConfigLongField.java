package org.oddlama.vane.core.config;

import java.lang.reflect.Field;
import java.util.function.Function;
import org.bukkit.configuration.file.YamlConfiguration;
import org.oddlama.vane.annotation.config.ConfigLong;
import org.oddlama.vane.core.YamlLoadException;

public class ConfigLongField extends ConfigField<Long> {

	public ConfigLong annotation;

	public ConfigLongField(Object owner, Field field, Function<String, String> map_name, ConfigLong annotation) {
		super(owner, field, map_name, "long", annotation.desc());
		this.annotation = annotation;
	}

	@Override
	public Long def() {
		final var override = overridden_def();
		if (override != null) {
			return override;
		} else {
			return annotation.def();
		}
	}

	@Override
	public void generate_yaml(StringBuilder builder, String indent, YamlConfiguration existing_compatible_config) {
		append_description(builder, indent);
		append_value_range(builder, indent, annotation.min(), annotation.max(), Long.MIN_VALUE, Long.MAX_VALUE);
		append_default_value(builder, indent, def());
		final var def = existing_compatible_config != null && existing_compatible_config.contains(yaml_path())
			? load_from_yaml(existing_compatible_config)
			: def();
		append_field_definition(builder, indent, def);
	}

	@Override
	public void check_loadable(YamlConfiguration yaml) throws YamlLoadException {
		check_yaml_path(yaml);

		if (!(yaml.get(yaml_path()) instanceof Number)) {
			throw new YamlLoadException("Invalid type for yaml path '" + yaml_path() + "', expected long");
		}

		var val = yaml.getLong(yaml_path());
		if (annotation.min() != Long.MIN_VALUE && val < annotation.min()) {
			throw new YamlLoadException(
				"Configuration '" + yaml_path() + "' has an invalid value: Value must be >= " + annotation.min()
			);
		}
		if (annotation.max() != Long.MAX_VALUE && val > annotation.max()) {
			throw new YamlLoadException(
				"Configuration '" + yaml_path() + "' has an invalid value: Value must be <= " + annotation.max()
			);
		}
	}

	public long load_from_yaml(YamlConfiguration yaml) {
		return yaml.getLong(yaml_path());
	}

	public void load(YamlConfiguration yaml) {
		try {
			field.setLong(owner, load_from_yaml(yaml));
		} catch (IllegalAccessException e) {
			throw new RuntimeException("Invalid field access on '" + field.getName() + "'. This is a bug.");
		}
	}
}
