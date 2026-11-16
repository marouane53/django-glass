package org.marouane.glassesspeech;

public class TranscriptionPolicyTest {
    public static void main(String[] args) throws Exception {
        int[] calls = new int[2];
        TranscriptionPolicy.Result first = TranscriptionPolicy.run(() -> { calls[0]++; return "question"; }, () -> { calls[1]++; return "fallback"; }, e -> true);
        require(first.text.equals("question") && first.provider.equals("mai") && calls[0] == 1 && calls[1] == 0, "primary success");
        calls[0] = 0;
        TranscriptionPolicy.Result recovered = TranscriptionPolicy.run(() -> { if (++calls[0] == 1) throw new Exception("temporary"); return "recovered"; }, () -> { throw new AssertionError("unneeded fallback"); }, e -> true);
        require(recovered.text.equals("recovered") && calls[0] == 2, "one bounded retry");
        calls[0] = 0;
        TranscriptionPolicy.Result alternate = TranscriptionPolicy.run(() -> { calls[0]++; throw new Exception("authentication"); }, () -> "Darija", e -> false);
        require(alternate.provider.equals("gemini") && alternate.text.equals("Darija") && calls[0] == 1, "hard failure falls back without retry");
        TranscriptionPolicy.Result silence = TranscriptionPolicy.run(() -> "", () -> { throw new AssertionError("silence is not a provider failure"); }, e -> false);
        require(silence.text.isEmpty() && silence.provider.equals("mai"), "valid silence");
        calls[0] = 0;
        try {
            TranscriptionPolicy.run(() -> { calls[0]++; throw new Exception("primary"); }, () -> { throw new Exception("fallback"); }, e -> true);
            throw new AssertionError("both providers failing must be reported");
        } catch (Exception expected) { require(calls[0] == 2 && expected.getSuppressed().length == 1, "bounded failure propagation"); }
        System.out.println("5 transcription recovery checks passed");
    }
    static void require(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
}
