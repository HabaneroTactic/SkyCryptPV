package io.github.habanerotactic.skycryptpv;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.tooltip.TooltipRenderUtil;
import net.minecraft.network.chat.Component;

/** Draws a small hint in the same box style as item tooltips. */
public final class Hint {

	private Hint() {
	}

	/** Draws the box with its text starting at (x, y), kept on screen. */
	public static void draw(GuiGraphicsExtractor graphics, Component text, int x, int y) {
		Font font = Minecraft.getInstance().font;
		int w = font.width(text);
		int h = 8;
		x = Math.max(4, Math.min(x, graphics.guiWidth() - w - 4));
		y = Math.max(4, Math.min(y, graphics.guiHeight() - h - 4));
		TooltipRenderUtil.extractTooltipBackground(graphics, x, y, w, h, null);
		graphics.text(font, text, x, y, 0xFFFFFFFF);
	}

	public static int width(Component text) {
		return Minecraft.getInstance().font.width(text);
	}

	/** A key's name as shown in Controls, e.g. "J" or "Left Cmd". */
	public static String keyName(net.minecraft.client.KeyMapping key) {
		return key.getTranslatedKeyMessage().getString();
	}

}
