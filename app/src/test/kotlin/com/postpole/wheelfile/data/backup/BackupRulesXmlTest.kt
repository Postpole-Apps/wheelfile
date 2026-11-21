package com.postpole.wheelfile.data.backup

import com.postpole.wheelfile.data.attachments.AttachmentStore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Element
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

/** Spec section 6.3: cloud backup copies nothing; device-to-device transfer carries the database, attachments and DataStore. */
class BackupRulesXmlTest {
    private data class Rule(val tag: String, val domain: String, val path: String?, val requireFlags: String?)

    private fun res(path: String): File =
        listOf("src/main/$path", "app/src/main/$path").map(::File).first { it.exists() }

    private fun root(path: String): Element =
        DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(res(path)).documentElement

    private fun rules(parent: Element): List<Rule> {
        val out = ArrayList<Rule>()
        val nodes = parent.childNodes
        for (i in 0 until nodes.length) {
            val e = nodes.item(i) as? Element ?: continue
            if (e.tagName == "include" || e.tagName == "exclude") {
                out += Rule(
                    e.tagName, e.getAttribute("domain"), e.getAttribute("path").ifEmpty { null },
                    e.getAttribute("requireFlags").ifEmpty { null },
                )
            }
        }
        return out
    }

    private fun section(name: String): List<Rule> {
        val e = root("res/xml/data_extraction_rules.xml").getElementsByTagName(name).item(0) as Element
        return rules(e)
    }

    @Test
    fun cloudBackupIsNone() {
        val cloud = section("cloud-backup")
        assertTrue("cloud backup must not include anything", cloud.none { it.tag == "include" })
        val excluded = cloud.filter { it.tag == "exclude" && it.path == null }.map { it.domain }.toSet()
        assertTrue(excluded.containsAll(setOf("root", "file", "database", "sharedpref", "external")))
        assertTrue(excluded.containsAll(setOf("device_root", "device_file", "device_database", "device_sharedpref")))
    }

    @Test
    fun deviceTransferCarriesDatabaseAttachmentsAndDataStore() {
        val inc = section("device-transfer").filter { it.tag == "include" }
        assertTrue(inc.any { it.domain == "database" && it.path == "app.db" })
        assertTrue(inc.any { it.domain == "file" && it.path == AttachmentStore.DIR_NAME })
        assertTrue(inc.any { it.domain == "file" && it.path == "datastore" })
        // Only those: nothing else (no shared preferences, no external storage) is transferred.
        assertTrue(inc.all { it.domain == "database" || it.domain == "file" })
        assertTrue(inc.filter { it.domain == "database" }.all { it.path!!.startsWith("app.db") })
    }

    @Test
    fun deviceTransferLeavesFirebaseAndAdsCachesBehind() {
        // Includes are an allow-list: the Firebase and ads databases and files are not on it, so they stay behind.
        val inc = section("device-transfer").filter { it.tag == "include" }
        val allowed = inc.map { it.domain to it.path }.toSet()
        assertEquals(
            setOf("database" to "app.db", "database" to "app.db-wal", "database" to "app.db-shm", "file" to "attachments", "file" to "datastore"),
            allowed,
        )
        val ex = section("device-transfer").filter { it.tag == "exclude" }
        assertTrue(ex.any { it.domain == "sharedpref" && it.path == null })
        assertTrue(ex.any { it.domain == "external" && it.path == null })
        assertTrue(inc.none { it.domain == "sharedpref" || it.domain == "external" })
    }

    @Test
    fun legacyRulesExcludeEverythingAndV28OnlyIncludesForDeviceTransfer() {
        val legacy = rules(root("res/xml/backup_rules.xml"))
        assertTrue(legacy.none { it.tag == "include" })
        assertTrue(legacy.map { it.domain }.containsAll(listOf("root", "file", "database", "sharedpref", "external")))

        val v28 = rules(root("res/xml-v28/backup_rules.xml"))
        val includes = v28.filter { it.tag == "include" }
        assertTrue(includes.isNotEmpty())
        assertTrue(includes.all { it.requireFlags == "deviceToDeviceTransfer" })
        assertTrue(includes.any { it.domain == "database" && it.path == "app.db" })
        assertTrue(includes.any { it.domain == "file" && it.path == AttachmentStore.DIR_NAME })
        assertTrue(includes.any { it.domain == "file" && it.path == "datastore" })
    }

    @Test
    fun manifestPointsAtTheRules() {
        val app = root("AndroidManifest.xml").getElementsByTagName("application").item(0) as Element
        val ns = "http://schemas.android.com/apk/res/android"
        assertEquals("true", app.getAttributeNS(ns, "allowBackup").ifEmpty { app.getAttribute("android:allowBackup") })
        assertEquals("@xml/data_extraction_rules", app.getAttribute("android:dataExtractionRules"))
        assertEquals("@xml/backup_rules", app.getAttribute("android:fullBackupContent"))
        assertFalse(app.getAttribute("android:allowBackup") == "false")
    }
}
