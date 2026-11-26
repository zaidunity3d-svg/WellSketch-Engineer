package com.wellsketch.engineer.intro

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.wellsketch.engineer.R
import com.wellsketch.engineer.core.schematic.Branding
import kotlinx.coroutines.delay

/** How long the intro stays before the home screen opens (a tap skips it). */
private const val INTRO_MILLIS = 1800L

private val IntroBackground = Color(0xFF16324F) // same as the launcher icon background

/**
 * Intro screen at app start: app name, scope and the designer credit
 * ("Designed by Zaid Fawzi"). Static (no animation); continues by itself or on tap.
 */
@Composable
fun IntroScreen(onFinished: () -> Unit) {
    val finished by rememberUpdatedState(onFinished)
    LaunchedEffect(Unit) {
        delay(INTRO_MILLIS)
        finished()
    }
    val context = LocalContext.current
    val version = remember { appVersion(context) }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(IntroBackground)
            .clickable { finished() },
    ) {
        Column(
            modifier = Modifier.align(Alignment.Center).padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Image(
                painter = painterResource(R.drawable.ic_launcher_foreground),
                contentDescription = null,
                modifier = Modifier.size(132.dp),
            )
            Text(
                Branding.APP_NAME,
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.SemiBold,
                color = Color.White,
                textAlign = TextAlign.Center,
            )
            Text(
                Branding.TAGLINE,
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White.copy(alpha = 0.8f),
                textAlign = TextAlign.Center,
            )
        }
        Column(
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 40.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(Branding.DESIGNER_CREDIT, style = MaterialTheme.typography.titleMedium, color = Color.White)
            version?.let {
                Text("Version $it", style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = 0.7f))
            }
        }
    }
}

private fun appVersion(context: Context): String? = runCatching {
    val pm = context.packageManager
    val info = if (Build.VERSION.SDK_INT >= 33) {
        pm.getPackageInfo(context.packageName, PackageManager.PackageInfoFlags.of(0))
    } else {
        @Suppress("DEPRECATION")
        pm.getPackageInfo(context.packageName, 0)
    }
    info.versionName
}.getOrNull()
