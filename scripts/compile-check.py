#!/usr/bin/env python3
"""Compile every module's Java sources against android.jar without Gradle.

This is the fast sanity check used when the full Android toolchain is not
available (for example in a sandbox with only a JDK and one platform jar).
It generates a temporary ``R.java`` stub per module from its ``src/main/res``
so resource references resolve, then compiles all main (and optionally the
library's test) sources together.

Usage:
    python3 scripts/compile-check.py                       # uses ANDROID_HOME
    python3 scripts/compile-check.py --android-jar /path/android-35.jar
    python3 scripts/compile-check.py --ecj /path/ecj-4.30.jar
    python3 scripts/compile-check.py --ecj /path/ecj-4.30.jar --java /path/to/java
    python3 scripts/compile-check.py --tests --junit /path/to/junit-4.13.2.jar

The normal build is still ``./gradlew assembleDebug``.
"""

import argparse
import glob
import os
import re
import shutil
import subprocess
import sys
import tempfile

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))

# (module dir, namespace, has unit tests). The app's R must be generated
# too because it references its own launcher resources in code-free manifest only,
# but its classes reference the library R.
MODULES = [
    ("soniq-base", "com.soniq.base", True),
    ("app", "com.spinback.gplaymusic", False),
]


def find_android_jar(explicit):
    if explicit:
        return explicit if os.path.isfile(explicit) else None
    candidates = []
    home = os.environ.get("ANDROID_HOME") or os.environ.get("ANDROID_SDK_ROOT")
    if home:
        candidates.append(os.path.join(home, "platforms", "android-35", "android.jar"))
        candidates.append(os.path.join(home, "platforms", "android-34", "android.jar"))
        # Newest platform wins if the preferred one is missing.
        candidates.extend(sorted(
            glob.glob(os.path.join(home, "platforms", "android-*", "android.jar")),
            reverse=True))
    for candidate in candidates:
        if os.path.isfile(candidate):
            return candidate
    return None


def resource_fields(res_dir):
    fields = []
    ids = []
    for folder, kind in (("drawable", "drawable"), ("layout", "layout"),
                         ("mipmap-anydpi-v26", "mipmap"), ("mipmap-anydpi", "mipmap"),
                         ("xml", "xml")):
        path = os.path.join(res_dir, folder)
        if not os.path.isdir(path):
            continue
        for name in sorted(os.listdir(path)):
            if not name.endswith(".xml"):
                continue
            fields.append((kind, os.path.splitext(name)[0]))
            # Layouts declare view ids with @+id/..., which also generate R.id.
            text = open(os.path.join(path, name), encoding="utf-8").read()
            for match in re.finditer(r'@\+id/([A-Za-z_][A-Za-z0-9_]*)', text):
                if match.group(1) not in ids:
                    ids.append(match.group(1))
    for name in ("strings.xml", "colors.xml", "themes.xml"):
        path = os.path.join(res_dir, "values", name)
        if not os.path.isfile(path):
            continue
        text = open(path, encoding="utf-8").read()
        for match in re.finditer(r'<string name="([^"]+)"', text):
            fields.append(("string", match.group(1)))
        for match in re.finditer(r'<color name="([^"]+)"', text):
            fields.append(("color", match.group(1)))
        for match in re.finditer(r'<style name="([^"]+)"', text):
            fields.append(("style", match.group(1).replace(".", "_")))
    fields.extend(("id", name) for name in ids)
    return fields


def write_r_stub(directory, package, res_dir):
    path = os.path.join(directory, package.replace(".", "/"))
    os.makedirs(path, exist_ok=True)
    lines = ["package %s;" % package, "", "public final class R {"]
    current = None
    for kind, name in resource_fields(res_dir):
        if kind != current:
            if current:
                lines.append("    }")
            lines.append("    public static final class %s {" % kind)
            current = kind
        lines.append("        public static final int %s = 0x7f%02x0000;" % (name, 1))
    if current:
        lines.append("    }")
    lines.append("}")
    with open(os.path.join(path, "R.java"), "w", encoding="utf-8") as handle:
        handle.write("\n".join(lines) + "\n")
    return os.path.join(path, "R.java")


def run(command, cwd=None):
    print("+ " + " ".join(command))
    result = subprocess.run(command, cwd=cwd)
    return result.returncode


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--android-jar", help="Path to platforms/android-XX/android.jar")
    parser.add_argument("--ecj", help="Path to ecj-X.jar; used when javac is unavailable")
    parser.add_argument("--java", help="java executable for running --ecj (default: PATH)")
    parser.add_argument("--tests", action="store_true",
                        help="Compile the library's test sources too (requires --junit)")
    parser.add_argument("--junit", help="Path to junit-X.jar for --tests")
    parser.add_argument("--output", help="Directory for .class files (default: temp)")
    args = parser.parse_args()

    android_jar = find_android_jar(args.android_jar)
    if not android_jar:
        print("ERROR: android.jar not found. Set ANDROID_HOME or pass --android-jar.",
              file=sys.stderr)
        return 2

    if args.tests and not (args.junit and os.path.exists(args.junit)):
        print("ERROR: --tests needs --junit /path/to/junit-4.13.2.jar", file=sys.stderr)
        return 2

    classpath = android_jar + (os.pathsep + args.junit if args.tests else "")
    output = args.output or tempfile.mkdtemp(prefix="soniq-check-")
    os.makedirs(output, exist_ok=True)
    stub_dir = tempfile.mkdtemp(prefix="soniq-r-")

    sources = []
    for module, package, has_tests in MODULES:
        module_dir = os.path.join(ROOT, *module.split("/"))
        sources.extend(sorted(glob.glob(os.path.join(
            module_dir, "src", "main", "java", "**", "*.java"), recursive=True)))
        res_dir = os.path.join(module_dir, "src", "main", "res")
        if os.path.isdir(res_dir):
            sources.append(write_r_stub(stub_dir, package, res_dir))
        if args.tests and has_tests:
            sources.extend(sorted(glob.glob(os.path.join(
                module_dir, "src", "test", "java", "**", "*.java"), recursive=True)))

    javac = shutil.which("javac")
    if javac:
        command = [javac, "-encoding", "UTF-8", "-source", "8", "-target", "8",
                   "-Xlint:-options", "-cp", classpath, "-d", output] + sources
    elif args.ecj:
        java = args.java or shutil.which("java") or "java"
        if args.java and not os.path.isfile(args.java):
            print("ERROR: --java executable not found: %s" % args.java, file=sys.stderr)
            return 2
        command = [java, "-jar", args.ecj, "-1.8", "-encoding", "UTF-8",
                   "-cp", classpath, "-d", output] + sources
    else:
        print("ERROR: javac not found and --ecj was not provided.", file=sys.stderr)
        return 2

    code = run(command)
    if code == 0:
        print("OK: %d Java files compiled against %s" % (len(sources), android_jar))
        if not args.output:
            shutil.rmtree(output, ignore_errors=True)
    shutil.rmtree(stub_dir, ignore_errors=True)
    return code


if __name__ == "__main__":
    sys.exit(main())
