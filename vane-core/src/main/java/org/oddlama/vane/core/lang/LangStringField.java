package org.oddlama.vane.core.lang;

import static org.reflections.ReflectionUtils.*;

import java.lang.reflect.Field;

import org.bukkit.configuration.file.YamlConfiguration;

import org.oddlama.vane.annotation.lang.LangString;
import org.oddlama.vane.core.Module;
import org.oddlama.vane.core.YamlLoadException;

public class LangStringField extends LangField<String> {
	public LangString annotation;

	public LangStringField(Module module, Field field, LangString annotation) {
		super(module, field);
		this.annotation = annotation;
	}

	@Override
	public void check_loadable(YamlConfiguration yaml) throws YamlLoadException {
		check_yaml_path(yaml);

		if (!yaml.isString(get_yaml_path())) {
			throw new YamlLoadException("Invalid type for yaml path '" + get_yaml_path() + "', expected string");
		}
	}

	public void load(YamlConfiguration yaml) {
		try {
			field.set(module, yaml.getString(get_yaml_path()));
		} catch (IllegalAccessException e) {
			throw new RuntimeException("Invalid field access on '" + field.getName() + "'. This is a bug.");
		}
	}
}

