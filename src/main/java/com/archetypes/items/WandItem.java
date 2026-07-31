package com.archetypes.items;

import java.util.function.Consumer;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
//? if >=1.21.11 {
import net.minecraft.world.item.component.TooltipDisplay;
//?}
//? if >=1.21.11 {
import org.jspecify.annotations.Nullable;
//?} else {
/*import org.jetbrains.annotations.Nullable;
*///?}

/**
 * A casting focus. The tooltip says what every wand does (cast) and what
 * this one adds (the bonus line) — bonuses read the MAIN hand only, so an
 * offhand wand contributes nothing and never doubles up.
 */
public class WandItem extends Item {
	private final @Nullable String bonusKey;

	public WandItem(final Properties properties, final @Nullable String bonusKey) {
		super(properties);
		this.bonusKey = bonusKey;
	}

	// 1.21.11 gave `appendHoverText` the TooltipDisplay parameter and swapped the
	// `List<Component>` sink for a `Consumer<Component>`. `List::add` IS a
	// `Consumer<Component>`, so the legacy arm hands the list through under the shared
	// name and every `lines.accept(...)` below stays one implementation.
	//? if >=1.21.11 {
	@Override
	public void appendHoverText(final ItemStack stack, final Item.TooltipContext context,
			final TooltipDisplay display, final Consumer<Component> lines, final TooltipFlag flag) {
		super.appendHoverText(stack, context, display, lines, flag);
	//?} elif >=1.20.5 {
	/*@Override
	public void appendHoverText(final ItemStack stack, final Item.TooltipContext context,
			final java.util.List<Component> list, final TooltipFlag flag) {
		super.appendHoverText(stack, context, list, flag);
		Consumer<Component> lines = list::add;
	*///?} else {
	/*// STAGE 5: `Item.TooltipContext` arrived with the component rework; below it the
	// second parameter is the nullable LEVEL the tooltip is being drawn in. Neither arm
	// reads it, so this is a parameter-list move and nothing else.
	@Override
	public void appendHoverText(final ItemStack stack,
			final net.minecraft.world.level.@org.jetbrains.annotations.Nullable Level level,
			final java.util.List<Component> list, final TooltipFlag flag) {
		super.appendHoverText(stack, level, list, flag);
		Consumer<Component> lines = list::add;
	*///?}
		lines.accept(Component.translatable("item.archetypes.wand.casts")
				.withStyle(ChatFormatting.GRAY));

		if (this.bonusKey != null) {
			lines.accept(Component.translatable(this.bonusKey).withStyle(ChatFormatting.BLUE));
		}
	}
}
