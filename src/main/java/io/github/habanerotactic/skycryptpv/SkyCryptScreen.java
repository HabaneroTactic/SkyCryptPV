package io.github.habanerotactic.skycryptpv;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.blaze3d.textures.GpuTextureView;
import net.dimaskama.mcef.api.MCEFApi;
import net.dimaskama.mcef.api.MCEFBrowser;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Util;
import org.cef.browser.CefBrowser;
import org.cef.browser.SkyCryptPvCef;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.function.Consumer;

// the viewer. page renders at the real window resolution so text stays sharp
public class SkyCryptScreen extends Screen {

	public static final int BAR = 22;
	private static final int CORNER_RADIUS = 6;
	private static final int BAR_COLOR = 0xFF1E1E22;
	private static final int BAR_BORDER = 0xFF34343A;
	private static final int BACKGROUND = 0xFF141416;
	private static final int TEXT = 0xFFE0E0E0;
	private static final int TEXT_DIM = 0xFF8A8A92;
	// wheel notch = 1, trackpads send small fractions
	private static final double WHEEL_PX = 100.0;
	private static final double TRACKPAD_PX = 10.0;
	// if chromium hasn't painted for this long, poke it (see tick())
	private static final long PAINT_WATCHDOG_MS = 2000;

	@Nullable
	private MCEFBrowser browser;
	@Nullable
	private String error;
	private String url;
	private String currentUrl;
	private EditBox nameBox;
	private Button backButton;
	private Button forwardButton;
	private Button favouriteButton;
	private EditBox findBox;
	private boolean finding;
	private int zoomLabelX;
	private int brandLeft, brandRight;
	private static final Component POWERED_BY = Component.literal("Powered by ")
			.append(Component.literal("SkyCrypt").withColor(0xFF22C55E));
	private int pageLeft, pageTop, pageWidth, pageHeight;
	private boolean browserFocused = true;
	private long zoomChangedAt = -1;
	private int zoomCheckTicks;
	private double scrollRemainder;
	private long lastPreciseScroll;
	private String shownPlayer = "";
	private long lastNudge;
	private int nudges;

	public SkyCryptScreen(String url) {
		super(Component.literal("SkyCrypt"));
		this.url = url;
		this.currentUrl = url;
	}

	public void load(String url) {
		this.url = url;
		this.currentUrl = url;
		browser = PvBrowser.open(url);
		focusBrowser();
	}

	@Nullable
	public MCEFBrowser browser() {
		return browser;
	}

	public String currentUrl() {
		return currentUrl;
	}

	public long millisSinceLastPaint() {
		return PvBrowser.millisSinceLastPaint();
	}

	@Override
	protected void init() {
		int[] rect = pageRect(width, height);
		pageLeft = rect[0];
		pageTop = rect[1];
		pageWidth = rect[2];
		pageHeight = rect[3];

		// simple mode: toolbar widgets still exist for shortcuts, just hidden
		int x = 2;
		backButton = toolbar(iconButton("←", "Back", x, b -> goBack()));
		x += 21;
		forwardButton = toolbar(iconButton("→", "Forward", x, b -> goForward()));
		x += 21;
		toolbar(iconButton("⟳", "Reload", x, b -> reload()));
		x += 25;

		int right = width - 2;
		right -= 20;
		if (PvConfig.simpleMode) {
			// just a small x on the corner
			int closeY = pageTop >= 16 ? pageTop - 15 : pageTop + 1;
			int closeX = pageTop >= 16 ? pageLeft + pageWidth - 14 : pageLeft + pageWidth - 15;
			addRenderableWidget(Button.builder(Component.literal("✕"), b -> onClose())
					.bounds(closeX, closeY, 14, 14)
					.tooltip(Tooltip.create(Component.literal("Close (Esc)")))
					.build());
		} else {
			addRenderableWidget(iconButton("✕", "Close (Esc)", right, b -> onClose()));
		}
		Component openLabel = Component.literal("Browser ↗");
		int openWidth = font.width(openLabel) + 10;
		right -= openWidth + 4;
		toolbar(Button.builder(openLabel, b -> Util.getPlatform().openUri(currentUrl))
				.bounds(right, 1, openWidth, 20)
				.tooltip(Tooltip.create(Component.literal("Open this page in your normal web browser")))
				.build());
		right -= 21 + 4;
		toolbar(iconButton("+", "Zoom in (Ctrl/Cmd +)", right, b -> changeZoom(0.1)));
		right -= 32;
		zoomLabelX = right + 16;
		right -= 21;
		toolbar(iconButton("−", "Zoom out (Ctrl/Cmd -)", right, b -> changeZoom(-0.1)));

		right -= 25;
		favouriteButton = toolbar(Button.builder(Component.literal("☆"), b -> toggleFavourite())
				.bounds(right, 1, 20, 20)
				.build());
		updateFavouriteButton();

		int goWidth = 24;
		int boxWidth = Math.max(50, Math.min(140, right - 4 - goWidth - 2 - x));
		String previous = nameBox != null ? nameBox.getValue() : playerFromUrl(currentUrl);
		nameBox = new EditBox(font, x, 2, boxWidth, 18, Component.literal("Player"));
		nameBox.setMaxLength(48);
		nameBox.setHint(Component.literal("Player [profile]").withStyle(ChatFormatting.DARK_GRAY));
		nameBox.setValue(previous);
		toolbar(nameBox);
		toolbar(Button.builder(Component.literal("Go"), b -> submitName())
				.bounds(x + boxWidth + 2, 1, goWidth, 20)
				.build());
		brandLeft = x + boxWidth + 2 + goWidth + 6;
		brandRight = right - 6;

		String previousFind = findBox != null ? findBox.getValue() : "";
		findBox = new EditBox(font, pageLeft + pageWidth - 156, pageTop + 4, 150, 16, Component.literal("Find"));
		findBox.setHint(Component.literal("Find on page… (Enter = next)").withStyle(ChatFormatting.DARK_GRAY));
		findBox.setValue(previousFind);
		findBox.setResponder(text -> runFind(text, true, false));
		findBox.setVisible(finding);
		addRenderableWidget(findBox);

		createBrowserIfReady();
		resizeBrowser();
	}

	private <T extends AbstractWidget> T toolbar(T widget) {
		return PvConfig.simpleMode ? widget : addRenderableWidget(widget);
	}

	public static int[] pageRect(int screenWidth, int screenHeight) {
		int top = PvConfig.simpleMode ? 0 : BAR;
		double size = PvConfig.panelSize / 100.0;
		int w = Math.max(1, (int) Math.round(screenWidth * size));
		int h = Math.max(1, (int) Math.round((screenHeight - top) * size));
		return new int[]{(screenWidth - w) / 2, top + (screenHeight - top - h) / 2, w, h};
	}

	private static Button iconButton(String icon, String tooltip, int x, Button.OnPress onPress) {
		return Button.builder(Component.literal(icon), onPress)
				.bounds(x, 1, 20, 20)
				.tooltip(Tooltip.create(Component.literal(tooltip)))
				.build();
	}

	@Override
	protected void setInitialFocus() {
		// keep focus on the page, not the first button
	}

	private void createBrowserIfReady() {
		if (browser != null || error != null || !PvBrowser.isReady()) {
			return;
		}
		browser = PvBrowser.open(url);
		error = PvBrowser.error();
		if (browser != null) {
			resizeBrowser();
			boolean focused = browserFocused;
			withBrowser(b -> b.setFocus(focused));
		}
	}

	private void resizeBrowser() {
		if (browser != null) {
			int scale = scale();
			PvBrowser.resize(pageWidth * scale, pageHeight * scale);
		}
	}

	private void withBrowser(Consumer<MCEFBrowser> action) {
		if (browser != null) {
			PvBrowser.call(action);
		}
	}

	@Override
	public void tick() {
		createBrowserIfReady();
		if (browser == null) {
			backButton.active = false;
			forwardButton.active = false;
			return;
		}
		CefBrowser cef = browser.getCefBrowser();

		// re-zooming re-lays out the whole page, so only do it once the user stops clicking +/-
		// or if chromium reset it (new pages start at 100%)
		long now = Util.getMillis();
		if (zoomChangedAt >= 0) {
			if (now - zoomChangedAt > 250) {
				zoomChangedAt = -1;
				PvBrowser.applyZoom();
			}
		} else if (++zoomCheckTicks >= 10) {
			zoomCheckTicks = 0;
			if (Math.abs(cef.getZoomLevel() - PvBrowser.targetZoomLevel()) > 0.01) {
				PvBrowser.applyZoom();
			}
		}

		backButton.active = cef.canGoBack();
		forwardButton.active = cef.canGoForward();
		String pageUrl = cef.getURL();
		if (pageUrl != null && !pageUrl.isEmpty()) {
			currentUrl = pageUrl;
		}
		String player = playerFromUrl(currentUrl);
		if (!nameBox.isFocused() && !player.equals(shownPlayer)) {
			shownPlayer = player;
			nameBox.setValue(player);
			updateFavouriteButton();
		}

		// a static page stops painting normally, so just resend the size and ask for a repaint
		// (also fixes the page if chromium lost its size)
		if (PvBrowser.millisSinceLastPaint() > PAINT_WATCHDOG_MS && now - lastNudge > PAINT_WATCHDOG_MS) {
			lastNudge = now;
			if (nudges++ == 3) {
				SkyCryptPV.LOGGER.info("Browser hasn't painted for a while; still nudging it");
			}
			resizeBrowser();
			withBrowser(b -> SkyCryptPvCef.invalidate(b.getCefBrowser()));
		}
	}

	@Override
	public void removed() {
		// browser stays alive so the next /pv is instant
		withBrowser(b -> b.setFocus(false));
		browser = null;
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}

	private void goBack() {
		if (browser != null && browser.getCefBrowser().canGoBack()) {
			withBrowser(b -> b.getCefBrowser().goBack());
		}
	}

	private void goForward() {
		if (browser != null && browser.getCefBrowser().canGoForward()) {
			withBrowser(b -> b.getCefBrowser().goForward());
		}
	}

	private void reload() {
		withBrowser(b -> b.getCefBrowser().reload());
	}

	// chromium's built-in find
	private void runFind(String text, boolean forward, boolean findNext) {
		if (text.isBlank()) {
			withBrowser(b -> b.getCefBrowser().stopFinding(true));
		} else {
			withBrowser(b -> b.getCefBrowser().find(text, forward, false, findNext));
		}
	}

	private void openFind() {
		finding = true;
		findBox.setVisible(true);
		unfocusBrowser();
		setFocused(findBox);
		findBox.setFocused(true);
		if (!findBox.getValue().isBlank()) {
			runFind(findBox.getValue(), true, false);
		}
	}

	private void closeFind() {
		finding = false;
		findBox.setVisible(false);
		withBrowser(b -> b.getCefBrowser().stopFinding(true));
		focusBrowser();
	}

	private void toggleFavourite() {
		String player = playerFromUrl(currentUrl);
		if (!player.isEmpty()) {
			PvConfig.toggleFavourite(player);
			updateFavouriteButton();
		}
	}

	private void updateFavouriteButton() {
		if (favouriteButton == null) {
			return;
		}
		String player = playerFromUrl(currentUrl);
		boolean starred = !player.isEmpty() && PvConfig.isFavourite(player);
		favouriteButton.setMessage(starred
				? Component.literal("★").withStyle(ChatFormatting.GOLD)
				: Component.literal("☆"));
		favouriteButton.setTooltip(Tooltip.create(Component.literal(starred
				? "Remove " + player + " from favourites"
				: "Add " + player + " to favourites (suggested first by /pv)")));
	}

	private void submitName() {
		String[] parts = nameBox.getValue().trim().split("\\s+");
		if (parts[0].isEmpty()) {
			return;
		}
		shownPlayer = parts[0];
		load(SkyCryptPV.statsUrl(parts[0], parts.length > 1 ? parts[1] : null));
	}

	private void changeZoom(double delta) {
		PvConfig.zoom = PvConfig.clampZoom(PvConfig.zoom + delta);
		PvConfig.save();
		zoomChangedAt = Util.getMillis();
	}

	private void resetZoom() {
		PvConfig.zoom = 1.0;
		PvConfig.save();
		zoomChangedAt = Util.getMillis();
	}

	@Override
	public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
		if (PvConfig.panelSize >= 100 || minecraft.level == null) {
			graphics.fill(0, 0, width, height, BACKGROUND);
		} else {
			// panel mode: dim the game instead of hiding it
			graphics.fill(0, 0, width, height, 0x70000000);
			if (!roundedCorners()) {
				graphics.fill(pageLeft - 1, pageTop - 1, pageLeft + pageWidth + 1, pageTop + pageHeight + 1, BAR_BORDER);
			}
		}
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
		// until chromium switches page, the texture still shows the old one
		boolean switching = PvBrowser.pendingUrl() != null;
		GpuTextureView page = browser != null && !switching ? browser.getTextureView() : null;
		if (page != null) {
			if (roundedCorners()) {
				blitRounded(graphics, page);
			} else {
				graphics.blit(page, RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR),
						pageLeft, pageTop, pageLeft + pageWidth, pageTop + pageHeight, 0.0F, 1.0F, 0.0F, 1.0F);
			}
		} else {
			drawStatus(graphics);
		}

		if (!PvConfig.simpleMode) {
			graphics.fill(0, 0, width, BAR - 1, BAR_COLOR);
			graphics.fill(0, BAR - 1, width, BAR, BAR_BORDER);
		}
		super.extractRenderState(graphics, mouseX, mouseY, partialTick);
		if (!PvConfig.simpleMode) {
			graphics.centeredText(font, Math.round(PvConfig.zoom * 100) + "%", zoomLabelX, 7, TEXT_DIM);
		}
		drawPoweredBy(graphics);

		if (browser != null && isOverPage(mouseX, mouseY)) {
			graphics.requestCursor(browser.getCursorType());
		}
	}

	// "Powered by SkyCrypt" (skycrypt asked for this). in the toolbar if there's room, otherwise under the panel
	private void drawPoweredBy(GuiGraphicsExtractor graphics) {
		int w = font.width(POWERED_BY);
		if (!PvConfig.simpleMode && brandRight - brandLeft >= w) {
			graphics.text(font, POWERED_BY, (brandLeft + brandRight - w) / 2, 7, TEXT_DIM);
			return;
		}
		int x = pageLeft + pageWidth - w - 4;
		int y = pageTop + pageHeight + 3;
		if (y + 9 > height) {
			y = pageTop + pageHeight - 12;
			graphics.fill(x - 3, y - 2, x + w + 3, y + 10, 0xB0101014);
		}
		graphics.text(font, POWERED_BY, x, y, TEXT_DIM);
	}

	private static boolean roundedCorners() {
		return PvConfig.roundedCorners && PvConfig.panelSize < 100;
	}

	// rounded corners: middle in one go, edge rows as 1px strips, in real pixels so the curve is smooth
	private void blitRounded(GuiGraphicsExtractor graphics, GpuTextureView page) {
		var sampler = RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR);
		int s = scale();
		int x0 = pageLeft * s;
		int y0 = pageTop * s;
		int w = pageWidth * s;
		int h = pageHeight * s;
		int r = Math.min(CORNER_RADIUS * s, Math.min(w, h) / 2);
		graphics.pose().pushMatrix();
		graphics.pose().scale(1.0F / s, 1.0F / s);
		graphics.blit(page, sampler, x0, y0 + r, x0 + w, y0 + h - r, 0.0F, 1.0F, (float) r / h, (float) (h - r) / h);
		for (int i = 0; i < r; i++) {
			double dy = r - i - 0.5;
			int inset = (int) Math.round(r - Math.sqrt(r * r - dy * dy));
			float u0 = (float) inset / w;
			float u1 = (float) (w - inset) / w;
			graphics.blit(page, sampler, x0 + inset, y0 + i, x0 + w - inset, y0 + i + 1,
					u0, u1, (float) i / h, (float) (i + 1) / h);
			int bottom = h - 1 - i;
			graphics.blit(page, sampler, x0 + inset, y0 + bottom, x0 + w - inset, y0 + bottom + 1,
					u0, u1, (float) bottom / h, (float) (bottom + 1) / h);
		}
		graphics.pose().popMatrix();
	}

	private void drawStatus(GuiGraphicsExtractor graphics) {
		int centerY = pageTop + pageHeight / 2;
		if (error != null) {
			graphics.centeredText(font, "The embedded browser failed to start.", pageLeft + pageWidth / 2, centerY - 16, 0xFFFF6B6B);
			graphics.centeredText(font, error, pageLeft + pageWidth / 2, centerY - 4, TEXT_DIM);
			graphics.centeredText(font, "Use \"Browser ↗\" to open the profile in your normal browser.", pageLeft + pageWidth / 2, centerY + 8, TEXT);
			return;
		}
		MCEFApi.Initialization init = MCEFApi.initialize();
		String message;
		String detail = null;
		if (browser != null) {
			String player = playerFromUrl(PvBrowser.pendingUrl() != null ? PvBrowser.pendingUrl() : currentUrl);
			message = player.isEmpty() ? "Loading SkyCrypt…" : "Loading " + player + "…";
		} else {
			float percent = init.getPercentage();
			String progress = percent >= 0 ? String.format(" %.0f%%", percent) : "";
			message = switch (init.getStage()) {
				case DOWNLOADING -> "Downloading Chromium" + progress;
				case EXTRACTING -> "Extracting Chromium" + progress;
				case INSTALL, INITIALIZING, NOT_STARTED, DONE -> "Starting Chromium…";
			};
			if (init.getStage() == MCEFApi.Initialization.Stage.DOWNLOADING
					|| init.getStage() == MCEFApi.Initialization.Stage.EXTRACTING) {
				detail = "One-time setup (about 150 MB), only on the first launch.";
			}
		}
		graphics.centeredText(font, message, pageLeft + pageWidth / 2, centerY - 4, TEXT);
		if (detail != null) {
			graphics.centeredText(font, detail, pageLeft + pageWidth / 2, centerY + 8, TEXT_DIM);
		}
	}

	private int scale() {
		return minecraft.getWindow().getGuiScale();
	}

	private boolean isOverPage(double x, double y) {
		return x >= pageLeft && x < pageLeft + pageWidth && y >= pageTop && y < pageTop + pageHeight;
	}

	private int pageX(double x) {
		return (int) ((x - pageLeft) * scale());
	}

	private int pageY(double y) {
		return (int) ((y - pageTop) * scale());
	}

	private MouseButtonEvent toPage(MouseButtonEvent event) {
		return new MouseButtonEvent(pageX(event.x()), pageY(event.y()), event.buttonInfo());
	}

	private void focusBrowser() {
		browserFocused = true;
		setFocused(null);
		if (nameBox != null) {
			nameBox.setFocused(false);
		}
		if (findBox != null) {
			findBox.setFocused(false);
		}
		withBrowser(b -> b.setFocus(true));
	}

	private void unfocusBrowser() {
		browserFocused = false;
		withBrowser(b -> b.setFocus(false));
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubled) {
		if (browser != null && isOverPage(event.x(), event.y())) {
			focusBrowser();
			switch (event.button()) {
				case GLFW.GLFW_MOUSE_BUTTON_4 -> goBack();
				case GLFW.GLFW_MOUSE_BUTTON_5 -> goForward();
				default -> {
					MouseButtonEvent pageEvent = toPage(event);
					withBrowser(b -> b.onMouseClicked(pageEvent, doubled));
				}
			}
			return true;
		}
		unfocusBrowser();
		if (super.mouseClicked(event, doubled)) {
			return true;
		}
		// click outside the panel to close
		if (PvConfig.panelSize < 100 && event.y() >= (PvConfig.simpleMode ? 0 : BAR)) {
			onClose();
			return true;
		}
		return false;
	}

	@Override
	public boolean mouseReleased(MouseButtonEvent event) {
		MouseButtonEvent pageEvent = toPage(event);
		withBrowser(b -> b.onMouseReleased(pageEvent));
		return super.mouseReleased(event);
	}

	@Override
	public void mouseMoved(double x, double y) {
		int pageX = pageX(x);
		int pageY = pageY(y);
		withBrowser(b -> b.onMouseMoved(pageX, pageY));
		super.mouseMoved(x, y);
	}

	@Override
	public boolean mouseScrolled(double x, double y, double horizontal, double vertical) {
		if (browser == null || !isOverPage(x, y)) {
			return super.mouseScrolled(x, y, horizontal, vertical);
		}
		long now = Util.getMillis();
		if (vertical != Math.rint(vertical)) {
			lastPreciseScroll = now;
		}
		double cssPixels = now - lastPreciseScroll < 500 ? TRACKPAD_PX : WHEEL_PX;
		scrollRemainder += vertical * cssPixels * PvBrowser.zoomFactor();
		int pixels = (int) scrollRemainder;
		scrollRemainder -= pixels;
		int pageX = pageX(x);
		int pageY = pageY(y);
		withBrowser(b -> SkyCryptPvCef.scroll(b.getCefBrowser(), pageX, pageY, pixels));
		return true;
	}

	@Override
	public boolean keyPressed(KeyEvent event) {
		int key = event.key();
		if (findBox.isFocused()) {
			switch (key) {
				case GLFW.GLFW_KEY_ESCAPE -> closeFind();
				case GLFW.GLFW_KEY_ENTER, GLFW.GLFW_KEY_KP_ENTER ->
						runFind(findBox.getValue(), (event.modifiers() & GLFW.GLFW_MOD_SHIFT) == 0, true);
				default -> {
					return super.keyPressed(event);
				}
			}
			return true;
		}
		if (key == GLFW.GLFW_KEY_ESCAPE) {
			onClose();
			return true;
		}
		if (nameBox.isFocused()) {
			if (key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER) {
				submitName();
				return true;
			}
			return super.keyPressed(event);
		}
		if ((event.modifiers() & (GLFW.GLFW_MOD_CONTROL | GLFW.GLFW_MOD_SUPER)) != 0) {
			switch (key) {
				case GLFW.GLFW_KEY_EQUAL, GLFW.GLFW_KEY_KP_ADD -> changeZoom(0.1);
				case GLFW.GLFW_KEY_MINUS, GLFW.GLFW_KEY_KP_SUBTRACT -> changeZoom(-0.1);
				case GLFW.GLFW_KEY_0, GLFW.GLFW_KEY_KP_0 -> resetZoom();
				case GLFW.GLFW_KEY_R -> reload();
				case GLFW.GLFW_KEY_LEFT_BRACKET -> goBack();
				case GLFW.GLFW_KEY_RIGHT_BRACKET -> goForward();
				case GLFW.GLFW_KEY_F -> openFind();
				case GLFW.GLFW_KEY_L -> {
					if (PvConfig.simpleMode) {
						break;
					}
					unfocusBrowser();
					setFocused(nameBox);
					nameBox.setFocused(true);
				}
				default -> {
					if (browserFocused) {
						withBrowser(b -> b.onKeyPressed(event));
					}
				}
			}
			return true;
		}
		if (browserFocused && browser != null) {
			withBrowser(b -> b.onKeyPressed(event));
			return true;
		}
		return super.keyPressed(event);
	}

	@Override
	public boolean keyReleased(KeyEvent event) {
		if (browserFocused && browser != null) {
			withBrowser(b -> b.onKeyReleased(event));
			return true;
		}
		return super.keyReleased(event);
	}

	@Override
	public boolean charTyped(CharacterEvent event) {
		if (browserFocused && browser != null && !nameBox.isFocused()) {
			withBrowser(b -> b.onCharTyped(event));
			return true;
		}
		return super.charTyped(event);
	}

	/** "https://sky.shiiyu.moe/stats/Name/Profile#Inventory" -> "Name" */
	private static String playerFromUrl(String url) {
		int start = url.indexOf("/stats/");
		if (start < 0) {
			return "";
		}
		String rest = url.substring(start + "/stats/".length());
		int end = rest.length();
		for (char c : new char[]{'/', '?', '#'}) {
			int i = rest.indexOf(c);
			if (i >= 0) {
				end = Math.min(end, i);
			}
		}
		return URLDecoder.decode(rest.substring(0, end), StandardCharsets.UTF_8);
	}

}
