package me.habitnudge.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.unit.dp

/**
 * The few Material icons the app needs that aren't in material-icons-core.
 * Path data copied from the Material Symbols set (24dp grid); built once, on first use.
 */
object Glyphs {
    val Alarm by lazy {
        glyph(
            "Alarm",
            "M22,5.72l-4.6,-3.86 -1.29,1.53 4.6,3.86L22,5.72zM7.88,3.39L6.6,1.86 2,5.71l1.29,1.53 4.59,-3.85z" +
                "M12.5,8L11,8v6l4.75,2.85 0.75,-1.23 -4,-2.37L12.5,8zM12,4c-4.97,0 -9,4.03 -9,9s4.02,9 9,9" +
                "c4.97,0 9,-4.03 9,-9s-4.03,-9 -9,-9zM12,20c-3.87,0 -7,-3.13 -7,-7s3.13,-7 7,-7 7,3.13 7,7 -3.13,7 -7,7z",
        )
    }

    val Hourglass by lazy {
        glyph(
            "Hourglass",
            "M6,2v6h0.01L6,8.01 10,12l-4,4 0.01,0.01L6,16.01L6,22h12v-5.99h-0.01L18,16l-4,-4 4,-3.99 -0.01,-0.01L18,8L18,2L6,2z" +
                "M16,16.5L16,20L8,20v-3.5l4,-4 4,4zM12,11.5l-4,-4L8,4h8v3.5l-4,4z",
        )
    }

    val BarChart by lazy {
        glyph("BarChart", "M5,9.2h3L8,19L5,19zM10.6,5h2.8v14h-2.8zM16.2,13L19,13v6h-2.8z")
    }

    val Notes by lazy {
        glyph("Notes", "M3,18h12v-2L3,16v2zM3,6v2h18L21,6L3,6zM3,13h18v-2L3,11v2z")
    }

    val Repeat by lazy {
        glyph("Repeat", "M7,7h10v3l4,-4 -4,-4v3L5,5v6h2L7,7zM17,17L7,17v-3l-4,4 4,4v-3h12v-6h-2v4z")
    }

    val BellOff by lazy {
        glyph(
            "BellOff",
            "M20,18.69L7.84,6.14 5.27,3.49 4,4.76l2.8,2.8v0.01c-0.52,0.99 -0.8,2.16 -0.8,3.42v5l-2,2v1h13.73l2,2" +
                "L21,19.72l-1,-1.03zM12,22c1.11,0 2,-0.89 2,-2h-4c0,1.11 0.89,2 2,2zM18,14.68L18,11c0,-3.08 -1.64,-5.64 -4.5,-6.32" +
                "L13.5,4c0,-0.83 -0.67,-1.5 -1.5,-1.5s-1.5,0.67 -1.5,1.5v0.68c-0.15,0.03 -0.29,0.08 -0.42,0.12" +
                "c-0.1,0.03 -0.2,0.07 -0.3,0.11h-0.01c-0.01,0 -0.01,0 -0.02,0.01 -0.23,0.09 -0.46,0.2 -0.68,0.31 0,0 -0.01,0 -0.01,0.01L18,14.68z",
        )
    }

    private fun glyph(name: String, path: String): ImageVector =
        ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f)
            .addPath(pathData = addPathNodes(path), fill = SolidColor(Color.Black))
            .build()
}
