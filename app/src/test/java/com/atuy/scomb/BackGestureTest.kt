package com.atuy.scomb

import com.atuy.scomb.ui.shouldCompleteBackGesture
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BackGestureTest {
    @Test fun shortDragReturnsToDetail() {
        assertFalse(shouldCompleteBackGesture(0.2f, 0f, 600f))
    }

    @Test fun longDragReturnsToOriginalScreen() {
        assertTrue(shouldCompleteBackGesture(0.4f, 0f, 600f))
    }

    @Test fun rightFlingCompletesShortDrag() {
        assertTrue(shouldCompleteBackGesture(0.05f, 700f, 600f))
    }

    @Test fun leftFlingCancelsLongDrag() {
        assertFalse(shouldCompleteBackGesture(0.7f, -700f, 600f))
    }

    @Test fun leftSwipeAtOriginDoesNotGoBack() {
        assertFalse(shouldCompleteBackGesture(0f, -700f, 600f))
        assertFalse(shouldCompleteBackGesture(0f, 700f, 600f))
    }
}
