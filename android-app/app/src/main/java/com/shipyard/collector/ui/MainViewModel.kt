package com.shipyard.collector.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.shipyard.collector.CollectorApplication
import com.shipyard.collector.data.repository.AuthRepository
import com.shipyard.collector.data.repository.CollectorRepository
import com.shipyard.collector.data.repository.UploadQueueRepository
import com.shipyard.collector.model.CaptureRecord
import com.shipyard.collector.model.FormSummary
import com.shipyard.collector.model.RecordStatus
import com.shipyard.collector.model.UploadBatchStatus
import com.shipyard.collector.model.UploadControllerState
import com.shipyard.collector.model.UserProfile
import com.shipyard.collector.service.UploadForegroundService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class AppScreen {
    LOGIN,
    DASHBOARD,
    FORM_PICKER,
    CAPTURE,
    RECORDS,
    UPLOAD
}

enum class FormSelectionTarget {
    CAPTURE,
    UPLOAD
}

data class AppUiState(
    val currentScreen: AppScreen = AppScreen.LOGIN,
    val selectionTarget: FormSelectionTarget = FormSelectionTarget.CAPTURE,
    val user: UserProfile? = null,
    val forms: List<FormSummary> = emptyList(),
    val selectedFormId: String? = null,
    val editingRecordId: String? = null,
    val records: List<CaptureRecord> = emptyList(),
    val uploadSelection: Set<String> = emptySet(),
    val queueState: UploadControllerState = UploadControllerState(),
    val loginError: String? = null,
    val bannerMessage: String? = null,
    val isBusy: Boolean = false
)

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val container = (application as CollectorApplication).appContainer
    private val authRepository: AuthRepository = container.authRepository
    private val collectorRepository: CollectorRepository = container.collectorRepository
    private val uploadQueueRepository: UploadQueueRepository = container.uploadQueueRepository

    private val _uiState = MutableStateFlow(AppUiState())
    val uiState: StateFlow<AppUiState> = _uiState.asStateFlow()
    private var lastQueueBannerMessage: String? = null

    init {
        observeSession()
        observeForms()
        observeRecords()
        observeQueueState()
    }

    fun login(phoneNumber: String, password: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isBusy = true, loginError = null) }
            val result = authRepository.login(phoneNumber, password)
            result.onSuccess { session ->
                _uiState.update {
                    it.copy(
                        isBusy = false,
                        loginError = null,
                        user = session.toUserProfile(),
                        currentScreen = AppScreen.DASHBOARD
                    )
                }
            }.onFailure { error ->
                _uiState.update {
                    it.copy(
                        isBusy = false,
                        loginError = error.message ?: "登录失败"
                    )
                }
            }
        }
    }

    fun logout() {
        viewModelScope.launch {
            val unresolved = collectorRepository.countUnresolvedRecords()
            if (unresolved > 0) {
                _uiState.update {
                    it.copy(bannerMessage = "还有 $unresolved 条未处理数据，暂不允许退出登录")
                }
                return@launch
            }
            authRepository.logout()
            _uiState.value = AppUiState()
        }
    }

    fun openCapturePicker() {
        pendingPhotoPaths = emptyList()
        pendingAudioPath = null
        _uiState.update {
            it.copy(
                currentScreen = AppScreen.FORM_PICKER,
                selectionTarget = FormSelectionTarget.CAPTURE,
                editingRecordId = null
            )
        }
    }

    fun openUploadPicker() {
        _uiState.update {
            it.copy(
                currentScreen = AppScreen.FORM_PICKER,
                selectionTarget = FormSelectionTarget.UPLOAD,
                editingRecordId = null
            )
        }
    }

    fun selectForm(formId: String) {
        _uiState.update {
            it.copy(
                selectedFormId = formId,
                currentScreen = if (it.selectionTarget == FormSelectionTarget.CAPTURE) {
                    AppScreen.CAPTURE
                } else {
                    AppScreen.UPLOAD
                }
            )
        }
    }

    fun saveRecord(locationName: String, textNote: String) {
        val state = uiState.value
        val formId = state.selectedFormId ?: return
        viewModelScope.launch {
            val result = if (state.editingRecordId == null) {
                collectorRepository.saveRecord(
                    formId = formId,
                    locationName = locationName,
                    textNote = textNote,
                    photoPaths = pendingPhotoPaths,
                    audioPath = pendingAudioPath
                )
            } else {
                collectorRepository.updateRecord(
                    recordId = state.editingRecordId,
                    locationName = locationName,
                    textNote = textNote,
                    photoPaths = pendingPhotoPaths,
                    audioPath = pendingAudioPath
                )
            }
            result.onSuccess {
                pendingPhotoPaths = emptyList()
                pendingAudioPath = null
                _uiState.update { state ->
                    state.copy(
                        currentScreen = AppScreen.RECORDS,
                        editingRecordId = null,
                        bannerMessage = "记录已保存到本地缓存"
                    )
                }
            }.onFailure { error ->
                _uiState.update {
                    it.copy(bannerMessage = error.message ?: "保存失败")
                }
            }
        }
    }

    private var pendingPhotoPaths: List<String> = emptyList()
    private var pendingAudioPath: String? = null

    fun updatePendingAttachments(photoPaths: List<String>, audioPath: String?) {
        pendingPhotoPaths = photoPaths
        pendingAudioPath = audioPath
    }

    fun openRecordEditor(recordId: String) {
        viewModelScope.launch {
            val record = collectorRepository.getRecord(recordId)
            if (record == null) {
                _uiState.update { it.copy(bannerMessage = "未找到要编辑的记录") }
                return@launch
            }
            if (record.status != RecordStatus.PENDING && record.status != RecordStatus.FAILED) {
                _uiState.update { it.copy(bannerMessage = "当前状态不可编辑") }
                return@launch
            }
            pendingPhotoPaths = record.photoPaths
            pendingAudioPath = record.audioPath
            _uiState.update {
                it.copy(
                    selectedFormId = record.formId,
                    editingRecordId = record.recordId,
                    currentScreen = AppScreen.CAPTURE
                )
            }
        }
    }

    fun showDashboard() {
        _uiState.update { it.copy(currentScreen = AppScreen.DASHBOARD, editingRecordId = null) }
    }

    fun showRecords() {
        _uiState.update { it.copy(currentScreen = AppScreen.RECORDS, editingRecordId = null) }
    }

    fun showUploadScreen() {
        _uiState.update { it.copy(currentScreen = AppScreen.UPLOAD, editingRecordId = null) }
    }

    fun toggleUploadSelection(recordId: String, checked: Boolean) {
        _uiState.update { state ->
            val next = state.uploadSelection.toMutableSet()
            if (checked) next.add(recordId) else next.remove(recordId)
            state.copy(uploadSelection = next)
        }
    }

    fun startUpload() {
        viewModelScope.launch {
            val state = uiState.value
            val user = state.user
            if (user?.canUpload != true) {
                _uiState.update { it.copy(bannerMessage = "当前账号没有上传权限，请联系管理员配置") }
                return@launch
            }
            val selectedFormId = state.selectedFormId
            val visibleCandidates = state.records.filter {
                it.formId == selectedFormId && (it.status == RecordStatus.PENDING || it.status == RecordStatus.FAILED)
            }
            if (visibleCandidates.isEmpty()) {
                _uiState.update { it.copy(bannerMessage = "当前表单没有可上传的本地缓存") }
                return@launch
            }
            val targetIds = if (state.uploadSelection.isNotEmpty()) {
                visibleCandidates.filter { it.recordId in state.uploadSelection }.map { it.recordId }
            } else {
                visibleCandidates.map { it.recordId }
            }

            val result = uploadQueueRepository.enqueue(targetIds)
            result.onSuccess {
                UploadForegroundService.start(getApplication())
                _uiState.update { current ->
                    current.copy(
                        currentScreen = AppScreen.UPLOAD,
                        uploadSelection = emptySet(),
                        bannerMessage = "上传队列已启动"
                    )
                }
            }.onFailure { error ->
                _uiState.update {
                    it.copy(bannerMessage = error.message ?: "无法启动上传")
                }
            }
        }
    }

    fun pauseUpload() {
        UploadForegroundService.pause(getApplication())
    }

    fun resumeUpload() {
        viewModelScope.launch {
            if (uiState.value.user?.canUpload != true) {
                _uiState.update { it.copy(bannerMessage = "当前账号没有上传权限，请联系管理员配置") }
                return@launch
            }
            val resumed = uploadQueueRepository.resumeActiveBatch()
            if (resumed) {
                UploadForegroundService.resume(getApplication())
            } else {
                _uiState.update { it.copy(bannerMessage = "当前没有可继续的上传任务") }
            }
        }
    }

    fun cancelUpload() {
        UploadForegroundService.cancel(getApplication())
    }

    fun deleteSelectedLocalRecords() {
        viewModelScope.launch {
            if (uiState.value.user?.canDeleteCache != true) {
                _uiState.update { it.copy(bannerMessage = "当前账号没有清理本地缓存权限") }
                return@launch
            }
            val selectedIds = uiState.value.uploadSelection
            collectorRepository.deleteRecords(selectedIds)
            _uiState.update {
                it.copy(
                    uploadSelection = emptySet(),
                    bannerMessage = "已删除选中的本地记录"
                )
            }
        }
    }

    fun clearSelectedUploadedRecords() {
        viewModelScope.launch {
            if (uiState.value.user?.canDeleteCache != true) {
                _uiState.update { it.copy(bannerMessage = "当前账号没有清理本地缓存权限") }
                return@launch
            }
            val selectedIds = uiState.value.uploadSelection
            collectorRepository.clearUploadedRecords(selectedIds)
            _uiState.update {
                it.copy(
                    uploadSelection = emptySet(),
                    bannerMessage = "已清理选中的已上传缓存"
                )
            }
        }
    }

    fun selectPendingRecords() {
        _uiState.update { state ->
            val selectedFormId = state.selectedFormId
            val selected = state.records.filter {
                it.formId == selectedFormId && (it.status == RecordStatus.PENDING || it.status == RecordStatus.FAILED)
            }.mapTo(mutableSetOf()) { it.recordId }
            state.copy(uploadSelection = selected)
        }
    }

    fun selectUploadedRecords() {
        _uiState.update { state ->
            val selectedFormId = state.selectedFormId
            val selected = state.records.filter {
                it.formId == selectedFormId && it.status == RecordStatus.UPLOADED
            }.mapTo(mutableSetOf()) { it.recordId }
            state.copy(uploadSelection = selected)
        }
    }

    fun clearUploadSelection() {
        _uiState.update { it.copy(uploadSelection = emptySet()) }
    }

    fun clearBanner() {
        _uiState.update { it.copy(bannerMessage = null) }
    }

    private fun observeSession() {
        viewModelScope.launch {
            authRepository.session.collect { session ->
                _uiState.update { state ->
                    val user = session?.toUserProfile()
                    state.copy(
                        user = user,
                        currentScreen = if (user == null) AppScreen.LOGIN else {
                            if (state.currentScreen == AppScreen.LOGIN) AppScreen.DASHBOARD else state.currentScreen
                        }
                    )
                }
            }
        }
    }

    private fun observeForms() {
        viewModelScope.launch {
            collectorRepository.forms.collect { forms ->
                _uiState.update { state ->
                    val selectedFormId = when {
                        forms.isEmpty() -> null
                        forms.any { it.id == state.selectedFormId } -> state.selectedFormId
                        else -> forms.first().id
                    }
                    state.copy(forms = forms, selectedFormId = selectedFormId)
                }
            }
        }
    }

    private fun observeRecords() {
        viewModelScope.launch {
            collectorRepository.records.collect { records ->
                _uiState.update { state ->
                    state.copy(
                        records = records,
                        uploadSelection = state.uploadSelection.filterTo(mutableSetOf()) { selected ->
                            records.any { it.recordId == selected }
                        }
                    )
                }
            }
        }
    }

    private fun observeQueueState() {
        viewModelScope.launch {
            uploadQueueRepository.queueState.collect { queueState ->
                _uiState.update { state ->
                    val nextBanner = if (
                        queueState.lastMessage != null &&
                        queueState.lastMessage != lastQueueBannerMessage &&
                        queueState.status != UploadBatchStatus.RUNNING
                    ) {
                        lastQueueBannerMessage = queueState.lastMessage
                        queueState.lastMessage
                    } else {
                        state.bannerMessage
                    }
                    state.copy(queueState = queueState, bannerMessage = nextBanner)
                }
            }
        }
    }
}
