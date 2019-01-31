package com.mercandalli.android.apps.screen_recorder.activity

import android.app.Activity
import android.view.View
import androidx.annotation.IdRes

object ActivityExtension {

    fun <T : View> Activity.bind(@IdRes res: Int): Lazy<T> {
        @Suppress("UNCHECKED_CAST")
        return lazy(LazyThreadSafetyMode.NONE) { findViewById<T>(res) }
    }
}
