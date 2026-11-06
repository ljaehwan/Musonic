package app.musonic.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

private data class Palette(
    val canvas: Color, val grouped: Color, val accent: Color, val muted: Color,
    val text: Color, val separator: Color, val player: Color, val mini: Color, val selected: Color,
)
private val LightPalette = Palette(Color(0xFFFCFCFD), Color(0xFFF0F0F4), Color(0xFFD52D4D), Color(0xFF73737C),
    Color(0xFF1C1C20), Color(0xFFDADAE0), Color(0xFFF4F3F5), Color(0xFFF9F9FB), Color.White)
private val DarkPalette = Palette(Color(0xFF111114), Color(0xFF202024), Color(0xFFFF6685), Color(0xFFAAAAB4),
    Color(0xFFF3F3F7), Color(0xFF393940), Color(0xFF19191D), Color(0xFF27272D), Color(0xFF35353D))
private val LocalPalette = staticCompositionLocalOf { LightPalette }
val CanvasColor: Color @Composable get() = LocalPalette.current.canvas
val Grouped: Color @Composable get() = LocalPalette.current.grouped
val Accent: Color @Composable get() = LocalPalette.current.accent
val Muted: Color @Composable get() = LocalPalette.current.muted
val TextPrimary: Color @Composable get() = LocalPalette.current.text
val Separator: Color @Composable get() = LocalPalette.current.separator
val PlayerBackground: Color @Composable get() = LocalPalette.current.player
val MiniBackground: Color @Composable get() = LocalPalette.current.mini
val SelectedBackground: Color @Composable get() = LocalPalette.current.selected
val OnPlaybackControl: Color @Composable get() = LocalPalette.current.canvas

@Composable
fun MusonicTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    val p = if (dark) DarkPalette else LightPalette
    val base = if (dark) darkColorScheme() else lightColorScheme()
    CompositionLocalProvider(LocalPalette provides p, androidx.compose.material3.LocalContentColor provides p.text) {
    MaterialTheme(colorScheme = base.copy(primary = Accent, onPrimary = if (dark) Color(0xFF240610) else Color.White, background = CanvasColor,
        surface = CanvasColor, surfaceVariant = Grouped, onSurface = TextPrimary, onBackground = TextPrimary,
        secondary = Accent, primaryContainer = Grouped, onPrimaryContainer = Accent,
        secondaryContainer = Grouped, onSecondaryContainer = Accent,
        outline = Muted, outlineVariant = Separator, onSurfaceVariant = Muted, surfaceTint = Color.Transparent),
        typography = Typography(
            headlineLarge = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Bold, fontSize = 34.sp, lineHeight = 41.sp, letterSpacing = (-1).sp),
            headlineMedium = TextStyle(fontWeight = FontWeight.Bold, fontSize = 26.sp, lineHeight = 33.sp, letterSpacing = (-.5).sp),
            titleLarge = TextStyle(fontWeight = FontWeight.Bold, fontSize = 21.sp, lineHeight = 27.sp, letterSpacing = (-.4).sp),
            titleMedium = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 16.sp),
            bodyLarge = TextStyle(fontSize = 16.sp, lineHeight = 24.sp),
            bodyMedium = TextStyle(fontSize = 14.sp, lineHeight = 21.sp),
            labelSmall = TextStyle(fontWeight = FontWeight.Medium, fontSize = 11.sp),
        ), content = content)
    }
}
