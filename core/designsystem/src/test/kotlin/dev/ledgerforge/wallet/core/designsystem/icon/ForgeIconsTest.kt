package dev.ledgerforge.wallet.core.designsystem.icon

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.assertTrue

@RunWith(AndroidJUnit4::class)
class ForgeIconsTest {
    @Test
    fun `the back icon flips in right-to-left layouts`() {
        // In Arabic or Hebrew the back button sits on the right, so its chevron has to point right.
        assertTrue(ForgeIcons.Back.autoMirror)
    }
}
