package io.github.habanerotactic.skycryptpv;

import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.options.controls.KeyBindsScreen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import org.jetbrains.annotations.Nullable;

import java.util.function.IntConsumer;
import java.util.function.IntFunction;

/** Opened from Mod Menu or /pv settings. Changes are saved when you press Done or Esc. */
public class PvSettingsScreen extends Screen {


	@Nullable
	private final Screen parent;
	private EditBox itemBox;
	private EditBox defaultBox;

	public PvSettingsScreen(@Nullable Screen parent) {
		super(Component.literal("SkyCrypt PV Settings"));
		this.parent = parent;
	}

	@Override
	protected void init() {
		int colWidth = Math.min(170, (width - 30) / 2);
		int left = width / 2 - colWidth - 5;
		int right = width / 2 + 5;
		int top = Math.max(34, height / 2 - 70);

		// Left column: how the viewer looks.
		int y = top;
		addRenderableWidget(CycleButton.booleanBuilder(Component.literal("Simple"), Component.literal("Toolbar"), PvConfig.simpleMode)
				.create(left, y, colWidth, 20, Component.literal("Style"), (button, simple) -> PvConfig.simpleMode = simple))
				.setTooltip(Tooltip.create(Component.literal(
						"Simple: just the page and a small ✕, like Lunar's /pv.\nToolbar: back/forward, player search box, zoom.")));
		y += 24;
		addRenderableWidget(new StepSlider(left, y, colWidth, "Size", 60, 100, 10, PvConfig.panelSize,
				v -> v == 100 ? "Full screen" : v + "%", v -> PvConfig.panelSize = v))
				.setTooltip(Tooltip.create(Component.literal(
						"Below full screen, the page is a centered panel and your game stays visible around it.")));
		y += 24;
		addRenderableWidget(CycleButton.onOffBuilder(PvConfig.roundedCorners)
				.create(left, y, colWidth, 20, Component.literal("Rounded corners"), (button, on) -> PvConfig.roundedCorners = on))
				.setTooltip(Tooltip.create(Component.literal("Only applies when Size is below full screen.")));
		y += 24;
		addRenderableWidget(new StepSlider(left, y, colWidth, "Page zoom", 50, 200, 10, (int) Math.round(PvConfig.zoom * 100),
				v -> v + "%", v -> PvConfig.zoom = v / 100.0));
		y += 24;
		addRenderableWidget(CycleButton.onOffBuilder(PvConfig.hints)
				.create(left, y, colWidth, 20, Component.literal("Hints"), (button, on) -> PvConfig.hints = on))
				.setTooltip(Tooltip.create(Component.literal(
						"Small tooltips saying how to /pv someone: when you hover a name in chat, and under the "
								+ "crosshair when you look at a player.")));
		y += 24;
		addRenderableWidget(CycleButton.onOffBuilder(PvConfig.keepWarm)
				.create(left, y, colWidth, 20, Component.literal("Keep browser warm"), (button, on) -> PvConfig.keepWarm = on))
				.setTooltip(Tooltip.create(Component.literal(
						"ON: the page stays loaded but frozen while closed, so /pv reopens instantly and profiles preload "
								+ "from the right-click menu.\nOFF: the page is shut down 15 s after you close it, so it uses "
								+ "nothing in the background, but /pv takes a few seconds longer.")));
		int leftBottom = y + 20;

		// Right column: ways to open a profile.
		y = top;
		addRenderableWidget(CycleButton.booleanBuilder(Component.literal("SkyCrypt"), Component.literal("Other mods"), PvConfig.overridePv)
				.create(right, y, colWidth, 20, Component.literal("/pv opens"), (button, on) -> PvConfig.overridePv = on))
				.setTooltip(Tooltip.create(Component.literal(
						"SkyCrypt: typing /pv opens this viewer.\nOther mods: /pv goes back to e.g. Skyblocker's own profile viewer "
								+ "(use /skycrypt for this one).\nApplies after you rejoin a server.")));
		y += 24;
		addRenderableWidget(CycleButton.onOffBuilder(PvConfig.chatPlainClick)
				.create(right, y, colWidth, 20, Component.literal("Click names in chat"), (button, on) -> PvConfig.chatPlainClick = on))
				.setTooltip(Tooltip.create(Component.literal(
						"ON: clicking the name of whoever sent a chat message opens their SkyCrypt.\n"
								+ "Turn OFF if another mod uses clicks in chat (e.g. copying messages). Holding your "
								+ "\"click any name\" key (see Keybinds) + click always works.")));
		y += 24;
		defaultBox = new EditBox(font, right + 70, y, colWidth - 70, 20, Component.literal("Plain /pv opens"));
		defaultBox.setMaxLength(16);
		defaultBox.setValue(PvConfig.defaultPlayer);
		defaultBox.setHint(Component.literal("yourself").withStyle(ChatFormatting.DARK_GRAY));
		defaultBox.setTooltip(Tooltip.create(Component.literal("Whose profile /pv opens when you don't type a name. Empty = yours.")));
		addRenderableWidget(defaultBox);
		y += 24;
		itemBox = new EditBox(font, right + 70, y, colWidth - 70, 20, Component.literal("Button item"));
		itemBox.setMaxLength(100);
		itemBox.setValue(PvConfig.buttonItem);
		itemBox.setTooltip(Tooltip.create(Component.literal(
				"Item shown as the button in Hypixel's profile menu, e.g. minecraft:ender_eye or minecraft:player_head")));
		addRenderableWidget(itemBox);
		y += 24;
		addRenderableWidget(Button.builder(Component.literal("Keybinds…"), b -> {
					save();
					minecraft.gui.setScreen(new KeyBindsScreen(this, minecraft.options));
				})
				.bounds(right, y, colWidth, 20)
				.tooltip(Tooltip.create(Component.literal(
						"Opens Minecraft's Key Binds. Under \"SkyCrypt PV\": the look-at-player key and the "
								+ "\"click any name in chat\" key.")))
				.build());

		addRenderableWidget(Button.builder(Component.literal("Done"), b -> onClose())
				.bounds(width / 2 - 75, Math.max(leftBottom, y + 20) + 14, 150, 20)
				.build());
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
		super.extractRenderState(graphics, mouseX, mouseY, partialTick);
		int top = Math.max(34, height / 2 - 70);
		graphics.centeredText(font, title, width / 2, top - 26, 0xFFFFFFFF);
		int colWidth = Math.min(170, (width - 30) / 2);
		graphics.centeredText(font, "Viewer", width / 2 - 5 - colWidth / 2, top - 12, 0xFFAAAAAA);
		graphics.centeredText(font, "Opening profiles", width / 2 + 5 + colWidth / 2, top - 12, 0xFFAAAAAA);
		graphics.text(font, "Default /pv", defaultBox.getX() - 68, defaultBox.getY() + 6, 0xFFE0E0E0);
		graphics.text(font, "Button item", itemBox.getX() - 68, itemBox.getY() + 6, 0xFFE0E0E0);
		if (!validItem(itemBox.getValue())) {
			graphics.text(font, Component.literal("Unknown item").withStyle(ChatFormatting.RED),
					itemBox.getX(), itemBox.getY() + 22, 0xFFFFFFFF);
		}
	}

	@Override
	public void onClose() {
		save();
		minecraft.gui.setScreen(parent);
	}

	private void save() {
		String item = itemBox.getValue().trim();
		PvConfig.buttonItem = validItem(item) ? item : "minecraft:ender_eye";
		ProfileMenuButton.reloadIcon();
		PvConfig.defaultPlayer = defaultBox.getValue().trim();
		PvConfig.save();
	}

	private static boolean validItem(String id) {
		Identifier parsed = Identifier.tryParse(id.trim());
		return parsed != null && BuiltInRegistries.ITEM.containsKey(parsed);
	}

	/** A slider that snaps to steps, so you can drag straight to a value instead of cycling through them. */
	private static final class StepSlider extends AbstractSliderButton {

		private final String label;
		private final int min;
		private final int max;
		private final int step;
		private final IntFunction<String> format;
		private final IntConsumer onChange;

		StepSlider(int x, int y, int width, String label, int min, int max, int step, int initial,
				IntFunction<String> format, IntConsumer onChange) {
			super(x, y, width, 20, Component.empty(), (Math.max(min, Math.min(max, initial)) - min) / (double) (max - min));
			this.label = label;
			this.min = min;
			this.max = max;
			this.step = step;
			this.format = format;
			this.onChange = onChange;
			updateMessage();
		}

		private int current() {
			int raw = (int) Math.round(min + value * (max - min));
			return Math.max(min, Math.min(max, Math.round(raw / (float) step) * step));
		}

		@Override
		protected void updateMessage() {
			setMessage(Component.literal(label + ": " + format.apply(current())));
		}

		@Override
		protected void applyValue() {
			onChange.accept(current());
		}

	}

}
