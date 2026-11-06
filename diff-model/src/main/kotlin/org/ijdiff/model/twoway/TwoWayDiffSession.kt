package org.ijdiff.model.twoway

import com.intellij.diff.comparison.ComparisonManager
import com.intellij.diff.comparison.ComparisonPolicy
import com.intellij.diff.comparison.InnerFragmentsPolicy
import com.intellij.diff.fragments.LineFragment
import com.intellij.diff.util.CancellationChecker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import org.ijdiff.core.LineNumberConvertor
import org.ijdiff.core.UnifiedFragmentBuilder
import org.ijdiff.model.document.DiffDocument

enum class HighlightPolicy(val innerPolicy: InnerFragmentsPolicy, val displayName: String) {
    BY_WORD(InnerFragmentsPolicy.WORDS, "Highlight Words"),
    BY_CHAR(InnerFragmentsPolicy.CHARS, "Highlight Characters"),
    BY_LINE(InnerFragmentsPolicy.NONE, "Highlight Lines"),
    DO_NOT_HIGHLIGHT(InnerFragmentsPolicy.NONE, "Do Not Highlight")
}

enum class DiffViewMode(val displayName: String) {
    SIDE_BY_SIDE("Side-by-Side Viewer"),
    UNIFIED("Unified (Oneside) Viewer")
}

enum class ChunkDirection {
    LEFT_TO_RIGHT, // >> (Apply left chunk into right document)
    RIGHT_TO_LEFT  // << (Apply right chunk into left document)
}

data class TwoWayDiffState(
    val leftText: String,
    val rightText: String,
    val fragments: List<LineFragment>,
    val lineConvertor: LineNumberConvertor,
    val unifiedResult: UnifiedFragmentBuilder.UnifiedDiffResult,
    val currentFragmentIndex: Int
)

/**
 * UI-agnostic session controller for 2-way Side-by-Side and Unified text comparisons.
 * Supports live re-diffing, policy switching, chunk apply/append (`>>` / `<<`), and explicit save.
 */
class TwoWayDiffSession(
    val leftDocument: DiffDocument,
    val rightDocument: DiffDocument,
    initialComparisonPolicy: ComparisonPolicy = ComparisonPolicy.DEFAULT,
    initialHighlightPolicy: HighlightPolicy = HighlightPolicy.BY_WORD,
    initialViewMode: DiffViewMode = DiffViewMode.SIDE_BY_SIDE,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
) {
    private val comparisonManager = ComparisonManager.getInstance()

    private val _comparisonPolicy = MutableStateFlow(initialComparisonPolicy)
    val comparisonPolicy: StateFlow<ComparisonPolicy> = _comparisonPolicy.asStateFlow()

    private val _highlightPolicy = MutableStateFlow(initialHighlightPolicy)
    val highlightPolicy: StateFlow<HighlightPolicy> = _highlightPolicy.asStateFlow()

    private val _viewMode = MutableStateFlow(initialViewMode)
    val viewMode: StateFlow<DiffViewMode> = _viewMode.asStateFlow()

    private val _state = MutableStateFlow(computeDiffSync(0))
    val state: StateFlow<TwoWayDiffState> = _state.asStateFlow()

    fun setComparisonPolicy(policy: ComparisonPolicy) {
        if (_comparisonPolicy.value == policy) return
        _comparisonPolicy.value = policy
        recomputeNow()
    }

    fun setHighlightPolicy(policy: HighlightPolicy) {
        if (_highlightPolicy.value == policy) return
        _highlightPolicy.value = policy
        recomputeNow()
    }

    fun setViewMode(mode: DiffViewMode) {
        _viewMode.value = mode
    }

    fun hasUnsavedChanges(): Boolean = leftDocument.isDirty.value || rightDocument.isDirty.value

    fun saveAll(): Boolean {
        var ok = true
        if (leftDocument.isDirty.value) {
            ok = leftDocument.save() && ok
        }
        if (rightDocument.isDirty.value) {
            ok = rightDocument.save() && ok
        }
        return ok
    }

    fun recomputeNow() {
        val currentIdx = _state.value.currentFragmentIndex
        _state.value = computeDiffSync(currentIdx)
    }

    suspend fun recomputeAsync() = withContext(Dispatchers.Default) {
        val currentIdx = _state.value.currentFragmentIndex
        _state.value = computeDiffSync(currentIdx)
    }

    /**
     * Replaces the target range of [fragment] in the destination document with the source range of [fragment]
     * from the origin document (`>>` or `<<` gutter action in IntelliJ).
     */
    fun replaceChunk(fragment: LineFragment, direction: ChunkDirection) {
        when (direction) {
            ChunkDirection.LEFT_TO_RIGHT -> {
                if (rightDocument.isReadOnly) return
                applyLineRangeReplacement(
                    sourceDoc = leftDocument,
                    sourceStartLine = fragment.startLine1,
                    sourceEndLine = fragment.endLine1,
                    targetDoc = rightDocument,
                    targetStartLine = fragment.startLine2,
                    targetEndLine = fragment.endLine2
                )
            }
            ChunkDirection.RIGHT_TO_LEFT -> {
                if (leftDocument.isReadOnly) return
                applyLineRangeReplacement(
                    sourceDoc = rightDocument,
                    sourceStartLine = fragment.startLine2,
                    sourceEndLine = fragment.endLine2,
                    targetDoc = leftDocument,
                    targetStartLine = fragment.startLine1,
                    targetEndLine = fragment.endLine1
                )
            }
        }
        recomputeNow()
    }

    /**
     * Appends the source lines of [fragment] after the target lines of [fragment] without deleting the target lines.
     */
    fun appendChunk(fragment: LineFragment, direction: ChunkDirection) {
        when (direction) {
            ChunkDirection.LEFT_TO_RIGHT -> {
                if (rightDocument.isReadOnly) return
                if (fragment.startLine1 == fragment.endLine1) return
                applyLineRangeReplacement(
                    sourceDoc = leftDocument,
                    sourceStartLine = fragment.startLine1,
                    sourceEndLine = fragment.endLine1,
                    targetDoc = rightDocument,
                    targetStartLine = fragment.endLine2,
                    targetEndLine = fragment.endLine2
                )
            }
            ChunkDirection.RIGHT_TO_LEFT -> {
                if (leftDocument.isReadOnly) return
                if (fragment.startLine2 == fragment.endLine2) return
                applyLineRangeReplacement(
                    sourceDoc = rightDocument,
                    sourceStartLine = fragment.startLine2,
                    sourceEndLine = fragment.endLine2,
                    targetDoc = leftDocument,
                    targetStartLine = fragment.endLine1,
                    targetEndLine = fragment.endLine1
                )
            }
        }
        recomputeNow()
    }

    fun nextDifference(): LineFragment? {
        val current = _state.value
        if (current.fragments.isEmpty()) return null
        val nextIdx = (current.currentFragmentIndex + 1).coerceAtMost(current.fragments.lastIndex)
        _state.value = current.copy(currentFragmentIndex = nextIdx)
        return current.fragments[nextIdx]
    }

    fun prevDifference(): LineFragment? {
        val current = _state.value
        if (current.fragments.isEmpty()) return null
        val prevIdx = (current.currentFragmentIndex - 1).coerceAtLeast(0)
        _state.value = current.copy(currentFragmentIndex = prevIdx)
        return current.fragments[prevIdx]
    }

    fun selectFragment(index: Int): LineFragment? {
        val current = _state.value
        if (index !in current.fragments.indices) return null
        _state.value = current.copy(currentFragmentIndex = index)
        return current.fragments[index]
    }

    /**
     * Ported from `DiffUtil.applyModification` in `intellij-community/platform/diff-impl`.
     * Handles all line-boundary cases cleanly (insertion, deletion, modification, and EOF newline handling).
     */
    private fun applyLineRangeReplacement(
        sourceDoc: DiffDocument,
        sourceStartLine: Int,
        sourceEndLine: Int,
        targetDoc: DiffDocument,
        targetStartLine: Int,
        targetEndLine: Int
    ) {
        val sourceLines = (sourceStartLine until sourceEndLine).map { sourceDoc.getLineText(it, includeNewline = false) }
        val targetOffsets = targetDoc.lineOffsets
        val targetLineCount = targetOffsets.lineCount
        val targetLines = (0 until targetLineCount).map { targetDoc.getLineText(it, includeNewline = false) }.toMutableList()

        // If target document was completely empty (1 empty line "") and we're inserting at line 0..0 or 0..1
        val newLines = mutableListOf<String>()
        newLines.addAll(targetLines.subList(0, targetStartLine.coerceIn(0, targetLines.size)))
        newLines.addAll(sourceLines)
        if (targetEndLine < targetLines.size) {
            newLines.addAll(targetLines.subList(targetEndLine, targetLines.size))
        }
        targetDoc.setText(newLines.joinToString("\n"), recordUndo = true)
    }

    private fun computeDiffSync(preferredFragmentIndex: Int): TwoWayDiffState {
        val text1 = leftDocument.text.value
        val text2 = rightDocument.text.value
        val offsets1 = leftDocument.lineOffsets
        val offsets2 = rightDocument.lineOffsets
        val policy = _comparisonPolicy.value
        val highlight = _highlightPolicy.value

        val fragments = if (highlight == HighlightPolicy.DO_NOT_HIGHLIGHT) {
            emptyList()
        } else {
            val baseFragments = comparisonManager.compareLines(
                text1, text2, ComparisonPolicy.DEFAULT.takeIf { policy == ComparisonPolicy.DEFAULT } ?: policy,
                CancellationChecker.EMPTY
            )
            comparisonManager.compareLinesInner(
                text1,
                text2,
                baseFragments,
                policy,
                highlight.innerPolicy,
                CancellationChecker.EMPTY
            )
        }

        val convertor = LineNumberConvertor.fromLineFragments(fragments, offsets1.lineCount, offsets2.lineCount)
        val unified = UnifiedFragmentBuilder(
            text1 = text1,
            text2 = text2,
            lineOffsets1 = offsets1,
            lineOffsets2 = offsets2,
            fragments = fragments
        ).exec()

        val clampedIdx = if (fragments.isEmpty()) -1 else preferredFragmentIndex.coerceIn(0, fragments.lastIndex)

        return TwoWayDiffState(
            leftText = text1,
            rightText = text2,
            fragments = fragments,
            lineConvertor = convertor,
            unifiedResult = unified,
            currentFragmentIndex = clampedIdx
        )
    }
}
