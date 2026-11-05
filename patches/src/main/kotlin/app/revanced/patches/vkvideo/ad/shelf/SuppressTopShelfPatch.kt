package app.revanced.patches.vkvideo.ad.shelf

import app.revanced.patcher.patch.resourcePatch

private val TOP_SHELF_LAYOUTS = arrayOf(
    "res/layout/catalog_video_topshelf.xml",
    "res/layout/catalog_video_music_topshelf.xml"
)

val suppressTopShelfPatch = resourcePatch(
    name = "Suppress top shelf",
    description = "Collapses the catalog promotional header shelves by zeroing out " +
        "catalog_video_topshelf and catalog_video_music_topshelf layouts."
) {
    compatibleWith("com.vk.vkvideo")

    apply {
        TOP_SHELF_LAYOUTS.forEach { path ->
            document(path).use { document ->
                val root = document.documentElement ?: return@use
                // Qualified names: the decoded APK DOM does not resolve these
                // attributes through the namespace-aware APIs, but the prefixed
                // form maps onto the existing android:* attributes.
                root.setAttribute("android:layout_height", "0dp")
                root.setAttribute("android:visibility", "gone")
            }
        }
    }
}
