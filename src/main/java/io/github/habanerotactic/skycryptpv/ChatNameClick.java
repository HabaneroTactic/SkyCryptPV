package io.github.habanerotactic.skycryptpv;

import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenMouseEvents;
import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Util;
import net.minecraft.client.gui.ActiveTextCollector;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.TextAlignment;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.util.FormattedCharSequence;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix3x2f;
import org.joml.Matrix3x2fc;
import org.joml.Vector2f;
import org.lwjgl.glfw.GLFW;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Click a player's name in chat to open their SkyCrypt page, like NEU did.
 * <ul>
 *   <li>Holding the "click any name" key (Left Cmd/Ctrl by default, rebindable) + click works on any name.</li>
 *   <li>A plain click works on a message's sender name (party, guild, DMs, public chat, Party Finder joins), but
 *   only if Hypixel hasn't attached its own click action to it, so Hypixel's own features keep working.</li>
 * </ul>
 */
public final class ChatNameClick {

	private static final Pattern NAME = Pattern.compile("[A-Za-z0-9_]{3,16}");
	/** Where the sender's name sits in Hypixel's chat formats; group 1 is the name. */
	private static final List<Pattern> SENDER_PATTERNS = List.of(
			// "Party Finder > Name joined the dungeon group! (Mage Level 40)"
			Pattern.compile("^Party Finder > ([A-Za-z0-9_]{1,16}) joined the"),
			// "[MVP+] Name joined the party." / "Name has left the party."
			Pattern.compile("^(?:\\[[^]]+] )?([A-Za-z0-9_]{1,16}) (?:joined|has left|left) the party"),
			// "Party > [MVP+] Name: hi", "Guild > Name [MOD]: hi", "From [VIP] Name: hi", "[287] ✿ [MVP+] Name: hi"
			Pattern.compile("^(?:(?:Party|Guild|Officer|Co-op) > |From |To |\\[\\d+] )(?:[^\\sA-Za-z0-9_\\[]+ )?(?:\\[[^]]+] )?([A-Za-z0-9_]{1,16})(?: \\[[^]]+])?: "),
			// Lobby chat: "[MVP+] Name: hi"
			Pattern.compile("^\\[[A-Z]+\\+*] ([A-Za-z0-9_]{1,16}): ")
	);

	private ChatNameClick() {
	}

	private static KeyMapping holdKey;

	public static void register() {
		boolean mac = Util.getPlatform() == Util.OS.OSX;
		holdKey = KeyMappingHelper.registerKeyMapping(new KeyMapping("key.skycrypt-pv.chat_click", InputConstants.Type.KEYSYM,
				mac ? GLFW.GLFW_KEY_LEFT_SUPER : GLFW.GLFW_KEY_LEFT_CONTROL, ViewPlayerKey.CATEGORY));
		ScreenEvents.AFTER_INIT.register((client, screen, width, height) -> {
			if (screen instanceof ChatScreen) {
				ScreenMouseEvents.allowMouseClick(screen).register((s, event) -> !handleClick(client, event));
				ScreenEvents.afterExtract(screen).register((s, graphics, mouseX, mouseY, partialTick) ->
						drawHint(client, s, graphics, mouseX, mouseY));
			}
		});
	}

	/** Returns true if the click opened a profile (and should not reach the chat screen). */
	private static boolean handleClick(Minecraft mc, MouseButtonEvent event) {
		if (event.button() != GLFW.GLFW_MOUSE_BUTTON_LEFT) {
			return false;
		}
		LineHit hit = lineAt(mc, (int) event.x(), (int) event.y());
		if (hit == null) {
			return false;
		}
		String word = wordAt(hit.text, hit.index);
		if (word == null || !NAME.matcher(word).matches()) {
			return false;
		}
		if (holdKeyDown(mc) || plainClickOpens(hit)) {
			SkyCryptPV.open(word, null);
			return true;
		}
		return false;
	}

	/** Whether the "hold to click any name" key is down; any key or mouse button can be bound to it. */
	private static boolean holdKeyDown(Minecraft mc) {
		if (holdKey.isUnbound()) {
			return false;
		}
		InputConstants.Key bound = KeyMappingHelper.getBoundKeyOf(holdKey);
		long window = mc.getWindow().handle();
		return bound.getType() == InputConstants.Type.MOUSE
				? GLFW.glfwGetMouseButton(window, bound.getValue()) == GLFW.GLFW_PRESS
				: InputConstants.isKeyDown(mc.getWindow(), bound.getValue());
	}

	/** A plain click opens the sender's name, unless Hypixel already made that name do something. */
	private static boolean plainClickOpens(LineHit hit) {
		boolean hasOwnAction = hit.style != null && hit.style.getClickEvent() != null;
		return PvConfig.chatPlainClick && !hasOwnAction && isSenderName(hit.text, hit.index);
	}

	/**
	 * While hovering a player's name, shows how to open their profile in a tooltip-style box just above the cursor,
	 * so it sits above (not on top of) Hypixel's own rank tooltip.
	 */
	private static void drawHint(Minecraft mc, Screen screen, GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
		if (!PvConfig.chatHint) {
			return;
		}
		LineHit hit = lineAt(mc, mouseX, mouseY);
		String word = hit == null ? null : wordAt(hit.text, hit.index);
		if (word == null || !NAME.matcher(word).matches()) {
			return;
		}
		Component text;
		if (plainClickOpens(hit) || holdKeyDown(mc)) {
			text = Component.literal("Click to /pv " + word).withStyle(ChatFormatting.LIGHT_PURPLE);
		} else if (!holdKey.isUnbound() && (isSenderName(hit.text, hit.index) || PvCommand.isKnownPlayer(word))) {
			text = Component.literal("Hold " + Hint.keyName(holdKey) + " + click to /pv " + word).withStyle(ChatFormatting.LIGHT_PURPLE);
		} else {
			return;
		}
		Hint.draw(graphics, text, mouseX + 12, mouseY - 24);
	}

	private static boolean isSenderName(String line, int index) {
		for (Pattern pattern : SENDER_PATTERNS) {
			Matcher m = pattern.matcher(line);
			if (m.find() && index >= m.start(1) && index < m.end(1)) {
				return true;
			}
		}
		return false;
	}

	@Nullable
	private static String wordAt(String text, int index) {
		if (index < 0 || index >= text.length() || !isNameChar(text.charAt(index))) {
			return null;
		}
		int start = index;
		int end = index;
		while (start > 0 && isNameChar(text.charAt(start - 1))) {
			start--;
		}
		while (end < text.length() && isNameChar(text.charAt(end))) {
			end++;
		}
		return text.substring(start, end);
	}

	private static boolean isNameChar(char c) {
		return c == '_' || (c < 128 && Character.isLetterOrDigit(c));
	}

	private record LineHit(String text, int index, @Nullable Style style) {
	}

	/** Asks the chat to lay out its visible lines and finds the line and character under the cursor. */
	@Nullable
	private static LineHit lineAt(Minecraft mc, int mouseX, int mouseY) {
		Font font = mc.font;
		LineFinder finder = new LineFinder(font, mouseX, mouseY);
		ChatComponent chat = mc.gui.hud.getChat();
		chat.captureClickableText(finder, mc.getWindow().getGuiScaledHeight(), mc.gui.hud.getGuiTicks(),
				ChatComponent.DisplayMode.FOREGROUND);
		return finder.hit;
	}

	private static final class LineFinder implements ActiveTextCollector {

		private final Font font;
		private final int mouseX;
		private final int mouseY;
		private Parameters parameters = new Parameters(new Matrix3x2f());
		@Nullable
		private LineHit hit;

		private LineFinder(Font font, int mouseX, int mouseY) {
			this.font = font;
			this.mouseX = mouseX;
			this.mouseY = mouseY;
		}

		@Override
		public Parameters defaultParameters() {
			return parameters;
		}

		@Override
		public void defaultParameters(Parameters parameters) {
			this.parameters = parameters;
		}

		@Override
		public void accept(TextAlignment alignment, int x, int y, Parameters params, FormattedCharSequence text) {
			if (hit != null) {
				return;
			}
			// Chat lines are drawn scaled/translated; map the cursor into the line's own coordinates.
			Matrix3x2fc pose = params.pose();
			Vector2f local = new Matrix3x2f(pose).invert().transformPosition(new Vector2f(mouseX, mouseY));
			if (local.y < y || local.y >= y + font.lineHeight) {
				return;
			}
			int left = alignment.calculateLeft(x, font, text);
			StringBuilder plain = new StringBuilder();
			float[] cursor = {left};
			int[] index = {-1};
			Style[] style = {null};
			text.accept((pos, charStyle, codepoint) -> {
				float width = font.width(FormattedCharSequence.codepoint(codepoint, charStyle));
				if (index[0] < 0 && local.x >= cursor[0] && local.x < cursor[0] + width) {
					index[0] = plain.length();
					style[0] = charStyle;
				}
				cursor[0] += width;
				plain.appendCodePoint(codepoint);
				return true;
			});
			if (index[0] >= 0) {
				hit = new LineHit(plain.toString(), index[0], style[0]);
			}
		}

		@Override
		public void acceptScrolling(Component text, int x, int left, int right, int top, int bottom, Parameters params) {
		}

	}

}
