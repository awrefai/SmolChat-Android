package io.shubham0204.smollmandroid.data

enum class AttachmentType {
    IMAGE,
    DOCUMENT,
}

data class StoredAttachment(
    val type: AttachmentType,
    val uri: String,
    val title: String,
    val summary: String,
)
