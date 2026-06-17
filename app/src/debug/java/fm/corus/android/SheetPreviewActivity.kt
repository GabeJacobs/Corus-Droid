package fm.corus.android

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import fm.corus.android.ui.components.CorusDraggableSheet
import fm.corus.android.ui.components.rememberCorusSheetState
import fm.corus.android.ui.theme.CorusTheme
import kotlinx.coroutines.launch

/**
 * DEBUG-ONLY scratch harness for eyeballing CorusDraggableSheet mechanics (peek
 * height, drag-to-expand-then-scroll, composer pinned at the peek, keyboard) on a
 * device without needing to sign into Corus. Launch with:
 *   adb shell am start -n fm.corus.android/.SheetPreviewActivity
 * Remove before merging.
 */
class SheetPreviewActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            CorusTheme(darkTheme = true) {
                var show by remember { mutableStateOf(true) }
                if (show) {
                    val sheetState = rememberCorusSheetState()
                    val scope = rememberCoroutineScope()
                    CorusDraggableSheet(onDismiss = { show = false }, sheetState = sheetState) {
                        Text(
                            "Comments",
                            modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp),
                            textAlign = TextAlign.Center,
                            style = MaterialTheme.typography.titleMedium,
                            color = Color.White,
                        )
                        HorizontalDivider()
                        // DIAGNOSTIC: plain weighted Box (no LazyColumn) to isolate weight behavior.
                        Box(modifier = Modifier.fillMaxWidth().weight(1f).background(Color(0xFF2196F3))) {
                            Text("LIST AREA (weight 1f)", color = Color.White, modifier = Modifier.padding(16.dp))
                        }
                        // DIAGNOSTIC: fixed-height colored composer to verify pinning/height.
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(72.dp)
                                .background(Color(0xFFFF5252)),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text("COMPOSER BAR", color = Color.Black)
                        }
                    }
                }
            }
        }
    }
}
