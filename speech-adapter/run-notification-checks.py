#!/usr/bin/env python3
"""Run synthetic Django checks and restore previously enabled call controls."""
import os
import shlex
import shutil
import subprocess
import sys
import time

ADB = os.environ.get("ADB") or shutil.which("adb")
if not ADB:
    sys.exit("adb was not found")

SERVICE = "org.marouane.djangoglasses/com.macedonga.visor.dots.DotCallAccessibilityService"
ACTIVITY = "org.marouane.djangoglasses/com.macedonga.visor.MainActivity"


def shell(*args):
    # adb joins remote arguments; quote the complete remote command, including empty values.
    return subprocess.check_output([ADB, "shell", shlex.join(args)], text=True)


def setting(name):
    return shell("settings", "--user", "0", "get", "secure", name).strip()


def services():
    return [x for x in setting("enabled_accessibility_services").split(":") if x not in ("", "null")]


def put(name, value):
    shell("settings", "--user", "0", "put", "secure", name, value)


def main():
    restore = SERVICE in services() and setting("accessibility_enabled") == "1"
    passed = False
    try:
        result = shell("am", "instrument", "--user", "0", "-w",
                       "org.marouane.glassesspeech.ui/.NotificationTargetChecks")
        for line in result.splitlines():
            if line.startswith("INSTRUMENTATION_RESULT:"):
                print(line)
        passed = "INSTRUMENTATION_RESULT: success=true" in result
    finally:
        shell("am", "start", "--user", "0", "-n", ACTIVITY)
        if restore:
            # Only rebind this already enabled service; merge with the current list each time.
            put("enabled_accessibility_services", ":".join(x for x in services() if x != SERVICE))
            time.sleep(0.5)
            current = services()
            put("enabled_accessibility_services", ":".join(current + ([SERVICE] if SERVICE not in current else [])))
            put("accessibility_enabled", "1")
            bound = False
            for _ in range(30):
                report = shell("dumpsys", "accessibility")
                bound = any("Bound services:" in line and SERVICE in line for line in report.splitlines())
                if bound:
                    break
                time.sleep(0.1)
            print("Django call controls restored and bound" if bound else "Check Django dot call controls in Accessibility settings")
            if not bound:
                passed = False
        else:
            print("Accessibility was not enabled before the test; access was not granted")
    return 0 if passed else 1


if __name__ == "__main__":
    sys.exit(main())
