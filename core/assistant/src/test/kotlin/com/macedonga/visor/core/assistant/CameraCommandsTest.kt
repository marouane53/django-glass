package com.macedonga.visor.core.assistant

import java.util.Locale
import org.junit.Assert.*
import org.junit.Test

class CameraCommandsTest {
    @Test fun requestedPhotoAndVideoPhrasesRouteToDistinctActions() {
        assertEquals(CameraAction.TAKE_PHOTO, cameraCommand("Hey Meta, take a picture.", Locale.US))
        assertEquals(CameraAction.TAKE_PHOTO, cameraCommand("Take a photo!", Locale.US))
        assertEquals(CameraAction.RECORD_VIDEO, cameraCommand("Hey Meta, take a video.", Locale.US))
        assertEquals(CameraAction.RECORD_VIDEO, cameraCommand("Can you please record a video?", Locale.US))
        assertEquals(CameraAction.STOP_RECORDING, cameraCommand("Stop recording, please.", Locale.US))
        assertEquals(CameraAction.STOP_RECORDING, cameraCommand("Hey Meta, stop the video!", Locale.US))
        assertEquals(CameraAction.RECORD_HYPERLAPSE, cameraCommand("Record hyperlapse", Locale.US))
    }

    @Test fun questionsNegationsAndCompoundRequestsAreLeftToTheModel() {
        for (question in listOf("Don't take a picture", "How do I take a video?", "Take a picture and send it to Mom",
            "Take a video of the dog", "Stop", "What is in this picture?"))
            assertNull(question, cameraCommand(question, Locale.US))
    }

    @Test fun existingVietnameseCommandsArePreservedAndOtherLanguagesUseTheModel() {
        assertEquals(CameraAction.TAKE_PHOTO, cameraCommand("Chụp ảnh", Locale.forLanguageTag("vi")))
        assertEquals(CameraAction.STOP_RECORDING, cameraCommand("Dừng quay video", Locale.forLanguageTag("vi")))
        assertNull(cameraCommand("Prends une photo", Locale.FRENCH))
    }
}
