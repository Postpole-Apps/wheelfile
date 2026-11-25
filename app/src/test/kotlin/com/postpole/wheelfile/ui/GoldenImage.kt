package com.postpole.wheelfile.ui

import android.graphics.Bitmap
import android.graphics.Color
import org.junit.Assert.fail
import java.io.File
import kotlin.math.abs

/**
 * Golden-image comparison for the screenshot tests, with no extra library. Goldens live in `app/src/test/golden/`.
 *
 * - Normal run: the rendered bitmap must match `<name>.png` (same size, and at most [MAX_DIFF_FRACTION] of the pixels
 *   may differ by more than [CHANNEL_TOLERANCE] in any channel, which absorbs anti-aliasing noise only).
 * - Re-record after an intended UI change: `./gradlew testDebugUnitTest --tests '*AdaptiveLayoutTest' -PupdateGoldens`,
 *   then commit the changed PNGs.
 * - A missing golden fails (it is never recorded silently). A failing comparison writes `<name>-actual.png` and
 *   `<name>-diff.png` to `build/golden-failures/` for inspection.
 */
object GoldenImage {
    private const val CHANNEL_TOLERANCE = 24
    private const val MAX_DIFF_FRACTION = 0.005

    private val goldenDir = File("src/test/golden")
    private val failureDir = File("build/golden-failures")

    private val update: Boolean get() = System.getProperty("updateGoldens") == "true"

    fun assertMatches(name: String, actual: Bitmap) {
        val file = File(goldenDir, "$name.png")
        if (update) {
            goldenDir.mkdirs()
            file.outputStream().use { actual.compress(Bitmap.CompressFormat.PNG, 100, it) }
            return
        }
        if (!file.exists()) fail("No golden for '$name'. Record it with -PupdateGoldens and commit ${file.path}.")
        val golden = android.graphics.BitmapFactory.decodeFile(file.path)
            ?: return fail("Golden ${file.path} is not a readable PNG.")
        if (golden.width != actual.width || golden.height != actual.height) {
            save(name, actual, null)
            fail("'$name' is ${actual.width}x${actual.height}, golden is ${golden.width}x${golden.height}.")
        }
        val diff = Bitmap.createBitmap(actual.width, actual.height, Bitmap.Config.ARGB_8888)
        var differing = 0
        for (y in 0 until actual.height) {
            for (x in 0 until actual.width) {
                val a = actual.getPixel(x, y)
                val g = golden.getPixel(x, y)
                val bad = abs(Color.red(a) - Color.red(g)) > CHANNEL_TOLERANCE ||
                    abs(Color.green(a) - Color.green(g)) > CHANNEL_TOLERANCE ||
                    abs(Color.blue(a) - Color.blue(g)) > CHANNEL_TOLERANCE ||
                    abs(Color.alpha(a) - Color.alpha(g)) > CHANNEL_TOLERANCE
                if (bad) differing++
                diff.setPixel(x, y, if (bad) Color.RED else Color.argb(40, Color.red(g), Color.green(g), Color.blue(g)))
            }
        }
        val fraction = differing.toDouble() / (actual.width * actual.height)
        if (fraction > MAX_DIFF_FRACTION) {
            save(name, actual, diff)
            fail("'$name' differs from its golden in %.2f%% of pixels (limit %.2f%%). See ${failureDir.path}.".format(fraction * 100, MAX_DIFF_FRACTION * 100))
        }
    }

    private fun save(name: String, actual: Bitmap, diff: Bitmap?) {
        failureDir.mkdirs()
        File(failureDir, "$name-actual.png").outputStream().use { actual.compress(Bitmap.CompressFormat.PNG, 100, it) }
        diff?.let { d -> File(failureDir, "$name-diff.png").outputStream().use { d.compress(Bitmap.CompressFormat.PNG, 100, it) } }
    }
}
