package com.doool.compose_viewpager.transformer.compose

import android.util.Log
import android.view.View
import androidx.viewpager.widget.ViewPager
import com.doool.viewpager.PageModifier
import com.doool.viewpager.ViewPagerTransformer

/*
Copyright 2018 Wajahat Karim
Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at
   http://www.apache.org/licenses/LICENSE-2.0
Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
*/

class CardFlipPageTransformer : ViewPagerTransformer {

    var isScalable = true
    private var flipOrientation = VERTICAL

    override fun transformPage(page: PageModifier, position: Float) {
        val percentage = 1 - Math.abs(position)
        page.cameraDistance = 120f
        setVisibility(page, position)
        setTranslation(page)
        setSize(page, position, percentage)
        setRotation(page, position, percentage)
    }

    private fun setVisibility(page: PageModifier, position: Float) {
        if (position < 0.5 && position > -0.5) {
            page.visibility= View.VISIBLE
        } else {
            page.visibility= View.INVISIBLE
        }
    }

    private fun setTranslation(page: PageModifier) {
        val scroll = -page.left
        page.translationX = scroll.toFloat()
    }

    private fun setSize(page: PageModifier, position: Float, percentage: Float) {
        // Do nothing, if its not scalable
        if (!isScalable) return
        page.scaleX = if (position != 0f && position != 1f) percentage else 1f
        page.scaleY = if (position != 0f && position != 1f) percentage else 1f
    }

    private fun setRotation(page: PageModifier, position: Float, percentage: Float) {
        if (flipOrientation == VERTICAL) {
            if (position > 0) {
                page.rotationY = -180 * (percentage + 1)
            } else {
                page.rotationY = 180 * (percentage + 1)
            }
        } else {
            if (position > 0) {
                page.rotationX = -180 * (percentage + 1)
            } else {
                page.rotationX = 180 * (percentage + 1)
            }
        }
    }

    fun getFlipOrientation(): Int {
        return flipOrientation
    }

    /**
     * Sets the Flip Orientation. Can be either CardFlipPageTransformer.HORIZONTAL or CardFlipPageTransformer.VERTICAL
     * @param flipOrientation Can be either CardFlipPageTransformer.HORIZONTAL or CardFlipPageTransformer.VERTICAL
     */
    fun setFlipOrientation(flipOrientation: Int) {
        this.flipOrientation = if (flipOrientation > 1) VERTICAL else HORIZONTAL
    }

    companion object {
        const val HORIZONTAL = 1
        const val VERTICAL = 2
    }
}