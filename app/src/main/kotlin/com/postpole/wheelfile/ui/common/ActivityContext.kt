package com.postpole.wheelfile.ui.common

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper

/** The hosting [Activity] of this context, or null when there is none (previews, some tests). */
tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
