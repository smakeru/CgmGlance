package ro.smakeru.cgmglance.provider.dexcom

import android.content.Context
import android.content.res.Resources
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.RemoteViews
import android.widget.TextView

/**
 * Same public RemoteViews.apply/tree-walk approach as Linx, without a Linx module dependency.
 * Collects visible TextViews only, never images/OCR or chart coordinates. Failures are empty
 * layouts, reported by the parser when no other representation yields a reading.
 */
internal class RemoteViewsTextExtractor(
    private val context: Context,
) {
    fun extract(
        remoteViews: RemoteViews,
        vendorPackage: String,
    ): List<DexcomTextView> =
        try {
            val resources = context.packageManager.getResourcesForApplication(vendorPackage)
            val root = remoteViews.apply(context, FrameLayout(context))
            buildList { collect(root, resources, this) }
        } catch (error: Exception) {
            // Resource/inflation diagnostics only: never log notification text or health values.
            Log.w("CgmGlance/Dexcom", "RemoteViews extraction failed (layout=${remoteViews.layoutId})", error)
            emptyList()
        }

    private fun collect(
        view: View,
        resources: Resources,
        into: MutableList<DexcomTextView>,
    ) {
        if (view.visibility != View.VISIBLE) return
        if (view is TextView) {
            val text = view.text?.toString().orEmpty()
            if (text.isNotBlank()) {
                val id =
                    try {
                        resources.getResourceEntryName(view.id)
                    } catch (_: Resources.NotFoundException) {
                        null
                    }
                into += DexcomTextView(id, text)
            }
        }
        if (view is ViewGroup) {
            for (index in 0 until view.childCount) collect(view.getChildAt(index), resources, into)
        }
    }
}
