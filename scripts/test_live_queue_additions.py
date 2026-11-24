from pathlib import Path
from tempfile import TemporaryDirectory
import subprocess
import os
root = Path(__file__).resolve().parents[1]
source = root / 'app/src/main/java/fm/corus/android/ui/screens/live/LiveQueueAdditions.kt'
assert source.exists(), 'Android still submits an entire stale queue for song additions'
with TemporaryDirectory() as directory:
    path = Path(directory) / 'Test.kt'
    path.write_text(source.read_text().split('\n', 1)[1] + '''
fun main() {
    check(liveQueueAdditions(listOf("a", "b"), listOf("a", "b", "c")) == listOf("c"))
    check(liveQueueAdditions(emptyList<String>(), listOf("c")) == listOf("c"))
    check(liveQueueAdditions(listOf("a", "b"), listOf("b", "a")) == null)
    check(liveQueueAdditions(listOf("a", "b"), listOf("b")) == null)
    println("PASS: Android sends only added songs; edits retain version protection")
}
''')
    jar = Path(directory) / 'test.jar'
    subprocess.run(['bash', '/Applications/Android Studio.app/Contents/plugins/Kotlin/kotlinc/bin/kotlinc', str(path), '-include-runtime', '-d', str(jar)], check=True, env={**os.environ, 'JAVA_HOME': '/Applications/Android Studio.app/Contents/jbr/Contents/Home'})
    subprocess.run(['/Applications/Android Studio.app/Contents/jbr/Contents/Home/bin/java', '-jar', str(jar)], check=True, env={**os.environ, 'JAVA_HOME': '/Applications/Android Studio.app/Contents/jbr/Contents/Home'})
viewmodel = (root / 'app/src/main/java/fm/corus/android/ui/screens/live/LiveViewModel.kt').read_text()
assert 'liveQueueAdditions(it, queue)' in viewmodel and '"append" to true' in viewmodel
