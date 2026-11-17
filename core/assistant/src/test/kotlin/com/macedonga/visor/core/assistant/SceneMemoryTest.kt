package com.macedonga.visor.core.assistant

import org.junit.Assert.*
import org.junit.Test

class SceneMemoryTest {
    @Test fun followUpsUseTheExactOriginalPhoto() {
        val memory = SceneMemory()
        val first = memory.remember(byteArrayOf(1, 2, 3))
        repeat(3) {
            assertSame(first, memory.current())
            assertEquals(PhotoUse.REUSE, photoUse(true, false, memory.current() != null, false))
        }
    }

    @Test fun automaticVisualHintsCannotReplaceTheReference() {
        assertEquals(PhotoUse.REUSE, photoUse(true, false, true, true))
        assertEquals(PhotoUse.CAPTURE, photoUse(true, false, false, true))
    }

    @Test fun anotherExplicitLookReplacesThePhoto() {
        val memory = SceneMemory()
        val first = memory.remember(byteArrayOf(1))
        assertEquals(PhotoUse.CAPTURE, photoUse(true, true, true, false))
        memory.clear()
        val next = memory.remember(byteArrayOf(2))
        assertNotEquals(first.frame, next.frame)
        assertArrayEquals(byteArrayOf(2), memory.current()!!.jpeg)
    }

    @Test fun failedNewCaptureDoesNotSilentlyReuseTheOldScene() {
        val memory = SceneMemory()
        memory.remember(byteArrayOf(1))
        memory.clear()
        assertNull(memory.current())
    }

    @Test fun endedAndDifferentSessionsDoNotRetainPhotoContext() {
        val firstSession = SceneMemory()
        firstSession.remember(byteArrayOf(1))
        assertNull(SceneMemory().current())
        firstSession.clear()
        assertNull(firstSession.current())
    }

    @Test fun disabledCameraNeitherCapturesNorReattachesAStoredImage() {
        for (explicit in listOf(false, true)) for (saved in listOf(false, true))
            assertEquals(PhotoUse.NONE, photoUse(false, explicit, saved, true))
        assertEquals(PhotoUse.NONE, photoUse(true, false, false, false))
    }
}
