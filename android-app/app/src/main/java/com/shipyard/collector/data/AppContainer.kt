package com.shipyard.collector.data

import android.content.Context
import androidx.room.Room
import com.shipyard.collector.data.local.AppDatabase
import com.shipyard.collector.data.remote.HttpCollectorApi
import com.shipyard.collector.data.repository.AuthRepository
import com.shipyard.collector.data.repository.CollectorRepository
import com.shipyard.collector.data.repository.UploadQueueRepository
import com.shipyard.collector.data.session.SessionStore

class AppContainer(context: Context) {
    private val appContext = context.applicationContext

    val database: AppDatabase by lazy {
        Room.databaseBuilder(appContext, AppDatabase::class.java, "shipyard-collector.db")
            .fallbackToDestructiveMigration()
            .build()
    }

    private val collectorApi: HttpCollectorApi by lazy {
        HttpCollectorApi()
    }

    private val sessionStore: SessionStore by lazy {
        SessionStore(appContext)
    }

    val authRepository: AuthRepository by lazy {
        AuthRepository(
            formDao = database.formDao(),
            sessionStore = sessionStore,
            collectorApi = collectorApi
        )
    }

    val collectorRepository: CollectorRepository by lazy {
        CollectorRepository(database)
    }

    val uploadQueueRepository: UploadQueueRepository by lazy {
        UploadQueueRepository(
            database = database,
            authRepository = authRepository,
            collectorApi = collectorApi
        )
    }
}
