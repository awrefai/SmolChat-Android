package io.shubham0204.smollmandroid.data

import androidx.room.TypeConverter
import java.util.Date
import org.json.JSONArray
import org.json.JSONObject

class Converters {
    @TypeConverter
    fun fromTimestamp(value: Long?): Date? = value?.let { Date(it) }

    @TypeConverter
    fun dateToTimestamp(date: Date?): Long? = date?.time?.toLong()

    @TypeConverter
    fun attachmentsToString(attachments: List<StoredAttachment>?): String =
        if (attachments.isNullOrEmpty()) {
            "[]"
        } else {
            val jsonArray = JSONArray()
            attachments.forEach { attachment ->
                jsonArray.put(
                    JSONObject()
                        .put("type", attachment.type.name)
                        .put("uri", attachment.uri)
                        .put("title", attachment.title)
                        .put("summary", attachment.summary),
                )
            }
            jsonArray.toString()
        }

    @TypeConverter
    fun stringToAttachments(value: String?): List<StoredAttachment> {
        if (value.isNullOrEmpty()) return emptyList()
        val jsonArray = JSONArray(value)
        val attachments = mutableListOf<StoredAttachment>()
        for (i in 0 until jsonArray.length()) {
            val jsonObject = jsonArray.getJSONObject(i)
            val type = AttachmentType.valueOf(jsonObject.getString("type"))
            val uri = jsonObject.getString("uri")
            val title = jsonObject.optString("title")
            val summary = jsonObject.optString("summary")
            attachments.add(StoredAttachment(type, uri, title, summary))
        }
        return attachments
    }

    @TypeConverter
    fun modalityToString(modality: ModelModality?): String = modality?.name ?: ModelModality.TEXT.name

    @TypeConverter
    fun stringToModality(value: String?): ModelModality =
        if (value.isNullOrEmpty()) {
            ModelModality.TEXT
        } else {
            runCatching { ModelModality.valueOf(value) }.getOrDefault(ModelModality.TEXT)
        }
}
