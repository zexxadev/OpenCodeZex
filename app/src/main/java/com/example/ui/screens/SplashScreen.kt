package com.example.ui.screens

import android.content.Context
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.VerifiedUser
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.data.firebase.GoogleAuthHelper
import com.example.ui.theme.LocalOpenCodeColors
import com.google.firebase.Firebase
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.auth
import kotlinx.coroutines.delay

enum class SplashPhase {
    INITIALIZING_FIREBASE,
    CHECKING_SESSION,
    SESSION_FOUND,
    AUTH_REQUIRED
}

data class StartupSessionResult(
    val hasActiveSession: Boolean
)

object StartupSessionCoordinator {
    suspend fun initializeAndCheckSession(
        context: Context,
        minSplashDurationMs: Long = 1050L,
        onPhaseChanged: (SplashPhase) -> Unit
    ): StartupSessionResult {
        val startTime = System.currentTimeMillis()
        onPhaseChanged(SplashPhase.INITIALIZING_FIREBASE)

        val appContext = context.applicationContext
        if (FirebaseApp.getApps(appContext).isEmpty()) {
            FirebaseApp.initializeApp(appContext)
        }

        // Small visual cadence so the Firebase init phase is readable
        delay(280L)
        onPhaseChanged(SplashPhase.CHECKING_SESSION)

        var activeUser = Firebase.auth.currentUser
        if (activeUser == null) {
            val autoSignInResult = GoogleAuthHelper.attemptAutoSignIn(context)
            activeUser = autoSignInResult.getOrNull() ?: Firebase.auth.currentUser
        }

        val hasSession = activeUser != null
        onPhaseChanged(
            if (hasSession) SplashPhase.SESSION_FOUND else SplashPhase.AUTH_REQUIRED
        )

        val elapsed = System.currentTimeMillis() - startTime
        val remaining = (minSplashDurationMs - elapsed).coerceAtLeast(0L)
        if (remaining > 0L) {
            delay(remaining)
        }

        return StartupSessionResult(hasActiveSession = hasSession)
    }
}

@Composable
fun SplashScreen(
    phase: SplashPhase,
    modifier: Modifier = Modifier
) {
    val semanticColors = LocalOpenCodeColors.current
    val primaryColor = MaterialTheme.colorScheme.primary
    val secondaryColor = MaterialTheme.colorScheme.secondary
    val backgroundColor = MaterialTheme.colorScheme.background

    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { visible = true }

    val entryScale by animateFloatAsState(
        targetValue = if (visible) 1f else 0.86f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "splash_logo_entry"
    )

    val infiniteTransition = rememberInfiniteTransition(label = "splash_pulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 0.96f,
        targetValue = 1.06f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1400, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "splash_glow_pulse"
    )

    val statusText = when (phase) {
        SplashPhase.INITIALIZING_FIREBASE -> stringResource(R.string.splash_status_init_firebase)
        SplashPhase.CHECKING_SESSION -> stringResource(R.string.splash_status_checking_session)
        SplashPhase.SESSION_FOUND -> stringResource(R.string.splash_status_ready)
        SplashPhase.AUTH_REQUIRED -> stringResource(R.string.splash_status_auth_required)
    }

    val containerBg = if (semanticColors.isGlass) Color.Transparent else backgroundColor

    Surface(
        modifier = modifier
            .fillMaxSize()
            .testTag("splash_screen"),
        color = containerBg
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .drawBehind {
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(primaryColor.copy(alpha = 0.20f), Color.Transparent),
                            center = Offset(size.width * 0.5f, size.height * 0.36f),
                            radius = size.maxDimension * 0.42f
                        ),
                        radius = size.maxDimension * 0.42f,
                        center = Offset(size.width * 0.5f, size.height * 0.36f)
                    )
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(secondaryColor.copy(alpha = 0.14f), Color.Transparent),
                            center = Offset(size.width * 0.22f, size.height * 0.78f),
                            radius = size.maxDimension * 0.36f
                        ),
                        radius = size.maxDimension * 0.36f,
                        center = Offset(size.width * 0.22f, size.height * 0.78f)
                    )
                }
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(horizontal = 24.dp, vertical = 28.dp)
        ) {
            Column(
                modifier = Modifier
                    .align(Alignment.Center)
                    .widthIn(max = 420.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(18.dp)
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.scale(entryScale)
                ) {
                    Surface(
                        shape = RoundedCornerShape(34.dp),
                        color = primaryColor.copy(alpha = 0.12f),
                        border = BorderStroke(1.dp, primaryColor.copy(alpha = 0.30f)),
                        modifier = Modifier
                            .size(124.dp)
                            .scale(pulseScale)
                    ) {}

                    Surface(
                        shape = RoundedCornerShape(28.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                        border = BorderStroke(1.5.dp, primaryColor.copy(alpha = 0.65f)),
                        shadowElevation = 10.dp,
                        modifier = Modifier.size(104.dp)
                    ) {
                        Image(
                            painter = painterResource(id = R.drawable.ic_opencode_fg),
                            contentDescription = stringResource(R.string.splash_logo_description),
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(6.dp)
                                .clip(RoundedCornerShape(22.dp))
                                .testTag("splash_logo_image")
                        )
                    }
                }

                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = stringResource(R.string.app_name),
                        style = MaterialTheme.typography.headlineLarge.copy(
                            fontWeight = FontWeight.ExtraBold,
                            letterSpacing = (-0.5).sp
                        ),
                        color = MaterialTheme.colorScheme.onBackground,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.testTag("splash_brand_title")
                    )

                    Text(
                        text = stringResource(R.string.splash_tagline),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                Surface(
                    shape = RoundedCornerShape(18.dp),
                    color = MaterialTheme.colorScheme.surfaceContainer,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    modifier = Modifier.fillMaxWidth(0.86f)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.VerifiedUser,
                                contentDescription = null,
                                tint = primaryColor,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            AnimatedContent(
                                targetState = statusText,
                                transitionSpec = {
                                    fadeIn(tween(180)) togetherWith fadeOut(tween(140))
                                },
                                modifier = Modifier
                                    .semantics(mergeDescendants = true) {}
                                    .testTag("splash_status_label"),
                                label = "splash_status_transition"
                            ) { label ->
                                Text(
                                    text = label,
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }

                        LinearProgressIndicator(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(4.dp)
                                .clip(CircleShape),
                            color = primaryColor,
                            trackColor = MaterialTheme.colorScheme.surfaceVariant,
                            strokeCap = StrokeCap.Round
                        )
                    }
                }
            }

            Text(
                text = "Firebase Security & Cloud Config",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.78f),
                modifier = Modifier.align(Alignment.BottomCenter)
            )
        }
    }
}
