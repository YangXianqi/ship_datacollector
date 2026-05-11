package com.shipyard.collector.data.repository

import com.shipyard.collector.data.local.FormDao
import com.shipyard.collector.data.local.FormEntity
import com.shipyard.collector.data.remote.CollectorApi
import com.shipyard.collector.data.session.SessionStore
import com.shipyard.collector.model.FormSummary
import com.shipyard.collector.model.LoginSession
import kotlinx.coroutines.flow.Flow

class AuthRepository(
    private val formDao: FormDao,
    private val sessionStore: SessionStore,
    private val collectorApi: CollectorApi
) {
    val session: Flow<LoginSession?> = sessionStore.session

    suspend fun login(phoneNumber: String, password: String): Result<LoginSession> = runCatching {
        val response = collectorApi.login(phoneNumber.trim(), password)
        sessionStore.saveSession(response.session)
        replaceForms(response.forms)
        response.session
    }

    suspend fun logout() {
        sessionStore.clearSession()
        formDao.clearAll()
    }

    private suspend fun replaceForms(forms: List<FormSummary>) {
        formDao.clearAll()
        formDao.upsertAll(
            forms.map {
                FormEntity(
                    formId = it.id,
                    formName = it.name,
                    defaultUploadMode = it.defaultUploadMode.name
                )
            }
        )
    }
}
