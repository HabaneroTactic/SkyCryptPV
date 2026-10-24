package io.github.habanerotactic.skycryptpv.selftest;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.blaze3d.textures.GpuTexture;
import io.github.habanerotactic.skycryptpv.SkyCryptScreen;
import net.dimaskama.mcef.api.MCEFBrowser;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.input.MouseButtonInfo;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.data.registries.VanillaRegistries;
import net.minecraft.util.Util;
import net.minecraft.world.flag.FeatureFlags;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static net.fabricmc.fabric.api.client.command.v2.ClientCommands.argument;
import static net.fabricmc.fabric.api.client.command.v2.ClientCommands.literal;

// runs /pv (with a competing /pv already registered), then scrolls, zooms, uses the name box
// and takes a screenshot after each step. enable with -Dskycryptpv.selftest=true
public class SelfTest implements ClientModInitializer {

	private static final Logger LOG = LoggerFactory.getLogger("SkyCrypt PV SelfTest");
	private static final String PLAYER = System.getProperty("skycryptpv.selftest.player", "HabaneroTactic");
	private static final String PROFILE = System.getProperty("skycryptpv.selftest.profile", "Blueberry");
	private static final String OTHER_PLAYER = System.getProperty("skycryptpv.selftest.other", "Refraction");
	// optional "x,y" click as fractions of the page, e.g. "0.5,0.3"
	private static final String CLICK = System.getProperty("skycryptpv.selftest.click");
	private static final long TIMEOUT = 600_000;
	// how long to wait for skycrypt to fill in data after loading
	private static final long SETTLE = Long.getLong("skycryptpv.selftest.settle", 10_000);
	// zoom in/out this many times after loading (for profiling)
	private static final int ZOOM_STRESS = Integer.getInteger("skycryptpv.selftest.zoomstress", 0);
	private int stressTicks;

	private int step;
	private long stepStart;
	private long pageReadyAt;
	private int screenshots;

	@Override
	public void onInitializeClient() {
		if (!Boolean.getBoolean("skycryptpv.selftest") || System.getProperty("skycryptpv.selftest.mode") != null) {
			return;
		}
		ClientTickEvents.END_CLIENT_TICK.register(this::tick);
	}

	private void next() {
		step++;
		stepStart = Util.getMillis();
	}

	private long elapsed() {
		return Util.getMillis() - stepStart;
	}

	private void tick(Minecraft mc) {
		try {
			run(mc);
		} catch (Throwable t) {
			LOG.error("SELFTEST FAIL: exception in step {}", step, t);
			mc.stop();
		}
	}

	private void run(Minecraft mc) {
		SkyCryptScreen screen = mc.gui.screen() instanceof SkyCryptScreen s ? s : null;
		switch (step) {
			case 0 -> {
				if (mc.gui.screen() instanceof TitleScreen && mc.gui.overlay() == null) {
					next();
				}
			}
			case 1 -> {
				if (elapsed() < 2000) {
					return;
				}
				runPvCommand();
				next();
			}
			case 2 -> {
				if (screen != null) {
					LOG.info("SELFTEST OK: /pv opened SkyCryptScreen at {}", screen.currentUrl());
					next();
				} else if (elapsed() > 5000) {
					fail(mc, "/pv did not open SkyCryptScreen (screen = " + mc.gui.screen() + ")");
				}
			}
			case 3 -> {
				if (screen == null) {
					fail(mc, "screen closed while loading");
					return;
				}
				MCEFBrowser browser = screen.browser();
				if (browser == null || browser.getTexture() == null || browser.getCefBrowser().isLoading()) {
					pageReadyAt = 0;
					if (elapsed() > TIMEOUT) {
						fail(mc, "page never finished loading");
					}
					return;
				}
				if (pageReadyAt == 0) {
					pageReadyAt = Util.getMillis();
					GpuTexture texture = browser.getTexture();
					LOG.info("SELFTEST page loaded after {} ms: url={} texture={}x{} window={}x{} (fb {}x{}) guiScale={} zoomLevel={}",
							elapsed(), browser.getCefBrowser().getURL(), texture.getWidth(0), texture.getHeight(0),
							mc.getWindow().getScreenWidth(), mc.getWindow().getScreenHeight(),
							mc.getWindow().getWidth(), mc.getWindow().getHeight(),
							mc.getWindow().getGuiScale(), browser.getCefBrowser().getZoomLevel());
				}
				// skycrypt fills in data after the load event
				if (Util.getMillis() - pageReadyAt > SETTLE && stressTicks < ZOOM_STRESS * 20) {
					if (stressTicks == 0) {
						LOG.info("SELFTEST zoomstress start");
					}
					if (stressTicks % 20 == 0) {
						key(screen, (stressTicks / 20) % 2 == 0 ? GLFW.GLFW_KEY_MINUS : GLFW.GLFW_KEY_EQUAL, GLFW.GLFW_MOD_SUPER);
					}
					if (++stressTicks == ZOOM_STRESS * 20) {
						LOG.info("SELFTEST zoomstress end");
					}
					return;
				}
				if (Util.getMillis() - pageReadyAt > SETTLE) {
					screenshot(mc, "loaded");
					screen.mouseMoved(screen.width / 2.0, screen.height / 2.0);
					screen.mouseScrolled(screen.width / 2.0, screen.height / 2.0, 0, -6);
					next();
				}
			}
			case 4 -> {
				if (elapsed() > 2500) {
					screenshot(mc, "scrolled-wheel");
					// trackpad: lots of small deltas
					for (int i = 0; i < 20; i++) {
						screen.mouseScrolled(screen.width / 2.0, screen.height / 2.0, 0, 0.3);
					}
					next();
				}
			}
			case 5 -> {
				if (elapsed() > 2500) {
					screenshot(mc, "scrolled-trackpad-up");
					if (CLICK != null) {
						String[] xy = CLICK.split(",");
						double x = Double.parseDouble(xy[0]) * screen.width;
						double y = 22 + Double.parseDouble(xy[1]) * (screen.height - 22);
						MouseButtonEvent event = new MouseButtonEvent(x, y, new MouseButtonInfo(GLFW.GLFW_MOUSE_BUTTON_LEFT, 0));
						screen.mouseMoved(x, y);
						screen.mouseClicked(event, false);
						screen.mouseReleased(event);
						LOG.info("SELFTEST clicked at gui {},{}", x, y);
					}
					next();
				}
			}
			case 6 -> {
				if (elapsed() > 5000) {
					if (CLICK != null) {
						screenshot(mc, "clicked");
					}
					LOG.info("SELFTEST url after click: {}", screen.currentUrl());
					key(screen, GLFW.GLFW_KEY_MINUS, GLFW.GLFW_MOD_SUPER);
					key(screen, GLFW.GLFW_KEY_MINUS, GLFW.GLFW_MOD_SUPER);
					next();
				}
			}
			case 7 -> {
				if (elapsed() > SETTLE / 2) {
					screenshot(mc, "zoomed-out");
					key(screen, GLFW.GLFW_KEY_0, GLFW.GLFW_MOD_SUPER);
					key(screen, GLFW.GLFW_KEY_L, GLFW.GLFW_MOD_SUPER);
					for (int i = 0; i < 40; i++) {
						key(screen, GLFW.GLFW_KEY_BACKSPACE, 0);
					}
					OTHER_PLAYER.codePoints().forEach(c -> screen.charTyped(new CharacterEvent(c)));
					key(screen, GLFW.GLFW_KEY_ENTER, 0);
					next();
				}
			}
			case 8 -> {
				MCEFBrowser browser = screen.browser();
				boolean loading = browser == null || browser.getCefBrowser().isLoading();
				if (elapsed() > Math.max(12_000, SETTLE) && !loading) {
					screenshot(mc, "other-player");
					LOG.info("SELFTEST url after name box: {}", screen.currentUrl());
					if (!screen.currentUrl().contains("/stats/" + OTHER_PLAYER)) {
						LOG.error("SELFTEST FAIL: name box did not navigate to {}", OTHER_PLAYER);
					}
					key(screen, GLFW.GLFW_KEY_ESCAPE, 0);
					next();
				}
			}
			case 9 -> {
				if (elapsed() > 1500) {
					if (mc.gui.screen() instanceof SkyCryptScreen) {
						LOG.error("SELFTEST FAIL: Esc did not close the screen");
					} else {
						LOG.info("SELFTEST OK: Esc closed the screen");
					}
					LOG.info("SELFTEST DONE ({} screenshots)", screenshots);
					mc.stop();
					next();
				}
			}
			default -> {
			}
		}
	}

	// like when skyblocker registered /pv first
	private void runPvCommand() {
		CommandDispatcher<FabricClientCommandSource> dispatcher = new CommandDispatcher<>();
		dispatcher.register(literal("pv").then(argument("username", StringArgumentType.word()).executes(ctx -> {
			LOG.error("SELFTEST FAIL: the competing /pv ran instead of SkyCrypt's");
			return 0;
		})));
		CommandBuildContext context = CommandBuildContext.simple(VanillaRegistries.createLookup(), FeatureFlags.DEFAULT_FLAGS);
		try {
			ClientCommandRegistrationCallback.EVENT.invoker().register(dispatcher, context);
		} catch (RuntimeException e) {
			LOG.warn("SELFTEST another mod's command listener threw outside a world (expected for some mods)", e);
		}
		try {
			dispatcher.execute("pv " + PLAYER + " " + PROFILE, null);
			LOG.info("SELFTEST OK: dispatched /pv {} {}", PLAYER, PROFILE);
		} catch (Exception e) {
			LOG.error("SELFTEST FAIL: /pv could not be executed", e);
		}
	}

	private static void key(SkyCryptScreen screen, int key, int modifiers) {
		KeyEvent event = new KeyEvent(key, 0, modifiers);
		screen.keyPressed(event);
		screen.keyReleased(event);
	}

	private void screenshot(Minecraft mc, String name) {
		if (mc.gui.screen() instanceof SkyCryptScreen screen) {
			LOG.info("SELFTEST before '{}': last paint {} ms ago, url={}", name, screen.millisSinceLastPaint(), screen.currentUrl());
		}
		String file = "selftest-" + (++screenshots) + "-" + name + ".png";
		Screenshot.grab(mc.gameDirectory, file, mc.gameRenderer.mainRenderTarget(), 1,
				message -> LOG.info("SELFTEST screenshot {}: {}", file, message.getString()));
	}

	private void fail(Minecraft mc, String why) {
		LOG.error("SELFTEST FAIL: {}", why);
		mc.stop();
		step = 99;
	}

}
