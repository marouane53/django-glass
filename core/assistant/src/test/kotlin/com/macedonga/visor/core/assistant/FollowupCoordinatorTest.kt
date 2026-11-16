package com.macedonga.visor.core.assistant

import org.junit.Assert.*
import org.junit.Test

class FollowupCoordinatorTest {
    @Test fun nativeThenPhoneCreatesOneReplyWithTheGlassesInteractionId() {
        val turns = FollowupCoordinator()
        assertTrue(turns.offerNative("glasses-1", "what model are you", 1000))
        assertEquals("glasses-1", turns.claimPhone("phone-1", 2000))
        assertNull(turns.claimNativeFallback("glasses-1"))
    }

    @Test fun phoneThenNativeDoesNotCreateASecondReply() {
        val turns = FollowupCoordinator()
        assertEquals("phone-1", turns.claimPhone("phone-1", 1000))
        assertFalse(turns.offerNative("glasses-1", "what model are you", 2000))
        assertNull(turns.claimNativeFallback("glasses-1"))
    }

    @Test fun nativeReportsJustAfterPlaybackDoNotRepeatTheAnswer() {
        val turns = FollowupCoordinator()
        turns.claimPhone("phone-1", 1000)
        turns.finishPhone("phone-1", 5000)
        assertFalse(turns.offerNative("late-duplicate", "the same question", 5100))
        assertTrue(turns.offerNative("next-turn", "a new question", 7500))
        assertEquals("next-turn", turns.claimPhone("phone-2", 8000))
    }

    @Test fun anUnansweredNativeTurnCanFallBackOnlyOnce() {
        val turns = FollowupCoordinator()
        turns.offerNative("glasses-1", "hello", 1000)
        assertEquals("hello", turns.claimNativeFallback("glasses-1")?.text)
        assertNull(turns.claimNativeFallback("glasses-1"))
    }

    @Test fun aNewNativeTurnCannotBeConsumedByAnOlderTimer() {
        val turns = FollowupCoordinator()
        turns.offerNative("old", "first", 1000)
        turns.offerNative("new", "second", 2000)
        assertNull(turns.claimNativeFallback("old"))
        assertEquals("new", turns.claimPhone("phone-1", 3000))
    }

    @Test fun aDelayedPhoneResultCannotDuplicateANativeFallback() {
        val turns = FollowupCoordinator()
        turns.offerNative("native", "question", 1000)
        assertNotNull(turns.claimNativeFallback("native"))
        assertNull(turns.claimPhone("late-phone", 9500))
        turns.finishNativeFallback("native", 12000)
        assertNull(turns.claimPhone("still-the-old-result", 12100))
        assertEquals("new-phone", turns.claimPhone("new-phone", 15000))
    }
}
