package arcane.entity;

import net.minecraft.component.DataComponentTypes;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;

/** Вспомогательное: предмет с эффектом зачарования (светится). */
final class Gear {
	private Gear() {
	}

	static ItemStack glint(Item item) {
		ItemStack stack = new ItemStack(item);
		stack.set(DataComponentTypes.ENCHANTMENT_GLINT_OVERRIDE, true);
		return stack;
	}
}
