package org.oddlama.vane.core.menu;

import org.bukkit.entity.Player;
import org.bukkit.Material;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.inventory.ItemStack;
import org.oddlama.vane.core.menu.Menu.ClickResult;
import org.oddlama.vane.core.functional.Function4;

public class MenuItemClickListener implements MenuWidget {
	private int slot;
	private Function4<Player, Menu, ClickType, InventoryAction, ClickResult> on_click;

	public MenuItemClickListener(int slot, final Function4<Player, Menu, ClickType, InventoryAction, ClickResult> on_click) {
		this.slot = slot;
		this.on_click = on_click;
	}

	public int slot() { return slot; }
	public boolean update(final Menu menu) {
		return false;
	}

	public ClickResult click(final Player player, final Menu menu, final ItemStack item, int slot, final ClickType type, final InventoryAction action) {
		if (this.slot != slot) {
			return ClickResult.IGNORE;
		}

		if (on_click != null) {
			return on_click.apply(player, menu, type, action);
		} else {
			return ClickResult.IGNORE;
		}
	}
}
