package org.ijdiff.model.document

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.ijdiff.core.LineOffsets
import org.ijdiff.core.LineOffsetsImpl
import java.nio.charset.Charset
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.exists
import kotlin.io.path.isWritable
import kotlin.io.path.name

enum class LineSeparator(val separator: String, val displayName: String) {
    LF("\n", "LF (\\n)"),
    CRLF("\r\n", "CRLF (\\r\\n)"),
    CR("\r", "CR (\\r)");

    companion object {
        fun detect(rawText: String): LineSeparator {
            val crlfIdx = rawText.indexOf("\r\n")
            if (crlfIdx >= 0) return CRLF
            val crIdx = rawText.indexOf('\r')
            if (crIdx >= 0) return CR
            return LF
        }

        fun normalizeToLf(rawText: String): String {
            if (!rawText.contains('\r')) return rawText
            return rawText.replace("\r\n", "\n").replace('\r', '\n')
        }
    }
}

/**
 * UI-toolkit-independent mutable document model with line offsets, undo/redo history,
 * dirty flag tracking, and explicit save/reload operations.
 */
class DiffDocument(
    initialText: String,
    val filePath: Path? = null,
    val title: String = filePath?.name ?: "Untitled",
    initialLineSeparator: LineSeparator = LineSeparator.LF,
    val charset: Charset = StandardCharsets.UTF_8,
    val isReadOnly: Boolean = false
) {
    private data class EditSnapshot(val text: String)

    private val _text = MutableStateFlow(LineSeparator.normalizeToLf(initialText))
    val text: StateFlow<String> = _text.asStateFlow()

    private var _savedText: String = _text.value
    private var _lineSeparator: LineSeparator = initialLineSeparator

    val lineSeparator: LineSeparator
        get() = _lineSeparator

    private val _isDirty = MutableStateFlow(false)
    val isDirty: StateFlow<Boolean> = _isDirty.asStateFlow()

    private val undoStack = ArrayDeque<EditSnapshot>()
    private val redoStack = ArrayDeque<EditSnapshot>()

    private val _canUndo = MutableStateFlow(false)
    val canUndo: StateFlow<Boolean> = _canUndo.asStateFlow()

    private val _canRedo = MutableStateFlow(false)
    val canRedo: StateFlow<Boolean> = _canRedo.asStateFlow()

    @Volatile
    var lineOffsets: LineOffsets = LineOffsetsImpl.create(_text.value)
        private set

    val lineCount: Int
        get() = lineOffsets.lineCount

    fun getLineText(line: Int, includeNewline: Boolean = false): String {
        val current = _text.value
        val offsets = lineOffsets
        if (line < 0 || line >= offsets.lineCount) return ""
        val start = offsets.getLineStart(line)
        val end = offsets.getLineEnd(line, includeNewline)
        return current.substring(start, end)
    }

    fun setText(newText: String, recordUndo: Boolean = true) {
        if (isReadOnly) return
        val normalized = LineSeparator.normalizeToLf(newText)
        val current = _text.value
        if (normalized == current) return

        if (recordUndo) {
            undoStack.addLast(EditSnapshot(current))
            if (undoStack.size > MAX_UNDO_HISTORY) {
                undoStack.removeFirst()
            }
            redoStack.clear()
        }
        applyTextInternal(normalized)
    }

    fun replaceString(startOffset: Int, endOffset: Int, replacement: CharSequence, recordUndo: Boolean = true) {
        if (isReadOnly) return
        val current = _text.value
        if (startOffset < 0 || endOffset > current.length || startOffset > endOffset) {
            throw IndexOutOfBoundsException("Invalid range [$startOffset, $endOffset) for length ${current.length}")
        }
        val sb = StringBuilder(current.length - (endOffset - startOffset) + replacement.length)
        sb.append(current, 0, startOffset)
        sb.append(LineSeparator.normalizeToLf(replacement.toString()))
        sb.append(current, endOffset, current.length)
        setText(sb.toString(), recordUndo = recordUndo)
    }

    fun undo(): Boolean {
        if (isReadOnly || undoStack.isEmpty()) return false
        val previous = undoStack.removeLast()
        redoStack.addLast(EditSnapshot(_text.value))
        applyTextInternal(previous.text)
        return true
    }

    fun redo(): Boolean {
        if (isReadOnly || redoStack.isEmpty()) return false
        val next = redoStack.removeLast()
        undoStack.addLast(EditSnapshot(_text.value))
        applyTextInternal(next.text)
        return true
    }

    /**
     * Explicitly writes the current document buffer to [filePath], preserving [_lineSeparator] and [charset].
     */
    fun save(): Boolean {
        val path = filePath ?: return false
        if (isReadOnly) return false
        val current = _text.value
        val diskText = if (_lineSeparator == LineSeparator.LF) {
            current
        } else {
            current.replace("\n", _lineSeparator.separator)
        }
        path.parent?.let { Files.createDirectories(it) }
        Files.writeString(path, diskText, charset)
        _savedText = current
        _isDirty.value = false
        return true
    }

    /**
     * Reloads the document from [filePath], discarding unsaved in-memory edits.
     */
    fun reloadFromDisk(): Boolean {
        val path = filePath ?: return false
        if (!path.exists()) return false
        val raw = Files.readString(path, charset)
        _lineSeparator = LineSeparator.detect(raw)
        val normalized = LineSeparator.normalizeToLf(raw)
        _savedText = normalized
        undoStack.clear()
        redoStack.clear()
        applyTextInternal(normalized)
        return true
    }

    private fun applyTextInternal(normalized: String) {
        lineOffsets = LineOffsetsImpl.create(normalized)
        _text.value = normalized
        _isDirty.value = (normalized != _savedText)
        _canUndo.value = undoStack.isNotEmpty()
        _canRedo.value = redoStack.isNotEmpty()
    }

    companion object {
        private const val MAX_UNDO_HISTORY = 200

        fun fromFile(path: Path, charset: Charset = StandardCharsets.UTF_8, forceReadOnly: Boolean = false): DiffDocument {
            val raw = if (path.exists()) Files.readString(path, charset) else ""
            val separator = LineSeparator.detect(raw)
            val writable = path.exists() && path.isWritable() && !forceReadOnly
            return DiffDocument(
                initialText = raw,
                filePath = path,
                title = path.toAbsolutePath().toString(),
                initialLineSeparator = separator,
                charset = charset,
                isReadOnly = !writable
            )
        }
    }
}
