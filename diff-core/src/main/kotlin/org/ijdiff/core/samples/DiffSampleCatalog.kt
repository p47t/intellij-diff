package org.ijdiff.core.samples

import org.ijdiff.core.TextDiffType
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.exists
import kotlin.io.path.isDirectory

data class FileDiffSampleSpec(
    val id: String,
    val title: String,
    val description: String,
    val leftFileName: String,
    val rightFileName: String,
    val minFragmentsDefault: Int,
    val maxFragmentsDefault: Int = Int.MAX_VALUE,
    val expectedFragmentsIgnoreWhitespace: Int? = null,
    val expectedDiffTypes: Set<TextDiffType>
) {
    fun resolveLeftPath(samplesRoot: Path): Path =
        samplesRoot.resolve("files").resolve(id).resolve(leftFileName)

    fun resolveRightPath(samplesRoot: Path): Path =
        samplesRoot.resolve("files").resolve(id).resolve(rightFileName)
}

data class DirectoryDiffSampleSpec(
    val id: String,
    val title: String,
    val description: String,
    val expectedTotalEntries: Int,
    val expectedModifiedCount: Int,
    val expectedDeletedCount: Int,
    val expectedAddedCount: Int,
    val expectedEqualCount: Int,
    val binaryRelativePaths: Set<String>
) {
    fun resolveLeftRoot(samplesRoot: Path): Path =
        samplesRoot.resolve("directories").resolve(id).resolve("left")

    fun resolveRightRoot(samplesRoot: Path): Path =
        samplesRoot.resolve("directories").resolve(id).resolve("right")
}

object DiffSampleCatalog {

    val FILE_SAMPLES: List<FileDiffSampleSpec> = listOf(
        FileDiffSampleSpec(
            id = "01-kotlin-refactoring",
            title = "01: Kotlin Class Refactoring",
            description = "Multi-hunk Kotlin class refactoring with added fields, functional pipeline, and inline string interpolation edits",
            leftFileName = "left.kt",
            rightFileName = "right.kt",
            minFragmentsDefault = 4,
            expectedDiffTypes = setOf(TextDiffType.MODIFIED, TextDiffType.INSERTED, TextDiffType.DELETED)
        ),
        FileDiffSampleSpec(
            id = "02-whitespace-and-indentation",
            title = "02: Whitespace & Indentation Policies",
            description = "Leading indentation, trailing spaces, internal alignment spaces, and a single numeric literal modification",
            leftFileName = "left.java",
            rightFileName = "right.java",
            minFragmentsDefault = 3,
            expectedFragmentsIgnoreWhitespace = 1,
            expectedDiffTypes = setOf(TextDiffType.MODIFIED)
        ),
        FileDiffSampleSpec(
            id = "03-patience-anchor-reordering",
            title = "03: Patience Diff Anchor Matching",
            description = "Repeated Python function boilerplate with a new function inserted in the middle and modified values",
            leftFileName = "left.py",
            rightFileName = "right.py",
            minFragmentsDefault = 3,
            expectedDiffTypes = setOf(TextDiffType.MODIFIED, TextDiffType.INSERTED)
        ),
        FileDiffSampleSpec(
            id = "04-json-nested-config",
            title = "04: Nested JSON Dependencies",
            description = "JSON configuration with semver version bumps, added scripts, and deleted legacy section",
            leftFileName = "left.json",
            rightFileName = "right.json",
            minFragmentsDefault = 2,
            expectedDiffTypes = setOf(TextDiffType.MODIFIED)
        ),
        FileDiffSampleSpec(
            id = "05-markdown-documentation",
            title = "05: Markdown Prose & Tables",
            description = "Prose word-level edits, Markdown table cell updates, and numbered list additions",
            leftFileName = "left.md",
            rightFileName = "right.md",
            minFragmentsDefault = 3,
            expectedDiffTypes = setOf(TextDiffType.MODIFIED)
        ),
        FileDiffSampleSpec(
            id = "06-edge-empty-to-populated",
            title = "06: Edge Case — Empty to Populated",
            description = "Left file is 0 bytes, Right file has 3 lines",
            leftFileName = "left.txt",
            rightFileName = "right.txt",
            minFragmentsDefault = 1,
            maxFragmentsDefault = 1,
            expectedFragmentsIgnoreWhitespace = 1,
            expectedDiffTypes = setOf(TextDiffType.MODIFIED, TextDiffType.INSERTED)
        ),
        FileDiffSampleSpec(
            id = "07-edge-populated-to-empty",
            title = "07: Edge Case — Populated to Empty",
            description = "Left file has 3 lines, Right file is 0 bytes",
            leftFileName = "left.txt",
            rightFileName = "right.txt",
            minFragmentsDefault = 1,
            maxFragmentsDefault = 1,
            expectedFragmentsIgnoreWhitespace = 1,
            expectedDiffTypes = setOf(TextDiffType.MODIFIED, TextDiffType.DELETED)
        ),
        FileDiffSampleSpec(
            id = "08-edge-identical-files",
            title = "08: Edge Case — Identical Rust Files",
            description = "Byte-for-byte identical files producing zero diff fragments",
            leftFileName = "left.rs",
            rightFileName = "right.rs",
            minFragmentsDefault = 0,
            maxFragmentsDefault = 0,
            expectedFragmentsIgnoreWhitespace = 0,
            expectedDiffTypes = emptySet()
        ),
        FileDiffSampleSpec(
            id = "09-edge-complete-rewrite",
            title = "09: Edge Case — Complete SQL Rewrite",
            description = "DDL schema completely replaced with an analytical SELECT query",
            leftFileName = "left.sql",
            rightFileName = "right.sql",
            minFragmentsDefault = 1,
            maxFragmentsDefault = 1,
            expectedFragmentsIgnoreWhitespace = 1,
            expectedDiffTypes = setOf(TextDiffType.MODIFIED)
        ),
        FileDiffSampleSpec(
            id = "10-unicode-and-emoji",
            title = "10: Unicode, CJK & Multi-Byte Emojis",
            description = "Internationalized properties with English, Chinese, Japanese, Korean, and surrogate-pair emojis",
            leftFileName = "left.properties",
            rightFileName = "right.properties",
            minFragmentsDefault = 1,
            expectedDiffTypes = setOf(TextDiffType.MODIFIED)
        ),
        FileDiffSampleSpec(
            id = "11-crlf-vs-lf-line-endings",
            title = "11: CRLF (Windows) vs LF (Unix) Line Endings",
            description = "Shell script with CRLF on left and LF on right plus localized environment variable edits",
            leftFileName = "left.sh",
            rightFileName = "right.sh",
            minFragmentsDefault = 2,
            expectedDiffTypes = setOf(TextDiffType.MODIFIED)
        ),
        FileDiffSampleSpec(
            id = "12-large-synthetic-stress",
            title = "12: 1,000-Line Stress Benchmark",
            description = "1,000-line telemetry log with 10 modified blocks, 10 deleted lines, and 10 inserted lines",
            leftFileName = "left.log",
            rightFileName = "right.log",
            minFragmentsDefault = 30,
            maxFragmentsDefault = 30,
            expectedFragmentsIgnoreWhitespace = 30,
            expectedDiffTypes = setOf(TextDiffType.MODIFIED, TextDiffType.DELETED, TextDiffType.INSERTED)
        )
    )

    val DIRECTORY_SAMPLES: List<DirectoryDiffSampleSpec> = listOf(
        DirectoryDiffSampleSpec(
            id = "dir-01-microservice-release",
            title = "Dir 01: Microservice v1 vs v2 Release Tree",
            description = "Nested directory tree with identical, modified, added, deleted, binary, and build-artifact files",
            expectedTotalEntries = 11,
            expectedModifiedCount = 3, // Server.kt, application.yml, assets/logo.bin
            expectedDeletedCount = 4,  // LegacyAuth.kt, migrate_v1.sh, build/Server.class, scratch.tmp
            expectedAddedCount = 2,    // OAuth2Provider.kt, rate-limits.json
            expectedEqualCount = 2,    // README.md, Models.kt
            binaryRelativePaths = setOf("assets/logo.bin", "build/Server.class")
        )
    )

    /**
     * Locates the root `samples/` directory from the current working directory or parent project root.
     */
    fun locateSamplesRoot(): Path {
        var current: Path? = Path.of("").toAbsolutePath().normalize()
        while (current != null) {
            val candidate = current.resolve("samples")
            if (candidate.exists() && candidate.isDirectory()) {
                return candidate
            }
            current = current.parent
        }
        throw IllegalStateException("Could not locate 'samples/' directory from ${Path.of("").toAbsolutePath()}")
    }
}
