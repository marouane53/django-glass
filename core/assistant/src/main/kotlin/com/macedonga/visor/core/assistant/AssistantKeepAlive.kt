package com.macedonga.visor.core.assistant

import com.macedonga.visor.core.protocol.ThriftWriter

/** The audio-session lease and partial response extend different deadlines; cloud speech needs both. */
internal fun assistantKeepAlive(interaction: String, partial: Boolean, sequence: Int, reason: String = "AwaitingResponse"): List<Pair<Int, ByteArray>> = buildList {
    add(EV_KEEP_AIR_SESSION_ALIVE to ThriftWriter().str(1, reason).i32(2, 20_000).i32(3, sequence).done())
    if (partial) add(EV_PARTIAL_VOICE_INTERACTION_FULFILLMENT_RESPONSE to ThriftWriter().str(2, interaction).bool(3, false).done())
}
