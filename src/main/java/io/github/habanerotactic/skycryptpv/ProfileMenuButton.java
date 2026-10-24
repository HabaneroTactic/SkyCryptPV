package io.github.habanerotactic.skycryptpv;

import io.github.habanerotactic.skycryptpv.mixin.AbstractContainerScreenAccessor;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenMouseEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.ContainerScreen;
import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.StringUtil;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

// adds the pv button to hypixel's "<name>'s Profile" menu, under the ender chest like NEU
public final class ProfileMenuButton {

	private static final Pattern TITLE = Pattern.compile("([A-Za-z0-9_]{1,16})['’]s Profile");
	// fallback slot if the ender chest isn't found
	private static final int FALLBACK_SLOT = 42;
	@Nullable
	private static ItemStack icon;

	private ProfileMenuButton() {
	}

	public static void register() {
		ScreenEvents.AFTER_INIT.register((client, screen, width, height) -> {
			if (!(screen instanceof ContainerScreen container)) {
				return;
			}
			String player = profileOwner(screen.getTitle());
			if (player == null) {
				return;
			}
			// only preload if keep browser warm is on
			if (PvConfig.keepWarm) {
				PvBrowser.prefetch(SkyCryptPV.statsUrl(player, null));
			}
			// draw with the background so item tooltips stay on top
			List<Component> tooltip = List.of(Component.literal("Click to run /pv " + player).withStyle(ChatFormatting.LIGHT_PURPLE));
			ScreenEvents.afterBackground(screen).register((s, graphics, mouseX, mouseY, partialTick) ->
					draw(container, graphics, mouseX, mouseY, tooltip));
			ScreenMouseEvents.allowMouseClick(screen).register((s, event) -> {
				if (buttonSlot(container) instanceof Slot slot && isOver(container, slot, event.x(), event.y())) {
					Minecraft mc = Minecraft.getInstance();
					if (mc.player != null) {
						mc.player.closeContainer();
					}
					SkyCryptPV.open(player, null);
					return false;
				}
				return true;
			});
		});
	}

	// "sssiuaaa's Profile" -> "sssiuaaa"
	@Nullable
	public static String profileOwner(Component title) {
		Matcher matcher = TITLE.matcher(StringUtil.stripColor(title.getString()));
		return matcher.find() ? matcher.group(1) : null;
	}

	@Nullable
	public static Slot buttonSlot(ContainerScreen screen) {
		ChestMenu menu = screen.getMenu();
		int size = menu.getRowCount() * 9;
		List<Slot> slots = menu.slots;
		for (int i = 0; i + 9 < size; i++) {
			if (isEnderChest(slots.get(i).getItem()) && isFree(slots.get(i + 9))) {
				return slots.get(i + 9);
			}
		}
		return FALLBACK_SLOT < size && isFree(slots.get(FALLBACK_SLOT)) ? slots.get(FALLBACK_SLOT) : null;
	}

	private static boolean isEnderChest(ItemStack stack) {
		return stack.is(Items.ENDER_CHEST) || StringUtil.stripColor(stack.getHoverName().getString()).contains("Ender Chest");
	}

	// hypixel fills empty slots with glass panes (often invisible with resource packs), count those as empty
	private static boolean isFree(Slot slot) {
		ItemStack stack = slot.getItem();
		if (stack.isEmpty()) {
			return true;
		}
		String name = StringUtil.stripColor(stack.getHoverName().getString()).trim();
		String id = BuiltInRegistries.ITEM.getKey(stack.getItem()).getPath();
		return name.isEmpty() || id.endsWith("glass_pane");
	}

	public static void reloadIcon() {
		icon = null;
	}

	private static ItemStack icon() {
		if (icon == null) {
			Identifier id = Identifier.tryParse(PvConfig.buttonItem);
			icon = id != null && BuiltInRegistries.ITEM.containsKey(id)
					? new ItemStack(BuiltInRegistries.ITEM.getValue(id))
					: new ItemStack(Items.ENDER_EYE);
		}
		return icon;
	}

	private static void draw(ContainerScreen screen, GuiGraphicsExtractor graphics, int mouseX, int mouseY, List<Component> tooltip) {
		Slot slot = buttonSlot(screen);
		if (slot == null) {
			return;
		}
		AbstractContainerScreenAccessor pos = (AbstractContainerScreenAccessor) screen;
		int x = pos.skycryptpv$getLeftPos() + slot.x;
		int y = pos.skycryptpv$getTopPos() + slot.y;
		graphics.item(icon(), x, y);
		if (isOver(screen, slot, mouseX, mouseY)) {
			graphics.setTooltipForNextFrame(Minecraft.getInstance().font, tooltip, Optional.empty(), mouseX, mouseY);
		}
	}

	private static boolean isOver(Screen screen, Slot slot, double mouseX, double mouseY) {
		AbstractContainerScreenAccessor pos = (AbstractContainerScreenAccessor) screen;
		int x = pos.skycryptpv$getLeftPos() + slot.x;
		int y = pos.skycryptpv$getTopPos() + slot.y;
		return mouseX >= x - 1 && mouseX < x + 17 && mouseY >= y - 1 && mouseY < y + 17;
	}

}
