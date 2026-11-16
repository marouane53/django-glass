package org.marouane.glassesspeech.ui;

import android.app.Instrumentation;
import android.os.Bundle;
import android.view.accessibility.AccessibilityNodeInfo;
import java.util.*;

/** Short-lived, ADB-invoked semantic actions; refuses to operate outside the specified app. */
public class UiAction extends Instrumentation {
    Bundle args;
    public void onCreate(Bundle arguments) { super.onCreate(arguments); args = arguments; start(); }
    public void onStart() {
        Bundle result = new Bundle();
        try {
            String pkg = args.getString("package", "com.macedonga.visor");
            String action = args.getString("action", "list");
            String label = args.getString("label", "");
            android.app.UiAutomation automation = getUiAutomation(android.app.UiAutomation.FLAG_DONT_SUPPRESS_ACCESSIBILITY_SERVICES);
            try { automation.waitForIdle(100, 1500); } catch (Exception ignored) {}
            AccessibilityNodeInfo root = automation.getRootInActiveWindow();
            for (int attempt = 0; root == null && attempt < 20; attempt++) {
                Thread.sleep(100);
                root = automation.getRootInActiveWindow();
            }
            if (root == null || !pkg.contentEquals(root.getPackageName())) throw new IllegalStateException("Expected app is not active: " + (root == null ? "no window" : root.getPackageName()));
            ArrayList<AccessibilityNodeInfo> nodes = new ArrayList<>(); collect(root, nodes);
            if (action.equals("list")) {
                ArrayList<String> labels = new ArrayList<>();
                for (AccessibilityNodeInfo n : nodes) if (n.isVisibleToUser() && !n.isPassword() && !"android.widget.EditText".contentEquals(n.getClassName())) {
                    if (n.getText() != null) labels.add(n.getText().toString().contains("@") ? "[account]" : n.getText().toString().substring(0, Math.min(n.getText().length(), 220)));
                    else if (n.getContentDescription() != null) labels.add("[" + n.getContentDescription() + "]");
                }
                result.putStringArrayList("labels", labels);
            } else if (action.equals("toggle_state")) {
                ArrayList<AccessibilityNodeInfo> matches = new ArrayList<>();
                for (AccessibilityNodeInfo n : nodes) if (n.isVisibleToUser() && label.contentEquals(n.getText() == null ? "" : n.getText())) matches.add(n);
                if (matches.size() != 1) throw new IllegalStateException("No unique visible toggle label");
                AccessibilityNodeInfo n = matches.get(0);
                while (n != null && !n.isCheckable()) n = n.getParent();
                if (n == null) throw new IllegalStateException("Toggle state is not exposed");
                result.putBoolean("success", true);
                result.putBoolean("checked", n.isChecked());
            } else if (action.equals("scroll")) {
                AccessibilityNodeInfo scroll = null; for (AccessibilityNodeInfo n : nodes) if (n.isVisibleToUser() && n.isScrollable()) scroll = n;
                result.putBoolean("success", scroll != null && scroll.performAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD));
            } else {
                ArrayList<AccessibilityNodeInfo> matches = new ArrayList<>();
                for (AccessibilityNodeInfo n : nodes) if (n.isVisibleToUser() && (
                    (action.equals("set_text") && "android.widget.EditText".contentEquals(n.getClassName()))
                    || ((action.equals("click") || action.equals("click_description")) && ((!action.equals("click_description") && label.contentEquals(n.getText() == null ? "" : n.getText())) || label.contentEquals(n.getContentDescription() == null ? "" : n.getContentDescription()))))) matches.add(n);
                if (matches.size() != 1) throw new IllegalStateException("No unique visible control");
                AccessibilityNodeInfo n = matches.get(0);
                boolean success;
                if (action.equals("set_text")) {
                    String text = new String(android.util.Base64.decode(args.getString("text64"), android.util.Base64.DEFAULT), java.nio.charset.StandardCharsets.UTF_8);
                    Bundle value = new Bundle(); value.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text);
                    success = n.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, value);
                } else {
                    while (n != null && !n.isClickable()) n = n.getParent();
                    success = n != null && n.isEnabled() && n.performAction(AccessibilityNodeInfo.ACTION_CLICK);
                }
                result.putBoolean("success", success);
            }
        } catch (Exception e) { result.putBoolean("success", false); result.putString("reason", e.getMessage()); }
        finish(-1, result);
    }
    private void collect(AccessibilityNodeInfo n, ArrayList<AccessibilityNodeInfo> result) { result.add(n); for (int i = 0; i < n.getChildCount(); i++) { AccessibilityNodeInfo child = n.getChild(i); if (child != null) collect(child, result); } }
}
