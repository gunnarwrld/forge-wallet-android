package dev.ledgerforge.wallet.core.designsystem.icon

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/** The app's few icons, drawn here rather than pulling in an icon library for one chevron. */
object ForgeIcons {
    /** Mirrored in right-to-left layouts, where the back button sits on the right. */
    val Back: ImageVector by lazy {
        ImageVector
            .Builder(
                name = "Back",
                defaultWidth = 24.dp,
                defaultHeight = 24.dp,
                viewportWidth = 24f,
                viewportHeight = 24f,
                autoMirror = true,
            ).apply {
                path(
                    stroke = SolidColor(Color.Black),
                    strokeLineWidth = 2f,
                    strokeLineCap = StrokeCap.Round,
                    strokeLineJoin = StrokeJoin.Round,
                ) {
                    moveTo(15f, 5f)
                    lineTo(8f, 12f)
                    lineTo(15f, 19f)
                }
            }.build()
    }
}
