package fm.corus.android.ui.components

import android.content.Context
import android.content.Intent
import fm.corus.android.R

/** The existing Settings invite text and native share surface. */
fun Context.shareCorusInvite() {
    val sendIntent = Intent(Intent.ACTION_SEND).apply {
        putExtra(Intent.EXTRA_TEXT, getString(fm.corus.android.localization.CorusStrings.settings_share_app_text))
        type = "text/plain"
    }
    startActivity(Intent.createChooser(sendIntent, getString(fm.corus.android.localization.CorusStrings.settings_share_app_chooser)))
}
