package fm.corus.android.ui.screens.auth

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/** Developer test setup, unreachable in production builds. */
@Composable
fun CorusStagingAuthScreen(viewModel: AuthViewModel) {
    var name by remember { mutableStateOf("") }
    val loading by viewModel.isLoading.collectAsState()
    val error by viewModel.error.collectAsState()
    Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Spacer(Modifier.height(32.dp))
        Text("Corus Staging", style = MaterialTheme.typography.headlineLarge)
        Text("Live testing with a synthetic account in cymbal-remake-staging.")
        OutlinedTextField(value = name, onValueChange = { if (it.length <= 40) name = it },
            label = { Text("Test name (e.g. Gabe or Isa)") }, singleLine = true,
            modifier = Modifier.fillMaxWidth())
        Button(onClick = { viewModel.signInToStaging(name.trim()) },
            enabled = !loading && name.isNotBlank(), modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
            if (loading) CircularProgressIndicator(Modifier.size(24.dp)) else Text("Enter staging")
        }
        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        Text("Only approved staging App Check clients can enroll. Purchases and public invitations are outside this test.")
    }
}
