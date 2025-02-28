package org.oddlama.vane.core.item;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.oddlama.vane.annotation.item.VaneItem;
import org.oddlama.vane.core.Listener;
import org.oddlama.vane.core.functional.Function2;
import org.oddlama.vane.core.module.Context;
import org.oddlama.vane.core.module.Module;

/**
 * Represents a custom item. A custom item can have different variants (e.g. stone, iron, golden, ...)
 * Remember that you should never reuse id's previously in use. Use the disabled tag for this to prevent
 * recipes from registering and events from being processed.
 */
public class CustomItem<T extends Module<T>, V extends CustomItem<T, V>> extends Listener<T> {

	// Track instances
	private static final Map<Class<?>, CustomItem<?, ?>> instances = new HashMap<>();
	// Reverse lookup model data -> custom item class, variant
	private static final Map<Integer, ReverseLookupEntry> reverse_lookup = new HashMap<>();

	private VaneItem annotation = getClass().getAnnotation(VaneItem.class);
	private String name;
	private Class<? extends ItemVariantEnum> variant_enum_class;
	private ItemVariantEnum variant_min;
	private ItemVariantEnum variant_max;

	// Track variants
	private final List<CustomItemVariant<T, V, ?>> variants = new ArrayList<>();

	/**
	 * Single variant item constructor.
	 */
	public CustomItem(
		Context<T> context,
		Function2<V, SingleVariant, CustomItemVariant<T, V, SingleVariant>> create_instance
	) {
		this(context, SingleVariant.class, SingleVariant.values(), create_instance);
	}

	/**
	 * Multi variant item constructor.
	 */
	@SuppressWarnings("unchecked")
	public <U extends ItemVariantEnum> CustomItem(
		Context<T> context,
		Class<U> variant_enum_class,
		U[] variant_enum_values,
		Function2<V, U, CustomItemVariant<T, V, U>> create_instance
	) {
		super(null);
		this.variant_enum_class = variant_enum_class;

		// Make namespace
		name = annotation.name();
		context = context.group("item_" + name, "Enable item " + name);
		set_context(context);

		// Track lowest and highest variant
		variant_min = variant_enum_values[0];
		variant_max = variant_enum_values[variant_enum_values.length - 1];

		// Check if instance is already exists
		if (instances.get(getClass()) != null) {
			throw new RuntimeException("Cannot create two instances of a custom item!");
		}

		// Create variants
		for (var variant : variant_enum_values) {
			final var v = create_instance.apply((V) this, variant);
			variants.add(v);
			reverse_lookup.put(v.model_data(), new ReverseLookupEntry(this, variant));
		}

		instances.put(getClass(), this);
	}

	/**
	 * Asserts that there is no other item with the same model data
	 */
	protected final void check_valid_model_data(CustomItemVariant<T, V, ?> variant) {
		for (var item : instances.values()) {
			for (var other_variant : item.variants()) {
				if (other_variant.base() == variant.base()) {
					if (other_variant.model_data() == variant.model_data()) {
						throw new RuntimeException(
							"Cannot register custom item " +
							getClass() +
							" variant " +
							variant +
							" with the same base material " +
							variant.base() +
							" and model_data as " +
							item.getClass() +
							" variant " +
							other_variant
						);
					}
				}
			}
		}
	}

	public final String name() {
		return name;
	}

	final void assert_correct_variant_class(ItemVariantEnum variant) {
		if (!variant.getClass().equals(variant_enum_class)) {
			throw new RuntimeException(
				"Invalid ItemVariantEnum class " +
				variant.getClass() +
				" for item " +
				getClass() +
				": expected " +
				variant_enum_class
			);
		}
	}

	public final boolean has_netherite_conversion() {
		return netherite_conversion_from() != null && netherite_conversion_to() != null;
	}

	public ItemVariantEnum netherite_conversion_from() {
		return null;
	}

	public ItemVariantEnum netherite_conversion_to() {
		return null;
	}

	/**
	 * Returns the assigned model data.
	 */
	@SuppressWarnings("unchecked")
	public final int model_data(ItemVariantEnum variant) {
		assert_correct_variant_class(variant);
		final var cls = get_module().model_data_enum();
		try {
			final var constant = name.toUpperCase();
			final var custom_item_id = (ModelDataEnum) Enum.valueOf(cls.asSubclass(Enum.class), constant);
			return get_module().model_data(custom_item_id.id(), variant.ordinal());
		} catch (IllegalArgumentException e) {
			get_module()
				.log.log(
					Level.SEVERE,
					"Missing enum entry for " + getClass() + ", must be called '" + name.toUpperCase() + "'"
				);
			throw e;
		}
	}

	/**
	 * Returns all variants of this item.
	 */
	public final List<CustomItemVariant<T, V, ?>> variants() {
		return variants;
	}

	/**
	 * Returns the lower bound for this custom item.
	 */
	private int model_data_range_lower_bound() {
		return model_data(variant_min);
	}

	/**
	 * Returns the upper bound for this custom item.
	 */
	private int model_data_range_upper_bound() {
		return model_data(variant_max);
	}

	public static boolean is_custom_item(@NotNull ItemStack item) {
		return item.hasItemMeta() && item.getItemMeta().hasCustomModelData();
	}

	public static ReverseLookupEntry from_item(@NotNull ItemStack item) {
		final var meta = item.getItemMeta();
		if (!meta.hasCustomModelData()) {
			return null;
		}
		return from_model_data(meta.getCustomModelData());
	}

	public static ReverseLookupEntry from_model_data(int model_data) {
		return reverse_lookup.get(model_data);
	}

	/**
	 * Returns the variant of the given item or null if the item
	 * is not an instance of this custom item.
	 */
	@SuppressWarnings("unchecked")
	public <U> U variant_of(@NotNull ItemStack item) {
		final var meta = item.getItemMeta();
		if (meta == null) {
			return null;
		}
		if (!meta.hasCustomModelData()) {
			return null;
		}

		// Check custom model data range
		final var custom_model_data = meta.getCustomModelData();
		if (
			model_data_range_lower_bound() <= custom_model_data && custom_model_data <= model_data_range_upper_bound()
		) {
			return (U) variants().get(custom_model_data - model_data_range_lower_bound());
		}

		return null;
	}

	/**
	 * Returns an itemstack of this item with the given variant.
	 */
	public <U extends ItemVariantEnum> ItemStack item(U variant) {
		return item(variant, 1);
	}

	/**
	 * Returns an itemstack of this item with the given variant and amount.
	 */
	public <U extends ItemVariantEnum> ItemStack item(U variant, int amount) {
		return item(getClass(), variant, amount);
	}

	/**
	 * Returns an itemstack of this item with the given variant.
	 */
	public <U extends ItemVariantEnum> ItemStack item(CustomItemVariant<T, V, U> variant) {
		return item(variant, 1);
	}

	/**
	 * Returns an itemstack of this item with the given variant and amount.
	 */
	public <U extends ItemVariantEnum> ItemStack item(CustomItemVariant<T, V, U> variant, int amount) {
		assert_correct_variant_class(variant.variant());
		return construct_item_stack(amount, this, variant);
	}

	/**
	 * Returns the variant for the given registered item and variant enum.
	 */
	@SuppressWarnings("unchecked")
	public static <U> U variant_of(Class<?> cls, ItemVariantEnum variant) {
		final var custom_item = instances.get(cls);
		custom_item.assert_correct_variant_class(variant);
		return (U) custom_item.variants().get(variant.ordinal());
	}

	/**
	 * Returns an itemstack for the given custom item with the given amount
	 */
	public static <U extends ItemVariantEnum> ItemStack item(Class<?> cls, U variant, int amount) {
		final var custom_item = instances.get(cls);
		custom_item.assert_correct_variant_class(variant);
		final var custom_item_variant = custom_item.variants().get(variant.ordinal());
		return construct_item_stack(amount, custom_item, custom_item_variant);
	}

	private static <A extends CustomItem<?, ?>, B extends CustomItemVariant<?, ?, ?>> ItemStack prepare_item_stack(
		@NotNull ItemStack item_stack,
		A custom_item,
		B custom_item_variant
	) {
		final var meta = item_stack.getItemMeta();
		meta.setCustomModelData(custom_item.model_data(custom_item_variant.variant()));
		meta.displayName(custom_item_variant.display_name());
		item_stack.setItemMeta(meta);
		return custom_item.modify_item_stack(custom_item_variant.modify_item_stack(item_stack));
	}

	private static <A extends CustomItem<?, ?>, B extends CustomItemVariant<?, ?, ?>> ItemStack construct_item_stack(
		int amount,
		A custom_item,
		B custom_item_variant
	) {
		final var item_stack = new ItemStack(custom_item_variant.base(), amount);
		return prepare_item_stack(item_stack, custom_item, custom_item_variant);
	}

	public static <U extends ItemVariantEnum> ItemStack modify_variant(@NotNull ItemStack item, U variant) {
		final var item_lookup = from_item(item);
		final var custom_item = item_lookup.custom_item;
		custom_item.assert_correct_variant_class(variant);
		final var custom_item_variant = custom_item.variants().get(variant.ordinal());
		final var item_stack = item.clone();
		item_stack.setType(custom_item_variant.base());
		return prepare_item_stack(item_stack, custom_item, custom_item_variant);
	}

	/**
	 * Convert an existing item to a custom item. Base type will be changed,
	 * but e.g. Enchantments and attributes will be kept.
	 */
	public static <A extends CustomItem<?, ?>, U extends ItemVariantEnum> ItemStack convert_existing(
		@NotNull ItemStack item,
		A custom_item,
		U variant
	) {
		custom_item.assert_correct_variant_class(variant);
		final var item_stack = item.clone();
		final var custom_item_variant = custom_item.variants().get(variant.ordinal());
		item_stack.setType(custom_item_variant.base());
		return prepare_item_stack(item_stack, custom_item, custom_item_variant);
	}

	/**
	 * Override this to add properties to created item stacks.
	 * Will be called after CustomItemVariant.modify_item_stack.
	 */
	public ItemStack modify_item_stack(ItemStack item_stack) {
		return item_stack;
	}

	public static enum SingleVariant implements ItemVariantEnum {
		SINGLETON;

		@Override
		public String prefix() {
			return "";
		}

		@Override
		public boolean enabled() {
			return true;
		}
	}

	public static class ReverseLookupEntry {

		public CustomItem<?, ?> custom_item;
		public ItemVariantEnum variant;

		public ReverseLookupEntry(CustomItem<?, ?> custom_item, ItemVariantEnum variant) {
			this.custom_item = custom_item;
			this.variant = variant;
		}
	}
}
