package org.marouane.glassesspeech;

import java.util.ArrayList;
import java.util.List;

public class PcmUtterancesTest {
    public static void main(String[] args) {
        PcmUtterances question = new PcmUtterances(); List<byte[]> joined = new ArrayList<>();
        feed(question, joined, 1000, true); feed(question, joined, 1200, false);
        require(joined.isEmpty(), "a natural pause must not submit half a question");
        feed(question, joined, 1000, true); feed(question, joined, 1600, false);
        require(joined.size() == 1 && joined.get(0).length >= 32000 * 3, "both halves remain one question");

        PcmUtterances separate = new PcmUtterances(); List<byte[]> turns = new ArrayList<>();
        for (int i = 0; i < 3; i++) { feed(separate, turns, 1000, true); feed(separate, turns, 1800, false); }
        require(turns.size() == 3, "three finished turns remain separate");

        PcmUtterances noise = new PcmUtterances(); List<byte[]> ignored = new ArrayList<>();
        feed(noise, ignored, 100, true); feed(noise, ignored, 1800, false);
        require(ignored.isEmpty() && noise.finish() == null, "a brief click is not a question");

        PcmUtterances last = new PcmUtterances(); List<byte[]> incomplete = new ArrayList<>();
        feed(last, incomplete, 800, true);
        require(last.finish() != null && last.finish() == null, "pipe close flushes speech exactly once");

        PcmUtterances continuous = new PcmUtterances(); List<byte[]> bounded = new ArrayList<>();
        feed(continuous, bounded, 21000, true);
        require(bounded.isEmpty(), "a question longer than 20 seconds must remain whole");
        feed(continuous, bounded, 40000, true);
        require(bounded.size() == 1 && bounded.get(0).length == 1920000, "continuous speech remains bounded at 60 seconds");
        PcmUtterances quick = new PcmUtterances(700); List<byte[]> whole = new ArrayList<>();
        feed(quick, whole, 1000, true); feed(quick, whole, 600, false);
        require(quick.early() == null, "a pause shorter than the early mark gives nothing early");
        feed(quick, whole, 100, false);
        byte[] first = quick.early();
        require(first != null && quick.early() == null && whole.isEmpty(), "the early copy comes once, and the utterance stays open");
        feed(quick, whole, 1000, true);
        feed(quick, whole, 700, false);
        byte[] second = quick.early();
        require(second != null && second.length > first.length, "after more speech a new early copy holds the whole question");
        feed(quick, whole, 900, false);
        require(whole.size() == 1 && quick.matchedEarly(), "a pause that proves final matches its early copy");

        PcmUtterances resumed = new PcmUtterances(700); List<byte[]> later = new ArrayList<>();
        feed(resumed, later, 1000, true); feed(resumed, later, 700, false);
        require(resumed.early() != null, "early copy before the speaker goes on");
        feed(resumed, later, 400, true);
        require(resumed.finish() != null && !resumed.matchedEarly(), "speech after the early copy makes it stale");
        PcmUtterances click = new PcmUtterances(700); List<byte[]> none = new ArrayList<>();
        feed(click, none, 100, true);
        require(click.active() && !click.talking(), "a click is not someone talking again");
        feed(click, none, 200, true);
        require(click.talking(), "a fifth of a second of voice is");
        System.out.println("8 speech-boundary regression checks passed");
    }
    private static void feed(PcmUtterances s, List<byte[]> out, int milliseconds, boolean speech) {
        byte[] frame = new byte[640];
        if (speech) for (int i = 0; i < frame.length; i += 2) { frame[i] = (byte)0xe8; frame[i + 1] = 3; }
        for (int i = 0; i < milliseconds / 20; i++) { byte[] complete = s.accept(frame, frame.length); if (complete != null) out.add(complete); }
    }
    private static void require(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
}
