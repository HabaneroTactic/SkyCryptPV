package org.cef.browser;

import java.awt.event.MouseWheelEvent;

/**
 * Lives in org.cef.browser to reach CefBrowser_N's protected methods.
 * MCEF's own scroll handler only sends whole ±100px steps, which makes trackpads unusable.
 */
public final class SkyCryptPvCef {

	private SkyCryptPvCef() {
	}

	/** Scrolls by an exact pixel amount (positive = up), with the cursor at (x, y) in browser pixels. */
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

	/** Asks Chromium to repaint the whole view. */
	public static void invalidate(CefBrowser browser) {
		if (browser instanceof CefBrowser_N nativeBrowser) {
			nativeBrowser.invalidate();
		}
	}

}
