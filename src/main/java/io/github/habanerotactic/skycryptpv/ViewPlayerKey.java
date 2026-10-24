package io.github.habanerotactic.skycryptpv;

import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.ChatFormatting;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;

// look at a player and press J
public final class ViewPlayerKey {

	private static final double RANGE = 48.0;
	private static final Pattern USERNAME = Pattern.compile("[A-Za-z0-9_]{1,16}");
	static final KeyMapping.Category CATEGORY =
			KeyMapping.Category.register(Identifier.fromNamespaceAndPath(SkyCryptPV.MOD_ID, "main"));
	private static KeyMapping key;
	@Nullable
	private static Player lookTarget;

	private ViewPlayerKey() {
	}

	public static void register() {
		key = KeyMappingHelper.registerKeyMapping(new KeyMapping(
				"key.skycrypt-pv.view_player", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_J, CATEGORY));
		HudElementRegistry.attachElementAfter(VanillaHudElements.CROSSHAIR,
				Identifier.fromNamespaceAndPath(SkyCryptPV.MOD_ID, "look_hint"), (graphics, delta) -> drawHint(graphics));
		ClientTickEvents.END_CLIENT_TICK.register(mc -> {
			lookTarget = PvConfig.lookHint && mc.gui.screen() == null && !key.isUnbound() ? lookedAtPlayer(mc) : null;
			while (key.consumeClick()) {
				Player target = lookedAtPlayer(mc);
				if (target != null) {
					SkyCryptPV.open(target.getGameProfile().name(), null);
				} else if (mc.player != null) {
					mc.player.sendOverlayMessage(Component.literal("Look at a player to view their SkyCrypt profile"));
				}
			}
		});
	}

	private static void drawHint(GuiGraphicsExtractor graphics) {
		Player target = lookTarget;
		Minecraft mc = Minecraft.getInstance();
		if (target == null) {
			return;
		}
		Component text = Component.literal("Press " + Hint.keyName(key) + " to /pv " + target.getGameProfile().name())
				.withStyle(ChatFormatting.LIGHT_PURPLE);
		Hint.draw(graphics, text, graphics.guiWidth() / 2 - Hint.width(text) / 2, graphics.guiHeight() / 2 + 14);
	}

	// player you're looking at, up to 48 blocks. skips hypixel npcs (not v4 uuids)
	@Nullable
	public static Player lookedAtPlayer(Minecraft mc) {
		if (mc.player == null || mc.level == null) {
			return null;
		}
		Vec3 eye = mc.player.getEyePosition(1.0F);
		Vec3 end = eye.add(mc.player.getViewVector(1.0F).scale(RANGE));
		Player best = null;
		double bestDistance = Double.MAX_VALUE;
		for (Player player : mc.level.players()) {
			if (player == mc.player || !isRealPlayer(player)) {
				continue;
			}
			Optional<Vec3> hit = player.getBoundingBox().inflate(0.3).clip(eye, end);
			if (hit.isPresent()) {
				double distance = hit.get().distanceToSqr(eye);
				if (distance < bestDistance) {
					bestDistance = distance;
					best = player;
				}
			}
		}
		return best;
	}

	private static boolean isRealPlayer(Player player) {
		UUID id = player.getUUID();
		String name = player.getGameProfile().name();
		return id != null && id.version() == 4 && name != null && USERNAME.matcher(name).matches();
	}

}
