package com.shipyard.collector.ui

import android.Manifest
import android.graphics.BitmapFactory
import android.media.MediaRecorder
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Logout
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.RotateLeft
import androidx.compose.material.icons.filled.RotateRight
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.collectAsState
import com.shipyard.collector.media.LocalMediaManager
import com.shipyard.collector.model.CaptureRecord
import com.shipyard.collector.model.FormSummary
import com.shipyard.collector.model.RecordStatus
import com.shipyard.collector.model.UploadBatchStatus
import com.shipyard.collector.model.UploadControllerState
import com.shipyard.collector.model.UserProfile
import com.shipyard.collector.network.ConnectivityObserver
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ShipyardCollectorApp(viewModel: MainViewModel) {
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current
    val connectivityObserver = remember(context) { ConnectivityObserver(context) }
    val isNetworkAvailable by connectivityObserver.isOnline.collectAsState(initial = true)
    var previousNetworkAvailable by remember { mutableStateOf(isNetworkAvailable) }

    LaunchedEffect(uiState.bannerMessage) {
        val message = uiState.bannerMessage ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(message)
        viewModel.clearBanner()
    }

    LaunchedEffect(isNetworkAvailable, uiState.records, uiState.user?.canUpload) {
        if (previousNetworkAvailable != isNetworkAvailable) {
            if (isNetworkAvailable && uiState.user?.canUpload == true) {
                val unresolvedCount = uiState.records.count {
                    it.status == RecordStatus.PENDING || it.status == RecordStatus.FAILED
                }
                if (unresolvedCount > 0) {
                    snackbarHostState.showSnackbar("网络已恢复，还有 $unresolvedCount 条记录可继续上传")
                }
            }
            previousNetworkAvailable = isNetworkAvailable
        }
    }

    MaterialTheme {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("船厂离线采集") },
                    actions = {
                        if (uiState.user != null) {
                            TextButton(onClick = viewModel::logout) {
                                Icon(Icons.Default.Logout, contentDescription = "Logout")
                                Spacer(Modifier.width(8.dp))
                                Text("退出")
                            }
                        }
                    }
                )
            },
            snackbarHost = { SnackbarHost(snackbarHostState) }
        ) { innerPadding ->
            when (uiState.currentScreen) {
                AppScreen.LOGIN -> LoginScreen(
                    modifier = Modifier.padding(innerPadding),
                    loginError = uiState.loginError,
                    isBusy = uiState.isBusy,
                    onLogin = viewModel::login
                )

                AppScreen.DASHBOARD -> DashboardScreen(
                    modifier = Modifier.padding(innerPadding),
                    user = uiState.user ?: return@Scaffold,
                    records = uiState.records,
                    queueState = uiState.queueState,
                    onCapture = viewModel::openCapturePicker,
                    onUpload = viewModel::openUploadPicker
                )

                AppScreen.FORM_PICKER -> FormPickerScreen(
                    modifier = Modifier.padding(innerPadding),
                    title = if (uiState.selectionTarget == FormSelectionTarget.CAPTURE) {
                        "选择采集表单"
                    } else {
                        "选择上传表单"
                    },
                    forms = uiState.forms,
                    onBack = viewModel::showDashboard,
                    onSelect = { viewModel.selectForm(it.id) }
                )

                AppScreen.CAPTURE -> CaptureScreen(
                    modifier = Modifier.padding(innerPadding),
                    form = uiState.forms.firstOrNull { it.id == uiState.selectedFormId } ?: return@Scaffold,
                    initialRecord = uiState.records.firstOrNull { it.recordId == uiState.editingRecordId },
                    onBack = if (uiState.editingRecordId == null) viewModel::showDashboard else viewModel::showRecords,
                    onAttachmentsChanged = viewModel::updatePendingAttachments,
                    onSave = viewModel::saveRecord
                )

                AppScreen.RECORDS -> RecordListScreen(
                    modifier = Modifier.padding(innerPadding),
                    form = uiState.forms.firstOrNull { it.id == uiState.selectedFormId } ?: return@Scaffold,
                    canUpload = uiState.user?.canUpload == true,
                    records = uiState.records.filter { it.formId == uiState.selectedFormId },
                    onBack = viewModel::showDashboard,
                    onUpload = viewModel::showUploadScreen,
                    onEditRecord = viewModel::openRecordEditor
                )

                AppScreen.UPLOAD -> UploadScreen(
                    modifier = Modifier.padding(innerPadding),
                    user = uiState.user,
                    form = uiState.forms.firstOrNull { it.id == uiState.selectedFormId },
                    records = uiState.records.filter { it.formId == uiState.selectedFormId },
                    selectedIds = uiState.uploadSelection,
                    queueState = uiState.queueState,
                    isNetworkAvailable = isNetworkAvailable,
                    onBack = viewModel::showDashboard,
                    onToggleSelection = viewModel::toggleUploadSelection,
                    onStartUpload = viewModel::startUpload,
                    onPauseUpload = viewModel::pauseUpload,
                    onResumeUpload = viewModel::resumeUpload,
                    onCancelUpload = viewModel::cancelUpload,
                    onSelectPending = viewModel::selectPendingRecords,
                    onSelectUploaded = viewModel::selectUploadedRecords,
                    onClearSelection = viewModel::clearUploadSelection,
                    onDeleteSelected = viewModel::deleteSelectedLocalRecords,
                    onClearUploaded = viewModel::clearSelectedUploadedRecords
                )
            }
        }
    }
}

@Composable
private fun LoginScreen(
    modifier: Modifier,
    loginError: String?,
    isBusy: Boolean,
    onLogin: (String, String) -> Unit
) {
    var phoneNumber by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Card(colors = CardDefaults.cardColors(containerColor = Color(0xFFE8F2F0))) {
                Column(Modifier.padding(16.dp)) {
                    Text("首次登录需要联网", fontWeight = FontWeight.Bold)
                    Text("登录成功后，30 天内可离线继续采集和上传缓存。")
                }
            }
        }
        item {
            OutlinedTextField(
                value = phoneNumber,
                onValueChange = { phoneNumber = it.take(11) },
                label = { Text("手机号") },
                modifier = Modifier.fillMaxWidth()
            )
        }
        item {
            OutlinedTextField(
                value = password,
                onValueChange = { password = it },
                label = { Text("密码") },
                modifier = Modifier.fillMaxWidth(),
                visualTransformation = PasswordVisualTransformation()
            )
        }
        loginError?.let { error ->
            item {
                Text(error, color = Color(0xFF9D3C3C))
            }
        }
        item {
            Button(
                enabled = !isBusy,
                onClick = { onLogin(phoneNumber, password) },
                modifier = Modifier.fillMaxWidth().height(56.dp)
            ) {
                Text(if (isBusy) "登录中..." else "登录")
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DashboardScreen(
    modifier: Modifier,
    user: UserProfile,
    records: List<CaptureRecord>,
    queueState: UploadControllerState,
    onCapture: () -> Unit,
    onUpload: () -> Unit
) {
    val pending = records.count { it.status == RecordStatus.PENDING }
    val uploading = records.count { it.status == RecordStatus.UPLOADING }
    val failed = records.count { it.status == RecordStatus.FAILED }
    val uploaded = records.count { it.status == RecordStatus.UPLOADED }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Card(colors = CardDefaults.cardColors(containerColor = Color(0xFFE8F2F0))) {
                Column(Modifier.padding(16.dp)) {
                    Text(user.displayName, fontWeight = FontWeight.Bold)
                    Text(user.phoneNumber)
                    Text("离线有效期至：${formatEpochMillis(user.offlineExpiryEpochMillis)}")
                    Text(
                        "权限：${if (user.canUpload) "可上传" else "仅采集"} / ${if (user.canDeleteCache) "可清缓存" else "不可删缓存"}"
                    )
                    if (!queueState.lastMessage.isNullOrBlank()) {
                        Spacer(Modifier.height(8.dp))
                        Text(queueState.lastMessage)
                    }
                    if (queueState.status != UploadBatchStatus.IDLE) {
                        Spacer(Modifier.height(8.dp))
                        Text("当前上传状态：${queueState.status.statusLabel()}")
                    }
                }
            }
        }
        item {
            FlowRow(
                maxItemsInEachRow = 2,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                StatusCard("待上传", pending, Color(0xFFF7E6B5))
                StatusCard("上传中", uploading, Color(0xFFD5E9FF))
                StatusCard("失败", failed, Color(0xFFF6C2C2))
                StatusCard("已上传", uploaded, Color(0xFFD4EFD6))
            }
        }
        item {
            Button(
                onClick = onCapture,
                modifier = Modifier.fillMaxWidth().height(56.dp)
            ) {
                Text("采集数据")
            }
        }
        item {
            Button(
                onClick = onUpload,
                enabled = user.canUpload,
                modifier = Modifier.fillMaxWidth().height(56.dp)
            ) {
                Text(if (user.canUpload) "上传数据" else "当前账号无上传权限")
            }
        }
    }
}

@Composable
private fun StatusCard(title: String, count: Int, color: Color) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = color)
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(title)
            Text(count.toString(), style = MaterialTheme.typography.headlineMedium)
        }
    }
}

@Composable
private fun FormPickerScreen(
    modifier: Modifier,
    title: String,
    forms: List<FormSummary>,
    onBack: () -> Unit,
    onSelect: (FormSummary) -> Unit
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            TextButton(onClick = onBack) { Text("返回") }
        }
        item {
            Text(title, style = MaterialTheme.typography.headlineSmall)
        }
        items(forms) { form ->
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onSelect(form) }
            ) {
                Column(Modifier.padding(16.dp)) {
                    Text(form.name, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(8.dp))
                    Text("待上传 ${form.pendingCount} 条")
                    Text("失败 ${form.failedCount} 条")
                    Text("默认策略：${form.defaultUploadMode}")
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CaptureScreen(
    modifier: Modifier,
    form: FormSummary,
    initialRecord: CaptureRecord?,
    onBack: () -> Unit,
    onAttachmentsChanged: (List<String>, String?) -> Unit,
    onSave: (locationName: String, textNote: String) -> Unit
) {
    val context = LocalContext.current
    val mediaManager = remember { LocalMediaManager(context) }
    val initialPhotoPaths = remember(initialRecord?.recordId) { initialRecord?.photoPaths.orEmpty() }
    val initialPhotoSet = remember(initialRecord?.recordId) { initialPhotoPaths.toSet() }
    val initialAudioPath = remember(initialRecord?.recordId) { initialRecord?.audioPath }
    var locationName by remember(initialRecord?.recordId) { mutableStateOf(initialRecord?.locationName.orEmpty()) }
    var textNote by remember(initialRecord?.recordId) { mutableStateOf(initialRecord?.textNote.orEmpty()) }
    val photoPaths = remember(initialRecord?.recordId) {
        mutableStateListOf<String>().apply {
            addAll(initialPhotoPaths)
        }
    }
    var audioPath by remember(initialRecord?.recordId) { mutableStateOf(initialRecord?.audioPath) }
    var currentRecordingPath by remember { mutableStateOf<String?>(null) }
    var mediaRecorder by remember { mutableStateOf<MediaRecorder?>(null) }
    var pendingCameraTarget by remember { mutableStateOf<PendingCameraCapture?>(null) }
    var didSave by remember(initialRecord?.recordId) { mutableStateOf(false) }
    var previewExpanded by remember(initialRecord?.recordId) { mutableStateOf(initialPhotoPaths.isNotEmpty()) }
    var selectedPhotoIndex by remember(initialRecord?.recordId) { mutableStateOf(0) }
    var pendingDeletePhotoPath by remember { mutableStateOf<String?>(null) }
    var pendingDeleteAudio by remember { mutableStateOf(false) }
    var pendingGalleryReplaceIndex by remember { mutableStateOf<Int?>(null) }
    var pendingCameraReplaceIndex by remember { mutableStateOf<Int?>(null) }
    var captureHint by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(initialRecord?.recordId) {
        onAttachmentsChanged(photoPaths.toList(), audioPath)
    }

    fun persistAttachmentState(message: String? = null) {
        onAttachmentsChanged(photoPaths.toList(), audioPath)
        if (!message.isNullOrBlank()) {
            captureHint = message
        }
        if (photoPaths.isEmpty()) {
            selectedPhotoIndex = 0
            previewExpanded = false
        } else {
            selectedPhotoIndex = selectedPhotoIndex.coerceIn(0, photoPaths.lastIndex)
        }
    }

    fun removePhoto(path: String) {
        photoPaths.remove(path)
        if (!initialPhotoSet.contains(path)) {
            mediaManager.deleteIfExists(path)
        }
        persistAttachmentState("图片已移除")
    }

    fun replacePhoto(index: Int, newPath: String, message: String) {
        val oldPath = photoPaths.getOrNull(index) ?: return
        photoPaths[index] = newPath
        if (oldPath != newPath && !initialPhotoSet.contains(oldPath)) {
            mediaManager.deleteIfExists(oldPath)
        }
        selectedPhotoIndex = index
        previewExpanded = true
        persistAttachmentState(message)
    }

    val takePhotoLauncher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { success ->
        val target = pendingCameraTarget
        if (success && target != null) {
            if (target.replaceIndex != null) {
                replacePhoto(target.replaceIndex, target.target.filePath, "图片已重拍替换")
            } else if (photoPaths.size < 5) {
                photoPaths.add(target.target.filePath)
                selectedPhotoIndex = photoPaths.lastIndex
                previewExpanded = true
                persistAttachmentState("拍照成功，已加入本地缓存")
            } else {
                mediaManager.deleteIfExists(target.target.filePath)
            }
        } else {
            mediaManager.deleteIfExists(target?.target?.filePath)
        }
        pendingCameraTarget = null
    }

    val cameraPermissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        val replacementIndex = pendingCameraReplaceIndex
        if (granted && (replacementIndex != null || photoPaths.size < 5)) {
            val target = mediaManager.createPhotoCaptureTarget()
            pendingCameraTarget = PendingCameraCapture(target = target, replaceIndex = replacementIndex)
            takePhotoLauncher.launch(target.uri)
        } else if (!granted) {
            captureHint = "未获得相机权限，无法拍照"
        }
        pendingCameraReplaceIndex = null
    }

    val galleryLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetMultipleContents()) { uris ->
        val remaining = 5 - photoPaths.size
        uris.take(remaining).forEach { uri ->
            photoPaths.add(mediaManager.copyImportedImage(uri))
        }
        if (uris.isNotEmpty()) {
            selectedPhotoIndex = photoPaths.lastIndex
            previewExpanded = true
            persistAttachmentState("相册图片已加入本地缓存")
        }
    }

    val replacePhotoLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        val replaceIndex = pendingGalleryReplaceIndex
        pendingGalleryReplaceIndex = null
        if (uri == null || replaceIndex == null) return@rememberLauncherForActivityResult
        val newPath = mediaManager.copyImportedImage(uri)
        replacePhoto(replaceIndex, newPath, "图片已从相册替换")
    }

    val audioPermissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) {
            val output = mediaManager.createAudioFile()
            val recorder = MediaRecorder().apply {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                setAudioEncodingBitRate(128000)
                setAudioSamplingRate(44100)
                setMaxDuration(60_000)
                setOutputFile(output.absolutePath)
                prepare()
                start()
            }
            mediaRecorder = recorder
            currentRecordingPath = output.absolutePath
            captureHint = "录音中，最多 60 秒"
        } else {
            captureHint = "未获得录音权限，无法录音"
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            runCatching { mediaRecorder?.stop() }
            mediaRecorder?.release()
            mediaRecorder = null
            currentRecordingPath?.let(mediaManager::deleteIfExists)
            if (!didSave) {
                photoPaths
                    .filterNot(initialPhotoSet::contains)
                    .forEach(mediaManager::deleteIfExists)
                audioPath
                    ?.takeIf { it != initialAudioPath }
                    ?.let(mediaManager::deleteIfExists)
            }
        }
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            TextButton(onClick = onBack) { Text("返回") }
        }
        item {
            Text(
                if (initialRecord == null) form.name else "编辑: ${form.name}",
                style = MaterialTheme.typography.headlineSmall
            )
        }
        item {
            OutlinedTextField(
                value = locationName,
                onValueChange = { locationName = it.take(50) },
                label = { Text("位置 / 部位名称") },
                modifier = Modifier.fillMaxWidth()
            )
        }
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                ActionButton(
                    modifier = Modifier.weight(1f),
                    icon = Icons.Default.PhotoCamera,
                    label = "拍照",
                    onClick = {
                        if (photoPaths.size < 5) {
                            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                        }
                    }
                )
                ActionButton(
                    modifier = Modifier.weight(1f),
                    icon = Icons.Default.Mic,
                    label = if (mediaRecorder == null) "录音" else "停止",
                    onClick = {
                        if (mediaRecorder == null) {
                            audioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                        } else {
                            runCatching { mediaRecorder?.stop() }
                            mediaRecorder?.release()
                            mediaRecorder = null
                            audioPath = currentRecordingPath
                            currentRecordingPath = null
                            persistAttachmentState("语音已保存到本地缓存")
                        }
                    }
                )
                ActionButton(
                    modifier = Modifier.weight(1f),
                    icon = Icons.Default.Edit,
                    label = "相册",
                    onClick = {
                        if (photoPaths.size < 5) {
                            galleryLauncher.launch("image/*")
                        }
                    }
                )
            }
        }
        item {
            OutlinedTextField(
                value = textNote,
                onValueChange = { textNote = it.take(200) },
                label = { Text("文字备注") },
                modifier = Modifier.fillMaxWidth(),
                supportingText = { Text("${textNote.length}/200") }
            )
        }
        item {
            Text("图片 ${photoPaths.size}/5（至少 1 张），语音 ${if (audioPath == null && currentRecordingPath == null) "未录制" else "已准备"}")
        }
        item {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                photoPaths.forEachIndexed { index, path ->
                    AssistChip(
                        onClick = {
                            selectedPhotoIndex = index
                            previewExpanded = true
                        },
                        label = { Text(File(path).name) },
                        leadingIcon = { Icon(Icons.Default.Edit, contentDescription = "预览图片") }
                    )
                }
                if (audioPath != null) {
                    AssistChip(
                        onClick = { previewExpanded = true },
                        label = { Text(File(audioPath!!).name) },
                        leadingIcon = { Icon(Icons.Default.Edit, contentDescription = "预览语音") }
                    )
                }
            }
        }
        item {
            if (!captureHint.isNullOrBlank()) {
                Card(colors = CardDefaults.cardColors(containerColor = Color(0xFFF6F0D2))) {
                    Text(
                        captureHint!!,
                        modifier = Modifier.padding(12.dp)
                    )
                }
            }
        }
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedButton(
                    onClick = { previewExpanded = !previewExpanded },
                    modifier = Modifier.weight(1f).height(56.dp)
                ) {
                    Text(if (previewExpanded) "收起预览" else "预览附件")
                }
                Button(
                    onClick = {
                        didSave = true
                        onAttachmentsChanged(photoPaths.toList(), audioPath)
                        onSave(locationName, textNote)
                    },
                    modifier = Modifier.weight(1f).height(56.dp),
                    enabled = locationName.isNotBlank() && photoPaths.size in 1..5 && mediaRecorder == null
                ) {
                    Text(if (initialRecord == null) "保存并退出" else "保存修改")
                }
            }
        }
        if (previewExpanded) {
            item {
                Card {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text("采集预览", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Text("位置：${locationName.ifBlank { "未填写" }}")
                        Text("文字备注：${textNote.ifBlank { "未填写" }}")
                        Text("语音：${audioPath?.let { File(it).name } ?: if (currentRecordingPath != null) "录音中" else "未录制"}")

                        if (photoPaths.isEmpty()) {
                            Text("还没有图片，请先拍照或从相册选择。", color = Color(0xFF9D3C3C))
                        } else {
                            Text("图片预览（点击缩略图切换）")
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                itemsIndexed(photoPaths) { index, path ->
                                    Card(
                                        modifier = Modifier
                                            .size(96.dp)
                                            .border(
                                                width = if (index == selectedPhotoIndex) 2.dp else 0.dp,
                                                color = if (index == selectedPhotoIndex) MaterialTheme.colorScheme.primary else Color.Transparent,
                                                shape = RoundedCornerShape(16.dp)
                                            )
                                            .clickable { selectedPhotoIndex = index },
                                        shape = RoundedCornerShape(16.dp)
                                    ) {
                                        AttachmentImage(path = path, modifier = Modifier.fillMaxSize())
                                    }
                                }
                            }

                            photoPaths.getOrNull(selectedPhotoIndex)?.let { selectedPath ->
                                AttachmentPreviewCard(
                                    path = selectedPath,
                                    title = File(selectedPath).name
                                )
                                FlowRow(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    ActionChip("左转 90°", Icons.Default.RotateLeft, onClick = {
                                        runCatching { mediaManager.rotateImage(selectedPath, -90f) }
                                            .onSuccess { captureHint = "图片已左转 90°" }
                                            .onFailure { captureHint = it.message ?: "图片旋转失败" }
                                    })
                                    ActionChip("右转 90°", Icons.Default.RotateRight, onClick = {
                                        runCatching { mediaManager.rotateImage(selectedPath, 90f) }
                                            .onSuccess { captureHint = "图片已右转 90°" }
                                            .onFailure { captureHint = it.message ?: "图片旋转失败" }
                                    })
                                    ActionChip("居中裁剪", Icons.Default.Edit, onClick = {
                                        runCatching { mediaManager.cropImageCenter(selectedPath) }
                                            .onSuccess { captureHint = "图片已裁剪" }
                                            .onFailure { captureHint = it.message ?: "图片裁剪失败" }
                                    })
                                    ActionChip("相册替换", Icons.Default.Refresh, onClick = {
                                        pendingGalleryReplaceIndex = selectedPhotoIndex
                                        replacePhotoLauncher.launch("image/*")
                                    })
                                    ActionChip("重拍替换", Icons.Default.PhotoCamera, onClick = {
                                        pendingCameraReplaceIndex = selectedPhotoIndex
                                        cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                                    })
                                    ActionChip("删除图片", Icons.Default.Delete, onClick = {
                                        pendingDeletePhotoPath = selectedPath
                                    })
                                }
                            }
                        }

                        if (audioPath != null) {
                            Card(colors = CardDefaults.cardColors(containerColor = Color(0xFFF7F5EC))) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(12.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(Modifier.weight(1f)) {
                                        Text("语音备注已保存", fontWeight = FontWeight.Bold)
                                        Text(File(audioPath!!).name, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    }
                                    TextButton(onClick = { pendingDeleteAudio = true }) {
                                        Text("删除语音")
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    pendingDeletePhotoPath?.let { path ->
        ConfirmDialog(
            title = "删除这张图片？",
            message = "删除后本地图片会一并移除，当前采集记录里也不再保留。",
            confirmLabel = "确认删除",
            onConfirm = {
                removePhoto(path)
                pendingDeletePhotoPath = null
            },
            onDismiss = { pendingDeletePhotoPath = null }
        )
    }

    if (pendingDeleteAudio) {
        ConfirmDialog(
            title = "删除语音备注？",
            message = "删除后这条语音不会参与上传，请确认是否移除。",
            confirmLabel = "确认删除",
            onConfirm = {
                if (audioPath != initialAudioPath) {
                    mediaManager.deleteIfExists(audioPath)
                }
                audioPath = null
                pendingDeleteAudio = false
                persistAttachmentState("语音已移除")
            },
            onDismiss = { pendingDeleteAudio = false }
        )
    }
}

@Composable
private fun ActionButton(
    modifier: Modifier,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit
) {
    Card(
        modifier = modifier
            .height(88.dp)
            .clickable(onClick = onClick)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(icon, contentDescription = label)
            Spacer(Modifier.height(8.dp))
            Text(label)
        }
    }
}

@Composable
private fun RecordListScreen(
    modifier: Modifier,
    form: FormSummary,
    canUpload: Boolean,
    records: List<CaptureRecord>,
    onBack: () -> Unit,
    onUpload: () -> Unit,
    onEditRecord: (String) -> Unit
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            TextButton(onClick = onBack) { Text("返回") }
        }
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(form.name, style = MaterialTheme.typography.headlineSmall)
                Button(onClick = onUpload, enabled = canUpload) {
                    Text(if (canUpload) "去上传" else "无上传权限")
                }
            }
        }
        items(records) { record ->
            RecordRow(record = record, onEditRecord = onEditRecord)
        }
    }
}

@Composable
private fun RecordRow(
    record: CaptureRecord,
    onEditRecord: (String) -> Unit
) {
    Card {
        Column(Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(record.locationName, fontWeight = FontWeight.Bold)
                if (record.status == RecordStatus.PENDING || record.status == RecordStatus.FAILED) {
                    TextButton(onClick = { onEditRecord(record.recordId) }) {
                        Text("编辑")
                    }
                }
            }
            Text("${record.photoPaths.size} 张图 / ${if (record.audioPath == null) "无" else "1"} 条语音 / ${if (record.textNote.isBlank()) "无文字" else "有文字"}")
            Text("状态：${record.status.label()}")
            record.failureReason?.let { Text("失败原因：$it", color = Color(0xFF9D3C3C)) }
        }
    }
}

@Composable
@OptIn(ExperimentalLayoutApi::class)
private fun UploadScreen(
    modifier: Modifier,
    user: UserProfile?,
    form: FormSummary?,
    records: List<CaptureRecord>,
    selectedIds: Set<String>,
    queueState: UploadControllerState,
    isNetworkAvailable: Boolean,
    onBack: () -> Unit,
    onToggleSelection: (String, Boolean) -> Unit,
    onStartUpload: () -> Unit,
    onPauseUpload: () -> Unit,
    onResumeUpload: () -> Unit,
    onCancelUpload: () -> Unit,
    onSelectPending: () -> Unit,
    onSelectUploaded: () -> Unit,
    onClearSelection: () -> Unit,
    onDeleteSelected: () -> Unit,
    onClearUploaded: () -> Unit
) {
    val uploadCandidates = records.filter { it.status == RecordStatus.PENDING || it.status == RecordStatus.FAILED }
    val selectedRecords = records.filter { it.recordId in selectedIds }
    val selectedUploadedCount = selectedRecords.count { it.status == RecordStatus.UPLOADED }
    val canUpload = user?.canUpload == true
    val canDeleteCache = user?.canDeleteCache == true
    val sortedRecords = records.sortedWith(
        compareBy<CaptureRecord> { it.status.displayOrder() }.thenByDescending { it.updatedAtEpochMillis }
    )
    var confirmAction by remember { mutableStateOf<UploadConfirmAction?>(null) }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            TextButton(onClick = onBack) { Text("返回") }
        }
        item {
            Card(colors = CardDefaults.cardColors(containerColor = Color(0xFFE9F1FF))) {
                Column(Modifier.padding(16.dp)) {
                    Text(form?.name ?: "未选择表单", fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(8.dp))
                    Text("1. 勾选记录  2. 点击开始上传  3. 成功后可清理本地缓存")
                    Spacer(Modifier.height(8.dp))
                    Text("状态：${queueState.status.statusLabel()}")
                    Text("网络：${if (isNetworkAvailable) "已连接" else "离线"}")
                    Text("待上传 ${uploadCandidates.size} 条 / 已上传 ${records.count { it.status == RecordStatus.UPLOADED }} 条")
                    Text("已完成 ${queueState.completedCount}/${queueState.totalCount}，成功 ${queueState.successCount}，失败 ${queueState.failedCount}")
                    if (isNetworkAvailable && canUpload && uploadCandidates.isNotEmpty() && queueState.status != UploadBatchStatus.RUNNING) {
                        Spacer(Modifier.height(8.dp))
                        Text("网络可用时可点击“继续上传”或“开始上传”处理剩余缓存。")
                    }
                    if (!canUpload || !canDeleteCache) {
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "权限：${if (canUpload) "可上传" else "不可上传"} / ${if (canDeleteCache) "可清缓存" else "不可删缓存"}",
                            color = Color(0xFF765B1E)
                        )
                    }
                    if (queueState.totalBytes > 0L) {
                        Spacer(Modifier.height(10.dp))
                        LinearProgressIndicator(
                            progress = queueState.progressPercent / 100f,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(Modifier.height(6.dp))
                        Text("当前进度 ${queueState.progressPercent}%")
                        queueState.currentLocationName?.let { Text("当前记录：$it") }
                        queueState.currentFileName?.let { Text("当前文件：$it") }
                    }
                    if (!queueState.lastMessage.isNullOrBlank()) {
                        Spacer(Modifier.height(8.dp))
                        Text(queueState.lastMessage)
                    }
                }
            }
        }
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Button(
                    onClick = onStartUpload,
                    enabled = canUpload && uploadCandidates.isNotEmpty() && isNetworkAvailable,
                    modifier = Modifier.weight(1f).height(54.dp)
                ) {
                    Icon(Icons.Default.CloudUpload, contentDescription = "开始上传")
                    Spacer(Modifier.width(8.dp))
                    Text("开始上传")
                }
                OutlinedButton(
                    onClick = onPauseUpload,
                    enabled = queueState.status == UploadBatchStatus.RUNNING,
                    modifier = Modifier.weight(1f).height(54.dp)
                ) {
                    Icon(Icons.Default.Pause, contentDescription = "暂停")
                    Spacer(Modifier.width(8.dp))
                    Text("暂停")
                }
            }
        }
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedButton(
                    onClick = onResumeUpload,
                    enabled = canUpload && isNetworkAvailable && uploadCandidates.isNotEmpty(),
                    modifier = Modifier.weight(1f).height(52.dp)
                ) {
                    Icon(Icons.Default.PlayArrow, contentDescription = "继续")
                    Spacer(Modifier.width(8.dp))
                    Text("继续上传")
                }
                OutlinedButton(
                    onClick = { confirmAction = UploadConfirmAction.CANCEL_BATCH },
                    enabled = queueState.status != UploadBatchStatus.IDLE,
                    modifier = Modifier.weight(1f).height(52.dp)
                ) {
                    Icon(Icons.Default.Stop, contentDescription = "取消")
                    Spacer(Modifier.width(8.dp))
                    Text("取消批次")
                }
            }
        }
        item {
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                ActionChip("全选待上传", Icons.Default.CloudUpload, onSelectPending)
                ActionChip("全选已上传", Icons.Default.PlayArrow, onSelectUploaded)
                ActionChip("清空勾选", Icons.Default.Stop, onClearSelection)
            }
        }
        item {
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                ActionChip(
                    label = "删除选中",
                    icon = Icons.Default.Delete,
                    enabled = canDeleteCache && selectedRecords.isNotEmpty(),
                    onClick = { confirmAction = UploadConfirmAction.DELETE_SELECTED }
                )
                ActionChip(
                    label = "清理已上传",
                    icon = Icons.Default.Delete,
                    enabled = canDeleteCache && selectedUploadedCount > 0,
                    onClick = { confirmAction = UploadConfirmAction.CLEAR_UPLOADED }
                )
            }
        }
        items(sortedRecords) { record ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color.White)
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Checkbox(
                    checked = selectedIds.contains(record.recordId),
                    onCheckedChange = { onToggleSelection(record.recordId, it) }
                )
                Column(Modifier.weight(1f)) {
                    Text(record.locationName, fontWeight = FontWeight.Bold)
                    Text("${record.formName} / ${record.status.label()}")
                    Text("更新时间：${formatEpochMillis(record.updatedAtEpochMillis)}")
                    record.failureReason?.let { Text(it, color = Color(0xFF9D3C3C)) }
                }
            }
        }
        if (uploadCandidates.isEmpty() && records.isEmpty()) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(160.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text("当前表单下没有本地缓存。")
                }
            }
        }
    }

    when (confirmAction) {
        UploadConfirmAction.DELETE_SELECTED -> ConfirmDialog(
            title = "删除选中的本地记录？",
            message = "本次将删除 ${selectedRecords.size} 条本地缓存，相关照片、录音和备注也会一起移除，删除后无法恢复。",
            confirmLabel = "确认删除",
            onConfirm = {
                onDeleteSelected()
                confirmAction = null
            },
            onDismiss = { confirmAction = null }
        )

        UploadConfirmAction.CLEAR_UPLOADED -> ConfirmDialog(
            title = "清理已上传缓存？",
            message = "本次将清理 ${selectedUploadedCount} 条已上传记录，只删除本地缓存，不影响后台已成功的数据。",
            confirmLabel = "确认清理",
            onConfirm = {
                onClearUploaded()
                confirmAction = null
            },
            onDismiss = { confirmAction = null }
        )

        UploadConfirmAction.CANCEL_BATCH -> ConfirmDialog(
            title = "取消当前上传批次？",
            message = "取消后本地缓存会继续保留，稍后仍可重新开始或继续上传。",
            confirmLabel = "确认取消",
            onConfirm = {
                onCancelUpload()
                confirmAction = null
            },
            onDismiss = { confirmAction = null }
        )

        null -> Unit
    }
}

@Composable
private fun ActionChip(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit,
    enabled: Boolean = true
) {
    AssistChip(
        enabled = enabled,
        onClick = onClick,
        label = { Text(label) },
        leadingIcon = { Icon(icon, contentDescription = label) }
    )
}

private fun UploadBatchStatus.statusLabel(): String = when (this) {
    UploadBatchStatus.IDLE -> "空闲"
    UploadBatchStatus.RUNNING -> "上传中"
    UploadBatchStatus.PAUSED -> "已暂停"
}

private fun RecordStatus.label(): String = when (this) {
    RecordStatus.PENDING -> "待上传"
    RecordStatus.UPLOADING -> "上传中"
    RecordStatus.FAILED -> "上传失败"
    RecordStatus.UPLOADED -> "已上传"
}

private fun RecordStatus.displayOrder(): Int = when (this) {
    RecordStatus.FAILED -> 0
    RecordStatus.UPLOADING -> 1
    RecordStatus.PENDING -> 2
    RecordStatus.UPLOADED -> 3
}

private fun formatEpochMillis(value: Long): String {
    val formatter = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.CHINA)
    return formatter.format(Date(value))
}

private data class PendingCameraCapture(
    val target: LocalMediaManager.MediaTarget,
    val replaceIndex: Int?
)

private enum class UploadConfirmAction {
    DELETE_SELECTED,
    CLEAR_UPLOADED,
    CANCEL_BATCH
}

@Composable
private fun ConfirmDialog(
    title: String,
    message: String,
    confirmLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(message) },
        confirmButton = {
            Button(onClick = onConfirm) {
                Text(confirmLabel)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        }
    )
}

@Composable
private fun AttachmentPreviewCard(
    path: String,
    title: String
) {
    Card {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.Bold)
            AttachmentImage(
                path = path,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(4f / 3f)
                    .clip(RoundedCornerShape(16.dp))
            )
        }
    }
}

@Composable
private fun AttachmentImage(
    path: String,
    modifier: Modifier = Modifier
) {
    val modifiedAt = File(path).lastModified()
    val bitmap = remember(path, modifiedAt) { BitmapFactory.decodeFile(path)?.asImageBitmap() }
    if (bitmap != null) {
        Image(
            bitmap = bitmap,
            contentDescription = File(path).name,
            modifier = modifier,
            contentScale = ContentScale.Crop
        )
    } else {
        Box(
            modifier = modifier.background(Color(0xFFECECEC)),
            contentAlignment = Alignment.Center
        ) {
            Text("无法预览图片", color = Color(0xFF6D6D6D))
        }
    }
}
