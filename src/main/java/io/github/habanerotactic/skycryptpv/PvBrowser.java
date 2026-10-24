package io.github.habanerotactic.skycryptpv;

import com.mojang.blaze3d.platform.Window;
import net.dimaskama.mcef.api.MCEFApi;
import net.dimaskama.mcef.api.MCEFBrowser;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.event.Event;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Util;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.cef.CefApp;
import org.cef.browser.CefDevToolsClient;
import org.cef.handler.CefRenderHandler;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * The one embedded browser, kept alive for the whole session. Starting Chromium's page process and SkyCrypt's
 * scripts is most of the wait, so the viewer reuses this browser and pages can be loaded before the viewer opens.
 */
public final class PvBrowser {

	/**
	 * Every call into Chromium goes through this one thread, in order. On macOS Chromium's UI thread is the game's
	 * main thread, so a call made from there runs synchronously and calls back into Java from deep inside the
	 * game's input/tick stack, where those callbacks overflow the stack. A dropped view-size callback then leaves
	 * the page frozen. Called from any other thread, Chromium queues the work for its own message loop instead.
	 */
	private static final ExecutorService CHROMIUM = Executors.newSingleThreadExecutor(runnable -> {
		Thread thread = new Thread(runnable, "SkyCrypt PV browser");
		thread.setDaemon(true);
		return thread;
	});

	@Nullable
	private static MCEFBrowser browser;
	@Nullable
	private static String error;
	private static volatile long lastPaint;
	private static final String MOD_VERSION = FabricLoader.getInstance().getModContainer("skycrypt-pv")
			.map(mod -> mod.getMetadata().getVersion().getFriendlyString().replaceAll("\\+.*", ""))
			.orElse("1.0.0");

	/**
	 * MCEF labels the browser "MCEF-Modern", and SkyCrypt's Cloudflare bot protection then makes its profile-data
	 * requests fail ("500 Failed to fetch ... section data"). Presenting as the regular Chrome this engine is fixes it.
	 * The claimed OS matches the real one, so the identity stays consistent with everything else the browser sends.
	 * "SkyCryptPV/<version>" stays on the end so SkyCrypt can always tell the mod's traffic apart.
	 */
	private static final String USER_AGENT_OVERRIDE = userAgentOverride();

	private static String userAgentOverride() {
		String os, platform, hintPlatform, hintVersion, arch;
		switch (Util.getPlatform()) {
			case WINDOWS -> {
				os = "Windows NT 10.0; Win64; x64";
				platform = "Win32";
				hintPlatform = "Windows";
				hintVersion = "10.0.0";
				arch = System.getProperty("os.arch", "").contains("aarch64") ? "arm" : "x86";
			}
			case LINUX -> {
				os = "X11; Linux x86_64";
				platform = "Linux x86_64";
				hintPlatform = "Linux";
				hintVersion = "";
				arch = "x86";
			}
			default -> {
				os = "Macintosh; Intel Mac OS X 10_15_7";
				platform = "MacIntel";
				hintPlatform = "macOS";
				hintVersion = "15.0.0";
				arch = "x86";
			}
		}
		return """
				{"userAgent": "Mozilla/5.0 (%s) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/146.0.0.0 Safari/537.36 SkyCryptPV/%s",
				 "acceptLanguage": "en-US,en",
				 "platform": "%s",
				 "userAgentMetadata": {
				   "brands": [{"brand": "Chromium", "version": "146"}, {"brand": "Google Chrome", "version": "146"}, {"brand": "Not.A/Brand", "version": "99"}],
				   "fullVersion": "146.0.7680.179", "platform": "%s", "platformVersion": "%s", "architecture": "%s", "model": "", "mobile": false}}
				""".formatted(os, MOD_VERSION, platform, hintPlatform, hintVersion, arch);
	}
	/** Kept open: the identity override only lasts while its DevTools session does. */
	@Nullable
	private static CefDevToolsClient devTools;
	private static volatile int autoRetries;
	/** Freeze the hidden page this long after it was last used. */
	private static final long FREEZE_AFTER_MS = 15_000;
	private static boolean active = true;
	private static long lastUsed;
	@Nullable
	private static String pendingUrl;
	private static int retries;
	private static int idleTicks;
	private static volatile boolean started;

	private PvBrowser() {
	}

	/**
	 * Closes the browser before MCEF shuts Chromium down on exit. Shutting Chromium down with a browser still open
	 * can hang until the game's shutdown watchdog kills it.
	 */
	public static void registerShutdown() {
		Identifier early = Identifier.fromNamespaceAndPath(SkyCryptPV.MOD_ID, "close_browser");
		ClientLifecycleEvents.CLIENT_STOPPING.addPhaseOrdering(early, Event.DEFAULT_PHASE);
		ClientLifecycleEvents.CLIENT_STOPPING.register(early, mc -> {
			if (browser != null) {
				browser.close();
				browser = null;
			}
		});
		// MCEF shuts Chromium down (in the default phase) from another thread, and on macOS that work has to run on
		// the main thread. If the game's main thread has already moved on, both wait on each other until the
		// shutdown watchdog kills the game and writes a crash report. Keep the main thread processing events here
		// until Chromium has finished.
		Identifier late = Identifier.fromNamespaceAndPath(SkyCryptPV.MOD_ID, "finish_chromium_shutdown");
		ClientLifecycleEvents.CLIENT_STOPPING.addPhaseOrdering(Event.DEFAULT_PHASE, late);
		ClientLifecycleEvents.CLIENT_STOPPING.register(late, mc -> {
			if (!started) {
				return;
			}
			CompletableFuture<MCEFApi> future = MCEFApi.initialize().getFuture();
			if (!future.isDone() || future.isCompletedExceptionally()) {
				return;
			}
			long deadline = Util.getMillis() + 5000;
			while (CefApp.getState() != CefApp.CefAppState.TERMINATED && Util.getMillis() < deadline) {
				GLFW.glfwPollEvents();
				try {
					Thread.sleep(5);
				} catch (InterruptedException e) {
					Thread.currentThread().interrupt();
					return;
				}
			}
		});
	}

	/** Starts Chromium in the background (downloading it on first launch). */
	public static void start() {
		started = true;
		MCEFApi.initialize();
	}

	public static boolean isStarted() {
		return started;
	}

	public static boolean isReady() {
		return started && MCEFApi.initialize().getFuture().isDone();
	}

	@Nullable
	public static MCEFBrowser get() {
		return browser;
	}

	@Nullable
	public static String error() {
		return error;
	}

	public static long millisSinceLastPaint() {
		return Util.getMillis() - lastPaint;
	}

	/**
	 * Shows {@code url} in the shared browser, creating it if Chromium is ready. Doesn't reload if that page is
	 * already showing, so reopening the same profile is instant.
	 */
	@Nullable
	public static MCEFBrowser open(String url) {
		setActive(true);
		if (browser == null) {
			create(url);
			pendingUrl = browser != null ? url : null;
		} else if (sameUrl(url, browser.getCefBrowser().getURL())) {
			pendingUrl = null;
		} else if (!sameUrl(url, pendingUrl)) {
			pendingUrl = url;
			retries = 0;
			call(b -> b.getCefBrowser().loadURL(url));
		}
		return browser;
	}

	/** The page last asked for, until Chromium has actually navigated to it. */
	@Nullable
	public static String pendingUrl() {
		return pendingUrl;
	}

	/**
	 * A load requested while another is still starting (e.g. the background warm-up of your own profile) can be
	 * dropped by Chromium, so keep asking for the pending page until it's the one showing.
	 */
	public static void tick() {
		if (browser == null) {
			return;
		}
		// Freeze the page once nobody is looking at it, so it costs nothing during normal play.
		boolean viewing = Minecraft.getInstance().gui.screen() instanceof SkyCryptScreen;
		if (viewing) {
			lastUsed = Util.getMillis();
		} else if (active && pendingUrl == null && !browser.getCefBrowser().isLoading()
				&& Util.getMillis() - lastUsed > FREEZE_AFTER_MS) {
			if (PvConfig.keepWarm) {
				setActive(false);
			} else {
				closeBrowser();
				return;
			}
		}
		if (pendingUrl == null) {
			return;
		}
		if (sameUrl(pendingUrl, browser.getCefBrowser().getURL())) {
			pendingUrl = null;
		} else if (!browser.getCefBrowser().isLoading() && ++idleTicks >= 20 && retries < 5) {
			idleTicks = 0;
			retries++;
			String url = pendingUrl;
			call(b -> b.getCefBrowser().loadURL(url));
		}
	}

	/** Starts loading a page in the background at the viewer's size, so it's ready when the viewer opens. */
	public static void prefetch(String url) {
		if (error != null || !isReady()) {
			return;
		}
		open(url);
		int[] size = viewerSize();
		resize(size[0], size[1]);
		applyZoom();
	}

	private static void create(String url) {
		if (error != null || !isReady()) {
			return;
		}
		CompletableFuture<MCEFApi> future = MCEFApi.initialize().getFuture();
		try {
			// Start blank: the identity has to be set before SkyCrypt's first request.
			browser = future.join().createBrowser("about:blank", false);
		} catch (RuntimeException e) {
			Throwable cause = e.getCause() != null ? e.getCause() : e;
			error = cause.getMessage() != null ? cause.getMessage() : cause.toString();
			SkyCryptPV.LOGGER.error("Could not start the embedded browser", e);
			return;
		}
		lastPaint = Util.getMillis();
		if (browser.getCefBrowser().getRenderHandler() instanceof CefRenderHandler renderHandler) {
			renderHandler.setOnPaintListener(event -> lastPaint = Util.getMillis());
		}
		int[] size = viewerSize();
		resize(size[0], size[1]);
		applyZoom();
		call(b -> {
			setUpDevTools(b);
			b.getCefBrowser().loadURL(url);
		});
	}

	/**
	 * Sets the Chrome identity (see {@link #USER_AGENT_OVERRIDE}) and logs failed SkyCrypt data requests. DevTools
	 * only works once Chromium has finished creating the browser, so retry for a few seconds.
	 */
	private static void setUpDevTools(MCEFBrowser b) {
		for (int attempt = 0; attempt < 25; attempt++) {
			try {
				CefDevToolsClient client = b.getCefBrowser().getDevToolsClient();
				if (client != null && !client.isClosed()) {
					client.executeDevToolsMethod("Network.setUserAgentOverride", USER_AGENT_OVERRIDE).get(3, TimeUnit.SECONDS);
					client.addEventListener((method, params) -> logFailedRequest(client, method, params));
					client.executeDevToolsMethod("Network.enable").get(3, TimeUnit.SECONDS);
					devTools = client;
					return;
				}
			} catch (Exception e) {
				if (attempt == 24) {
					SkyCryptPV.LOGGER.warn("Couldn't set the browser identity; SkyCrypt may refuse to load profiles", e);
				}
			}
			try {
				Thread.sleep(200);
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
				return;
			}
		}
	}

	/**
	 * Logs SkyCrypt's failed requests, and reloads (up to twice) when its server errors. Those "500 Failed to fetch
	 * ... section data" errors are usually temporary on SkyCrypt's side and go away on a retry.
	 */
	private static void logFailedRequest(CefDevToolsClient client, String method, String params) {
		if (!"Network.responseReceived".equals(method) || !params.contains("shiiyu.moe")) {
			return;
		}
		JsonObject event = JsonParser.parseString(params).getAsJsonObject();
		JsonObject response = event.getAsJsonObject("response");
		int status = response.get("status").getAsInt();
		String url = response.get("url").getAsString();
		boolean document = event.has("type") && "Document".equals(event.get("type").getAsString());
		if (document && status < 400) {
			autoRetries = 0;
		}
		if (status < 400 || !(document || url.contains("/_app/remote/"))) {
			return;
		}
		String endpoint = url.replaceAll("\\?.*", "");
		client.executeDevToolsMethod("Network.getResponseBody", "{\"requestId\": \"" + event.get("requestId").getAsString() + "\"}")
				.whenComplete((body, error) -> SkyCryptPV.LOGGER.warn("SkyCrypt request failed: {} {} {}", status, endpoint,
						body == null ? "" : body.replaceAll("\\s+", " ").substring(0, Math.min(300, body.length()))));
		if (status >= 500 && autoRetries < 2) {
			autoRetries++;
			SkyCryptPV.LOGGER.info("SkyCrypt server error, reloading (attempt {})", autoRetries);
			CompletableFuture.delayedExecutor(2, TimeUnit.SECONDS).execute(() -> call(b -> b.getCefBrowser().reload()));
		}
	}

	/**
	 * A hidden page is limited to 1 frame a second. Animations, the 3D player model and redraws all run per frame, so
	 * this removes nearly all of its background cost; the page stays loaded so reopening is still instant.
	 * (Freezing the page through DevTools was tried and left it stuck when reopened.)
	 */
	public static void setActive(boolean on) {
		lastUsed = Util.getMillis();
		if (active == on || browser == null) {
			return;
		}
		active = on;
		call(b -> b.getCefBrowser().setWindowlessFrameRate(on ? 30 : 1));
	}

	/** Shuts the page down completely (Keep browser warm = off). The next /pv starts a fresh one. */
	private static void closeBrowser() {
		MCEFBrowser closing = browser;
		browser = null;
		devTools = null;
		pendingUrl = null;
		active = true;
		if (closing != null) {
			CHROMIUM.execute(() -> Minecraft.getInstance().schedule(closing::close));
		}
	}

	public static boolean isActive() {
		return active;
	}

	/** Runs a Chromium call off the game thread (see {@link #CHROMIUM}). */
	public static void call(Consumer<MCEFBrowser> action) {
		MCEFBrowser target = browser;
		if (target == null) {
			return;
		}
		CHROMIUM.execute(() -> {
			try {
				action.accept(target);
			} catch (RuntimeException e) {
				SkyCryptPV.LOGGER.warn("Browser call failed", e);
			}
		});
	}

	public static void resize(int pixelWidth, int pixelHeight) {
		int width = Math.max(1, pixelWidth);
		int height = Math.max(1, pixelHeight);
		call(b -> b.resize(width, height));
	}

	/** Pixel size of the page area in the full-screen viewer. */
	public static int[] viewerSize() {
		Window window = Minecraft.getInstance().getWindow();
		int scale = window.getGuiScale();
		int[] rect = SkyCryptScreen.pageRect(window.getGuiScaledWidth(), window.getGuiScaledHeight());
		return new int[]{rect[2] * scale, rect[3] * scale};
	}

	/** Device pixels per CSS pixel: the display's density (2 on Retina) times the user's zoom. */
	public static double zoomFactor() {
		Window window = Minecraft.getInstance().getWindow();
		double density = window.getScreenWidth() > 0 ? (double) window.getWidth() / window.getScreenWidth() : 1.0;
		return Math.max(0.25, density * PvConfig.zoom);
	}

	/** Chromium zoom levels are exponential: factor = 1.2 ^ level */
	public static double targetZoomLevel() {
		return Math.log(zoomFactor()) / Math.log(1.2);
	}

	public static void applyZoom() {
		double level = targetZoomLevel();
		call(b -> b.getCefBrowser().setZoomLevel(level));
	}

	private static boolean sameUrl(String a, @Nullable String b) {
		return b != null && normalize(a).equals(normalize(b));
	}

	private static String normalize(String url) {
		int hash = url.indexOf('#');
		String s = (hash >= 0 ? url.substring(0, hash) : url).toLowerCase(Locale.ROOT);
		return s.endsWith("/") ? s.substring(0, s.length() - 1) : s;
	}

}
