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

// settings, saved in config/skycrypt-pv.properties
public final class PvConfig {

	public static final double MIN_ZOOM = 0.5;
	public static final double MAX_ZOOM = 2.0;

	private static final Path FILE = FabricLoader.getInstance().getConfigDir().resolve(SkyCryptPV.MOD_ID + ".properties");

	// 1.0 = same size as a normal browser
	public static double zoom = 1.0;
	public static boolean overridePv = true;
	public static String buttonItem = "minecraft:ender_eye";
	// lunar style, no toolbar
	public static boolean simpleMode = false;
	// % of the screen, below 100 the game shows around it
	public static int panelSize = 100;
	public static boolean roundedCorners = true;
	public static boolean chatPlainClick = true;
	public static boolean chatHint = true;
	public static boolean lookHint = true;
	public static boolean keepWarm = true;
	// empty = yourself
	public static String defaultPlayer = "";
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
			// old versions had one "hints" setting for both
			String hints = props.getProperty("hints", "true");
			chatHint = Boolean.parseBoolean(props.getProperty("chatHint", hints));
			lookHint = Boolean.parseBoolean(props.getProperty("lookHint", hints));
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
		props.setProperty("chatHint", Boolean.toString(chatHint));
		props.setProperty("lookHint", Boolean.toString(lookHint));
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

	// returns true if they're now a favourite
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
