package com.kartus.sportswidget.data

import android.content.Context

/**
 * Hand-rolled dependency graph.
 *
 * The app has exactly one repository and one API client, both process-wide
 * singletons shared between the Activity and the widget worker. A DI framework
 * would add a build-time processor and a lot of ceremony for two objects.
 */
object ServiceLocator {

    @Volatile
    private var repository: MlbRepository? = null

    fun repository(context: Context): MlbRepository =
        repository ?: synchronized(this) {
            repository ?: MlbRepository(
                StatsApiClient(context.applicationContext.cacheDir),
            ).also { repository = it }
        }
}
