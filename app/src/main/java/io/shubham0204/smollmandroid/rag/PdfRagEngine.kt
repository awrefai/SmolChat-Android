package io.shubham0204.smollmandroid.rag

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import com.tom_roush.pdfbox.android.PdfBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper
import java.io.IOException
import java.util.Locale
import kotlin.math.min
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.koin.core.annotation.Single

@Single
class PdfRagEngine(
    private val context: Context,
) {
    init {
        PdfBoxResourceLoader.init(context)
    }

    data class DocumentChunk(
        val pageIndex: Int,
        val text: String,
    )

    data class PdfKnowledgeBase(
        val title: String,
        val pageCount: Int,
        val chunks: List<DocumentChunk>,
    )

    data class RagResult(
        val context: String,
        val sources: List<DocumentChunk>,
    )

    suspend fun buildKnowledgeBase(uri: Uri): PdfKnowledgeBase =
        withContext(Dispatchers.IO) {
            context.contentResolver.openInputStream(uri).use { inputStream ->
                if (inputStream == null) {
                    throw IOException("Unable to open PDF stream")
                }
                PDDocument.load(inputStream).use { document ->
                    val stripper = PDFTextStripper()
                    val chunks = mutableListOf<DocumentChunk>()
                    for (page in 1..document.numberOfPages) {
                        stripper.startPage = page
                        stripper.endPage = page
                        val rawText = stripper.getText(document).trim()
                        if (rawText.isNotEmpty()) {
                            chunks += splitIntoChunks(rawText).map { chunk ->
                                DocumentChunk(pageIndex = page - 1, text = chunk)
                            }
                        }
                    }
                    val displayName = getDisplayName(uri) ?: "PDF Document"
                    PdfKnowledgeBase(displayName, document.numberOfPages, chunks)
                }
            }
        }

    suspend fun query(
        knowledgeBase: PdfKnowledgeBase,
        question: String,
    ): RagResult =
        withContext(Dispatchers.Default) {
            val questionTokens = tokenize(question)
            val scored =
                knowledgeBase.chunks.map { chunk ->
                    val chunkTokens = tokenize(chunk.text)
                    val score = cosineSimilarity(questionTokens, chunkTokens)
                    chunk to score
                }
            val topChunks =
                scored
                    .sortedByDescending { it.second }
                    .take(3)
                    .filter { it.second > 0 }
                    .map { it.first }
            val contextText =
                if (topChunks.isEmpty()) {
                    "No relevant context could be retrieved from the attached document."
                } else {
                    topChunks.joinToString(separator = "\n\n") { chunk ->
                        "Page ${chunk.pageIndex + 1}: ${chunk.text.trim()}"
                    }
                }
            RagResult(contextText, topChunks)
        }

    private fun splitIntoChunks(text: String): List<String> {
        val sanitized = text.replace("\n\n", "\n").trim()
        if (sanitized.length <= MAX_CHUNK_CHARACTERS) return listOf(sanitized)
        val chunks = mutableListOf<String>()
        var start = 0
        while (start < sanitized.length) {
            val end = min(start + MAX_CHUNK_CHARACTERS, sanitized.length)
            val substring = sanitized.substring(start, end)
            chunks += substring
            start = end
        }
        return chunks
    }

    private fun tokenize(text: String): Map<String, Int> {
        val tokens = mutableMapOf<String, Int>()
        text
            .lowercase(Locale.US)
            .split(Regex("[^a-z0-9]+"))
            .filter { it.isNotBlank() }
            .forEach { token -> tokens[token] = tokens.getOrDefault(token, 0) + 1 }
        return tokens
    }

    private fun cosineSimilarity(
        first: Map<String, Int>,
        second: Map<String, Int>,
    ): Double {
        if (first.isEmpty() || second.isEmpty()) return 0.0
        val intersection = first.keys.intersect(second.keys)
        if (intersection.isEmpty()) return 0.0
        var dotProduct = 0.0
        intersection.forEach { key ->
            dotProduct += first.getValue(key) * second.getValue(key)
        }
        val firstMagnitude = kotlin.math.sqrt(first.values.sumOf { it * it }.toDouble())
        val secondMagnitude = kotlin.math.sqrt(second.values.sumOf { it * it }.toDouble())
        if (firstMagnitude == 0.0 || secondMagnitude == 0.0) return 0.0
        return dotProduct / (firstMagnitude * secondMagnitude)
    }

    private fun getDisplayName(uri: Uri): String? {
        val cursor = context.contentResolver.query(uri, null, null, null, null) ?: return null
        cursor.use {
            if (it.moveToFirst()) {
                val nameColumn = it.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                if (nameColumn >= 0) {
                    return it.getString(nameColumn)
                }
            }
        }
        return null
    }

    companion object {
        private const val MAX_CHUNK_CHARACTERS = 1200
    }
}
