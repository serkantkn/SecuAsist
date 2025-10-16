package com.serkantken.secuasist.utils

import android.R
import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.app.Activity
import android.content.res.Configuration
import android.view.View
import android.view.ViewGroup
import android.view.ViewGroup.LayoutParams
import android.view.ViewOutlineProvider
import com.orhanobut.hawk.Hawk
import eightbitlab.com.blurview.BlurView
import eightbitlab.com.blurview.RenderScriptBlur

class Tools(private val activity: Activity) {

    fun blur(views: Array<BlurView>, radius: Float, isRounded: Boolean) {
        for (view in views) {
            val decorView: View = activity.window.decorView
            val rootView = decorView.findViewById<ViewGroup>(R.id.content)
            val windowBackground = decorView.background
            Hawk.init(activity).build()

            view.setupWith(rootView, RenderScriptBlur(activity))
                .setFrameClearDrawable(windowBackground)
                .setBlurRadius(radius)
                .setBlurAutoUpdate(true)
            if (Hawk.contains("enable_blur")) {
                if (Hawk.get<Boolean>("enable_blur") == true) {
                    view.setBlurEnabled(true)
                } else {
                    view.setBlurEnabled(false)
                }
            } else {
                view.setBlurEnabled(true)
            }
            if (isRounded) {
                view.outlineProvider = ViewOutlineProvider.BACKGROUND
                view.clipToOutline = true
            }
        }
    }

    fun convertDpToPixel(dp: Int): Int {
        val density: Float = activity.resources.displayMetrics.density
        return (dp * density).toInt()
    }

    fun getNavigationBarHeight(orientation: Int): Int {
        var result = 0
        val resourceId = activity.resources.getIdentifier(if (orientation == Configuration.ORIENTATION_PORTRAIT) "navigation_bar_height" else "navigation_bar_height_landscape", "dimen", "android")
        if (resourceId > 0) {
            result = activity.resources.getDimensionPixelSize(resourceId)
        }
        return result
    }

    fun doSizingAnimation(direction: Int, isExpanded: Boolean, containerView: View, packageView: View, completion: () -> Unit) {
        if (direction == 0) {
            val initialWidth = containerView.width
            packageView.measure(
                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
                View.MeasureSpec.makeMeasureSpec(containerView.height, View.MeasureSpec.EXACTLY)
            )
            val targetWidth: Int
            if (isExpanded) {
                targetWidth = initialWidth - packageView.measuredWidth
                packageView.visibility = View.GONE
                completion()
            } else
                targetWidth = initialWidth + packageView.measuredWidth

            val animator = ValueAnimator.ofInt(initialWidth, targetWidth)
            animator.addUpdateListener { animation ->
                val value = animation.animatedValue as Int
                val layoutParams = containerView.layoutParams
                layoutParams.width = value
                containerView.layoutParams = layoutParams
            }
            if (!isExpanded) {
                animator.addListener(object : AnimatorListenerAdapter() {
                    override fun onAnimationEnd(animation: Animator) {
                        packageView.visibility = View.VISIBLE
                        val layoutParams = containerView.layoutParams
                        layoutParams.height = LayoutParams.WRAP_CONTENT
                        containerView.layoutParams = layoutParams
                        completion()
                    }
                })
            }
            animator.duration = 300
            animator.start()
        } else if (direction == 1) {
            val initialHeight = containerView.height
            packageView.measure(
                View.MeasureSpec.makeMeasureSpec(containerView.width, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
            )
            val targetWidth: Int
            if (isExpanded) {
                targetWidth = initialHeight - packageView.measuredHeight
                packageView.visibility = View.GONE
                completion()
            } else
                targetWidth = initialHeight + packageView.measuredHeight

            val animator = ValueAnimator.ofInt(initialHeight, targetWidth)
            animator.addUpdateListener { animation ->
                val value = animation.animatedValue as Int
                val layoutParams = containerView.layoutParams
                layoutParams.height = value
                containerView.layoutParams = layoutParams
            }
            if (!isExpanded) {
                animator.addListener(object : AnimatorListenerAdapter() {
                    override fun onAnimationEnd(animation: Animator) {
                        packageView.visibility = View.VISIBLE
                        val layoutParams = containerView.layoutParams
                        layoutParams.height = LayoutParams.WRAP_CONTENT
                        containerView.layoutParams = layoutParams
                        completion()
                    }
                })
            }
            animator.duration = 300
            animator.start()
        }
    }
}