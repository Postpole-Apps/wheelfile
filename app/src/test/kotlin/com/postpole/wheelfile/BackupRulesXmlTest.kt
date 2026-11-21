package com.postpole.wheelfile

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Element
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

/** docs/spec.md §6.3: cloud backup copies nothing; phone-to-phone transfer copies database, attachments, DataStore. */
class BackupRulesXmlTest {
    private data class Rule(val tag: String, val domain: String, val path: String?, val requireFlags: String?)

    private fun root(path: String): Element {
        val file = File("src/main/res/$path")
        assertTrue("missing $path", file.exists())
        return DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(file).documentElement
    }

    private fun rules(parent: Element): List<Rule> = (0 until parent.childNodes.length)
        .mapNotNull { parent.childNodes.item(it) as? Element }
        .map { Rule(it.tagName, it.getAttribute("domain"), it.getAttribute("path").ifEmpty { null }, it.getAttribute("requireFlags").ifEmpty { null }) }

    private fun section(name: String): List<Rule> {
        val el = root("xml/data_extraction_rules.xml")
        return rules(el.getElementsByTagName(name).item(0) as Element)
    }

    @Test
    fun manifestAllowsBackupAndPointsAtBothRuleFiles() {
        val app = root("../AndroidManifest.xml").getElementsByTagName("application").item(0) as Element
        assertEquals("true", app.getAttribute("android:allowBackup"))
        assertEquals("@xml/data_extraction_rules", app.getAttribute("android:dataExtractionRules"))
        assertEquals("@xml/backup_rules", app.getAttribute("android:fullBackupContent"))
    }

    @Test
    fun cloudBackupIncludesNothing() {
        val cloud = section("cloud-backup")
        assertTrue(cloud.none { it.tag == "include" })
        val excluded = cloud.filter { it.tag == "exclude" }.map { it.domain }.toSet()
        assertEquals(setOf("root", "file", "database", "sharedpref", "external"), excluded)
    }

    @Test
    fun deviceTransferIncludesDatabaseAttachmentsAndDataStore() {
        val includes = section("device-transfer").filter { it.tag == "include" }.map { it.domain to it.path }
        assertTrue(includes.contains("database" to "."))
        assertTrue(includes.contains("file" to "attachments"))
        assertTrue(includes.contains("file" to "datastore"))
        assertEquals("only these three are copied", 3, includes.size)
    }

    @Test
    fun deviceTransferExcludesFirebaseAndAdsCaches() {
        val excludes = section("device-transfer").filter { it.tag == "exclude" }
        assertTrue(excludes.any { it.domain == "database" && it.path == "google_app_measurement.db" })
        assertTrue(excludes.any { it.domain == "sharedpref" })
        assertFalse(section("device-transfer").any { it.tag == "include" && it.domain == "sharedpref" })
    }

    @Test
    fun legacyRulesExcludeEverythingByDefault() {
        val legacy = rules(root("xml/backup_rules.xml"))
        assertTrue(legacy.none { it.tag == "include" })
        assertEquals(setOf("root", "file", "database", "sharedpref", "external"), legacy.map { it.domain }.toSet())
    }

    @Test
    fun api28RulesIncludeOnlyForDeviceToDeviceTransfer() {
        val v28 = rules(root("xml-v28/backup_rules.xml"))
        val includes = v28.filter { it.tag == "include" }
        assertEquals(setOf("database" to ".", "file" to "attachments", "file" to "datastore"), includes.map { it.domain to it.path }.toSet())
        assertTrue("an include without the flag would reach cloud backup", includes.all { it.requireFlags == "deviceToDeviceTransfer" })
    }
}
