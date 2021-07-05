package com.doool.viewpager

import android.view.View
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.LayoutScopeMarker
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.GraphicsLayerScope
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.unit.Constraints
import com.doool.viewpager.transformers.DefaultTransformer
import kotlinx.coroutines.launch

@Composable
fun rememberLazyViewPagerState(
    currentPage: Int = 0,
    pagerTransformer: ViewPagerTransformer
): ViewPagerState {
    return remember(pagerTransformer) {
        ViewPagerState(currentPage, pagerTransformer)
    }
}

interface ViewPagerTransformer {
    fun transformPage(view: PageModifier, position: Float)
}

@Stable
class ViewPagerState(
    var currentPage: Int = 0,
    private val pagerTransformer: ViewPagerTransformer
) {
    var dragOffset = Animatable(0f)

    var itemSize: Int = 0
    var itemCount = 0

    fun calculate(index: Int): Float {
        return (index + dragOffset.value / itemSize)
    }

    fun transformPage(page: PageModifier, index: Int) {
        pagerTransformer.transformPage(page, calculate(index))
    }

    suspend fun initOffset() {
        dragOffset.snapTo(itemSize.toFloat() * currentPage)
    }

    suspend fun updateOffset(offset: Float) {
        dragOffset.snapTo(dragOffset.value + offset)
    }

    suspend fun updatePage() {
        val searchRange = if (calculate(currentPage) < 0f)
            (currentPage until itemCount)
        else
            (0..currentPage)

        searchRange.forEach { index ->
            if (calculate(index) in -0.5f..0.5f) {
                currentPage = index

                dragOffset.animateTo(-index * itemSize.toFloat())
                return
            }
        }

        dragOffset.animateTo(-currentPage * itemSize.toFloat())
    }
}

enum class ViewPagerOrientation {
    Horizontal, Vertical
}

@LayoutScopeMarker
interface ViewPagerScope {
    fun item(content: @Composable ViewPagerItemScope.() -> Unit)
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
    override val itemCount: Int get() = list.size

    override fun item(content: @Composable ViewPagerItemScope.() -> Unit) {
        list.add(content)
    }

    override fun getContent(index: Int): @Composable ViewPagerItemScope.() -> Unit {
        return list.getOrNull(index) ?: {}
    }
}

private class ViewPagerItemScopeImpl(val index: Int, val pagerState: ViewPagerState) :
    ViewPagerItemScope {
    override fun getPagePosition(): Float {
        return pagerState.calculate(index)
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

@Composable
fun ViewPager(
    modifier: Modifier = Modifier,
    initPage: Int = 0,
    orientation: ViewPagerOrientation,
    transformer: ViewPagerTransformer = DefaultTransformer(),
    pageScale: Float = 0.5f,
    content: ViewPagerScope.() -> Unit
) {
    val viewPagerState = rememberLazyViewPagerState(initPage, transformer)
    val viewPagerItemProvider by rememberStateOfItemsProvider(content)

    val coroutineScope = rememberCoroutineScope()

    viewPagerState.itemCount = viewPagerItemProvider.itemCount

    val draggableState = rememberDraggableState {
        coroutineScope.launch {
            viewPagerState.updateOffset(it)
        }
    }

    val draggableModifier = Modifier
        .draggable(draggableState,
            orientation = if (orientation == ViewPagerOrientation.Vertical)
                Orientation.Vertical else
                Orientation.Horizontal,
            onDragStopped = {
                viewPagerState.updatePage()
            }
        )

    Box(
        modifier = modifier
            .composed { draggableModifier }
            .fillMaxSize()
    ) {
        Layout(modifier = Modifier.fillMaxSize(), content = {
            (0 until viewPagerItemProvider.itemCount).forEachIndexed { index, item ->
                viewPagerItemProvider.getContent(index)
                    .invoke(ViewPagerItemScopeImpl(index, viewPagerState))
            }
        }) { measurables, constraints ->
            val scaledConstraints = constraints.scale(pageScale)

            val pageInfoList =
                measurables.map { Pair(it.measure(scaledConstraints), PageModifier()) }

            viewPagerState.itemSize =
                if (orientation == ViewPagerOrientation.Horizontal) scaledConstraints.maxWidth
                else scaledConstraints.maxHeight

            layout(scaledConstraints.maxWidth, scaledConstraints.maxHeight) {
                pageInfoList.forEachIndexed { index, (placeable, modifier) ->
                    var offsetX = (scaledConstraints.maxWidth - placeable.width) / 2
                    var offsetY = (scaledConstraints.maxHeight - placeable.height) / 2

                    val pageOffset = viewPagerState.calculate(index)
                    when (orientation) {
                        ViewPagerOrientation.Horizontal -> offsetX += (placeable.width * pageOffset).toInt()
                        ViewPagerOrientation.Vertical -> offsetY += (placeable.height * pageOffset).toInt()
                    }

                    if (modifier.visibility == View.VISIBLE) {
                        placeable.placeWithLayer(offsetX, offsetY) {
                            viewPagerState.transformPage(
                                modifier.apply {
                                    width = placeable.width
                                    height = placeable.height
                                    layerScope = this@placeWithLayer
                                },
                                index
                            )
                        }
                    }
                }
            }
        }
    }

    LaunchedEffect(viewPagerState) {
        viewPagerState.initOffset()
    }
}

fun Constraints.scale(scale: Float) = Constraints(
    minWidth = (minWidth * scale).toInt(),
    maxWidth = (maxWidth * scale).toInt(),
    minHeight = (minHeight * scale).toInt(),
    maxHeight = (maxHeight * scale).toInt()
)


class PageModifier(
) {
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