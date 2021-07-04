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
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.GraphicsLayerScope
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.unit.Constraints
import com.doool.viewpager.transformers.Pager2_SpinnerTransformer
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
    val pagerTransformer: ViewPagerTransformer
) {
    lateinit var orientation: ViewPagerOrientation

    var viewSize: Size? = null
    var dragOffset = Animatable(0f)

    var itemCount = 0

    private fun getTargetBaseSize(): Float {
        return if (orientation == ViewPagerOrientation.Vertical) viewSize?.height ?: 0f
        else viewSize?.width ?: 0f
    }

    fun calculate(index: Int): Float {
        return (index + dragOffset.value / getTargetBaseSize())
    }

    fun transformPage(page: PageModifier, index: Int) {
        pagerTransformer.transformPage(page, calculate(index))
    }

    suspend fun initOffset() {
        dragOffset.snapTo(getTargetBaseSize() * currentPage)
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

                dragOffset.animateTo(-index * getTargetBaseSize())
                return
            }
        }

        dragOffset.animateTo(-currentPage * getTargetBaseSize())
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
    transformer: ViewPagerTransformer = Pager2_SpinnerTransformer(),
    pageScale: Float = 1f,
    content: ViewPagerScope.() -> Unit
) {
    val viewPagerState = rememberLazyViewPagerState(initPage, transformer)

    val viewPagerItemProvider by rememberStateOfItemsProvider(content)

    val visibilityies = (0..viewPagerItemProvider.itemCount).map {
        mutableStateOf(View.VISIBLE)
    }

    val coroutineScope = rememberCoroutineScope()

    viewPagerState.itemCount = viewPagerItemProvider.itemCount
    viewPagerState.orientation = orientation

    LaunchedEffect(viewPagerState) {
        viewPagerState.initOffset()
    }

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
            val scaledConstraints = Constraints(
                maxWidth = (constraints.maxWidth * pageScale).toInt(),
                maxHeight = (constraints.maxHeight * pageScale).toInt()
            )

            val placeables = measurables.map { it.measure(scaledConstraints) }

            viewPagerState.viewSize =
                Size(placeables.first().width.toFloat(), placeables.first().height.toFloat())

            layout(scaledConstraints.maxWidth, scaledConstraints.maxHeight) {
                placeables.forEachIndexed { index, placeable ->

                    var offsetX = (scaledConstraints.maxWidth - placeable.width) / 2
                    var offsetY = (scaledConstraints.maxHeight - placeable.height) / 2

                    if (orientation == ViewPagerOrientation.Horizontal)
                        offsetX += (placeable.width * viewPagerState.calculate(index)).toInt()
                    else offsetY += (placeable.height * viewPagerState.calculate(index)).toInt()

                    if (visibilityies[index].value == View.VISIBLE) {
                        placeable.placeWithLayer(offsetX, offsetY) {
                            viewPagerState.transformPage(
                                PageModifier(
                                    placeable.width,
                                    placeable.height,
                                    this,
                                    visibilityies[index]
                                ),
                                index
                            )
                        }
                    }
                }
            }
        }
    }
}

class PageModifier(
    val width: Int,
    val height: Int,
    private val layerScope: GraphicsLayerScope,
    visibilityState: MutableState<Int>
) {
    var visibility: Int by visibilityState

    var scaleX: Float by layerScope::scaleX
    var scaleY: Float by layerScope::scaleY

    var translationX: Float by layerScope::translationX
    var translationY: Float by layerScope::translationY

    var rotationX: Float by layerScope::rotationX
    var rotationY: Float by layerScope::rotationY
    var rotation: Float by layerScope::rotationZ

    var cameraDistance: Float by layerScope::cameraDistance

    var alpha by layerScope::alpha

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