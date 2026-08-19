package com.pandian.tobacco

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp

val BrandBurgundy = Color(0xFF9F211B)
val BrandBurgundyDark = Color(0xFF681713)
val BrandCrimson = Color(0xFFBE3328)
val BrandGold = Color(0xFFD4A128)
val BrandCream = Color(0xFFFFF7E8)
val BrandSurface = Color(0xFFFFFCF6)
val BrandInk = Color(0xFF34201D)
val BrandMuted = Color(0xFF75635D)

private val Colors = lightColorScheme(
    primary = BrandBurgundy,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFFFDAD5),
    onPrimaryContainer = Color(0xFF4A0B08),
    secondary = BrandMuted,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFF4E2DB),
    onSecondaryContainer = BrandInk,
    tertiary = BrandGold,
    onTertiary = Color(0xFF3C2C00),
    tertiaryContainer = Color(0xFFFFE9A8),
    onTertiaryContainer = Color(0xFF3A2A00),
    background = BrandCream,
    onBackground = BrandInk,
    surface = BrandSurface,
    onSurface = BrandInk,
    surfaceVariant = Color(0xFFF4E8DE),
    onSurfaceVariant = BrandMuted,
    outline = Color(0xFFA68C82),
    outlineVariant = Color(0xFFE2CCC3)
)

private val BrandShapes = Shapes(
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(18.dp),
    large = RoundedCornerShape(26.dp)
)

@Composable
fun TobaccoTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = Colors, typography = Typography(), shapes = BrandShapes, content = content)
}
