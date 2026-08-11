package com.valepoint.hfo.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/** A 1970s station control room: eau-de-nil panels, cream dials, brass and bakelite. */
object P {
    val Room = Color(0xFF121412)
    val PanelFace = Color(0xFF3E4B44)
    val PanelHigh = Color(0xFF4E5D55)
    val PanelLow = Color(0xFF2C352F)
    val Bezel = Color(0xFF0C0E0D)
    val Dial = Color(0xFFE7E1CE)
    val DialShadow = Color(0xFFCBC4AE)
    val DialDark = Color(0xFF191C1A)
    val Ink = Color(0xFF15181A)
    val Needle = Color(0xFFB0281C)
    val NeedleWhite = Color(0xFFF2EDDD)
    val Legend = Color(0xFFDCD6C4)
    val LegendDim = Color(0xFF8E968B)
    val Brass = Color(0xFFC6A452)
    val LampRed = Color(0xFFE8422F)
    val LampAmber = Color(0xFFF0A62F)
    val LampGreen = Color(0xFF56C05A)
    val LampWhite = Color(0xFFF4EFDD)
    val LampBlue = Color(0xFF63A8E8)
    val Off = Color(0xFF23271F)
    val Trace = Color(0xFF7BD68A)
    val Danger = Color(0xFF7A1E13)
}

private val Scheme = darkColorScheme(
    primary = P.Brass,
    onPrimary = P.Ink,
    secondary = P.PanelHigh,
    background = P.Room,
    onBackground = P.Legend,
    surface = P.PanelFace,
    onSurface = P.Legend,
    error = P.LampRed,
)

private val PanelType = Typography(
    bodyLarge = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 14.sp),
    bodyMedium = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 12.sp),
    bodySmall = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 10.sp),
    labelLarge = TextStyle(
        fontFamily = FontFamily.Monospace, fontSize = 12.sp,
        fontWeight = FontWeight.Bold, letterSpacing = 1.sp
    ),
    titleMedium = TextStyle(
        fontFamily = FontFamily.Monospace, fontSize = 15.sp,
        fontWeight = FontWeight.Bold, letterSpacing = 1.5.sp
    ),
)

@Composable
fun StationTheme(dark: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = Scheme, typography = PanelType, content = content)
}
