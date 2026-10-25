package dev.gdk.bumblebee.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.SubcomposeLayout
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import dev.gdk.bumblebee.ui.theme.Lime
import dev.gdk.bumblebee.ui.theme.ShapePill
import dev.gdk.bumblebee.ui.theme.Stroke

/** Horizontal pager of equal-height cards with a dot indicator (§4.22). */
@Composable
fun InsightPager(
    pageCount: Int,
    modifier: Modifier = Modifier,
    content: @Composable (page: Int) -> Unit,
) {
    val count = pageCount.coerceAtLeast(0)
    if (count == 0) return
    // A new count starts a fresh pager, so no page left over from a longer list asks for an index
    // that's gone.
    key(count) { PagerBody(count, modifier, content) }
}

@Composable
private fun PagerBody(
    count: Int,
    modifier: Modifier,
    content: @Composable (page: Int) -> Unit,
) {
    val pagerState = rememberPagerState { count }

    Column(modifier.fillMaxWidth()) {
        SubcomposeLayout(Modifier.fillMaxWidth()) { constraints ->
            val pageWidth = if (constraints.hasBoundedWidth) constraints.maxWidth else 0
            val pageMaxHeight = if (constraints.hasBoundedHeight) constraints.maxHeight else 4096
            val pageConstraints = Constraints(
                minWidth = pageWidth,
                maxWidth = if (pageWidth == 0) Constraints.Infinity else pageWidth,
                minHeight = 0,
                maxHeight = pageMaxHeight,
            )
            var maxHeight = 0
            for (page in 0 until count) {
                val measured = subcompose("measure-$page") {
                    Box(Modifier.fillMaxWidth()) { content(page) }
                }.map { it.measure(pageConstraints) }
                maxHeight = maxOf(maxHeight, measured.maxOfOrNull { it.height } ?: 0)
            }
            val pageHeight = maxHeight.toDp()
            val pagerConstraints = Constraints(
                minWidth = constraints.minWidth,
                maxWidth = constraints.maxWidth,
                minHeight = 0,
                maxHeight = Constraints.Infinity,
            )
            val pager = subcompose("pager") {
                HorizontalPager(
                    state = pagerState,
                    pageSpacing = 12.dp,
                    modifier = Modifier.fillMaxWidth(),
                ) { page ->
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height(pageHeight),
                    ) {
                        content(page)
                    }
                }
            }.first().measure(pagerConstraints)
            val width = pager.width
            val height = if (constraints.hasBoundedHeight) {
                pager.height.coerceIn(constraints.minHeight, constraints.maxHeight)
            } else {
                pager.height
            }
            layout(width, height) {
                pager.place(0, 0)
            }
        }
        Row(
            Modifier
                .fillMaxWidth()
                .padding(top = 12.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            repeat(count) { index ->
                val selected = pagerState.currentPage == index
                Box(
                    Modifier
                        .padding(horizontal = 4.dp)
                        .size(8.dp)
                        .background(if (selected) Lime else Stroke, ShapePill),
                )
            }
        }
    }
}
