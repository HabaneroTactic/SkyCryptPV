package io.github.habanerotactic.skycryptpv;

import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

/** Tiny persisted settings: config/skycrypt-pv.properties */
public final class PvConfig {

	public static final double MIN_ZOOM = 0.5;
	public static final double MAX_ZOOM = 2.0;

	private static final Path FILE = FabricLoader.getInstance().getConfigDir().resolve(SkyCryptPV.MOD_ID + ".properties");

	/** Page zoom on top of the display's pixel density. 1.0 = same size as a normal desktop browser. */
	public static double zoom = 1.0;
	/** Replace other mods' /pv (e.g. Skyblocker's) with this one. /skycrypt always works regardless. */
	public static boolean overridePv = true;
	/** Item shown as the PV button in Hypixel's profile menu, e.g. minecraft:ender_eye or minecraft:player_head. */
	public static String buttonItem = "minecraft:ender_eye";
	/** Lunar-style: no toolbar, just the page and a small close button. */
	public static boolean simpleMode = false;
	/** Size of the viewer as a percentage of the screen. Below 100 the game stays visible around it. */
	public static int panelSize = 100;
	/** Round the panel's corners when it isn't full screen. */
	public static boolean roundedCorners = true;
	/** Plain-clicking the name of whoever sent a chat message opens their profile. */
	public static boolean chatPlainClick = true;
	/** "⌘+Click: /pv Name" next to chat names, and "[J] Name" under the crosshair. */
	public static boolean hints = true;
	/** Keep the page loaded (frozen) between views for instant reopening; off closes it completely instead. */
	public static boolean keepWarm = true;
	/** Who plain /pv opens. Empty = yourself. */
	public static String defaultPlayer = "";
	/** Starred players, always suggested by /pv tab-complete. */
	public static final List<String> favourites = new ArrayList<>();

	private PvConfig() {
	}

	public static void load() {
		if (!Files.exists(FILE)) {
			save();
			return;
		}
		Properties props = new Properties();
		try (Reader reader = Files.newBufferedReader(FILE)) {
			props.load(reader);
			zoom = clampZoom(Double.parseDouble(props.getProperty("zoom", "1.0")));
			overridePv = Boolean.parseBoolean(props.getProperty("overridePv", "true"));
			buttonItem = props.getProperty("buttonItem", buttonItem).trim();
			simpleMode = Boolean.parseBoolean(props.getProperty("simpleMode", "false"));
			panelSize = clampPanel(Integer.parseInt(props.getProperty("panelSize", "100").trim()));
			roundedCorners = Boolean.parseBoolean(props.getProperty("roundedCorners", "true"));
			hints = Boolean.parseBoolean(props.getProperty("hints", "true"));
			keepWarm = Boolean.parseBoolean(props.getProperty("keepWarm", "true"));
			chatPlainClick = Boolean.parseBoolean(props.getProperty("chatPlainClick", "true"));
			defaultPlayer = props.getProperty("defaultPlayer", "").trim();
			favourites.clear();
			for (String name : props.getProperty("favourites", "").split(",")) {
				if (!name.isBlank()) {
					favourites.add(name.trim());
				}
			}
		} catch (IOException | NumberFormatException e) {
			SkyCryptPV.LOGGER.warn("Failed to read {}, using defaults", FILE, e);
		}
	}

	public static void save() {
		Properties props = new Properties();
		props.setProperty("zoom", Double.toString(zoom));
		props.setProperty("overridePv", Boolean.toString(overridePv));
		props.setProperty("buttonItem", buttonItem);
		props.setProperty("simpleMode", Boolean.toString(simpleMode));
		props.setProperty("panelSize", Integer.toString(panelSize));
		props.setProperty("roundedCorners", Boolean.toString(roundedCorners));
		props.setProperty("hints", Boolean.toString(hints));
		props.setProperty("keepWarm", Boolean.toString(keepWarm));
		props.setProperty("chatPlainClick", Boolean.toString(chatPlainClick));
		props.setProperty("defaultPlayer", defaultPlayer);
		props.setProperty("favourites", String.join(",", favourites));
		try (Writer writer = Files.newBufferedWriter(FILE)) {
			props.store(writer, "SkyCrypt PV settings. overridePv=false leaves /pv to other mods (/skycrypt always works). buttonItem = any item id.");
		} catch (IOException e) {
			SkyCryptPV.LOGGER.warn("Failed to write {}", FILE, e);
		}
	}

	public static boolean isFavourite(String player) {
		return favourites.stream().anyMatch(name -> name.equalsIgnoreCase(player));
	}

	/** Adds or removes a favourite; returns true if the player is now a favourite. */
	public static boolean toggleFavourite(String player) {
		boolean added = !isFavourite(player);
		if (added) {
			favourites.add(player);
		} else {
			favourites.removeIf(name -> name.equalsIgnoreCase(player));
		}
		save();
		return added;
	}

	public static int clampPanel(int value) {
		return Math.max(50, Math.min(100, value));
	}

	public static double clampZoom(double value) {
		return Math.max(MIN_ZOOM, Math.min(MAX_ZOOM, Math.round(value * 10.0) / 10.0));
	}

}
