package org.oddlama.vane.core.lang;

import java.util.ArrayList;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.ComponentLike;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.jetbrains.annotations.NotNull;
import org.oddlama.vane.core.module.Module;

public class TranslatedMessage {

	private Module<?> module;
	private String key;
	private String default_translation;

	public TranslatedMessage(final Module<?> module, final String key, final String default_translation) {
		this.module = module;
		this.key = key;
		this.default_translation = default_translation;
	}

	public String key() {
		return key;
	}

	public String str(Object... args) {
		try {
			final var list = new Object[args.length];
			for (int i = 0; i < args.length; ++i) {
				if (args[i] instanceof Component) {
					list[i] = LegacyComponentSerializer.legacySection().serialize((Component) args[i]);
				} else if (args[i] instanceof String) {
					list[i] = args[i];
				} else {
					throw new RuntimeException(
						"Error while formatting message '" + key() + "', invalid argument to str() serializer" + args[i]
					);
				}
			}
			return String.format(default_translation, list);
		} catch (Exception e) {
			throw new RuntimeException("Error while formatting message '" + key() + "'", e);
		}
	}

	public @NotNull Component str_component(Object... args) {
		return LegacyComponentSerializer.legacySection().deserialize(str(args));
	}

	public Component format(Object... args) {
		if (!module.core.config_client_side_translations) {
			return str_component(args);
		}

		final var list = new ArrayList<ComponentLike>();
		for (final var o : args) {
			if (o instanceof ComponentLike) {
				list.add((ComponentLike) o);
			} else if (o instanceof String) {
				list.add(LegacyComponentSerializer.legacySection().deserialize((String) o));
			} else {
				throw new RuntimeException("Error while formatting message '" + key() + "', got invalid argument " + o);
			}
		}
		return Component.translatable(key, list);
	}

	public void broadcast_server_players(Object... args) {
		final var component = format(args);
		for (var player : module.getServer().getOnlinePlayers()) {
			player.sendMessage(component);
		}
	}

	public void broadcast_server(Object... args) {
		final var component = format(args);
		for (var player : module.getServer().getOnlinePlayers()) {
			player.sendMessage(component);
		}
		module.log.info("[broadcast] " + str(args));
	}

	public void broadcast_world(final World world, Object... args) {
		final var component = format(args);
		for (var player : world.getPlayers()) {
			player.sendMessage(component);
		}
	}

	public void send(final CommandSender sender, Object... args) {
		if (sender == null || sender == module.getServer().getConsoleSender()) {
			module.log.info(str(args));
		} else {
			sender.sendMessage(format(args));
		}
	}

	public void send_and_log(final CommandSender sender, Object... args) {
		module.log.info(str(args));

		// Also send to sender if it's not the console
		if (sender == null || sender == module.getServer().getConsoleSender()) {
			return;
		} else {
			sender.sendMessage(format(args));
		}
	}
}
