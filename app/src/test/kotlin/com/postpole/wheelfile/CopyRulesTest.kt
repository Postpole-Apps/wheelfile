package com.postpole.wheelfile

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Copy rules of spec §6.2. Scans every strings.xml under the res values folders, the listing text files under `listing/` and the
 * privacy policy draft. The patterns live in one list so the DE/FR/ES strings are checked too.
 *
 * The connectivity word is written as an escaped regex (`\x6c` is the letter L) so the word itself never appears in
 * the repository text.
 */
class CopyRulesTest {
    private val root = File("..")
    private val i = RegexOption.IGNORE_CASE

    /** Banned in every language and every scanned text. */
    private val banned: List<Pair<String, Regex>> = listOf(
        "connectivity word (EN, also used in DE)" to Regex("off\\x6cine", i),
        "connectivity phrase (DE)" to Regex("ohne\\s+(internet|verbindung|netz)|nicht\\s+verbunden", i),
        "connectivity phrase (FR)" to Regex("hors[\\s-]+\\x6cigne|hors\\s+connexion|sans\\s+(connexion|internet)", i),
        "connectivity phrase (ES)" to Regex("sin\\s+(conexi[o\\u00f3]n|internet)|fuera\\s+de\\s+\\x6c[i\\u00ed]nea", i),
        "no data collected" to Regex("no\\s+data\\s+collected|keine\\s+daten\\s+(erfasst|erhoben|gesammelt)", i),
        "no data collected (FR/ES)" to Regex(
            "aucune\\s+donn[e\\u00e9]e\\s+collect[e\\u00e9]e|no\\s+se\\s+recopilan\\s+datos|ning[u\\u00fa]n\\s+dato\\s+recopilado",
            i,
        ),
        "fully private" to Regex(
            "(100\\s*%|completely)\\s+private|(100\\s*%|vollst[a\\u00e4]ndig|komplett)\\s+privat|" +
                "(100\\s*%|totalement|enti[e\\u00e8]rement)\\s+priv[e\\u00e9]|(100\\s*%|totalmente)\\s+privad[oa]",
            i,
        ),
        "tracking claims" to Regex(
            "zero\\s+tracking|no\\s+tracking|kein\\s+tracking|keine\\s+nachverfolgung|z[e\\u00e9]ro\\s+suivi|" +
                "pas\\s+de\\s+suivi|cero\\s+seguimiento|sin\\s+seguimiento|sin\\s+rastreo",
            i,
        ),
        "features we do not have" to Regex(
            "\\b(sync|cloud\\s+backup|family\\s+sharing|share\\s+with\\s+family|multi-?device)\\b|" +
                "synchronisier|synchronisation|cloud-?backup|familienfreigabe|mehrger[a\\u00e4]te|" +
                "sauvegarde\\s+(dans\\s+le\\s+)?cloud|partage\\s+familial|multi-?appareil|" +
                "sincroniz|copia\\s+en\\s+la\\s+nube|compartir\\s+en\\s+familia|multidispositivo",
            i,
        ),
    )

    /** Only banned in paywall strings: promises of Pro features that are not built. */
    private val paywallBanned = Regex(
        "future\\s+pro|coming\\s+soon|demn[a\\u00e4]chst|bald\\s+verf[u\\u00fc]gbar|zuk[u\\u00fc]nftig|" +
            "bient[o\\u00f4]t\\s+disponible|[a\\u00e0]\\s+venir|pr[o\\u00f3]ximamente|muy\\s+pronto",
        i,
    )

    private val competitors = Regex("\\b(fuelio|drivvo|acar|simply\\s*auto|fuelly)\\b", i)

    private val languages = listOf("values" to "en", "values-de" to "de", "values-fr" to "fr", "values-es" to "es")

    private fun stringsFiles(): List<File> =
        File("src/main/res").listFiles { f -> f.isDirectory && f.name.startsWith("values") }.orEmpty()
            .map { File(it, "strings.xml") }.filter { it.exists() }.sortedBy { it.path }

    private data class Entry(val file: String, val name: String, val text: String)

    private fun entries(file: File): List<Entry> =
        Regex("<string\\s+name=\"([^\"]+)\"[^>]*>(.*?)</string>", RegexOption.DOT_MATCHES_ALL)
            .findAll(file.readText()).map { Entry(file.path, it.groupValues[1], it.groupValues[2]) }.toList()

    private fun listingFiles(): List<File> =
        File(root, "listing").walkTopDown().filter { it.isFile && it.extension in setOf("txt", "md") }.toList()

    private fun privacyDraft() = File(root, "docs/privacy-policy-draft.md")

    private fun scannedTexts(): List<Pair<String, String>> =
        stringsFiles().flatMap { f -> entries(f).map { "${it.file}#${it.name}" to it.text } } +
            listingFiles().map { it.path to it.readText() } +
            listOf(privacyDraft().path to privacyDraft().readText())

    @Test fun allFourLanguagesExist() {
        val dirs = stringsFiles().map { it.parentFile.name }
        assertEquals(languages.map { it.first }.sorted(), dirs.sorted())
    }

    @Test fun bannedPhrasesAppearNowhere() {
        val hits = scannedTexts().flatMap { (where, text) ->
            banned.filter { (_, re) -> re.containsMatchIn(text) }.map { "$where: ${it.first}" }
        }
        assertTrue("Banned copy: $hits", hits.isEmpty())
    }

    @Test fun paywallStringsMakeNoPromisesOfUnbuiltFeatures() {
        val hits = stringsFiles().flatMap { entries(it) }
            .filter { it.name.startsWith("paywall_") || it.name.startsWith("pro_") || it.name.startsWith("reports_locked") }
            .filter { paywallBanned.containsMatchIn(it.text) }
            .map { "${it.file}#${it.name}" }
        assertTrue("Paywall promises: $hits", hits.isEmpty())
    }

    @Test fun competitorNamesAreNotInAppOrInAppLabels() {
        val hits = stringsFiles().flatMap { entries(it) }.filter { competitors.containsMatchIn(it.text) }
            .map { "${it.file}#${it.name}" }
        assertTrue("Competitor names in strings (D3: neutral in-app labels): $hits", hits.isEmpty())
    }

    @Test fun competitorNamesAreNotInTitleOrShortDescription() {
        val listing = File(root, "listing/en-US")
        val title = File(listing, "title.txt").readText()
        val short = File(listing, "short-description.txt").readText()
        assertTrue(!competitors.containsMatchIn(title))
        assertTrue(!competitors.containsMatchIn(short))
    }

    @Test fun listingFieldsRespectPlayLimits() {
        val listing = File(root, "listing/en-US")
        assertEquals("Wheelfile: Car Maintenance Log", File(listing, "title.txt").readText().trim())
        assertTrue(File(listing, "title.txt").readText().trim().length <= 30)
        assertTrue(File(listing, "short-description.txt").readText().trim().length <= 80)
        assertTrue(File(listing, "full-description.txt").readText().trim().length <= 4000)
    }

    @Test fun listingTextsCarryNoPricesOrAdFreeClaims() {
        val bad = Regex("[\\$\\u20AC\\u00A3\\u20B9]|\\bno\\s+ads\\b|ad-?free", i)
        listingFiles().forEach { assertTrue("${it.path} has a price or ad-free claim", !bad.containsMatchIn(it.readText())) }
    }

    @Test fun requiredPositiveSentence_en() {
        val en = File("src/main/res/values/strings.xml").readText()
        assertTrue(
            "Your vehicle logs are stored on your device." in en || "Your data stays on your device." in en,
        )
        val welcome = entries(File("src/main/res/values/strings.xml")).first { it.name == "welcome_body" }
        assertEquals("Your vehicle logs are stored on your device.", welcome.text)
    }

    @Test fun requiredPositiveSentence_translated() {
        val expected = mapOf(
            "values-de" to "Deine Fahrzeugprotokolle werden auf deinem Gerät gespeichert.",
            "values-fr" to "Vos journaux de véhicule sont stockés sur votre appareil.",
            "values-es" to "Tus registros del vehículo se guardan en tu dispositivo.",
        )
        expected.forEach { (dir, sentence) ->
            val welcome = entries(File("src/main/res/$dir/strings.xml")).first { it.name == "welcome_body" }
            assertEquals(dir, sentence, welcome.text)
        }
    }

    @Test fun everyTranslationHasTheSamePlaceholdersAsEnglish() {
        val en = entries(File("src/main/res/values/strings.xml")).associateBy { it.name }
        val placeholder = Regex("%(\\d+\\$)?[sdf]")
        val problems = mutableListOf<String>()
        stringsFiles().filter { it.parentFile.name != "values" }.forEach { f ->
            entries(f).forEach { t ->
                val source = en[t.name] ?: run { problems += "${f.path}#${t.name}: unknown key"; return@forEach }
                val a = placeholder.findAll(source.text).map { it.value }.sorted().toList()
                val b = placeholder.findAll(t.text).map { it.value }.sorted().toList()
                if (a != b) problems += "${f.path}#${t.name}: $a vs $b"
            }
        }
        assertTrue("Placeholder mismatch: $problems", problems.isEmpty())
    }

    @Test fun everyTranslatableStringIsTranslatedInDeFrEs() {
        val english = File("src/main/res/values/strings.xml").readText()
        val names = Regex("<string\\s+name=\"([^\"]+)\"(?![^>]*translatable=\"false\")[^>]*>").findAll(english)
            .map { it.groupValues[1] }.toSet()
        listOf("values-de", "values-fr", "values-es").forEach { dir ->
            val have = entries(File("src/main/res/$dir/strings.xml")).map { it.name }.toSet()
            assertEquals("$dir is missing: ${names - have}", emptySet<String>(), names - have)
        }
    }

    @Test fun noConnectivityWordAnywhereInTheRepository() {
        val skip = setOf("build", ".git", ".gradle", ".idea", "node_modules")
        val re = banned.first().second
        val hits = root.walkTopDown()
            .onEnter { it.name !in skip }
            .filter { it.isFile && it.extension in setOf("kt", "kts", "xml", "md", "txt", "yml", "yaml", "json", "pro") }
            .filter { re.containsMatchIn(it.readText()) }
            .map { it.path }.toList()
        assertTrue("Connectivity word found in: $hits", hits.isEmpty())
    }
}
