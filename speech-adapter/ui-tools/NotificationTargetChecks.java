package org.marouane.glassesspeech.ui;

import android.app.Instrumentation;
import android.os.Bundle;

/** Runs with the app's UID, so Android's default-SMS package lookup is tested accurately. */
public class NotificationTargetChecks extends Instrumentation {
    public void onCreate(Bundle arguments) { super.onCreate(arguments); start(); }
    public void onStart() {
        Bundle result = new Bundle();
        try {
            result.putInt("checks_passed", NotificationChecks.run(getTargetContext()));
            result.putBoolean("success", true);
        } catch (Throwable e) {
            result.putBoolean("success", false);
            result.putString("reason", e.getClass().getSimpleName() + ": " + e.getMessage());
        }
        finish(-1, result);
    }
}
