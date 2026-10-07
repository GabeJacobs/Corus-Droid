#!/usr/bin/env python3
"""Run production catalog discovery in isolation from Android UI compilation."""
from pathlib import Path
import re
import subprocess
import tempfile
import os

source = (Path(__file__).resolve().parents[1] / "app/src/main/java/fm/corus/android/service/RemoteConfigService.kt").read_text()
start = source.index("    class DebugFeatureFlag(")
end = source.index("    val debugOverrideCount:", start)
catalog = source[start:end]
names = set(re.findall(r'DebugFeatureFlag\("[^"]+"\) \{ (\w+) \}', catalog))
stub = '''
object BuildConfig { const val DEBUG = true }
class Value(val text: String) { fun asString() = text }
class Remote {
  val all = mapOf("future_experiment_enabled" to Value("false"), "future_experiment_variant" to Value("b"))
  fun getBoolean(key: String) = all[key]?.text == "true"
}
class User(val uid: String = "test")
class Auth { val currentUser = User() }
class RemoteConfigService {
  private val remoteConfig = Remote()
  private val auth = Auth()
  private val debugServerUid: String? = null
  private val debugServerValues = emptyMap<String, String>()
'''
stub += "\n".join(f"val {name} = false" for name in sorted(names)) + "\n" + catalog + "\n}\n"
stub += '''
fun main() {
  val flags = RemoteConfigService().debugFeatureFlags
  check(flags.any { it.key == "future_experiment_enabled" }) { "Future flag is missing" }
  check(flags.any { it.key == "future_experiment_variant" }) { "Future variant is missing" }
  check(flags.count { it.key == "books_enabled" } == 1) { "Registered flag duplicated" }
  check(flags.first { it.key == "future_experiment_enabled" }.rawValue == "false")
  check(!flags.first { it.key == "future_experiment_enabled" }.allowsLocalOverride)
  check(flags.first { it.key == "books_enabled" }.allowsLocalOverride)
  check(!flags.first { it.key == "for_you_prototype_enabled" }.allowsLocalOverride)
  println("Production catalog discovery passed")
}
'''
with tempfile.TemporaryDirectory(prefix="corus-debug-catalog-") as folder:
    path = Path(folder) / "Catalog.kt"
    path.write_text(stub)
    binary = Path(folder) / "catalog.jar"
    env = dict(os.environ, JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home")
    compiler = "/Applications/Android Studio.app/Contents/plugins/Kotlin/kotlinc/bin/kotlinc"
    subprocess.run(["bash", compiler, str(path), "-include-runtime", "-d", str(binary)], check=True, env=env)
    subprocess.run([env["JAVA_HOME"] + "/bin/java", "-jar", str(binary)], check=True)
