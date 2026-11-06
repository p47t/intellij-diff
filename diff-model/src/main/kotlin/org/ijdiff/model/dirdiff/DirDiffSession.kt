package org.ijdiff.model.dirdiff

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import org.ijdiff.model.document.DiffDocument
import org.ijdiff.model.twoway.TwoWayDiffSession
import java.nio.file.FileSystems
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import kotlin.io.path.exists
import kotlin.io.path.isDirectory
import kotlin.io.path.isRegularFile
import kotlin.io.path.name
import kotlin.math.abs

enum class DirCompareMode(val displayName: String) {
    CONTENT("Binary / Text Content"),
    SIZE("File Size"),
    SIZE_AND_TIMESTAMP("Size and Timestamp")
}

enum class DirDiffStatus(val symbol: String, val displayName: String) {
    ADDED("+", "Only in Right (Added)"),
    DELETED("-", "Only in Left (Deleted)"),
    MODIFIED("*", "Different (Modified)"),
    EQUAL("=", "Equal")
}

enum class DirSyncAction(val symbol: String, val description: String) {
    COPY_TO_RIGHT(">>", "Copy Left to Right"),
    COPY_TO_LEFT("<<", "Copy Right to Left"),
    DELETE_LEFT("X L", "Delete Left File"),
    DELETE_RIGHT("X R", "Delete Right File"),
    SKIP("--", "Do Nothing")
}

data class FileMeta(
    val path: Path,
    val size: Long,
    val lastModifiedMillis: Long
)

data class DirDiffEntry(
    val relativePath: String,
    val fileName: String,
    val parentRelativePath: String,
    val leftFile: FileMeta?,
    val rightFile: FileMeta?,
    val status: DirDiffStatus,
    val syncAction: DirSyncAction,
    val isBinary: Boolean
)

data class SyncResultSummary(
    val copiedToRight: Int,
    val copiedToLeft: Int,
    val deletedLeft: Int,
    val deletedRight: Int,
    val errors: List<String>
)

/**
 * UI-agnostic session controller for Directory / Folder Comparison & Synchronization (`DirDiffViewer`).
 * Supports async folder walking, content/size/timestamp comparison, status & glob filtering,
 * file copy/delete synchronization, and an embedded [TwoWayDiffSession] for the selected file pair.
 */
class DirDiffSession(
    val leftRoot: Path,
    val rightRoot: Path,
    initialCompareMode: DirCompareMode = DirCompareMode.CONTENT
) {
    private val _compareMode = MutableStateFlow(initialCompareMode)
    val compareMode: StateFlow<DirCompareMode> = _compareMode.asStateFlow()

    private val _statusFilter = MutableStateFlow(
        setOf(DirDiffStatus.ADDED, DirDiffStatus.DELETED, DirDiffStatus.MODIFIED)
    )
    val statusFilter: StateFlow<Set<DirDiffStatus>> = _statusFilter.asStateFlow()

    private val _globFilter = MutableStateFlow("")
    val globFilter: StateFlow<String> = _globFilter.asStateFlow()

    private val _allEntries = MutableStateFlow<List<DirDiffEntry>>(emptyList())
    val allEntries: StateFlow<List<DirDiffEntry>> = _allEntries.asStateFlow()

    private val _filteredEntries = MutableStateFlow<List<DirDiffEntry>>(emptyList())
    val filteredEntries: StateFlow<List<DirDiffEntry>> = _filteredEntries.asStateFlow()

    private val _selectedEntry = MutableStateFlow<DirDiffEntry?>(null)
    val selectedEntry: StateFlow<DirDiffEntry?> = _selectedEntry.asStateFlow()

    private val _previewSession = MutableStateFlow<TwoWayDiffSession?>(null)
    val previewSession: StateFlow<TwoWayDiffSession?> = _previewSession.asStateFlow()

    private val _isScanning = MutableStateFlow(false)
    val isScanning: StateFlow<Boolean> = _isScanning.asStateFlow()

    fun setCompareMode(mode: DirCompareMode) {
        if (_compareMode.value == mode) return
        _compareMode.value = mode
        scanSync()
    }

    fun toggleStatusFilter(status: DirDiffStatus) {
        val current = _statusFilter.value.toMutableSet()
        if (!current.add(status)) {
            current.remove(status)
        }
        _statusFilter.value = current
        applyFilters()
    }

    fun setStatusFilter(statuses: Set<DirDiffStatus>) {
        _statusFilter.value = statuses
        applyFilters()
    }

    fun setGlobFilter(globExpression: String) {
        _globFilter.value = globExpression.trim()
        applyFilters()
    }

    fun setSyncAction(relativePath: String, action: DirSyncAction) {
        _allEntries.value = _allEntries.value.map { entry ->
            if (entry.relativePath == relativePath) entry.copy(syncAction = action) else entry
        }
        applyFilters()
    }

    fun selectEntry(entry: DirDiffEntry?) {
        _selectedEntry.value = entry
        if (entry == null || entry.isBinary) {
            _previewSession.value = null
            return
        }
        val leftDoc = if (entry.leftFile != null && entry.leftFile.path.exists()) {
            DiffDocument.fromFile(entry.leftFile.path)
        } else {
            DiffDocument(
                initialText = "",
                filePath = leftRoot.resolve(entry.relativePath),
                title = "${leftRoot.resolve(entry.relativePath)} (Not Present)",
                isReadOnly = true
            )
        }
        val rightDoc = if (entry.rightFile != null && entry.rightFile.path.exists()) {
            DiffDocument.fromFile(entry.rightFile.path)
        } else {
            DiffDocument(
                initialText = "",
                filePath = rightRoot.resolve(entry.relativePath),
                title = "${rightRoot.resolve(entry.relativePath)} (Not Present)",
                isReadOnly = true
            )
        }
        _previewSession.value = TwoWayDiffSession(leftDocument = leftDoc, rightDocument = rightDoc)
    }

    suspend fun scanAsync() = withContext(Dispatchers.IO) {
        scanSync()
    }

    fun scanSync() {
        _isScanning.value = true
        try {
            val leftMap = collectFiles(leftRoot)
            val rightMap = collectFiles(rightRoot)
            val allRelativePaths = (leftMap.keys + rightMap.keys).toSortedSet()
            val mode = _compareMode.value

            val entries = allRelativePaths.map { relPath ->
                val leftMeta = leftMap[relPath]
                val rightMeta = rightMap[relPath]

                val status = when {
                    leftMeta == null && rightMeta != null -> DirDiffStatus.ADDED
                    leftMeta != null && rightMeta == null -> DirDiffStatus.DELETED
                    leftMeta != null && rightMeta != null -> {
                        if (areFilesEqual(leftMeta, rightMeta, mode)) {
                            DirDiffStatus.EQUAL
                        } else {
                            DirDiffStatus.MODIFIED
                        }
                    }
                    else -> DirDiffStatus.EQUAL
                }

                val defaultAction = when (status) {
                    DirDiffStatus.DELETED -> DirSyncAction.COPY_TO_RIGHT
                    DirDiffStatus.ADDED -> DirSyncAction.COPY_TO_LEFT
                    DirDiffStatus.MODIFIED -> DirSyncAction.COPY_TO_RIGHT
                    DirDiffStatus.EQUAL -> DirSyncAction.SKIP
                }

                val samplePath = leftMeta?.path ?: rightMeta?.path
                val binary = samplePath != null && isLikelyBinary(samplePath)
                val relPathObj = Path.of(relPath)

                DirDiffEntry(
                    relativePath = relPath,
                    fileName = relPathObj.name,
                    parentRelativePath = relPathObj.parent?.toString()?.replace('\\', '/') ?: "",
                    leftFile = leftMeta,
                    rightFile = rightMeta,
                    status = status,
                    syncAction = defaultAction,
                    isBinary = binary
                )
            }

            _allEntries.value = entries
            applyFilters()
        } finally {
            _isScanning.value = false
        }
    }

    /**
     * Executes synchronization for a single [DirDiffEntry] or a batch of entries.
     */
    fun synchronizeEntries(entries: List<DirDiffEntry>): SyncResultSummary {
        var copiedRight = 0
        var copiedLeft = 0
        var deletedLeft = 0
        var deletedRight = 0
        val errors = mutableListOf<String>()

        for (entry in entries) {
            try {
                when (entry.syncAction) {
                    DirSyncAction.COPY_TO_RIGHT -> {
                        val src = entry.leftFile?.path
                        if (src != null && src.exists()) {
                            val dst = rightRoot.resolve(entry.relativePath)
                            dst.parent?.let { Files.createDirectories(it) }
                            Files.copy(src, dst, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.COPY_ATTRIBUTES)
                            copiedRight++
                        }
                    }
                    DirSyncAction.COPY_TO_LEFT -> {
                        val src = entry.rightFile?.path
                        if (src != null && src.exists()) {
                            val dst = leftRoot.resolve(entry.relativePath)
                            dst.parent?.let { Files.createDirectories(it) }
                            Files.copy(src, dst, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.COPY_ATTRIBUTES)
                            copiedLeft++
                        }
                    }
                    DirSyncAction.DELETE_LEFT -> {
                        val target = entry.leftFile?.path
                        if (target != null && target.exists()) {
                            Files.deleteIfExists(target)
                            deletedLeft++
                        }
                    }
                    DirSyncAction.DELETE_RIGHT -> {
                        val target = entry.rightFile?.path
                        if (target != null && target.exists()) {
                            Files.deleteIfExists(target)
                            deletedRight++
                        }
                    }
                    DirSyncAction.SKIP -> Unit
                }
            } catch (e: Exception) {
                errors.add("${entry.relativePath}: ${e.message}")
            }
        }

        val previouslySelectedRelPath = _selectedEntry.value?.relativePath
        scanSync()
        if (previouslySelectedRelPath != null) {
            val updated = _filteredEntries.value.find { it.relativePath == previouslySelectedRelPath }
                ?: _filteredEntries.value.firstOrNull()
            selectEntry(updated)
        }

        return SyncResultSummary(
            copiedToRight = copiedRight,
            copiedToLeft = copiedLeft,
            deletedLeft = deletedLeft,
            deletedRight = deletedRight,
            errors = errors
        )
    }

    private fun applyFilters() {
        val allowedStatuses = _statusFilter.value
        val globRaw = _globFilter.value
        val (includeMatchers, excludeMatchers) = parseGlobRules(globRaw)

        val filtered = _allEntries.value.filter { entry ->
            if (entry.status !in allowedStatuses) return@filter false
            val relPath = Path.of(entry.relativePath)
            val fileNamePath = Path.of(entry.fileName)
            if (excludeMatchers.any { it.matches(relPath) || it.matches(fileNamePath) }) {
                return@filter false
            }
            if (includeMatchers.isNotEmpty() && includeMatchers.none { it.matches(relPath) || it.matches(fileNamePath) }) {
                return@filter false
            }
            true
        }

        _filteredEntries.value = filtered
        val currentSelected = _selectedEntry.value
        if (currentSelected == null || filtered.none { it.relativePath == currentSelected.relativePath }) {
            selectEntry(filtered.firstOrNull { !it.isBinary } ?: filtered.firstOrNull())
        } else {
            val refreshed = filtered.first { it.relativePath == currentSelected.relativePath }
            _selectedEntry.value = refreshed
        }
    }

    private fun parseGlobRules(globExpression: String): Pair<List<java.nio.file.PathMatcher>, List<java.nio.file.PathMatcher>> {
        if (globExpression.isBlank()) return emptyList<java.nio.file.PathMatcher>() to emptyList()
        val fs = FileSystems.getDefault()
        val includes = mutableListOf<java.nio.file.PathMatcher>()
        val excludes = mutableListOf<java.nio.file.PathMatcher>()
        for (token in globExpression.split(';', ',')) {
            val trimmed = token.trim()
            if (trimmed.isEmpty()) continue
            val isExclude = trimmed.startsWith("!")
            val pattern = if (isExclude) trimmed.substring(1).trim() else trimmed
            if (pattern.isEmpty()) continue
            runCatching {
                val matcher = fs.getPathMatcher("glob:$pattern")
                if (isExclude) excludes.add(matcher) else includes.add(matcher)
            }
        }
        return includes to excludes
    }

    private fun collectFiles(root: Path): Map<String, FileMeta> {
        if (!root.exists() || !root.isDirectory()) return emptyMap()
        val result = linkedMapOf<String, FileMeta>()
        Files.walk(root).use { stream ->
            stream.filter { it.isRegularFile() }.forEach { file ->
                val rel = root.relativize(file).toString().replace('\\', '/')
                // Skip .git internal directory by default
                if (rel.startsWith(".git/") || rel.contains("/.git/")) return@forEach
                val attrs = Files.readAttributes(file, java.nio.file.attribute.BasicFileAttributes::class.java)
                result[rel] = FileMeta(
                    path = file,
                    size = attrs.size(),
                    lastModifiedMillis = attrs.lastModifiedTime().toMillis()
                )
            }
        }
        return result
    }

    private fun areFilesEqual(left: FileMeta, right: FileMeta, mode: DirCompareMode): Boolean {
        return when (mode) {
            DirCompareMode.SIZE -> left.size == right.size
            DirCompareMode.SIZE_AND_TIMESTAMP -> {
                left.size == right.size && abs(left.lastModifiedMillis - right.lastModifiedMillis) < 2000L
            }
            DirCompareMode.CONTENT -> {
                if (left.size != right.size) return false
                if (left.size == 0L) return true
                Files.mismatch(left.path, right.path) == -1L
            }
        }
    }

    private fun isLikelyBinary(path: Path): Boolean {
        if (!path.exists() || !path.isRegularFile()) return false
        return try {
            Files.newInputStream(path).use { input ->
                val buf = ByteArray(1024)
                val read = input.read(buf)
                if (read <= 0) return false
                for (i in 0 until read) {
                    if (buf[i] == 0.toByte()) return true
                }
                false
            }
        } catch (_: Exception) {
            false
        }
    }
}
