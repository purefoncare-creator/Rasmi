package com.rasmi.purevon.presentation.screen.conversation

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.core.content.FileProvider
import com.rasmi.purevon.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Delegate that handles attachment lifecycle (add, remove, clear)
 * and contact sharing (vCard creation & attachment).
 * Extracted from ConversationViewModel for maintainability.
 */
internal class AttachmentDelegate(
    private val context: Context,
    private val _uiState: MutableStateFlow<ConversationUiState>,
    private val viewModelScope: CoroutineScope,
    private val vCardBuilder: VCardBuilder,
    private val loadContactsIfNeeded: () -> Unit
) {
    companion object {
        private const val TAG = "ConversationViewModel"
        private const val MAX_ATTACHMENTS = 10
        private const val MAX_MMS_SIZE = 3 * 1024 * 1024L // 3MB before compression
        private const val VIDEO_WARNING_SIZE = 900 * 1024L
    }

    fun attachFile(attachment: AttachmentData) {
        viewModelScope.launch(Dispatchers.IO) {
            val newAttachmentSize = try {
                context.contentResolver.openAssetFileDescriptor(Uri.parse(attachment.uri), "r")?.use { afd ->
                    afd.length.takeIf { len -> len >= 0 } ?: 0L
                } ?: 0L
            } catch (e: Exception) {
                0L
            }

            // ✅ FIX #41 + #16: All checks and mutations inside update{} to avoid race condition
            _uiState.update { state ->
                if (state.attachments.size >= MAX_ATTACHMENTS) {
                    return@update state.copy(error = context.getString(R.string.msg_max_attachments))
                }
                val totalSize = state.attachmentsTotalSize + newAttachmentSize
                val warningMsg = if (totalSize > MAX_MMS_SIZE) {
                    if (attachment.mimeType?.startsWith("video/") == true) {
                        context.getString(R.string.msg_video_too_large, (newAttachmentSize / (1024 * 1024)).toInt())
                    } else {
                        context.getString(R.string.msg_attachments_too_large, (totalSize / 1024).toInt())
                    }
                } else if (attachment.mimeType?.startsWith("video/") == true && newAttachmentSize > VIDEO_WARNING_SIZE) {
                    context.getString(R.string.msg_video_will_compress)
                } else {
                    null
                }
                state.copy(
                    attachments = state.attachments + attachment,
                    attachmentsTotalSize = totalSize,
                    error = warningMsg
                )
            }
        }
    }

    fun removeAttachment(uri: String) {
        // Clean up temp files (camera photos, recordings, vCards)
        try {
            val removedAtt = _uiState.value.attachments.find { it.uri == uri }
            removedAtt?.let { att ->
                val parsedUri = Uri.parse(att.uri)
                if (parsedUri.scheme == "file") {
                    val file = File(parsedUri.path ?: return@let)
                    if (file.absolutePath.startsWith(context.cacheDir.absolutePath)) {
                        file.delete()
                    }
                } else if (parsedUri.scheme == "content" && parsedUri.authority == "${context.packageName}.fileprovider") {
                    try {
                        context.contentResolver.query(parsedUri, null, null, null, null)?.use { }
                    } catch (_: Exception) { /* ignore */ }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error cleaning temp file", e)
        }
        _uiState.update { state ->
            val removedSize = try {
                context.contentResolver.openAssetFileDescriptor(Uri.parse(uri), "r")?.use { afd ->
                    afd.length.takeIf { len -> len >= 0 } ?: 0L
                } ?: 0L
            } catch (_: Exception) { 0L }
            state.copy(
                attachments = state.attachments.filter { att -> att.uri != uri },
                attachmentsTotalSize = (state.attachmentsTotalSize - removedSize).coerceAtLeast(0L),
                error = null
            )
        }
    }

    fun clearAllAttachments() {
        _uiState.update {
            it.copy(
                attachments = emptyList(),
                attachmentsTotalSize = 0L
            )
        }
    }

    fun showContactPicker() {
        _uiState.update { it.copy(showContactPickerDialog = true) }
        loadContactsIfNeeded()
    }

    fun hideContactPicker() {
        _uiState.update { it.copy(showContactPickerDialog = false) }
    }

    fun onContactSelected(contact: com.rasmi.purevon.domain.model.Contact) {
        _uiState.update {
            it.copy(
                showContactPickerDialog = false,
                pendingContactPreview = contact
            )
        }
    }

    fun confirmContactShare() {
        val contact = _uiState.value.pendingContactPreview ?: return
        _uiState.update { it.copy(pendingContactPreview = null) }

        viewModelScope.launch(Dispatchers.IO) {
            try {
                val vCardContent = vCardBuilder.buildVCardString(contact)

                // Support Arabic/Unicode names in filename
                val safeName = contact.name
                    .replace("[\\\\/:*?\"<>|]".toRegex(), "_")
                    .trim()
                    .ifBlank { "contact_${System.currentTimeMillis()}" }
                val fileName = "$safeName.vcf"
                val contactsDir = File(context.cacheDir, "contacts").apply { mkdirs() }
                val file = File(contactsDir, fileName)
                file.writeText(vCardContent, Charsets.UTF_8)

                val uri = FileProvider.getUriForFile(
                    context,
                    "${context.packageName}.fileprovider",
                    file
                )

                val attachment = AttachmentData(
                    uri = uri.toString(),
                    fileName = fileName,
                    mimeType = "text/vcard",
                    isImage = false,
                    fileSize = file.length()
                )

                _uiState.update {
                    // ✅ FIX #42: Update attachmentsTotalSize when sharing contact
                    it.copy(
                        attachments = it.attachments + attachment,
                        attachmentsTotalSize = it.attachmentsTotalSize + (attachment.fileSize ?: 0L)
                    )
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error attaching contact", e)
                withContext(Dispatchers.Main) {
                    _uiState.update { it.copy(error = context.getString(R.string.contact_share_error)) }
                }
            }
        }
    }

    fun dismissContactPreview() {
        _uiState.update { it.copy(pendingContactPreview = null) }
    }
}
