package io.github.habanerotactic.skycryptpv.selftest;

import io.github.habanerotactic.skycryptpv.PvBrowser;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.util.Util;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** -Dskycryptpv.selftest.mode=fps: average FPS with the background page active vs frozen. */
public class FpsSelfTest implements ClientModInitializer {

	private static final Logger LOG = LoggerFactory.getLogger("SkyCrypt PV FpsTest");
	private int step;
	private long stepStart;
	private long fpsSum;
	private int samples;
	private int lastSecond = -1;
	private boolean loadedReal;

	@Override
	public void onInitializeClient() {
		if (!Boolean.getBoolean("skycryptpv.selftest") || !"fps".equals(System.getProperty("skycryptpv.selftest.mode"))) {
			return;
		}
		ClientTickEvents.END_CLIENT_TICK.register(this::run);
	}

	private void next() {
		step++;
		stepStart = Util.getMillis();
		fpsSum = 0;
		samples = 0;
	}

	private long elapsed() {
		return Util.getMillis() - stepStart;
	}

	private void sample(Minecraft mc) {
		int second = (int) (elapsed() / 1000);
		if (second != lastSecond && elapsed() > 2000) {
			lastSecond = second;
			fpsSum += mc.getFps();
			samples++;
		}
	}

	private void run(Minecraft mc) {
		switch (step) {
			case 0 -> {
				if (mc.gui.screen() instanceof TitleScreen && mc.gui.overlay() == null) {
					mc.options.enableVsync().set(false);
					mc.options.framerateLimit().set(260);
					String id = "fpstest-" + System.currentTimeMillis();
					mc.createWorldOpenFlows().createFreshLevel(id,
							new LevelSettings(id, GameType.CREATIVE, LevelSettings.DifficultySettings.DEFAULT, true, WorldDataConfiguration.DEFAULT),
							WorldOptions.testWorldWithRandomSeed(), WorldPresets::createTestWorldDimensions, new TitleScreen());
					next();
				}
			}
			case 1 -> {
				// Load a real profile (with SkyCrypt's animated 3D player model) in the background, like after
				// joining Hypixel, then wait for it to finish loading.
				if (!loadedReal && mc.player != null && PvBrowser.get() != null && elapsed() > 5_000) {
					loadedReal = true;
					// A page that never stops animating (like a stuck spinner or a 3D model), to check freezing stops it.
					PvBrowser.prefetch(Boolean.getBoolean("skycryptpv.selftest.busy")
							? "data:text/html,<style>div{width:600px;height:600px;background:linear-gradient(red,blue);animation:s 1s linear infinite}@keyframes s{to{transform:rotate(360deg)}}</style><div></div><script>function f(){let x=0;for(let i=0;i<3e6;i++)x+=Math.sqrt(i);requestAnimationFrame(f)}f()</script>"
							: io.github.habanerotactic.skycryptpv.SkyCryptPV.statsUrl("HabaneroTactic", null));
				}
				if (mc.player != null && PvBrowser.get() != null && PvBrowser.pendingUrl() == null
						&& loadedReal && !PvBrowser.get().getCefBrowser().isLoading() && elapsed() > 40_000) {
					next();
				}
			}
			case 2, 3, 4, 5 -> {
				boolean activePhase = step % 2 == 0;
				PvBrowser.setActive(activePhase);
				sample(mc);
				if (elapsed() > 12_000) {
					LOG.info("FPSTEST round {} page {}: avg {} fps over {}s", (step - 2) / 2 + 1,
							activePhase ? "ACTIVE" : "FROZEN", fpsSum / Math.max(1, samples), samples);
					if (step == 5) {
						mc.stop();
					}
					next();
				}
			}
			default -> {
			}
		}
	}

}
