package org.ijdiff.model

import com.intellij.diff.comparison.ComparisonPolicy
import org.ijdiff.model.config.IjDiffConfig
import org.ijdiff.model.dirdiff.DirDiffSession
import org.ijdiff.model.dirdiff.DirDiffStatus
import org.ijdiff.model.dirdiff.DirSyncAction
import org.ijdiff.model.document.DiffDocument
import org.ijdiff.model.document.LineSeparator
import org.ijdiff.model.twoway.ChunkDirection
import org.ijdiff.model.twoway.TwoWayDiffSession
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.exists
import kotlin.io.path.readText
import kotlin.io.path.writeText

class DiffModelTest {

    @Test
    fun `DiffDocument tracks dirty state, undo-redo, and explicit save with line separator preservation`(
        @TempDir tempDir: Path
    ) {
        val file = tempDir.resolve("sample.txt")
        Files.writeString(file, "line1\r\nline2\r\nline3")

        val doc = DiffDocument.fromFile(file)
        assertEquals(LineSeparator.CRLF, doc.lineSeparator)
        assertEquals("line1\nline2\nline3", doc.text.value)
        assertFalse(doc.isDirty.value)

        doc.setText("line1\nline2_edited\nline3")
        assertTrue(doc.isDirty.value)
        assertTrue(doc.canUndo.value)
        // Disk file is NOT modified until explicit save()
        assertEquals("line1\r\nline2\r\nline3", Files.readString(file))

        // Undo & Redo
        assertTrue(doc.undo())
        assertFalse(doc.isDirty.value)
        assertTrue(doc.redo())
        assertTrue(doc.isDirty.value)

        // Explicit Save writes CRLF back to disk
        assertTrue(doc.save())
        assertFalse(doc.isDirty.value)
        assertEquals("line1\r\nline2_edited\r\nline3", Files.readString(file))
    }

    @Test
    fun `TwoWayDiffSession applies replaceChunk and appendChunk in both directions with undo`() {
        val left = DiffDocument("header\nleft_value = 1\nfooter")
        val right = DiffDocument("header\nright_value = 2\nfooter\nextra_right")

        val session = TwoWayDiffSession(left, right)
        assertEquals(2, session.state.value.fragments.size)

        // Replace first chunk Left -> Right (>>)
        val firstChunk = session.state.value.fragments[0]
        session.replaceChunk(firstChunk, ChunkDirection.LEFT_TO_RIGHT)
        assertEquals("header\nleft_value = 1\nfooter\nextra_right", right.text.value)
        assertEquals(1, session.state.value.fragments.size)

        // Undo on right document and recompute
        assertTrue(right.undo())
        session.recomputeNow()
        assertEquals(2, session.state.value.fragments.size)

        // Append first chunk Right -> Left (<<)
        session.appendChunk(session.state.value.fragments[0], ChunkDirection.RIGHT_TO_LEFT)
        assertEquals("header\nleft_value = 1\nright_value = 2\nfooter", left.text.value)
    }

    @Test
    fun `DirDiffSession scans directories, filters by status and glob, previews diff, and synchronizes files`(
        @TempDir tempDir: Path
    ) {
        val leftDir = tempDir.resolve("left").also { Files.createDirectories(it) }
        val rightDir = tempDir.resolve("right").also { Files.createDirectories(it) }

        // Equal file
        leftDir.resolve("same.txt").writeText("identical\n")
        rightDir.resolve("same.txt").writeText("identical\n")

        // Modified file
        leftDir.resolve("mod.kt").writeText("val x = 1\n")
        rightDir.resolve("mod.kt").writeText("val x = 2\n")

        // Only in left (Deleted in right)
        leftDir.resolve("only_left.txt").writeText("left content\n")

        // Only in right (Added in right)
        rightDir.resolve("sub").also { Files.createDirectories(it) }
            .resolve("only_right.txt").writeText("right content\n")

        // Ignored via glob filter
        leftDir.resolve("ignored.tmp").writeText("temp")

        val dirSession = DirDiffSession(leftDir, rightDir)
        dirSession.scanSync()

        assertEquals(5, dirSession.allEntries.value.size)
        // By default, EQUAL is excluded from statusFilter
        assertEquals(4, dirSession.filteredEntries.value.size)

        // Apply glob exclusion !*.tmp
        dirSession.setGlobFilter("!*.tmp")
        assertEquals(3, dirSession.filteredEntries.value.size)

        val modEntry = dirSession.filteredEntries.value.first { it.relativePath == "mod.kt" }
        assertEquals(DirDiffStatus.MODIFIED, modEntry.status)

        // Selecting an entry creates an embedded TwoWayDiffSession preview
        dirSession.selectEntry(modEntry)
        val preview = dirSession.previewSession.value
        assertNotNull(preview)
        assertEquals(1, preview!!.state.value.fragments.size)

        // Synchronize all filtered entries (only_left.txt -> right, sub/only_right.txt -> left, mod.kt -> right)
        val summary = dirSession.synchronizeEntries(dirSession.filteredEntries.value)
        assertEquals(2, summary.copiedToRight)
        assertEquals(1, summary.copiedToLeft)
        assertTrue(summary.errors.isEmpty())

        assertTrue(rightDir.resolve("only_left.txt").exists())
        assertTrue(leftDir.resolve("sub/only_right.txt").exists())
        assertEquals("val x = 1\n", rightDir.resolve("mod.kt").readText())
    }

    @Test
    fun `IjDiffConfig parses TOML font table, aliases, inline comments, and saves round-trip`(
        @TempDir tempDir: Path
    ) {
        val parsed1 = IjDiffConfig.parse(
            """
            # Custom TOML font config
            theme = "light" # inline comment
            unified = true
            whitespace = "ignore"

            [font]
            family = "Ioskeley Mono"
            size = 16
            """.trimIndent()
        )
        assertEquals("Ioskeley Mono", parsed1.fontFamily)
        assertEquals(16, parsed1.fontSize)
        assertEquals("light", parsed1.theme)
        assertTrue(parsed1.unified)
        assertEquals("ignore", parsed1.whitespace)
        assertEquals(ComparisonPolicy.IGNORE_WHITESPACES, parsed1.resolveComparisonPolicy())

        // Also supports [font] face alias, literal strings, and bounds clamping
        val parsed2 = IjDiffConfig.parse(
            """
            [font]
            face = 'Fira Code'
            size = 99
            """.trimIndent()
        )
        assertEquals("Fira Code", parsed2.fontFamily)
        assertEquals(IjDiffConfig.MAX_FONT_SIZE, parsed2.fontSize)
        assertFalse(parsed2.unified)
        assertEquals("default", parsed2.whitespace)
        assertEquals(ComparisonPolicy.DEFAULT, parsed2.resolveComparisonPolicy())

        // Round-trip save and load as TOML
        val configFile = tempDir.resolve("ijdiff.toml")
        IjDiffConfig.save(parsed1, configFile)
        val tomlText = Files.readString(configFile)
        assertTrue(tomlText.contains("unified = true"))
        assertTrue(tomlText.contains("whitespace = \"ignore\""))
        assertTrue(tomlText.contains("[font]"))
        assertTrue(tomlText.contains("family = \"Ioskeley Mono\""))
        assertTrue(tomlText.contains("size = 16"))

        val reloaded = IjDiffConfig.load(configFile)
        assertEquals("Ioskeley Mono", reloaded.fontFamily)
        assertEquals(16, reloaded.fontSize)
        assertEquals("light", reloaded.theme)
        assertTrue(reloaded.unified)
        assertEquals("ignore", reloaded.whitespace)
    }
}
