package io.github.habanerotactic.skycryptpv.selftest;

import com.mojang.authlib.GameProfile;
import io.github.habanerotactic.skycryptpv.ProfileMenuButton;
import io.github.habanerotactic.skycryptpv.PvBrowser;
import io.github.habanerotactic.skycryptpv.SkyCryptPV;
import io.github.habanerotactic.skycryptpv.SkyCryptScreen;
import io.github.habanerotactic.skycryptpv.ViewPlayerKey;
import io.github.habanerotactic.skycryptpv.mixin.AbstractContainerScreenAccessor;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenMouseEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.inventory.ContainerScreen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.input.MouseButtonInfo;
import net.minecraft.client.player.RemotePlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Util;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.minecraft.world.phys.Vec3;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.UUID;

/**
 * -Dskycryptpv.selftest.mode=menu: creates a world, opens a fake Hypixel "<name>'s Profile" menu, clicks the
 * SkyCrypt slot through the same event path a real click takes, checks the look-at-player keybind against a
 * fake player, and times how fast the viewer reopens with the warm browser.
 */
public class MenuSelfTest implements ClientModInitializer {

	private static final Logger LOG = LoggerFactory.getLogger("SkyCrypt PV MenuTest");
	private static final String OWNER = "sssiuaaa";

	private int step;
	private long stepStart;
	private long clickedAt;
	private int shots;
	private RemotePlayer dummy;
	private long dummyAt;
	private long throttledAt;
	private int[] hintPoint;

	@Override
	public void onInitializeClient() {
		if (!Boolean.getBoolean("skycryptpv.selftest") || !"menu".equals(System.getProperty("skycryptpv.selftest.mode"))) {
			return;
		}
		ClientTickEvents.END_CLIENT_TICK.register(mc -> {
			try {
				run(mc);
			} catch (Throwable t) {
				LOG.error("MENUTEST FAIL: exception in step {}", step, t);
				mc.stop();
			}
		});
	}

	private void next() {
		step++;
		stepStart = Util.getMillis();
	}

	private long elapsed() {
		return Util.getMillis() - stepStart;
	}

	private void run(Minecraft mc) {
		switch (step) {
			case 0 -> {
				if (mc.gui.screen() instanceof TitleScreen && mc.gui.overlay() == null) {
					String id = "pvtest-" + System.currentTimeMillis();
					mc.createWorldOpenFlows().createFreshLevel(id,
							new LevelSettings(id, GameType.CREATIVE, LevelSettings.DifficultySettings.DEFAULT, true, WorldDataConfiguration.DEFAULT),
							WorldOptions.testWorldWithRandomSeed(), WorldPresets::createTestWorldDimensions, new TitleScreen());
					next();
				}
			}
			case 1 -> {
				// In the world, Chromium ready, and the background warm-up had a moment to start.
				if (mc.player != null && mc.level != null && mc.gui.screen() == null && PvBrowser.isReady() && elapsed() > 3000) {
					LOG.info("MENUTEST in world; warm browser exists: {}", PvBrowser.get() != null);
					openFakeProfileMenu(mc);
					next();
				}
			}
			case 2 -> {
				if (elapsed() > 2500 && mc.gui.screen() instanceof ContainerScreen screen) {
					Slot slot = ProfileMenuButton.buttonSlot(screen);
					LOG.info("MENUTEST owner parsed={} buttonSlot={} prefetching={}",
							ProfileMenuButton.profileOwner(screen.getTitle()), slot == null ? null : slot.index,
							PvBrowser.get() == null ? null : PvBrowser.get().getCefBrowser().getURL());
					shot(mc, "profile-menu");
					if (slot == null) {
						fail(mc, "no slot for the SkyCrypt button");
						return;
					}
					next();
				}
			}
			case 3 -> {
				if (elapsed() > 1000 && mc.gui.screen() instanceof ContainerScreen screen) {
					Slot slot = ProfileMenuButton.buttonSlot(screen);
					AbstractContainerScreenAccessor pos = (AbstractContainerScreenAccessor) screen;
					MouseButtonEvent click = new MouseButtonEvent(pos.skycryptpv$getLeftPos() + slot.x + 8,
							pos.skycryptpv$getTopPos() + slot.y + 8, new MouseButtonInfo(GLFW.GLFW_MOUSE_BUTTON_LEFT, 0));
					// Same order as MouseHandler: Fabric's allow event first, then the screen.
					boolean allowed = ScreenMouseEvents.allowMouseClick(screen).invoker().allowMouseClick(screen, click);
					if (allowed) {
						screen.mouseClicked(click, false);
						fail(mc, "the SkyCrypt slot click was not intercepted");
						return;
					}
					clickedAt = Util.getMillis();
					next();
				}
			}
			case 4 -> {
				if (mc.gui.screen() instanceof SkyCryptScreen screen) {
					boolean painted = screen.browser() != null && screen.browser().getTexture() != null
							&& PvBrowser.pendingUrl() == null;
					if (painted && elapsed() > 300) {
						LOG.info("MENUTEST OK: viewer open {} ms after click, url={}", Util.getMillis() - clickedAt, screen.currentUrl());
						if (!screen.currentUrl().contains("/stats/" + OWNER)) {
							LOG.error("MENUTEST FAIL: viewer is not showing {}", OWNER);
						}
						next();
					}
				} else if (elapsed() > 60_000) {
					fail(mc, "clicking the slot did not show the profile (screen = " + mc.gui.screen() + ")");
				}
			}
			case 5 -> {
				if (elapsed() > 12_000) {
					shot(mc, "viewer-from-menu");
					mc.gui.setScreen(null);
					next();
				}
			}
			case 6 -> {
				long now = Util.getMillis();
				if (elapsed() < 1000) {
					return;
				}
				if (dummy == null) {
					testKeybindTargeting(mc);
					dummyAt = now;
					return;
				}
				if (throttledAt == 0) {
					if (now - dummyAt < 1500) {
						return;
					}
					shot(mc, "look-hint");
					dummy.discard();
					// Throttle the hidden page like after 15 s idle, then check reopening brings it back.
					PvBrowser.setActive(false);
					throttledAt = now;
					return;
				}
				if (now - throttledAt < 4000) {
					return;
				}
				clickedAt = now;
				SkyCryptPV.open(OWNER, null);
				next();
			}
			case 7 -> {
				if (mc.gui.screen() instanceof SkyCryptScreen screen && screen.browser() != null
						&& screen.browser().getTexture() != null && PvBrowser.pendingUrl() == null
						&& Util.getMillis() - clickedAt > 300 && PvBrowser.millisSinceLastPaint() < 500) {
					LOG.info("MENUTEST OK: reopen showed the page {} ms after /pv (loading={})",
							Util.getMillis() - clickedAt, screen.browser().getCefBrowser().isLoading());
					next();
				} else if (elapsed() > 5000) {
					fail(mc, "reopen after throttling: page did not paint again (stuck)");
				}
			}
			case 8 -> {
				if (elapsed() > 1500) {
					shot(mc, "reopened");
					LOG.info("MENUTEST DONE");
					next();
				}
			}
			case 9 -> {
				if (elapsed() > 1500) {
					SkyCryptPV.open("HabaneroTactic", "Blueberry");
					next();
				}
			}
			case 10 -> {
				if (elapsed() > 25_000 && mc.gui.screen() instanceof SkyCryptScreen screen) {
					key(screen, GLFW.GLFW_KEY_F, GLFW.GLFW_MOD_SUPER);
					"SkyCrypt".codePoints().forEach(c -> screen.charTyped(new net.minecraft.client.input.CharacterEvent(c)));
					key(screen, GLFW.GLFW_KEY_ENTER, 0);
					next();
				}
			}
			case 11 -> {
				if (elapsed() > 2000) {
					shot(mc, "find");
					mc.gui.setScreen(null);
					var chat = mc.gui.hud.getChat();
					chat.addClientSystemMessage(Component.literal("Bank Interest: 1,000 coins"));
					chat.addClientSystemMessage(Component.literal("Guild > [MVP+] HabaneroTactic [Vet]: anyone doing kuudra"));
					chat.addClientSystemMessage(Component.literal("Party Finder > Refraction joined the dungeon group! (Mage Level 50)"));
					next();
				}
			}
			case 12 -> {
				if (elapsed() > 500) {
					mc.gui.hud.getChat().openScreen(net.minecraft.client.gui.components.ChatComponent.ChatMethod.MESSAGE, net.minecraft.client.gui.screens.ChatScreen::new);
					next();
				}
			}
			case 13 -> {
				if (elapsed() > 1000 && mc.gui.screen() instanceof net.minecraft.client.gui.screens.ChatScreen screen) {
					shot(mc, "chat");
					LOG.info("MENUTEST chat plain-click opens: {}", sweepChat(mc, screen, 0));
					LOG.info("MENUTEST chat cmd-click opens: {}", sweepChat(mc, screen, GLFW.GLFW_MOD_SUPER));
					if (hintPoint != null) {
						double toScreen = mc.getWindow().getGuiScale() * mc.getWindow().getScreenWidth() / (double) mc.getWindow().getWidth();
						try {
							for (String field : new String[]{"xpos", "ypos"}) {
								java.lang.reflect.Field f = net.minecraft.client.MouseHandler.class.getDeclaredField(field);
								f.setAccessible(true);
								f.setDouble(mc.mouseHandler, (field.equals("xpos") ? hintPoint[0] + 1 : hintPoint[1] + 1) * toScreen);
							}
						} catch (ReflectiveOperationException e) {
							LOG.warn("couldn't move the mouse", e);
						}
					}
					next();
				}
			}
			case 14 -> {
				if (elapsed() > 1500) {
					shot(mc, "chat-hint");
					mc.gui.setScreen(null);
					mc.gui.setScreen(new io.github.habanerotactic.skycryptpv.PvSettingsScreen(null));
					// Park the cursor in the corner so no button tooltip covers the screenshot.
					try {
						for (String field : new String[]{"xpos", "ypos"}) {
							java.lang.reflect.Field f = net.minecraft.client.MouseHandler.class.getDeclaredField(field);
							f.setAccessible(true);
							f.setDouble(mc.mouseHandler, 1);
						}
					} catch (ReflectiveOperationException e) {
						LOG.warn("couldn't move the mouse", e);
					}
					next();
				}
			}
			case 15 -> {
				if (elapsed() > 1500) {
					shot(mc, "settings");
					next();
				}
			}
			case 16 -> {
				if (elapsed() > 1500) {
					mc.stop();
					next();
				}
			}
			default -> {
			}
		}
	}

	private void openFakeProfileMenu(Minecraft mc) {
		SimpleContainer contents = new SimpleContainer(54);
		contents.setItem(2, new ItemStack(Items.DIAMOND_SWORD));
		for (int row = 1; row <= 4; row++) {
			contents.setItem(row * 9 + 1, new ItemStack(Items.EMERALD_BLOCK));
			contents.setItem(row * 9 + 2, new ItemStack(Items.GOLDEN_CHESTPLATE));
		}
		contents.setItem(15, new ItemStack(Items.FEATHER));
		contents.setItem(16, new ItemStack(Items.EMERALD));
		contents.setItem(33, new ItemStack(Items.ENDER_CHEST));
		contents.setItem(49, new ItemStack(Items.BARRIER));
		// Like Hypixel: every empty slot holds a nameless glass pane (invisible with many resource packs).
		for (int i = 0; i < 54; i++) {
			if (contents.getItem(i).isEmpty()) {
				ItemStack filler = new ItemStack(Items.GLASS_PANE);
				filler.set(net.minecraft.core.component.DataComponents.CUSTOM_NAME, Component.literal(" "));
				contents.setItem(i, filler);
			}
		}
		ChestMenu menu = ChestMenu.sixRows(1, mc.player.getInventory(), contents);
		mc.player.containerMenu = menu;
		mc.gui.setScreen(new ContainerScreen(menu, mc.player.getInventory(), Component.literal(OWNER + "'s Profile")));
	}

	private void testKeybindTargeting(Minecraft mc) {
		RemotePlayer dummy = new RemotePlayer(mc.level, new GameProfile(UUID.randomUUID(), "Refraction"));
		Vec3 look = mc.player.getViewVector(1.0F);
		Vec3 spot = mc.player.getEyePosition(1.0F).add(look.scale(6)).subtract(0, dummy.getEyeHeight(), 0);
		dummy.setPos(spot.x, spot.y, spot.z);
		dummy.setId(-424242);
		mc.level.addEntity(dummy);
		Player target = ViewPlayerKey.lookedAtPlayer(mc);
		String name = target == null ? null : target.getGameProfile().name();
		if ("Refraction".equals(name)) {
			LOG.info("MENUTEST OK: keybind targets the player in view ({})", name);
		} else {
			LOG.error("MENUTEST FAIL: keybind target was {}", name);
		}
		this.dummy = dummy;
	}

	/** Clicks across the bottom of the chat; returns which names a click would open (without opening them). */
	private java.util.Set<String> sweepChat(Minecraft mc, net.minecraft.client.gui.screens.Screen screen, int modifiers) {
		java.util.Set<String> opened = new java.util.TreeSet<>();
		SkyCryptPV.openInterceptor = name -> opened.add(name) || true;
		int[] point = new int[2];
		int h = screen.height;
		for (int y = h - 70; y < h - 14; y += 2) {
			for (int x = 1; x < 320; x += 2) {
				MouseButtonEvent click = new MouseButtonEvent(x, y, new MouseButtonInfo(GLFW.GLFW_MOUSE_BUTTON_LEFT, modifiers));
				point[0] = x;
				point[1] = y;
				int before = opened.size();
				ScreenMouseEvents.allowMouseClick(screen).invoker().allowMouseClick(screen, click);
				if (modifiers == 0 && opened.size() > before && opened.contains("Refraction") && hintPoint == null) {
					hintPoint = new int[]{x, y};
				}
			}
		}
		SkyCryptPV.openInterceptor = null;
		return opened;
	}

	private static void key(net.minecraft.client.gui.screens.Screen screen, int key, int modifiers) {
		net.minecraft.client.input.KeyEvent event = new net.minecraft.client.input.KeyEvent(key, 0, modifiers);
		screen.keyPressed(event);
		screen.keyReleased(event);
	}

	private void shot(Minecraft mc, String name) {
		String file = "menutest-" + (++shots) + "-" + name + ".png";
		Screenshot.grab(mc.gameDirectory, file, mc.gameRenderer.mainRenderTarget(), 1,
				message -> LOG.info("MENUTEST screenshot {}", file));
	}

	private void fail(Minecraft mc, String why) {
		LOG.error("MENUTEST FAIL: {}", why);
		mc.stop();
		step = 99;
	}

}
