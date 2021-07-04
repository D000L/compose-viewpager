package com.doool.viewpager.transformers

import android.view.View
import com.doool.viewpager.PageModifier
import com.doool.viewpager.ViewPagerTransformer

class DefaultTransformer : ViewPagerTransformer {
    override fun transformPage(view: PageModifier, position: Float) {
        view.visibility = View.VISIBLE
    }
}