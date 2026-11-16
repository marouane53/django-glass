package com.macedonga.visor.core.assistant

import java.text.Normalizer
import java.util.Locale

/** Complete camera commands bypass model generation; descriptions, negations and compound requests do not. */
internal fun cameraCommand(question: String, locale: Locale): CameraAction? {
    if (locale.language != "vi" && locale.language != "en") return null
    val words = Normalizer.normalize(question, Normalizer.Form.NFC).lowercase(Locale.ROOT)
        .trim().trimEnd('.', '!', '?').trim().replace(Regex("[\\s\\p{Z}]+"), " ")
        .replace(Regex("^(?:hey meta|hi meta|okay meta|ok meta)(?:, ?| )"), "")
        .replace(Regex("^(?:(?:can you|could you) )?(?:please )?"), "")
        .replace(Regex(",? (?:please|thanks)$"), "")
    return when (words) {
        "chụp", "chụp ảnh", "chụp hình", "take photo", "take a photo", "take picture", "take a picture",
        "capture a photo", "take a snapshot" -> CameraAction.TAKE_PHOTO
        "quay video", "quay phim", "bắt đầu quay video", "bắt đầu quay phim", "record video", "record a video",
        "take video", "take a video", "shoot a video", "film a video", "start recording", "start recording video" -> CameraAction.RECORD_VIDEO
        "quay hyperlapse", "bắt đầu quay hyperlapse", "record hyperlapse", "start hyperlapse",
        "start recording hyperlapse" -> CameraAction.RECORD_HYPERLAPSE
        "dừng quay", "dừng quay video", "dừng quay phim", "dừng quay hyperlapse", "stop recording",
        "stop video", "stop the video", "stop recording video", "stop recording hyperlapse", "stop hyperlapse" -> CameraAction.STOP_RECORDING
        else -> null
    }
}
