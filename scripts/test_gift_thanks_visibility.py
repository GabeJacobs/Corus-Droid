#!/usr/bin/env python3
"""Compile and execute the production Kotlin receipt visibility expression."""
from pathlib import Path
import re
import os
import subprocess
import tempfile

root = Path(__file__).resolve().parents[1]
source = (root / 'app/src/main/java/fm/corus/android/ui/components/PostGiftRow.kt').read_text()
rule = re.search(r'visible = (.*actionGift.*),\n', source).group(1)
kotlin = '''
data class Receipt(val canThank: Boolean)
fun visible(uid: String?, recipientId: String, actionGift: Receipt?, actionIsThanked: Boolean): Boolean =
''' + rule + '''
fun main() {
    check(!visible("gabe", "arielle", Receipt(false), true)) { "Sender must not see Thanked" }
    check(!visible("bystander", "arielle", Receipt(false), true))
    check(!visible(null, "arielle", Receipt(true), true))
    check(!visible("gabe", "arielle", Receipt(false), false))
    check(visible("arielle", "arielle", Receipt(true), false))
    check(visible("arielle", "arielle", Receipt(false), true))
    check(!visible("arielle", "arielle", Receipt(false), false))
    check(!visible("arielle", "arielle", null, true))
    println("PASS: production Android Gift thanks visibility for recipient, sender, bystander, signed-out and absent receipts")
}
'''
# Use the compiler already bundled in the local Gradle distribution.
compilers = list((Path.home() / '.gradle/wrapper/dists').rglob('kotlin-compiler-embeddable-*.jar'))
assert compilers, 'Run the Gradle wrapper once to make its bundled Kotlin compiler available'
lib = sorted(compilers)[-1].parent
stdlib = next(lib.glob('kotlin-stdlib-*.jar'))
java_home = Path(os.environ.get('JAVA_HOME', '/Applications/Android Studio.app/Contents/jbr/Contents/Home'))
java = str(java_home / 'bin/java') if (java_home / 'bin/java').exists() else 'java'
with tempfile.TemporaryDirectory(prefix='corus-gift-thanks-') as directory:
    path = Path(directory)
    main = path / 'main.kt'
    main.write_text(kotlin)
    classes = path / 'classes'
    subprocess.run([java, '-cp', str(lib / '*'), 'org.jetbrains.kotlin.cli.jvm.K2JVMCompiler',
                    '-no-stdlib', '-no-reflect', '-classpath', str(stdlib), str(main), '-d', str(classes)], check=True)
    subprocess.run([java, '-cp', str(classes) + ':' + str(stdlib), 'MainKt'], check=True)
