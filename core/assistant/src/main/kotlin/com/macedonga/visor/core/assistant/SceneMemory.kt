package com.macedonga.visor.core.assistant

/** One reference image for one open glasses conversation; never a continuous camera feed. */
internal class SceneMemory {
    data class Photo(val jpeg: ByteArray, val frame: Int)
    private var photo: Photo? = null
    private var frame = 0

    @Synchronized fun current(): Photo? = photo
    @Synchronized fun remember(jpeg: ByteArray): Photo = Photo(jpeg, ++frame).also { photo = it }
    @Synchronized fun clear() { photo = null }
}

internal enum class PhotoUse { NONE, CAPTURE, REUSE }

/** A reference photo wins over automatic capture hints; a new view requires an explicit request. */
internal fun photoUse(enabled: Boolean, explicit: Boolean, saved: Boolean, deviceVisual: Boolean): PhotoUse = when {
    !enabled -> PhotoUse.NONE
    explicit -> PhotoUse.CAPTURE
    saved -> PhotoUse.REUSE
    deviceVisual -> PhotoUse.CAPTURE
    else -> PhotoUse.NONE
}
