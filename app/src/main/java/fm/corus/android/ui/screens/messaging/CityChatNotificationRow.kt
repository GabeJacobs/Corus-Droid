package fm.corus.android.ui.screens.messaging

import fm.corus.android.ui.components.parityCopy
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

@Composable
internal fun CityChatNotificationRow(model: MessageThreadViewModel) {
    var mode by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf(false) }
    var open by remember { mutableStateOf(false) }
    val scope=rememberCoroutineScope()
    suspend fun update(value: String? = null) { busy=true;error=false;try{mode=model.cityNotifications(value)}catch(e:kotlinx.coroutines.CancellationException){throw e}catch(_:Exception){error=true}finally{busy=false} }
    LaunchedEffect(Unit) { update() }
    Row(Modifier.fillMaxWidth().padding(horizontal=16.dp),verticalAlignment=Alignment.CenterVertically) {
        Text(parityCopy("Notifications"),Modifier.weight(1f))
        Box {
            TextButton(enabled=!busy,onClick={if(error)scope.launch{update()}else open=true}) { Text(if(error)"Retry" else when(mode){"all"->"All messages";"none"->"Muted";"mentions"->"Mentions only";else->"Loading…"}) }
            DropdownMenu(expanded=open,onDismissRequest={open=false}) { listOf("all" to "All messages","mentions" to "Mentions only","none" to "Muted").forEach{(value,title)->DropdownMenuItem(text={Text(parityCopy(title))},onClick={open=false;scope.launch{update(value)}})} }
        }
    }
}
