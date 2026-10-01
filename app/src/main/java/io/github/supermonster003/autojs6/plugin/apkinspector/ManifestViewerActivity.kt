package io.github.supermonster003.autojs6.plugin.apkinspector

import org.autojs.plugin.packagearchive.PackageInspectionLimits

import android.content.Context
import android.content.Intent
import android.graphics.Typeface
import android.os.Bundle
import android.text.Spannable
import android.text.SpannableString
import android.text.Spanned
import android.text.style.BackgroundColorSpan
import android.text.style.ForegroundColorSpan
import android.text.style.StyleSpan
import android.view.inputmethod.EditorInfo
import android.widget.TextView
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import androidx.core.widget.doAfterTextChanged
import com.google.android.material.color.MaterialColors
import io.github.supermonster003.autojs6.plugin.apkinspector.databinding.ActivityManifestViewerBinding
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.roundToInt

class ManifestViewerActivity : AppCompatActivity() {

    private lateinit var binding: ActivityManifestViewerBinding
    private lateinit var palette: SyntaxPalette
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var document: ManifestTextDocument? = null
    private var styledText: Spannable? = null
    private var searchResult = ManifestSearchResult(emptyList(), truncated = false)
    private val searchSpans = mutableListOf<Any>()
    private var searchJob: Job? = null
    private var searchGeneration = 0
    private var selectedMatch = -1
    private var restoredMatch = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        MaterialThemeController.applySystemBars(this)
        binding = ActivityManifestViewerBinding.inflate(layoutInflater)
        setContentView(binding.root)
        configureResponsiveTitle()
        palette = resolvePalette()
        setupToolbar()
        setupSearch(savedInstanceState)

        val manifestFile = PackageCacheStager.resolveManifestFile(
            this,
            intent.getStringExtra(EXTRA_MANIFEST_PATH),
        )
        if (manifestFile == null || manifestFile.length() > MAX_MANIFEST_BYTES) {
            finish()
            return
        }
        loadManifest(manifestFile)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putBoolean(STATE_SEARCH_VISIBLE, binding.manifestSearchPanel.isVisible)
        outState.putString(STATE_SEARCH_QUERY, binding.manifestSearchInput.text?.toString().orEmpty())
        outState.putInt(STATE_SELECTED_MATCH, selectedMatch.coerceAtLeast(0))
    }

    override fun onDestroy() {
        searchJob?.cancel()
        scope.cancel()
        super.onDestroy()
    }

    private fun setupToolbar() {
        binding.toolbar.setNavigationOnClickListener { finish() }
        binding.toolbar.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                R.id.action_find_manifest -> {
                    showSearch(requestKeyboard = shouldRequestSearchKeyboard())
                    true
                }

                else -> false
            }
        }
    }

    private fun configureResponsiveTitle() {
        val configuration = resources.configuration
        val metrics = resources.displayMetrics
        val screenWidthDp = configuration.screenWidthDp.takeIf { width -> width > 0 }
            ?: (metrics.widthPixels / metrics.density).roundToInt()
        val compact = ResponsiveLayoutPolicy.shouldUseCompactHeader(
            screenWidthDp = screenWidthDp,
            fontScale = configuration.fontScale,
        )
        binding.toolbar.setTitle(
            if (compact) R.string.manifest_compact_title else R.string.manifest_title,
        )
    }

    private fun shouldRequestSearchKeyboard(): Boolean {
        val configuration = resources.configuration
        val metrics = resources.displayMetrics
        val screenHeightDp = configuration.screenHeightDp.takeIf { height -> height > 0 }
            ?: (metrics.heightPixels / metrics.density).roundToInt()
        return ResponsiveLayoutPolicy.shouldRequestManifestSearchKeyboard(
            screenHeightDp = screenHeightDp,
            fontScale = configuration.fontScale,
        )
    }

    private fun setupSearch(savedInstanceState: Bundle?) {
        ViewCompat.setAccessibilityPaneTitle(
            binding.manifestSearchPanel,
            getString(R.string.action_find_manifest),
        )
        binding.manifestSearchInput.doAfterTextChanged { editable ->
            scheduleSearch(editable?.toString().orEmpty())
        }
        binding.manifestSearchInput.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                hideKeyboard()
                moveMatch(1)
                true
            } else {
                false
            }
        }
        binding.manifestSearchInput.setOnClickListener {
            if (!binding.manifestSearchInput.showSoftInputOnFocus) {
                binding.manifestSearchInput.showSoftInputOnFocus = true
                WindowCompat.getInsetsController(window, binding.root)
                    .show(WindowInsetsCompat.Type.ime())
            }
        }
        binding.previousManifestMatch.setOnClickListener { moveMatch(-1) }
        binding.nextManifestMatch.setOnClickListener { moveMatch(1) }
        binding.closeManifestSearch.setOnClickListener { hideSearch() }
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (binding.manifestSearchPanel.isVisible) {
                    hideSearch()
                } else {
                    isEnabled = false
                    onBackPressedDispatcher.onBackPressed()
                }
            }
        })
        updateSearchControls()

        restoredMatch = savedInstanceState?.getInt(STATE_SELECTED_MATCH, 0) ?: 0
        if (savedInstanceState?.getBoolean(STATE_SEARCH_VISIBLE) == true) {
            showSearch(requestKeyboard = false)
            val query = savedInstanceState.getString(STATE_SEARCH_QUERY).orEmpty()
            binding.manifestSearchInput.setText(query)
            binding.manifestSearchInput.setSelection(query.length)
        }
    }

    private fun loadManifest(manifestFile: File) {
        scope.launch {
            val source = withContext(Dispatchers.IO) {
                runCatching { manifestFile.readText(Charsets.UTF_8) }.getOrNull()
            }
            if (source == null) {
                finish()
                return@launch
            }
            val loadedDocument = withContext(Dispatchers.Default) {
                ManifestTextDocument.create(source)
            }
            val loadedText = withContext(Dispatchers.Default) {
                createStyledText(loadedDocument)
            }
            document = loadedDocument
            binding.manifestText.setText(loadedText, TextView.BufferType.SPANNABLE)
            styledText = binding.manifestText.text as Spannable
            scheduleSearch(binding.manifestSearchInput.text?.toString().orEmpty(), SEARCH_DEBOUNCE_NONE)
        }
    }

    private fun createStyledText(document: ManifestTextDocument): SpannableString {
        val output = SpannableString(document.displayText)
        document.syntaxRanges.forEach { syntax ->
            val color = when (syntax.kind) {
                XmlSyntaxKind.TAG -> palette.tag
                XmlSyntaxKind.ATTRIBUTE -> palette.attribute
                XmlSyntaxKind.VALUE, XmlSyntaxKind.CDATA -> palette.value
                XmlSyntaxKind.COMMENT, XmlSyntaxKind.DECLARATION -> palette.comment
            }
            output.setSpan(
                ForegroundColorSpan(color),
                syntax.range.start,
                syntax.range.endExclusive,
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE,
            )
            if (syntax.kind == XmlSyntaxKind.COMMENT) {
                output.setSpan(
                    StyleSpan(Typeface.ITALIC),
                    syntax.range.start,
                    syntax.range.endExclusive,
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE,
                )
            }
        }
        document.lineNumberRanges.take(MAX_STYLED_LINE_NUMBERS).forEach { range ->
            output.setSpan(
                ForegroundColorSpan(palette.lineNumber),
                range.start,
                range.endExclusive,
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE,
            )
        }
        return output
    }

    private fun resolvePalette(): SyntaxPalette = SyntaxPalette(
        tag = MaterialColors.getColor(
            this,
            androidx.appcompat.R.attr.colorPrimary,
            FALLBACK_PRIMARY,
        ),
        attribute = MaterialColors.getColor(
            this,
            com.google.android.material.R.attr.colorTertiary,
            FALLBACK_TERTIARY,
        ),
        value = MaterialColors.getColor(
            this,
            com.google.android.material.R.attr.colorSecondary,
            FALLBACK_SECONDARY,
        ),
        comment = MaterialColors.getColor(
            this,
            com.google.android.material.R.attr.colorOnSurfaceVariant,
            FALLBACK_ON_SURFACE_VARIANT,
        ),
        lineNumber = MaterialColors.getColor(
            this,
            com.google.android.material.R.attr.colorOnSurfaceVariant,
            FALLBACK_ON_SURFACE_VARIANT,
        ),
        match = MaterialColors.getColor(
            this,
            com.google.android.material.R.attr.colorSecondaryContainer,
            FALLBACK_SECONDARY_CONTAINER,
        ),
        currentMatch = MaterialColors.getColor(
            this,
            com.google.android.material.R.attr.colorTertiaryContainer,
            FALLBACK_TERTIARY_CONTAINER,
        ),
        onCurrentMatch = MaterialColors.getColor(
            this,
            com.google.android.material.R.attr.colorOnTertiaryContainer,
            FALLBACK_ON_TERTIARY_CONTAINER,
        ),
    )

    private fun showSearch(requestKeyboard: Boolean) {
        binding.manifestSearchPanel.isVisible = true
        binding.manifestSearchInput.showSoftInputOnFocus = requestKeyboard
        binding.manifestSearchInput.requestFocus()
        if (requestKeyboard) {
            WindowCompat.getInsetsController(window, binding.root).show(WindowInsetsCompat.Type.ime())
        } else {
            hideKeyboard()
        }
    }

    private fun hideSearch() {
        binding.manifestSearchInput.text?.clear()
        binding.manifestSearchPanel.isVisible = false
        hideKeyboard()
        clearSearchHighlights()
        searchResult = ManifestSearchResult(emptyList(), truncated = false)
        selectedMatch = -1
        updateSearchControls()
    }

    private fun hideKeyboard() {
        WindowCompat.getInsetsController(window, binding.root).hide(WindowInsetsCompat.Type.ime())
    }

    private fun scheduleSearch(query: String, debounceMillis: Long = SEARCH_DEBOUNCE_MILLIS) {
        searchJob?.cancel()
        searchGeneration += 1
        val generation = searchGeneration
        val loadedDocument = document
        if (query.isEmpty() || loadedDocument == null) {
            searchResult = ManifestSearchResult(emptyList(), truncated = false)
            selectedMatch = -1
            clearSearchHighlights()
            updateSearchControls()
            return
        }
        binding.manifestSearchStatus.isVisible = false
        setMatchNavigationEnabled(false)
        searchJob = scope.launch {
            if (debounceMillis > 0) {
                delay(debounceMillis)
            }
            val result = withContext(Dispatchers.Default) {
                ManifestTextSearch.find(loadedDocument.source, query)
            }
            if (generation != searchGeneration) {
                return@launch
            }
            searchResult = result
            selectedMatch = if (result.matches.isEmpty()) {
                -1
            } else {
                restoredMatch.coerceIn(0, result.matches.lastIndex)
            }
            restoredMatch = 0
            applySearchHighlights()
            updateSearchControls()
            scrollToCurrentMatch(smooth = false)
        }
    }

    private fun moveMatch(delta: Int) {
        val count = searchResult.matches.size
        if (count == 0) {
            return
        }
        selectedMatch = Math.floorMod(selectedMatch + delta, count)
        applySearchHighlights()
        updateSearchControls()
        scrollToCurrentMatch(smooth = true)
    }

    private fun applySearchHighlights() {
        val loadedDocument = document ?: return
        val output = styledText ?: return
        clearSearchHighlights()
        searchResult.matches.forEachIndexed { index, sourceRange ->
            val isCurrent = index == selectedMatch
            loadedDocument.displayRanges(sourceRange).forEach { displayRange ->
                val background = BackgroundColorSpan(
                    if (isCurrent) palette.currentMatch else palette.match,
                )
                output.setSpan(
                    background,
                    displayRange.start,
                    displayRange.endExclusive,
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE,
                )
                searchSpans += background
                if (isCurrent) {
                    val foreground = ForegroundColorSpan(palette.onCurrentMatch)
                    output.setSpan(
                        foreground,
                        displayRange.start,
                        displayRange.endExclusive,
                        Spanned.SPAN_EXCLUSIVE_EXCLUSIVE,
                    )
                    searchSpans += foreground
                }
            }
        }
        binding.manifestText.invalidate()
    }

    private fun clearSearchHighlights() {
        val output = styledText ?: return
        searchSpans.forEach(output::removeSpan)
        searchSpans.clear()
        binding.manifestText.invalidate()
    }

    private fun updateSearchControls() {
        val count = searchResult.matches.size
        binding.manifestSearchStatus.apply {
            isVisible = binding.manifestSearchInput.text?.isNotEmpty() == true
            text = when {
                count == 0 -> getString(R.string.manifest_search_no_matches)
                searchResult.truncated -> getString(
                    R.string.manifest_search_match_position_truncated,
                    selectedMatch + 1,
                    count + 1,
                )

                else -> getString(
                    R.string.manifest_search_match_position,
                    selectedMatch + 1,
                    count,
                )
            }
        }
        setMatchNavigationEnabled(count > 0)
    }

    private fun setMatchNavigationEnabled(enabled: Boolean) {
        binding.previousManifestMatch.isEnabled = enabled
        binding.previousManifestMatch.alpha = if (enabled) ENABLED_ALPHA else DISABLED_ALPHA
        binding.nextManifestMatch.isEnabled = enabled
        binding.nextManifestMatch.alpha = if (enabled) ENABLED_ALPHA else DISABLED_ALPHA
    }

    private fun scrollToCurrentMatch(smooth: Boolean) {
        val loadedDocument = document ?: return
        val match = searchResult.matches.getOrNull(selectedMatch) ?: return
        val displayOffset = loadedDocument.displayOffset(match.start)
        binding.manifestText.post {
            val layout = binding.manifestText.layout ?: return@post
            val line = layout.getLineForOffset(displayOffset.coerceAtMost(layout.text.length))
            val target = (layout.getLineTop(line) - binding.manifestScroll.height / 3).coerceAtLeast(0)
            if (smooth) {
                binding.manifestScroll.smoothScrollTo(0, target)
            } else {
                binding.manifestScroll.scrollTo(0, target)
            }
        }
    }

    private data class SyntaxPalette(
        val tag: Int,
        val attribute: Int,
        val value: Int,
        val comment: Int,
        val lineNumber: Int,
        val match: Int,
        val currentMatch: Int,
        val onCurrentMatch: Int,
    )

    companion object {
        private const val EXTRA_MANIFEST_PATH =
            "io.github.supermonster003.autojs6.plugin.apkinspector.extra.MANIFEST_PATH"
        private const val STATE_SEARCH_VISIBLE = "manifest_search_visible"
        private const val STATE_SEARCH_QUERY = "manifest_search_query"
        private const val STATE_SELECTED_MATCH = "manifest_selected_match"
        private const val MAX_MANIFEST_BYTES = 4L * PackageInspectionLimits.MANIFEST_BYTES
        private const val MAX_STYLED_LINE_NUMBERS = 20_000
        private const val SEARCH_DEBOUNCE_MILLIS = 150L
        private const val SEARCH_DEBOUNCE_NONE = 0L
        private const val ENABLED_ALPHA = 1.0f
        private const val DISABLED_ALPHA = 0.38f
        private val FALLBACK_PRIMARY = 0xFF8A4F00.toInt()
        private val FALLBACK_SECONDARY = 0xFF006874.toInt()
        private val FALLBACK_TERTIARY = 0xFF6A5D00.toInt()
        private val FALLBACK_ON_SURFACE_VARIANT = 0xFF51443A.toInt()
        private val FALLBACK_SECONDARY_CONTAINER = 0xFF9EEFFD.toInt()
        private val FALLBACK_TERTIARY_CONTAINER = 0xFFF5E56D.toInt()
        private val FALLBACK_ON_TERTIARY_CONTAINER = 0xFF201C00.toInt()

        internal fun createIntent(context: Context, path: String): Intent =
            Intent(context, ManifestViewerActivity::class.java).putExtra(EXTRA_MANIFEST_PATH, path)
    }
}
