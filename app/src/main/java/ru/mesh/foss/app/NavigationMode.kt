package ru.mesh.foss.app

import android.content.res.Configuration

/**
 * Выбор раскладки навигации.
 *
 * В альбомной ориентации нижняя панель занимает слишком ценную высоту,
 * поэтому переносим её влево в виде рельса. В портретной оставляем снизу.
 */
object NavigationMode {
    fun useSideRail(orientation: Int): Boolean =
        orientation == Configuration.ORIENTATION_LANDSCAPE
}
