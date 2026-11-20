package com.example.ui.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

@Immutable
data class OpenCodeSemanticColors(
    val isDark: Boolean,
    val isGlass: Boolean = false,
    val isMiuix: Boolean = false,
    val codeBackground: Color,
    val codeBorder: Color,
    val diffAddBackground: Color,
    val diffAddText: Color,
    val diffRemoveBackground: Color,
    val diffRemoveText: Color,
    val warning: Color,
    val warningContainer: Color,
    val onWarningContainer: Color,
    val success: Color,
    val successContainer: Color,
    val onSuccessContainer: Color,
    val subtleBorder: Color
)

val LocalOpenCodeColors = staticCompositionLocalOf {
    OpenCodeSemanticColors(
        isDark = true,
        codeBackground = ObsidianSurface,
        codeBorder = ObsidianOutline,
        diffAddBackground = Color(0xFF0F2E22),
        diffAddText = ElectricMint,
        diffRemoveBackground = Color(0xFF381619),
        diffRemoveText = CoralCrimson,
        warning = WarmAmber,
        warningContainer = WarmAmberContainer,
        onWarningContainer = OnWarmAmberContainer,
        success = ElectricMint,
        successContainer = ElectricMintContainer,
        onSuccessContainer = OnElectricMintContainer,
        subtleBorder = ObsidianOutlineSubtle
    )
}

private val DarkColorScheme = darkColorScheme(
    primary = ElectricMint,
    onPrimary = OnElectricMint,
    primaryContainer = ElectricMintContainer,
    onPrimaryContainer = OnElectricMintContainer,
    secondary = CobaltSky,
    onSecondary = OnCobaltSky,
    secondaryContainer = CobaltSkyContainer,
    onSecondaryContainer = OnCobaltSkyContainer,
    tertiary = WarmAmber,
    onTertiary = Color(0xFF281800),
    tertiaryContainer = WarmAmberContainer,
    onTertiaryContainer = OnWarmAmberContainer,
    error = CoralCrimson,
    onError = OnCoralCrimson,
    errorContainer = CoralCrimsonContainer,
    onErrorContainer = OnCoralCrimsonContainer,
    background = ObsidianBg,
    onBackground = TextPrimaryDark,
    surface = ObsidianSurface,
    onSurface = TextPrimaryDark,
    surfaceVariant = ObsidianSurfaceHigh,
    onSurfaceVariant = TextSecondaryDark,
    surfaceContainerLowest = ObsidianBg,
    surfaceContainerLow = ObsidianSurface,
    surfaceContainer = ObsidianSurfaceHigh,
    surfaceContainerHigh = ObsidianSurfaceHighest,
    surfaceContainerHighest = Color(0xFF2B3142),
    outline = ObsidianOutline,
    outlineVariant = ObsidianOutlineSubtle
)

private val LightColorScheme = lightColorScheme(
    primary = EmeraldLight,
    onPrimary = Color.White,
    primaryContainer = EmeraldLightContainer,
    onPrimaryContainer = OnEmeraldLightContainer,
    secondary = BlueLight,
    onSecondary = Color.White,
    secondaryContainer = BlueLightContainer,
    onSecondaryContainer = OnBlueLightContainer,
    tertiary = AmberLight,
    onTertiary = Color.White,
    tertiaryContainer = AmberLightContainer,
    onTertiaryContainer = OnAmberLightContainer,
    error = CrimsonLight,
    onError = Color.White,
    errorContainer = CrimsonLightContainer,
    onErrorContainer = OnCrimsonLightContainer,
    background = PaperBg,
    onBackground = TextPrimaryLight,
    surface = PaperSurface,
    onSurface = TextPrimaryLight,
    surfaceVariant = PaperSurfaceHigh,
    onSurfaceVariant = TextSecondaryLight,
    surfaceContainerLowest = PaperSurface,
    surfaceContainerLow = Color(0xFFF9FAFB),
    surfaceContainer = PaperSurfaceHigh,
    surfaceContainerHigh = PaperSurfaceHighest,
    surfaceContainerHighest = Color(0xFFD8DFEA),
    outline = PaperOutline,
    outlineVariant = PaperOutlineSubtle
)

private val MaterialGlassColorScheme = darkColorScheme(
    primary = GlassAqua,
    onPrimary = OnGlassAqua,
    primaryContainer = GlassAquaContainer,
    onPrimaryContainer = OnGlassAquaContainer,
    secondary = GlassIris,
    onSecondary = Color(0xFF0E1442),
    secondaryContainer = GlassIrisContainer,
    onSecondaryContainer = OnGlassIrisContainer,
    tertiary = WarmAmber,
    onTertiary = Color(0xFF281800),
    tertiaryContainer = Color(0x88422B08),
    onTertiaryContainer = OnWarmAmberContainer,
    error = CoralCrimson,
    onError = OnCoralCrimson,
    errorContainer = Color(0x994B181C),
    onErrorContainer = OnCoralCrimsonContainer,
    background = GlassBackdropBase,
    onBackground = GlassTextPrimary,
    surface = GlassSurface,
    onSurface = GlassTextPrimary,
    surfaceVariant = GlassSurfaceHigh,
    onSurfaceVariant = GlassTextSecondary,
    surfaceContainerLowest = Color(0x99091021),
    surfaceContainerLow = GlassSurface,
    surfaceContainer = GlassSurfaceHigh,
    surfaceContainerHigh = GlassSurfaceHighest,
    surfaceContainerHighest = Color(0xE02D4678),
    outline = GlassOutline,
    outlineVariant = GlassOutlineSubtle
)

private val MiuixColorScheme = lightColorScheme(
    primary = MiuixBlue,
    onPrimary = Color.White,
    primaryContainer = MiuixBlueContainer,
    onPrimaryContainer = OnMiuixBlueContainer,
    secondary = MiuixTeal,
    onSecondary = Color.White,
    secondaryContainer = MiuixTealContainer,
    onSecondaryContainer = OnMiuixTealContainer,
    tertiary = AmberLight,
    onTertiary = Color.White,
    tertiaryContainer = AmberLightContainer,
    onTertiaryContainer = OnAmberLightContainer,
    error = Color(0xFFE53935),
    onError = Color.White,
    errorContainer = Color(0xFFFFEBEE),
    onErrorContainer = Color(0xFFB71C1C),
    background = MiuixBg,
    onBackground = MiuixTextPrimary,
    surface = MiuixSurface,
    onSurface = MiuixTextPrimary,
    surfaceVariant = MiuixSurfaceHigh,
    onSurfaceVariant = MiuixTextSecondary,
    surfaceContainerLowest = MiuixSurface,
    surfaceContainerLow = Color(0xFFFAFBFD),
    surfaceContainer = MiuixSurfaceHigh,
    surfaceContainerHigh = MiuixSurfaceHighest,
    surfaceContainerHighest = Color(0xFFDDE5F0),
    outline = MiuixOutline,
    outlineVariant = MiuixOutlineSubtle
)

private val OpenCodeShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(14.dp),
    large = RoundedCornerShape(18.dp),
    extraLarge = RoundedCornerShape(24.dp)
)

private val MaterialGlassShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(22.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

private val MiuixShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(14.dp),
    medium = RoundedCornerShape(18.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(30.dp)
)

enum class ThemePreference(
    val label: String,
    val shortLabel: String,
    val previewColorHex: Long
) {
    DARK("Gelap", "Gelap", 0xFF34D399),
    LIGHT("Terang", "Terang", 0xFF059669),
    MATERIAL_GLASS("Material Glass", "Glass", 0xFF5EEAD4),
    MIUIX("Miuix", "Miuix", 0xFF0D84FF),
    SYSTEM("Sistem", "Sistem", 0xFF60A5FA)
}

@Composable
fun OpenCodeTheme(
    themePreference: ThemePreference = ThemePreference.DARK,
    content: @Composable () -> Unit
) {
    val isDark = when (themePreference) {
        ThemePreference.DARK -> true
        ThemePreference.LIGHT -> false
        ThemePreference.MATERIAL_GLASS -> true
        ThemePreference.MIUIX -> false
        ThemePreference.SYSTEM -> isSystemInDarkTheme()
    }

    val colorScheme = when (themePreference) {
        ThemePreference.DARK -> DarkColorScheme
        ThemePreference.LIGHT -> LightColorScheme
        ThemePreference.MATERIAL_GLASS -> MaterialGlassColorScheme
        ThemePreference.MIUIX -> MiuixColorScheme
        ThemePreference.SYSTEM -> if (isDark) DarkColorScheme else LightColorScheme
    }

    val shapes = when (themePreference) {
        ThemePreference.MATERIAL_GLASS -> MaterialGlassShapes
        ThemePreference.MIUIX -> MiuixShapes
        else -> OpenCodeShapes
    }

    val semanticColors = when (themePreference) {
        ThemePreference.MATERIAL_GLASS -> OpenCodeSemanticColors(
            isDark = true,
            isGlass = true,
            codeBackground = Color(0xCC091021),
            codeBorder = GlassOutline,
            diffAddBackground = Color(0x880D362A),
            diffAddText = GlassAqua,
            diffRemoveBackground = Color(0x8842171D),
            diffRemoveText = CoralCrimson,
            warning = WarmAmber,
            warningContainer = Color(0x88422B08),
            onWarningContainer = OnWarmAmberContainer,
            success = GlassAqua,
            successContainer = Color(0x880E3B33),
            onSuccessContainer = OnGlassAquaContainer,
            subtleBorder = GlassOutlineSubtle
        )
        ThemePreference.MIUIX -> OpenCodeSemanticColors(
            isDark = false,
            isMiuix = true,
            codeBackground = Color(0xFFEEF2F8),
            codeBorder = MiuixOutline,
            diffAddBackground = Color(0xFFDCFCE7),
            diffAddText = Color(0xFF047857),
            diffRemoveBackground = Color(0xFFFEE2E2),
            diffRemoveText = Color(0xFFDC2626),
            warning = AmberLight,
            warningContainer = AmberLightContainer,
            onWarningContainer = OnAmberLightContainer,
            success = MiuixGreen,
            successContainer = MiuixGreenContainer,
            onSuccessContainer = OnMiuixGreenContainer,
            subtleBorder = MiuixOutlineSubtle
        )
        else -> if (isDark) {
            OpenCodeSemanticColors(
                isDark = true,
                codeBackground = Color(0xFF0A0C10),
                codeBorder = ObsidianOutline,
                diffAddBackground = Color(0xFF0E2C20),
                diffAddText = ElectricMint,
                diffRemoveBackground = Color(0xFF341518),
                diffRemoveText = CoralCrimson,
                warning = WarmAmber,
                warningContainer = WarmAmberContainer,
                onWarningContainer = OnWarmAmberContainer,
                success = ElectricMint,
                successContainer = ElectricMintContainer,
                onSuccessContainer = OnElectricMintContainer,
                subtleBorder = ObsidianOutlineSubtle
            )
        } else {
            OpenCodeSemanticColors(
                isDark = false,
                codeBackground = Color(0xFFF1F4F8),
                codeBorder = PaperOutline,
                diffAddBackground = Color(0xFFDCFCE7),
                diffAddText = Color(0xFF15803D),
                diffRemoveBackground = Color(0xFFFEE2E2),
                diffRemoveText = Color(0xFFB91C1C),
                warning = AmberLight,
                warningContainer = AmberLightContainer,
                onWarningContainer = OnAmberLightContainer,
                success = EmeraldLight,
                successContainer = EmeraldLightContainer,
                onSuccessContainer = OnEmeraldLightContainer,
                subtleBorder = PaperOutlineSubtle
            )
        }
    }

    CompositionLocalProvider(LocalOpenCodeColors provides semanticColors) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = OpenCodeTypography,
            shapes = shapes
        ) {
            if (semanticColors.isGlass) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.linearGradient(
                                colors = listOf(
                                    GlassBackdropBase,
                                    GlassBackdropGlowTop,
                                    GlassBackdropGlowBottom
                                )
                            )
                        )
                        .drawBehind {
                            drawCircle(
                                brush = Brush.radialGradient(
                                    colors = listOf(Color(0x4538BDF8), Color.Transparent),
                                    center = Offset(size.width * 0.15f, size.height * 0.12f),
                                    radius = size.maxDimension * 0.45f
                                ),
                                radius = size.maxDimension * 0.45f,
                                center = Offset(size.width * 0.15f, size.height * 0.12f)
                            )
                            drawCircle(
                                brush = Brush.radialGradient(
                                    colors = listOf(Color(0x3D818CF8), Color.Transparent),
                                    center = Offset(size.width * 0.88f, size.height * 0.72f),
                                    radius = size.maxDimension * 0.5f
                                ),
                                radius = size.maxDimension * 0.5f,
                                center = Offset(size.width * 0.88f, size.height * 0.72f)
                            )
                        }
                ) {
                    content()
                }
            } else {
                content()
            }
        }
    }
}
