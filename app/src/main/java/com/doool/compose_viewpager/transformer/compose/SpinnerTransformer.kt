package com.doool.compose_viewpager.transformer.compose

import android.view.View
import com.doool.viewpager.PageModifier
import com.doool.viewpager.ViewPagerTransformer

class Pager2_SpinnerTransformer : ViewPagerTransformer {
    override fun transformPage(view: PageModifier, position: Float) {
        with(view) {

            translationX = -position * width
            if (Math.abs(position) <= 0.5) {
                visibility = View.VISIBLE
                scaleX = 1 - Math.abs(position)
                scaleY = 1 - Math.abs(position)
            } else if (Math.abs(position) > 0.5) {
                visibility = View.GONE
            }
            if (position < -1) {  // [-Infinity,-1)
                // This page is way off-screen to the left.
                alpha = 0f
            } else if (position <= 0) {   // [-1,0]
                alpha = 1f
                rotation = 360 * Math.abs(position)
            } else if (position <= 1) {   // (0,1]
                alpha = 1f
                rotation = -360 * Math.abs(position)
            } else {  // (1,+Infinity]
                // This page is way off-screen to the right.
                alpha = 0f
            }
        }
    }
}