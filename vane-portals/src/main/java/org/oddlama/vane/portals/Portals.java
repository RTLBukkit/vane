package org.oddlama.vane.portals;

import static org.oddlama.vane.util.WorldUtil.broadcast;
import static org.oddlama.vane.util.WorldUtil.change_time_smoothly;
import static org.oddlama.vane.util.Util.ms_to_ticks;

import java.text.MessageFormat;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;

import org.bukkit.GameMode;
import org.bukkit.World;
import org.bukkit.Material;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;

import org.oddlama.vane.annotation.config.ConfigBoolean;
import org.oddlama.vane.annotation.config.ConfigLong;
import org.oddlama.vane.annotation.config.ConfigMaterialSet;
import org.oddlama.vane.annotation.config.ConfigVersion;
import org.oddlama.vane.annotation.VaneModule;
import org.oddlama.vane.annotation.lang.LangMessage;
import org.oddlama.vane.annotation.lang.LangString;
import org.oddlama.vane.annotation.lang.LangVersion;
import org.oddlama.vane.core.Module;
import org.oddlama.vane.util.Nms;

@VaneModule
public class Portals extends Module implements Listener {
	// Configuration
	@ConfigVersion(1)
	public long config_version;

	// Language
	@LangVersion(1)
	public long lang_version;

	@Override
	public void on_enable() {
		register_listener(this);
	}

	@Override
	protected void on_disable() {
		unregister_listener(this);
	}

	@Override
	protected void on_config_change() {
	}
}
