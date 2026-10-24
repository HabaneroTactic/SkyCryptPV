package org.cef.browser;

import java.awt.event.MouseWheelEvent;

// in org.cef.browser to use CefBrowser_N's protected methods.
// mcef's scroll only does 100px steps, which is useless on trackpads
public final class SkyCryptPvCef {

	private SkyCryptPvCef() {
	}

	// scroll by exact pixels (positive = up)
	public static void scroll(CefBrowser browser, int x, int y, int pixels) {
		if (pixels == 0 || !(browser instanceof CefBrowser_N nativeBrowser)) {
			return;
		}
		// jcef sends getUnitsToScroll() (= scrollAmount * wheelRotation) to Chromium as the pixel delta.
		nativeBrowser.sendMouseWheelEvent(new MouseWheelEvent(
				browser.getUIComponent(),
				MouseWheelEvent.MOUSE_WHEEL,
				System.currentTimeMillis(),
				0,
				x,
				y,
				0,
				false,
				MouseWheelEvent.WHEEL_UNIT_SCROLL,
				1,
				pixels
		));
	}

	public static void invalidate(CefBrowser browser) {
		if (browser instanceof CefBrowser_N nativeBrowser) {
			nativeBrowser.invalidate();
		}
	}

}
