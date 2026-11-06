package app.musonic.update

import app.musonic.R
import app.musonic.settings.*

import android.app.Application
import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

internal data class UpdateState(val release: ReleaseInfo? = null, val busy: Boolean = false,
    val progress: Float? = null, val file: File? = null, val message: UiText? = null, val show: Boolean = false)
internal class UpdateViewModel(app: Application) : AndroidViewModel(app) {
    private val repository = UpdateRepository(app)
    private val mutable = MutableStateFlow(UpdateState(release = repository.cached()))
    val state = mutable.asStateFlow()
    init { UpdateWorker.schedule(app); check(false) }
    fun dismiss() { if (!mutable.value.busy) mutable.update { it.copy(show = false) } }
    fun open() { mutable.update { it.copy(show = true) } }
    fun check(force: Boolean = true) {
        if (mutable.value.busy) return
        mutable.update { it.copy(busy = true, message = null) }
        viewModelScope.launch {
            try {
                val release = repository.checkForUpdate(force)
                mutable.update { it.copy(release = release, file = null, show = release != null || it.show,
                    message = if (release == null) uiText(R.string.up_to_date) else null) }
                release?.let { UpdateWorker.notifyUpdate(getApplication(), it) }
            } catch (_: Exception) {
                mutable.update { it.copy(message = if (force) uiText(R.string.update_check_failed) else null) }
            } finally { mutable.update { it.copy(busy = false) } }
        }
    }
    fun download() {
        val release = mutable.value.release ?: return
        if (mutable.value.busy) return
        mutable.update { it.copy(busy = true, progress = 0f, message = null) }
        viewModelScope.launch {
            try {
                val file = repository.download(release) { progress -> mutable.update { it.copy(progress = progress) } }
                mutable.update { it.copy(file = file, message = uiText(R.string.download_complete)) }
            } catch (_: Exception) {
                mutable.update { it.copy(file = null, message = uiText(R.string.download_verify_failed)) }
            } finally { mutable.update { it.copy(busy = false, progress = null) } }
        }
    }
    fun install(context: Context) {
        val info = mutable.value.release ?: return
        val file = mutable.value.file ?: return
        if (mutable.value.busy) return
        mutable.update { it.copy(busy = true) }
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) { repository.verify(file, info) }
                val uri = FileProvider.getUriForFile(context, "${context.packageName}.updates", file)
                context.startActivity(Intent(Intent.ACTION_VIEW).setDataAndType(uri, "application/vnd.android.package-archive")
                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK))
            } catch (_: Exception) {
                mutable.update { it.copy(message = uiText(R.string.installer_failed), file = null) }
            } finally { mutable.update { it.copy(busy = false) } }
        }
    }
}
