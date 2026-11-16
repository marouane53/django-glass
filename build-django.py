#!/usr/bin/env python3
"""Build the personal fork using publisher app credentials supplied in memory, never in source."""
import argparse
import os
from pathlib import Path
import re
import subprocess
import sys

ROOT = Path(__file__).resolve().parent
parser = argparse.ArgumentParser()
parser.add_argument("--identity-config", type=Path, help="Private, temporary publisher BuildConfig extracted from the official APK")
parser.add_argument("tasks", nargs="*", default=[":app:testDebugUnitTest", ":core:assistant:testDebugUnitTest", ":core:media:testDebugUnitTest", ":app:assembleRelease"])
args = parser.parse_args()
env = os.environ.copy()
env.setdefault("JAVA_HOME", "/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home")
env.setdefault("ANDROID_HOME", "/opt/homebrew/share/android-commandlinetools")
env.setdefault("VISOR_KEYSTORE", str(ROOT / "speech-adapter/build/local-signing.jks"))
env.setdefault("VISOR_KEYSTORE_PASSWORD", "android")
env.setdefault("VISOR_KEY_ALIAS", "local")
if args.identity_config:
    for name, value in re.findall(r'public static final String (META_\w+_TOKEN) = "([^"]*)";', args.identity_config.read_text()):
        env["VISOR_" + name] = value
if any(not env.get(name) for name in ("VISOR_META_HW_TOKEN", "VISOR_META_AR_TOKEN", "VISOR_META_FRL_TOKEN")):
    sys.exit("Missing publisher app configuration. Supply it via process environment or a private temporary BuildConfig; do not add credentials to source.")
log = ROOT / "build/django-build.log"
log.parent.mkdir(exist_ok=True)
with log.open("w") as output:
    status = subprocess.run([str(ROOT / "gradlew"), *args.tasks, "--console=plain"], cwd=ROOT, env=env, stdout=output, stderr=subprocess.STDOUT).returncode
print("Build passed" if status == 0 else "Build failed; inspect build/django-build.log")
sys.exit(status)
