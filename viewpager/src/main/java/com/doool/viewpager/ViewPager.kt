package com.doool.viewpager

import android.view.View
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.GraphicsLayerScope
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.ParentDataModifier
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import com.doool.viewpager.transformers.DefaultTransformer
import kotlinx.coroutines.launch

@Composable
fun rememberViewPagerState(
    currentPage: Int = 0,
    offscreenPageLimit: Int = -1
): ViewPagerState {
    return remember {
        ViewPagerState(currentPage, offscreenPageLimit)
    }
}

interface ViewPagerTransformer {
    fun transformPage(view: PageModifier, position: Float)
}

@Stable
class ViewPagerState(
    initPage: Int = 0,
    private var offscreenPageLimit: Int = -1
) {
    private var dragOffset = Animatable(0f)

    private var currentPage: Int = initPage
        set(value) {
            field = value
            updateOffScreenRange()
        }

    var pageCount: Int = 0
        set(value) {
            field = value
            updateOffScreenRange()
        }
    var pageSize: Int = 0

    var pageRange by mutableStateOf(IntRange(0, pageCount))

    private fun updateOffScreenRange() {
        pageRange = IntRange(
            Math.max(0, currentPage - offscreenPageLimit),
            Math.min(currentPage + offscreenPageLimit, pageCount)
        )
    }

    fun calculatePageOffset(index: Int): Float {
        return (index + dragOffset.value / pageSize)
    }

    suspend fun initOffset() {
        dragOffset.snapTo(pageSize.toFloat() * currentPage)
    }

    suspend fun updateOffset(offset: Float) {
        dragOffset.snapTo(dragOffset.value + offset)
    }

    suspend fun updatePage() {
        val searchRange = if (calculatePageOffset(currentPage) < 0f)
            (currentPage until pageCount)
        else
            (0..currentPage)

        searchRange.forEach { index ->
            if (calculatePageOffset(index) in -0.5f..0.5f) {
                currentPage = index
                dragOffset.animateTo(-index * pageSize.toFloat())
                return
            }
        }

        dragOffset.animateTo(-currentPage * pageSize.toFloat())
    }
}

@LayoutScopeMarker
interface ViewPagerScope {
    fun item(content: @Composable ViewPagerItemScope.() -> Unit)
    fun items(count: Int, content: @Composable ViewPagerItemScope.(index: Int) -> Unit)
    fun <T> items(items: List<T>, content: @Composable ViewPagerItemScope.(item: T) -> Unit)
}

@LayoutScopeMarker
interface ViewPagerItemScope {
    fun getPagePosition(): Float
}

interface ViewPagerItemProvider {
    val itemCount: Int

    fun getContent(index: Int): @Composable ViewPagerItemScope.() -> Unit
}

private class ViewPagerScopeImpl : ViewPagerScope, ViewPagerItemProvider {

    private val list: MutableList<(@Composable ViewPagerItemScope.() -> Unit)> = mutableListOf()
    override var itemCount: Int by mutableStateOf(0)

    override fun item(content: @Composable ViewPagerItemScope.() -> Unit) {
        list.add(content)
        updateCount()
    }

    override fun items(count: Int, content: @Composable ViewPagerItemScope.(index: Int) -> Unit) {
        (0 until count).forEach { index ->
            list.add { content(index) }
        }
        updateCount()
    }

    override fun <T> items(
        items: List<T>,
        content: @Composable ViewPagerItemScope.(item: T) -> Unit
    ) {
        items.forEach { item ->
            list.add { content(item) }
        }
        updateCount()
    }

    override fun getContent(index: Int): @Composable ViewPagerItemScope.() -> Unit {
        return list.getOrNull(index) ?: {}
    }

    private fun updateCount() {
        itemCount = list.size
    }
}

private class ViewPagerItemScopeImpl(val index: Int, val pagerState: ViewPagerState) :
    ViewPagerItemScope {
    override fun getPagePosition(): Float {
        return pagerState.calculatePageOffset(index)
    }
}

@Composable
private fun rememberStateOfItemsProvider(
    content: ViewPagerScope.() -> Unit
): State<ViewPagerItemProvider> {
    val latestContent = rememberUpdatedState(content)
    return remember {
        derivedStateOf { ViewPagerScopeImpl().apply(latestContent.value) }
    }
}

class PageData {
    var index: Int = -1
    val pageModifier = PageModifier()
}

internal class PageDataModifier(private val index: Int) : ParentDataModifier {
    override fun Density.modifyParentData(parentData: Any?): PageData {
        return ((parentData as? PageData) ?: PageData()).apply {
            index = this@PageDataModifier.index
        }
    }
}

enum class ViewPagerOrientation {
    Horizontal, Vertical
}

@Composable
fun ViewPager(
    modifier: Modifier = Modifier,
    initPage: Int = 0,
    pageScale: Float = 1f,
    offscreenPageLimit: Int = -1,
    orientation: ViewPagerOrientation,
    transformer: ViewPagerTransformer = DefaultTransformer(),
    state: ViewPagerState = rememberViewPagerState(initPage, offscreenPageLimit),
    content: ViewPagerScope.() -> Unit
) {
    val viewPagerItemProvider by rememberStateOfItemsProvider(content)

    val coroutineScope = rememberCoroutineScope()

    val draggableState = rememberDraggableState {
        coroutineScope.launch {
            state.updateOffset(it)
        }
    }

    val draggableModifier = Modifier
        .draggable(draggableState,
            orientation = if (orientation == ViewPagerOrientation.Vertical)
                Orientation.Vertical else
                Orientation.Horizontal,
            onDragStopped = {
                state.updatePage()
            }
        )

    state.pageCount = viewPagerItemProvider.itemCount

    Layout(
        modifier = modifier
            .composed { draggableModifier }
            .fillMaxSize(),
        content = {
            state.pageRange.forEach { index ->
                key(index) {
                    Box(modifier = modifier.then(PageDataModifier(index))) {
                        val scope = remember(index, state) {
                            ViewPagerItemScopeImpl(index, state)
                        }
                        viewPagerItemProvider.getContent(index).invoke(scope)
                    }
                }
            }
        }) { measurables, constraints ->
        val scaledConstraints = constraints.scale(pageScale)

        val pageInfoList = measurables.map { it.measure(scaledConstraints) }

        state.pageSize =
            if (orientation == ViewPagerOrientation.Horizontal) scaledConstraints.maxWidth
            else scaledConstraints.maxHeight

        layout(scaledConstraints.maxWidth, scaledConstraints.maxHeight) {
            pageInfoList.forEachIndexed { index, placeable ->
                val currentPageData = measurables[index].parentData as PageData

                val realIndex = currentPageData.index
                val pageModifier = currentPageData.pageModifier

                var offsetX = (scaledConstraints.maxWidth - placeable.width) / 2
                var offsetY = (scaledConstraints.maxHeight - placeable.height) / 2

                val pageOffset = state.calculatePageOffset(realIndex)
                when (orientation) {
                    ViewPagerOrientation.Horizontal -> offsetX += (placeable.width * pageOffset).toInt()
                    ViewPagerOrientation.Vertical -> offsetY += (placeable.height * pageOffset).toInt()
                }

                if (pageModifier.visibility == View.VISIBLE) {
                    placeable.placeWithLayer(offsetX, offsetY) {
                        transformer.transformPage(pageModifier.apply {
                            width = placeable.width
                            height = placeable.height
                            layerScope = this@placeWithLayer
                        }, pageOffset)
                    }
                }
            }
        }
    }

    LaunchedEffect(state) {
        state.initOffset()
    }
}

fun Constraints.scale(scale: Float) = Constraints(
    minWidth = (minWidth * scale).toInt(),
    maxWidth = (maxWidth * scale).toInt(),
    minHeight = (minHeight * scale).toInt(),
    maxHeight = (maxHeight * scale).toInt()
)


class PageModifier {
    lateinit var layerScope: GraphicsLayerScope

    var height: Int = 0
    var width: Int = 0

    var visibility: Int by mutableStateOf(View.VISIBLE)

    var scaleX: Float
        get() = layerScope.scaleX
        set(value) {
            layerScope.scaleX = value
        }
    var scaleY: Float
        get() = layerScope.scaleY
        set(value) {
            layerScope.scaleY = value
        }

    var translationX: Float
        get() = layerScope.translationX
        set(value) {
            layerScope.translationX = value
        }
    var translationY: Float
        get() = layerScope.translationY
        set(value) {
            layerScope.translationY = value
        }

    var rotationX: Float
        get() = layerScope.rotationX
        set(value) {
            layerScope.rotationX = value
        }
    var rotationY: Float
        get() = layerScope.rotationY
        set(value) {
            layerScope.rotationY = value
        }
    var rotation: Float
        get() = layerScope.rotationZ
        set(value) {
            layerScope.rotationZ = value
        }

    var cameraDistance: Float
        get() = layerScope.cameraDistance
        set(value) {
            layerScope.cameraDistance = value
        }

    var alpha: Float
        get() = layerScope.alpha
        set(value) {
            layerScope.alpha = value
        }

    var pivotX: Float
        get() = layerScope.transformOrigin.pivotFractionX * width
        set(value) {
            layerScope.transformOrigin =
                layerScope.transformOrigin.copy(pivotFractionX = value / width)
        }

    var pivotY: Float
        get() = layerScope.transformOrigin.pivotFractionY * height
        set(value) {
            layerScope.transformOrigin =
                layerScope.transformOrigin.copy(pivotFractionY = value / height)
        }

    var pivotFractionX: Float
        get() = layerScope.transformOrigin.pivotFractionX
        set(value) {
            layerScope.transformOrigin = layerScope.transformOrigin.copy(pivotFractionX = value)
        }

    var pivotFractionY: Float
        get() = layerScope.transformOrigin.pivotFractionY
        set(value) {
            layerScope.transformOrigin = layerScope.transformOrigin.copy(pivotFractionY = value)
        }
}