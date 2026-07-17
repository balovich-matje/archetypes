package com.archetypes.items;

import java.util.function.Consumer;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import org.jspecify.annotations.Nullable;

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

	@Override
	public void appendHoverText(final ItemStack stack, final Item.TooltipContext context,
			final TooltipDisplay display, final Consumer<Component> lines, final TooltipFlag flag) {
		super.appendHoverText(stack, context, display, lines, flag);
		lines.accept(Component.translatable("item.archetypes.wand.casts")
				.withStyle(ChatFormatting.GRAY));

		if (this.bonusKey != null) {
			lines.accept(Component.translatable(this.bonusKey).withStyle(ChatFormatting.BLUE));
		}
	}
}
