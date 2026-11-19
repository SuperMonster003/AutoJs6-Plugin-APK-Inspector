package io.github.supermonster003.autojs6.plugin.apkinspector

import androidx.annotation.ColorInt
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.isSpecified
import androidx.compose.ui.unit.sp

/** The prefix of the test tags of this page (`<prefix>.releaseHistory.list` and so on). */
internal const val RELEASE_HISTORY_TAG_PREFIX = "apkInspector.releaseHistory"

/** The colors of the release history page, taken from the plugin's own palette (see the activity). */
internal data class ReleaseHistoryPalette(
    val dark: Boolean,
    @param:ColorInt val primary: Int,
    @param:ColorInt val primaryContainer: Int,
    @param:ColorInt val background: Int,
    @param:ColorInt val surface: Int,
    @param:ColorInt val onSurface: Int,
    @param:ColorInt val onSurfaceVariant: Int,
    @param:ColorInt val outlineVariant: Int,
)

/** The minimal Material 3 theme of the release history page: the plugin's palette mapped onto the M3 roles. */
@Composable
internal fun PluginReleaseHistoryTheme(palette: ReleaseHistoryPalette, content: @Composable () -> Unit) {
    val base = if (palette.dark) darkColorScheme() else lightColorScheme()
    val colors = base.copy(
        primary = Color(palette.primary),
        primaryContainer = Color(palette.primaryContainer),
        background = Color(palette.background),
        onBackground = Color(palette.onSurface),
        surface = Color(palette.surface),
        onSurface = Color(palette.onSurface),
        onSurfaceVariant = Color(palette.onSurfaceVariant),
        outlineVariant = Color(palette.outlineVariant),
    )
    val type = Typography().let { t ->
        t.copy(
            titleLarge = t.titleLarge.copy(fontSize = 20.sp, lineHeight = 28.sp, fontWeight = FontWeight.Medium),
            titleMedium = t.titleMedium.copy(fontSize = 16.sp, lineHeight = 24.sp, fontWeight = FontWeight.Medium),
            bodyLarge = t.bodyLarge.copy(fontSize = 16.sp, lineHeight = 24.sp),
            bodyMedium = t.bodyMedium.copy(fontSize = 14.sp, lineHeight = 20.sp),
            bodySmall = t.bodySmall.copy(fontSize = 13.sp, lineHeight = 18.sp),
            labelMedium = t.labelMedium.copy(fontSize = 12.sp, lineHeight = 16.sp),
        )
    }
    MaterialTheme(colorScheme = colors, typography = type, content = content)
}

/** The versions shown expanded; saved as a list so the choice survives rotation and process death. */
private val ExpandedVersionsSaver = Saver<Set<String>, ArrayList<String>>(
    save = { ArrayList(it) },
    restore = { it.toSet() },
)

/**
 * Release history, following the M3 release history page of the official plugins
 * (`AUTOJS6_PLUGIN_RELEASE_HISTORY_AGENTS.md`): the title "Release history" with the plugin name as the subtitle and
 * one group per version. A collapsed version shows its date and how many items each category has; an expanded one
 * lists its items under each category, the category named once. The newest version starts expanded; the top bar
 * expands or collapses all of them.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ReleaseHistoryScreen(entries: List<ReleaseHistoryEntry>, onBack: () -> Unit) {
    var expanded by rememberSaveable(stateSaver = ExpandedVersionsSaver) {
        mutableStateOf(entries.firstOrNull()?.let { setOf(it.version) }.orEmpty())
    }
    val allExpanded = entries.isNotEmpty() && entries.all { it.version in expanded }
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        contentColor = MaterialTheme.colorScheme.onBackground,
        topBar = {
            TopAppBar(
                title = {
                    // Content direction: a Latin plugin name keeps its order in RTL locales.
                    Column {
                        Text(
                            text = stringResource(R.string.release_history),
                            style = MaterialTheme.typography.titleLarge.copy(textDirection = TextDirection.Content),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            text = stringResource(R.string.app_name),
                            style = MaterialTheme.typography.bodySmall.copy(textDirection = TextDirection.Content),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack, modifier = Modifier.testTag("$RELEASE_HISTORY_TAG_PREFIX.navigation")) {
                        Icon(
                            painter = painterResource(R.drawable.ic_arrow_back),
                            contentDescription = stringResource(R.string.action_navigate_up),
                        )
                    }
                },
                actions = {
                    if (entries.isNotEmpty()) {
                        IconButton(
                            onClick = {
                                expanded = if (allExpanded) emptySet() else entries.mapTo(mutableSetOf()) { it.version }
                            },
                            modifier = Modifier.testTag("$RELEASE_HISTORY_TAG_PREFIX.toggleAll"),
                        ) {
                            Icon(
                                painter = painterResource(
                                    if (allExpanded) R.drawable.ic_unfold_less_24 else R.drawable.ic_unfold_more_24,
                                ),
                                contentDescription = stringResource(
                                    if (allExpanded) R.string.release_history_collapse_all else R.string.release_history_expand_all,
                                ),
                            )
                        }
                    }
                },
                modifier = Modifier.testTag("$RELEASE_HISTORY_TAG_PREFIX.topBar"),
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    scrolledContainerColor = MaterialTheme.colorScheme.surface,
                    navigationIconContentColor = MaterialTheme.colorScheme.onSurface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface,
                    actionIconContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                ),
            )
        },
    ) { padding ->
        if (entries.isEmpty()) {
            ReleaseHistoryEmptyState(Modifier.fillMaxSize().padding(padding))
            return@Scaffold
        }
        LazyColumn(modifier = Modifier.fillMaxSize().padding(padding).testTag("$RELEASE_HISTORY_TAG_PREFIX.list")) {
            itemsIndexed(entries, key = { _, entry -> entry.version }) { index, entry ->
                if (index > 0) {
                    HorizontalDivider(
                        modifier = Modifier.padding(horizontal = ReleaseHistoryDefaults.horizontalPadding),
                        thickness = 1.dp,
                        color = MaterialTheme.colorScheme.outlineVariant,
                    )
                }
                val isExpanded = entry.version in expanded
                ReleaseEntry(
                    entry = entry,
                    expanded = isExpanded,
                    onToggle = { expanded = if (isExpanded) expanded - entry.version else expanded + entry.version },
                )
            }
        }
    }
}

/** Dimensions of the release history page (`AUTOJS6_PLUGIN_RELEASE_HISTORY_AGENTS.md`, layout). */
internal object ReleaseHistoryDefaults {
    val horizontalPadding = 24.dp
    val headerEndPadding = 16.dp
    val verticalPadding = 16.dp
    val bulletSlot = 20.dp
    val bulletSize = 5.dp
    val bulletStart = 6.dp
    val groupSpacing = 14.dp
    val itemSpacing = 6.dp
}

/** No candidate changelog holds a release: a warning icon and one line, 48 dp below the top bar. */
@Composable
private fun ReleaseHistoryEmptyState(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.padding(horizontal = ReleaseHistoryDefaults.horizontalPadding)
            .testTag("$RELEASE_HISTORY_TAG_PREFIX.emptyState"),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(48.dp))
        Icon(
            painter = painterResource(R.drawable.ic_warning_24),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(48.dp),
        )
        Spacer(Modifier.height(16.dp))
        Text(
            text = stringResource(R.string.release_history_load_failed),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ReleaseEntry(entry: ReleaseHistoryEntry, expanded: Boolean, onToggle: () -> Unit) {
    val state = stringResource(if (expanded) R.string.release_history_expanded else R.string.release_history_collapsed)
    val chevronRotation by animateFloatAsState(if (expanded) 180f else 0f, label = "chevron")
    Column(modifier = Modifier.fillMaxWidth()) {
        // The header is the one focus stop of a release (Tab walks the history, Enter expands or collapses it) and
        // reads version, date, counts and state in one go.
        Column(
            modifier = Modifier
                .testTag("$RELEASE_HISTORY_TAG_PREFIX.entry.${entry.version}")
                .fillMaxWidth()
                .clickable(role = Role.Button, onClick = onToggle)
                .semantics { stateDescription = state }
                .padding(
                    start = ReleaseHistoryDefaults.horizontalPadding,
                    end = ReleaseHistoryDefaults.headerEndPadding,
                    top = ReleaseHistoryDefaults.verticalPadding,
                    bottom = if (expanded) 0.dp else ReleaseHistoryDefaults.verticalPadding,
                ),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = entry.version,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f),
                )
                if (entry.date != null) {
                    Text(
                        text = entry.date,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(Modifier.width(8.dp))
                Icon(
                    painter = painterResource(R.drawable.ic_expand_more_24),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(24.dp).rotate(chevronRotation),
                )
            }
            val counts = entry.categoryCounts
            if (!expanded && counts.isNotEmpty()) {
                FlowRow(
                    modifier = Modifier.padding(top = 10.dp).testTag("$RELEASE_HISTORY_TAG_PREFIX.counts.${entry.version}"),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    counts.forEach { (tag, count) -> CategoryChip(tag, count) }
                }
            }
        }
        AnimatedVisibility(visible = expanded, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("$RELEASE_HISTORY_TAG_PREFIX.body.${entry.version}")
                    // A screen reader reads the items of a release in one go after its header.
                    .semantics(mergeDescendants = true) {}
                    .padding(
                        start = ReleaseHistoryDefaults.horizontalPadding,
                        end = ReleaseHistoryDefaults.horizontalPadding,
                        bottom = ReleaseHistoryDefaults.verticalPadding,
                    ),
            ) {
                entry.groups.forEachIndexed { index, group ->
                    val top = if (index == 0) 10.dp else ReleaseHistoryDefaults.groupSpacing
                    if (group.tag != null) CategoryChip(group.tag, count = null, modifier = Modifier.padding(top = top))
                    group.items.forEachIndexed { itemIndex, text ->
                        val itemTop = if (group.tag == null && itemIndex == 0) top else ReleaseHistoryDefaults.itemSpacing
                        BulletItem(text, Modifier.padding(top = itemTop))
                    }
                }
            }
        }
    }
}

/** A category label (primary container, 10 dp corners), with the item count in the collapsed summary. */
@Composable
private fun CategoryChip(tag: String, count: Int?, modifier: Modifier = Modifier) {
    val color = MaterialTheme.colorScheme.primary
    Row(
        modifier = modifier
            .background(MaterialTheme.colorScheme.primaryContainer, RoundedCornerShape(10.dp))
            .padding(horizontal = 8.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text = tag, style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Medium), color = color)
        if (count != null) {
            Spacer(Modifier.width(4.dp))
            Text(text = count.toString(), style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold), color = color)
        }
    }
}

/**
 * One item: a drawn dot (not a font glyph, so it looks the same with every font) centered on the first line, the text
 * beside it; wrapped lines align with the text, not with the dot.
 */
@Composable
private fun BulletItem(text: String, modifier: Modifier = Modifier) {
    val style = MaterialTheme.typography.bodyMedium
    val firstLine: Dp = with(LocalDensity.current) { if (style.lineHeight.isSpecified) style.lineHeight.toDp() else 20.dp }
    val dotColor = MaterialTheme.colorScheme.onSurfaceVariant
    Row(modifier = modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
        Box(modifier = Modifier.width(ReleaseHistoryDefaults.bulletSlot).height(firstLine), contentAlignment = Alignment.CenterStart) {
            Box(
                modifier = Modifier
                    .padding(start = ReleaseHistoryDefaults.bulletStart)
                    .size(ReleaseHistoryDefaults.bulletSize)
                    .background(dotColor, CircleShape),
            )
        }
        Text(text = text, style = style, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.weight(1f))
    }
}
