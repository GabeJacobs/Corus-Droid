package fm.corus.android.ui.screens.profile

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import fm.corus.android.ui.theme.CorusColors
import fm.corus.android.ui.theme.CorusFont

@Composable
fun ProfileCityNameRow(
    displayName: String,
    cityLabel: String?,
    onCityClick: (() -> Unit)?,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = displayName,
            style = CorusFont.usernameLarge,
            color = CorusColors.Text,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        if (!cityLabel.isNullOrEmpty() && onCityClick != null) {
            Spacer(Modifier.height(2.dp))
            Text(
                text = cityLabel,
                style = CorusFont.caption,
                color = CorusColors.Tertiary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.clickable(onClick = onCityClick),
            )
        }
    }
}
