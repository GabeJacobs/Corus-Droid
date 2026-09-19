package fm.corus.android.ui.screens.profile

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Looper
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocationOff
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.People
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import fm.corus.android.R
import fm.corus.android.ui.components.parityCopy
import fm.corus.android.ui.screens.map.MapAudienceOption
import fm.corus.android.ui.screens.map.MapIntroBenefitRow
import fm.corus.android.ui.screens.map.MapSheetPrimaryButton
import fm.corus.android.ui.theme.CorusColors
import fm.corus.android.ui.theme.CorusFont
import fm.corus.android.ui.theme.CorusSpacing

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditProfileCitySection(viewModel: EditProfileViewModel) {
    if (!viewModel.mapEnabled) return
    val context = LocalContext.current
    val ownCity by viewModel.ownCity.collectAsState()
    val ownAudience by viewModel.ownAudience.collectAsState()
    val findingCity by viewModel.findingCity.collectAsState()
    val showCityOnProfile by viewModel.showCityOnProfile.collectAsState()
    val shareError by viewModel.shareError.collectAsState()
    val sharing = ownCity != null && ownAudience != "off"
    val prefs = remember { context.getSharedPreferences("map_onboarding", Context.MODE_PRIVATE) }
    val uid = viewModel.currentUserId.orEmpty()

    var dialog by remember { mutableStateOf("") }
    var audience by remember { mutableStateOf(viewModel.sheetAudience(ownAudience, sharing)) }
    var locationListener by remember { mutableStateOf<LocationListener?>(null) }
    var locationAction by remember { mutableStateOf<((Location) -> Unit)?>(null) }
    val locationManager = remember { context.getSystemService(Context.LOCATION_SERVICE) as LocationManager }

    DisposableEffect(Unit) {
        onDispose {
            locationListener?.let(locationManager::removeUpdates)
        }
    }

    fun locateThenShare() {
        val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        if (granted) {
            requestCityLocation(
                context = context,
                locationManager = locationManager,
                onListener = { locationListener = it },
                onLocation = { location ->
                    val action = locationAction
                    locationAction = null
                    action?.invoke(location)
                },
                onFail = {
                    locationAction = null
                    viewModel.clearFindingCity()
                },
            )
        }
    }

    val permission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { result ->
        if (result.values.any { it }) locateThenShare() else {
            locationAction = null
            viewModel.clearFindingCity()
        }
    }

    fun openSharingFlow() {
        audience = viewModel.sheetAudience(ownAudience, sharing)
        dialog = if (prefs.getBoolean("intro.$uid", false)) "audience" else "intro"
    }

    fun startShare(selected: String) {
        dialog = ""
        if (selected == "off") {
            viewModel.applyAudience("off", null) {}
            return
        }
        locationAction = { location -> viewModel.applyAudience(selected, location) {} }
        viewModel.markFindingCity()
        val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        if (granted) locateThenShare()
        else permission.launch(arrayOf(Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.ACCESS_FINE_LOCATION))
    }

    if (shareError != null) {
        AlertDialog(
            onDismissRequest = { viewModel.clearShareError() },
            title = { Text(parityCopy("Couldn't share your city"), style = CorusFont.songTitle, color = CorusColors.Text) },
            text = { Text(shareError!!, style = CorusFont.body, color = CorusColors.Text) },
            confirmButton = {
                TextButton(onClick = { viewModel.clearShareError() }) {
                    Text(stringResource(R.string.common_ok), style = CorusFont.button, color = CorusColors.Accent)
                }
            },
            containerColor = CorusColors.Background,
        )
    }

    Column(modifier = Modifier.fillMaxWidth()) {
        Text(stringResource(R.string.edit_profile_field_city), style = CorusFont.sectionHeader, color = CorusColors.Secondary)
        Spacer(modifier = Modifier.height(CorusSpacing.sm))
        if (findingCity) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(CorusSpacing.sm),
            ) {
                CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = CorusColors.Accent)
                Text(parityCopy("Finding your city"), style = CorusFont.captionMedium, color = CorusColors.Text)
            }
        } else if (sharing && ownCity != null) {
            val city = ownCity!!
            val sharingLabel = listOf(city.cityName, city.regionName)
                .filter { it.isNotBlank() }
                .joinToString(", ")
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(CorusSpacing.xs),
            ) {
                Text(
                    "${stringResource(R.string.map_sharing_label)} $sharingLabel",
                    style = CorusFont.captionMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                Text(
                    parityCopy("Change"),
                    modifier = Modifier
                        .clickable(enabled = !findingCity) { openSharingFlow() }
                        .padding(vertical = 4.dp),
                    style = CorusFont.captionMedium,
                    color = CorusColors.Accent,
                )
            }
            Spacer(modifier = Modifier.height(CorusSpacing.sm))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(CorusColors.CardBackground, RoundedCornerShape(CorusSpacing.cornerRadiusMedium))
                    .border(1.dp, CorusColors.Divider, RoundedCornerShape(CorusSpacing.cornerRadiusMedium))
                    .padding(CorusSpacing.md),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(parityCopy("Show on profile"), Modifier.weight(1f), style = CorusFont.body)
                Switch(checked = showCityOnProfile, onCheckedChange = viewModel::updateShowCityOnProfile)
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(CorusColors.CardBackground, RoundedCornerShape(CorusSpacing.cornerRadiusMedium))
                    .border(1.dp, CorusColors.Divider, RoundedCornerShape(CorusSpacing.cornerRadiusMedium))
                    .padding(CorusSpacing.md),
                verticalArrangement = Arrangement.spacedBy(CorusSpacing.sm),
            ) {
                TextButton(onClick = { openSharingFlow() }, enabled = !findingCity, contentPadding = PaddingValues(0.dp)) {
                    Text(parityCopy("Share your city"), style = CorusFont.captionMedium, color = CorusColors.Accent)
                }
                Text(
                    parityCopy("Corus will group you in your nearest city and never share or store your precise location."),
                    style = CorusFont.caption,
                    color = CorusColors.Tertiary,
                )
            }
        }
    }

    if (dialog.isNotEmpty()) {
        ModalBottomSheet(
            onDismissRequest = { if (dialog != "intro") dialog = "" },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            containerColor = CorusColors.Background,
        ) {
            LazyColumn(
                contentPadding = PaddingValues(start = 24.dp, top = 24.dp, end = 24.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                if (dialog == "intro") {
                    item {
                        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(parityCopy("Explore the Corus Map"), style = CorusFont.songTitleLarge)
                        }
                    }
                    listOf(
                        Triple(Icons.Default.People, "Find people by city", "Message, meet up, or go to concerts together."),
                        Triple(Icons.Default.LocationOff, "Share your city, not your exact location.", "People can find you in the city you choose."),
                        Triple(Icons.Default.Lock, "No one sees you until you choose.", "Change this anytime."),
                    ).forEach { (icon, title, body) ->
                        item { MapIntroBenefitRow(icon, title, body) }
                    }
                    item {
                        MapSheetPrimaryButton(onClick = {
                            prefs.edit().putBoolean("intro.$uid", true).apply()
                            dialog = "audience"
                        }) { Text(parityCopy("Next")) }
                    }
                } else {
                    item {
                        Column(
                            Modifier.fillMaxWidth(),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Text(parityCopy("Who can see your city"), style = CorusFont.songTitleLarge)
                            Text(parityCopy("If you share, people see your city — not your street or a live pin. You can change this anytime."), style = CorusFont.caption, color = CorusColors.Secondary)
                        }
                    }
                    listOf(
                        Triple("everyone", "Everyone", "Anyone on Corus can see your city."),
                        Triple("following", "People I follow", "Only accounts you follow."),
                        Triple("off", "No one", "Don’t share. You can still explore."),
                    ).forEach { (value, title, subtitle) ->
                        item {
                            MapAudienceOption(value, title, subtitle, audience == value) {
                                audience = value
                                viewModel.rememberAudience(value)
                            }
                        }
                    }
                    item {
                        MapSheetPrimaryButton(onClick = { startShare(audience) }) { Text(parityCopy("Done")) }
                    }
                }
            }
        }
    }
}

private fun requestCityLocation(
    context: Context,
    locationManager: LocationManager,
    onListener: (LocationListener?) -> Unit,
    onLocation: (Location) -> Unit,
    onFail: () -> Unit,
) {
    try {
        val providers = buildList {
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED &&
                locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)
            ) add(LocationManager.GPS_PROVIDER)
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED &&
                locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
            ) add(LocationManager.NETWORK_PROVIDER)
        }
        if (providers.isEmpty()) {
            onFail()
            return
        }
        val last = providers.mapNotNull { locationManager.getLastKnownLocation(it) }.maxByOrNull { it.time }
        if (last != null && System.currentTimeMillis() - last.time < 90_000) {
            onLocation(last)
            return
        }
        val listener = object : LocationListener {
            override fun onLocationChanged(location: Location) {
                locationManager.removeUpdates(this)
                onListener(null)
                onLocation(location)
            }
        }
        onListener(listener)
        providers.forEach { provider ->
            locationManager.requestLocationUpdates(provider, 0L, 0f, listener, Looper.getMainLooper())
        }
    } catch (_: SecurityException) {
        onFail()
    } catch (_: Exception) {
        onFail()
    }
}
