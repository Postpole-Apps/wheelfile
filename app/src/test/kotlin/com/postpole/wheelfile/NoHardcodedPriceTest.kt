package com.postpole.wheelfile

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Prices come only from Play `ProductDetails` (spec §5.1) and currency comes from the device locale and the user's
 * choice. So neither the code nor any string may carry a currency amount, and there are no INR, rupee or UPI defaults.
 */
class NoHardcodedPriceTest {
    private val symbols = "\\$\\u20AC\\u00A3\\u00A5\\u20B9"
    private val codes = "USD|EUR|GBP|INR|CAD|AUD|JPY|CHF|Rs\\.?"

    /** `$4`, `4,99 EUR`, `EUR 4.99`, `4.99€` and similar. */
    private val amount = Regex(
        "[$symbols]\\s?\\d|\\d[\\d.,]*\\s?[$symbols]|\\b(?:$codes)\\s?\\d|\\d[\\d.,]*\\s?(?:$codes)\\b",
    )

    /** Region defaults that must not exist: the rupee sign, the code, and the UPI payment brand. */
    private val regionDefaults = Regex("\\u20B9|\\bINR\\b|\\bUPI\\b", RegexOption.IGNORE_CASE)

    private fun sources(): List<File> {
        val roots = listOf(File("src/main/kotlin"), File("src/main/res"))
        return roots.flatMap { root ->
            root.walkTopDown().filter { it.isFile && (it.extension == "kt" || it.extension == "xml") }.toList()
        } + File("build.gradle.kts")
    }

    /** `%1$s` and `%2$d` are format arguments, not amounts. */
    private val formatArgument = Regex("%\\d+\\$[sdf]")

    private fun stripComments(file: File): String {
        val text = file.readText().replace(formatArgument, "")
        return if (file.extension == "xml") {
            text.replace(Regex("<!--.*?-->", RegexOption.DOT_MATCHES_ALL), "")
        } else {
            text.replace(Regex("/\\*.*?\\*/", RegexOption.DOT_MATCHES_ALL), "").replace(Regex("(?m)//.*$"), "")
        }
    }

    @Test fun noCurrencyAmountsInCodeOrStrings() {
        val hits = sources().flatMap { f ->
            amount.findAll(stripComments(f)).map { "${f.path}: ${it.value}" }.toList()
        }
        assertTrue("Hard-coded currency amounts: $hits", hits.isEmpty())
    }

    @Test fun noInrRupeeOrUpiDefaults() {
        val hits = sources().flatMap { f ->
            regionDefaults.findAll(f.readText()).map { "${f.path}: ${it.value}" }.toList()
        }
        assertTrue("Region defaults found: $hits", hits.isEmpty())
    }

    @Test fun paywallPricesAreFormatArguments() {
        val strings = File("src/main/res/values/strings.xml").readText()
        val buy = Regex("<string name=\"paywall_buy\">(.*?)</string>").find(strings)?.groupValues?.get(1)
        assertTrue("paywall_buy must take the Play price as an argument", buy != null && "%1\$s" in buy)
        val screen = File("src/main/kotlin/com/postpole/wheelfile/ui/pro/ProScreen.kt").readText()
        assertTrue("The paywall shows the price it was given", "R.string.paywall_buy, it" in screen)
    }

    @Test fun billingReadsThePriceFromProductDetails() {
        val gateway = File("src/main/kotlin/com/postpole/wheelfile/billing/PlayBillingGateway.kt").readText()
        assertTrue("formattedPrice" in gateway)
    }
}
