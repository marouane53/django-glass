package org.marouane.glassesspeech;

/** Provider recovery does not end the Android recognition session. No audio or text is persisted here. */
final class TranscriptionPolicy {
    interface Attempt { String call() throws Exception; }
    interface Retryable { boolean test(Exception failure); }
    static final class Result {
        final String text, provider;
        final Exception primaryFailure;
        Result(String value, String chosen, Exception failure) { text = value; provider = chosen; primaryFailure = failure; }
    }
    static Result run(Attempt primary, Attempt fallback, Retryable retryable) throws Exception {
        Exception failure;
        try { return new Result(primary.call(), "mai", null); }
        catch (Exception e) { failure = e; }
        if (retryable.test(failure)) {
            Thread.sleep(200);
            try { return new Result(primary.call(), "mai", failure); }
            catch (Exception e) { failure = e; }
        }
        try { return new Result(fallback.call(), "gemini", failure); }
        catch (Exception e) { e.addSuppressed(failure); throw e; }
    }
}
