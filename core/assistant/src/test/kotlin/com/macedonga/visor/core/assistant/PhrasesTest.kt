package com.macedonga.visor.core.assistant

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PhrasesTest {
    @Test fun theFirstSentenceGoesAsSoonAsTheSpaceAfterItIsWritten() {
        val p = Phrases()
        p.add("A compass needle is a small magnet")
        assertNull(p.take())
        p.add(".")
        assertNull(p.take())
        p.add(" It lines up with")
        assertEquals("A compass needle is a small magnet. ", p.take())
        assertNull(p.take())
    }

    @Test fun laterPiecesTakeEverySentenceWrittenMeanwhile() {
        val p = Phrases()
        p.add("A compass needle is a small magnet. It lines up with Earth's field. That field runs north to south! So the needle")
        assertEquals("A compass needle is a small magnet. ", p.take())
        assertEquals("It lines up with Earth's field. That field runs north to south! ", p.take())
        assertNull(p.take())
        p.add(" points north.")
        p.end()
        assertEquals("So the needle points north.", p.take())
        assertNull(p.take())
        assertEquals(
            "A compass needle is a small magnet. It lines up with Earth's field. That field runs north to south! So the needle points north.",
            p.spoken(),
        )
    }

    @Test fun aShortOpeningWaitsForTheNextSentence() {
        val p = Phrases()
        p.add("Sure. It is 3.5 km to Dr. Amrani's office, e.g. ten minutes by car. Then ")
        assertEquals("Sure. It is 3.5 km to Dr. Amrani's office, e.g. ten minutes by car. ", p.take())
    }

    @Test fun numbersListsAndLinksAreNotCutApart() {
        assertEquals(0, Phrases.cut("The total comes to 1. 5 if you count", first = true))
        assertEquals(0, Phrases.cut("It is 21 degrees ([weather.com](https://weather.com/a.b?c=1", first = true))
        assertEquals("It is 21 degrees today. ".length, Phrases.cut("It is 21 degrees today. ([weather.com](https://w", first = true))
    }

    @Test fun darijaAndLineBreaksEndSentences() {
        val p = Phrases()
        p.add("واش بغيتي نشرح ليك كتر؟ إيه، البوصلة فيها مغناطيس.\nوهي")
        assertEquals("واش بغيتي نشرح ليك كتر؟ ", p.take())
        assertEquals("إيه، البوصلة فيها مغناطيس.\n", p.take())
    }

    @Test fun aBrokenAnswerKeepsWhatWasSpokenAndDropsTheRest() {
        val p = Phrases()
        p.add("The first sentence is whole. The second one never")
        assertEquals("The first sentence is whole. ", p.take())
        p.fail()
        assertNull(p.take())
        assertEquals("The first sentence is whole. ", p.spoken())
    }

    @Test fun awaitWakesWhenASentenceCompletesAndEndsWithTheAnswer() = runBlocking {
        val p = Phrases()
        val first = async(Dispatchers.Default) { p.await() }
        p.add("This is the first sentence")
        p.add(". And the rest")
        assertEquals("This is the first sentence. ", first.await())
        val rest = async(Dispatchers.Default) { p.await() }
        p.end()
        assertEquals("And the rest", rest.await())
        assertNull(p.await())
    }
}
