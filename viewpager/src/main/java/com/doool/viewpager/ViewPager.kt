package com.doool.viewpager

import android.util.Log
import android.view.View
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.GraphicsLayerScope
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.unit.Constraints
import com.doool.viewpager.transformers.DefaultTransformer
import kotlinx.coroutines.CoroutineScope
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

    fun transformPage(view: ViewPage, position: Float)
}

@Stable
class ViewPagerState(
    var currentPage: Int = 0,
    val pagerTransformer: ViewPagerTransformer
) {
    lateinit var scope: CoroutineScope
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

    fun transformPage(page: ViewPage, index: Int) {
        pagerTransformer.transformPage(page, calculate(index))
    }

    fun initOffset() {
        scope.launch {
            dragOffset.snapTo(getTargetBaseSize() * currentPage)
        }
    }

    fun updateOffset(offset: Float) {
        scope.launch {
            dragOffset.snapTo(dragOffset.value + offset)
        }
    }

    fun updatePage() {
        val searchRange = if (calculate(currentPage) < 0f)
            (currentPage until itemCount)
        else
            (0..currentPage)

        searchRange.forEach { index ->
            if (calculate(index) in -0.5f..0.5f) {
                currentPage = index

                scope.launch {
                    dragOffset.animateTo(-index * getTargetBaseSize())
                }
                return
            }
        }

        scope.launch {
            dragOffset.animateTo(-currentPage * getTargetBaseSize())
        }
    }
}

enum class ViewPagerOrientation {
    Horizontal, Vertical
}

@LayoutScopeMarker
interface ViewPagerScope {
    fun getPagePosition(): Float
}

class ViewPagerScopeImpl(private val index: Int, private val viewPagerState: ViewPagerState) :
    ViewPagerScope {
    override fun getPagePosition(): Float {
        return viewPagerState.calculate(index)
    }
}

@Composable
fun <T : Any> ViewPager(
    modifier: Modifier = Modifier,
    items: List<T> = listOf(),
    initPage: Int = 0,
    orientation: ViewPagerOrientation,
    transformer: ViewPagerTransformer = DefaultTransformer(),
    pageScale: Float = 1f,
    content: @Composable ViewPagerScope.(T) -> Unit
) {
    val viewPagerState = rememberLazyViewPagerState(initPage, transformer)

    val visibilityies = (0..items.size).map {
        remember {
            mutableStateOf(View.VISIBLE)
        }
    }

    viewPagerState.scope = rememberCoroutineScope()
    viewPagerState.itemCount = items.size
    viewPagerState.orientation = orientation

    viewPagerState.initOffset()

    val draggableState = rememberDraggableState {
        Log.d("asdfasf", it.toString())
        viewPagerState.updateOffset(it)
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
            items.forEachIndexed { index, item ->
                Box(modifier = Modifier) {
                    ViewPagerScopeImpl(index, viewPagerState).content(item)
                }
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
                                ViewPage(placeable.width, placeable.height, this)
                                    .apply {
                                        visibilityFun = {
                                            visibilityies[index].value = it
                                        }
                                    },
                                index
                            )
                        }
                    }
                }
            }
        }
    }
}

class ViewPage(val width: Int, val height: Int, private val layerScope: GraphicsLayerScope) {
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

    var visibility: Int = 0
        set(value) {
            visibilityFun(value)
        }


    var visibilityFun: (Int) -> Unit = {}

}