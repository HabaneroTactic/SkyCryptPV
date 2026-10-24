package io.github.habanerotactic.skycryptpv;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

public class SkyCryptPV implements ClientModInitializer {

	public static final String MOD_ID = "skycrypt-pv";
	public static final Logger LOGGER = LoggerFactory.getLogger("SkyCrypt PV");
	public static final String SITE = "https://sky.shiiyu.moe";

	private static boolean warmedUp;
	private static int quietTicks;
	/** Test hook: return true to swallow an open request. */
	@Nullable
	public static java.util.function.Predicate<String> openInterceptor;

	@Override
	public void onInitializeClient() {
		PvConfig.load();
		PvCommand.register();
		ProfileMenuButton.register();
		ViewPlayerKey.register();
		ChatNameClick.register();
		PvBrowser.registerShutdown();
		// Once in a world, open Chromium in the background with your own profile, so the first /pv is fast.
		ClientTickEvents.END_CLIENT_TICK.register(mc -> {
			// Loading Chromium swaps out the process's memory allocator, which can corrupt memory on macOS if other
			// threads are busy at that moment (it crashed ~1 in 5 launches during startup). So wait until the
			// loading screen is gone and the game has been idle for a few seconds.
			if (!PvBrowser.isStarted()) {
				quietTicks = mc.gui.overlay() == null ? quietTicks + 1 : 0;
				if (quietTicks >= 60) {
					PvBrowser.start();
				}
			}
			PvBrowser.tick();
			if (!warmedUp && PvConfig.keepWarm && mc.player != null && PvBrowser.isReady()) {
				warmedUp = true;
				PvBrowser.prefetch(statsUrl(mc.getUser().getName(), null));
			}
		});
	}

	public static String statsUrl(String player, @Nullable String profile) {
		String url = SITE + "/stats/" + encode(player);
		if (profile != null && !profile.isBlank()) {
			url += "/" + encode(profile);
		}
		return url;
	}

	/**
	 * Opens the viewer on the next client tick. Opening it straight from a command handler would be
	 * undone by the chat screen closing itself right after the command is sent.
	 */
	public static void open(String player, @Nullable String profile) {
		if (openInterceptor != null && openInterceptor.test(player)) {
			return;
		}
		Minecraft mc = Minecraft.getInstance();
		String url = statsUrl(player, profile);
		mc.schedule(() -> {
			if (mc.gui.screen() instanceof SkyCryptScreen screen) {
				screen.load(url);
			} else {
				mc.gui.setScreen(new SkyCryptScreen(url));
			}
		});
	}

	private static String encode(String s) {
		return URLEncoder.encode(s.trim(), StandardCharsets.UTF_8).replace("+", "%20");
	}

}
