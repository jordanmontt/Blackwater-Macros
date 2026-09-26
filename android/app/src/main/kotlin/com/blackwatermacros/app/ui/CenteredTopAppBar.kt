package com.blackwatermacros.app.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import com.blackwatermacros.app.R
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

/**
 * App header that always centers the title over the full screen width,
 * regardless of an optional leading (back) or trailing (info) control. The
 * side control is absolutely positioned so it never shifts the title.
 */
@Composable
fun CenteredTopAppBar(
    title: String,
    leading: (@Composable () -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
    Box(
        Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .height(56.dp),
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .align(Alignment.Center)
                .fillMaxWidth(),
        )
        if (leading != null) {
            Row(
                Modifier
                    .align(Alignment.CenterStart)
                    .fillMaxWidth()
                    .height(56.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                leading()
            }
        }
        if (trailing != null) {
            Box(
                Modifier
                    .align(Alignment.CenterEnd)
                    .fillMaxSize(),
                contentAlignment = Alignment.CenterEnd,
            ) {
                trailing()
            }
        }
    }
}

/** The Blackwater 20 kg plate (same logo as the web), shown at the start of each tab's header. */
@Composable
fun AppLogo(modifier: Modifier = Modifier, size: Dp = 32.dp) {
    Image(
        painter = painterResource(R.drawable.logo_plate),
        contentDescription = null,
        modifier = modifier.size(size),
    )
}

@Composable
fun TabHeaderLogo() {
    AppLogo(Modifier.padding(start = 16.dp))
}
