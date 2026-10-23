package com.hycnyl.ehbridge.ui

import android.content.ActivityNotFoundException
import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.OpenInNew
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import com.hycnyl.ehbridge.BuildConfig
import com.hycnyl.ehbridge.R

private const val AUTHOR_PROFILE = "https://github.com/Hycnyl"
private const val PROJECT_HOME = "https://github.com/Hycnyl/EhBridge"

/** About information comes first; the usage guide follows in the same scrollable page. */
@Composable
internal fun AboutContent() {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        AboutHeading(stringResource(R.string.about_software))
        Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surfaceContainerLow) {
            Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.app_name), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                Text(stringResource(R.string.about_version, BuildConfig.VERSION_NAME), style = MaterialTheme.typography.bodyMedium)
                Text(stringResource(R.string.about_summary), style = MaterialTheme.typography.bodyMedium)
                Text(stringResource(R.string.about_package, BuildConfig.APPLICATION_ID), style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        AboutHeading(stringResource(R.string.about_author))
        AboutLink("Hycnyl", AUTHOR_PROFILE)

        AboutHeading(stringResource(R.string.about_project))
        AboutLink(PROJECT_HOME, PROJECT_HOME)

        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        AboutHeading(stringResource(R.string.guide_title))
        GuideSection(R.string.guide_folders_title, R.string.guide_folders)
        GuideSection(R.string.guide_scan_title, R.string.guide_scan)
        GuideSection(R.string.guide_run_title, R.string.guide_run)
        GuideSection(R.string.guide_formats_title, R.string.guide_formats)
        GuideSection(R.string.guide_optional_title, R.string.guide_optional)
        GuideSection(R.string.guide_compatibility_title, R.string.guide_compatibility)
        GuideSection(R.string.guide_privacy_title, R.string.guide_privacy)
    }
}

@Composable
private fun AboutLink(label: String, url: String) {
    val uriHandler = LocalUriHandler.current
    val context = LocalContext.current
    val openLink = {
        try {
            uriHandler.openUri(url)
        } catch (_: ActivityNotFoundException) {
            Toast.makeText(context, R.string.about_link_unavailable, Toast.LENGTH_SHORT).show()
        } catch (_: IllegalArgumentException) {
            // AndroidUriHandler wraps an unavailable ACTION_VIEW activity in this type.
            Toast.makeText(context, R.string.about_link_unavailable, Toast.LENGTH_SHORT).show()
        } catch (_: SecurityException) {
            Toast.makeText(context, R.string.about_link_unavailable, Toast.LENGTH_SHORT).show()
        }
    }
    Surface(onClick = openLink, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow) {
        Row(Modifier.heightIn(min = 56.dp).padding(16.dp), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Text(label, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.primary, textDecoration = TextDecoration.Underline)
            Icon(Icons.Outlined.OpenInNew, stringResource(R.string.about_open_link), tint = MaterialTheme.colorScheme.primary)
        }
    }
}

@Composable
private fun AboutHeading(text: String) {
    Text(text, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold,
        modifier = Modifier.semantics { heading() })
}

@Composable
private fun GuideSection(title: Int, body: Int) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(stringResource(title), style = MaterialTheme.typography.titleSmall,
            modifier = Modifier.semantics { heading() })
        Text(stringResource(body), style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
