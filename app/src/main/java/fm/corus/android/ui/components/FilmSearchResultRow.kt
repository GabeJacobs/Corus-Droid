package fm.corus.android.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.valentinilk.shimmer.shimmer
import fm.corus.android.data.model.CymbalMovie
import fm.corus.android.ui.theme.CorusColors
import fm.corus.android.ui.theme.CorusFont
import fm.corus.android.ui.theme.CorusSpacing

@Composable
fun FilmSearchResultRow(
    movie: CymbalMovie,
    onClick: () -> Unit,
) {
    val directorLoaded = movie.directorName.isNotBlank()

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = CorusSpacing.lg, vertical = CorusSpacing.sm)
            .heightIn(min = CorusSpacing.touchTarget),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AsyncImage(
            model = movie.posterURL,
            contentDescription = movie.title,
            modifier = Modifier
                .width(40.dp)
                .height(60.dp)
                .clip(RoundedCornerShape(CorusSpacing.cornerRadius)),
            contentScale = ContentScale.Crop,
        )
        Spacer(modifier = Modifier.width(CorusSpacing.md))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = movie.title,
                style = CorusFont.body,
                color = CorusColors.Text,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (directorLoaded) {
                Row(horizontalArrangement = Arrangement.spacedBy(CorusSpacing.xs)) {
                    Text(
                        text = movie.directorName,
                        style = CorusFont.caption,
                        color = CorusColors.Secondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    if (movie.year.isNotBlank()) {
                        Text(
                            text = movie.year,
                            style = CorusFont.caption,
                            color = CorusColors.Tertiary,
                        )
                    }
                }
            } else {
                Row(
                    modifier = Modifier.shimmer(),
                    horizontalArrangement = Arrangement.spacedBy(CorusSpacing.xs),
                ) {
                    Box(
                        modifier = Modifier
                            .width(80.dp)
                            .height(11.dp)
                            .clip(RoundedCornerShape(3.dp))
                            .background(CorusColors.Skeleton),
                    )
                    if (movie.year.isNotBlank()) {
                        Text(
                            text = movie.year,
                            style = CorusFont.caption,
                            color = CorusColors.Tertiary,
                        )
                    }
                }
            }
        }
    }
}
