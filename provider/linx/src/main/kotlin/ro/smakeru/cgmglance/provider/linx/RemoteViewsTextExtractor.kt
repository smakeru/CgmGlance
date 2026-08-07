package ro.smakeru.cgmglance.provider.linx

import android.content.Context
import android.content.res.Resources
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.RemoteViews
import android.widget.TextView

/**
 * Turns the vendor's notification layout into the flat list of texts the parser understands.
 *
 * The LinX ongoing notification carries no `android.title` or `android.text`; its content lives
 * in a custom `RemoteViews` tree. `RemoteViews.apply` inflates that tree in our own process
 * using the vendor's resources — a documented public API, the same one the system status bar
 * uses, and the reason this app needs no reverse engineering.
 *
 * View ids are translated back to their simple entry names (`tv_glucose`) through the vendor
 * package's own [Resources]. Names survive vendor updates far better than numeric ids or tree
 * positions, which is what makes the parser's primary lookup durable.
 */
class RemoteViewsTextExtractor(
    private val context: Context,
) {
    /**
     * Inflates [remoteViews] and collects every non-empty [TextView] in tree order.
     *
     * Returns an empty list when the layout cannot be inflated. That happens when the vendor
     * ships a layout referencing resources we cannot resolve, and it is a normal failure to
     * report rather than a crash to propagate: the alternative is an app that dies whenever
     * the vendor updates.
     */
    fun extract(
        remoteViews: RemoteViews,
        vendorPackage: String,
    ): List<LinxTextView> {
        val vendorResources = resourcesFor(vendorPackage)

        val root =
            try {
                remoteViews.apply(context, FrameLayout(context))
            } catch (error: RuntimeException) {
                // Inflation reaches into another app's resources; anything it throws is about
                // that app, not about us.
                return emptyList()
            }

        val collected = mutableListOf<LinxTextView>()
        collect(root, vendorResources, collected)
        return collected
    }

    private fun collect(
        view: View,
        vendorResources: Resources?,
        into: MutableList<LinxTextView>,
    ) {
        if (view is TextView) {
            val text = view.text?.toString().orEmpty()
            if (text.isNotBlank()) {
                into += LinxTextView(id = entryNameOf(view.id, vendorResources), text = text)
            }
        }
        if (view is ViewGroup) {
            for (index in 0 until view.childCount) {
                collect(view.getChildAt(index), vendorResources, into)
            }
        }
    }

    /** Resolves a numeric view id back to its simple name, or `null` when it has none. */
    private fun entryNameOf(
        id: Int,
        vendorResources: Resources?,
    ): String? {
        if (id == View.NO_ID || vendorResources == null) return null
        return try {
            vendorResources.getResourceEntryName(id)
        } catch (error: Resources.NotFoundException) {
            // Ids belonging to the framework's notification decoration rather than to the
            // vendor layout land here. The parser treats them as unnamed, which is correct.
            null
        }
    }

    private fun resourcesFor(vendorPackage: String): Resources? =
        try {
            context.packageManager.getResourcesForApplication(vendorPackage)
        } catch (error: Exception) {
            null
        }
}
