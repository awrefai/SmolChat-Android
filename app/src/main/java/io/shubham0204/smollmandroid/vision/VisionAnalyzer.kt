package io.shubham0204.smollmandroid.vision

import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.core.graphics.blue
import androidx.core.graphics.green
import androidx.core.graphics.red
import androidx.palette.graphics.Palette
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.label.ImageLabeling
import com.google.mlkit.vision.label.defaults.ImageLabelerOptions
import io.shubham0204.smollmandroid.util.await
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.koin.core.annotation.Single

@Single
class VisionAnalyzer(
    private val context: Context,
) {
    data class VisionAnalysisResult(
        val description: String,
        val labels: List<String>,
        val width: Int,
        val height: Int,
        val dominantColors: List<String>,
    )

    suspend fun analyzeImage(uri: Uri): VisionAnalysisResult =
        withContext(Dispatchers.Default) {
            val image = InputImage.fromFilePath(context, uri)
            val labeler = ImageLabeling.getClient(ImageLabelerOptions.DEFAULT_OPTIONS)
            val labels = labeler.process(image).await().take(5)
            val labelDescriptions =
                labels
                    .map {
                        val confidence = (it.confidence * 100).roundToInt()
                        "${it.text} ($confidence%)"
                    }
            val description =
                if (labelDescriptions.isEmpty()) {
                    "No prominent subjects detected in the selected image."
                } else {
                    "Detected subjects: ${labelDescriptions.joinToString()}"
                }

            val sizeAndPalette = decodePalette(uri)
            val width = sizeAndPalette?.first ?: 0
            val height = sizeAndPalette?.second ?: 0
            val dominantColors = sizeAndPalette?.third ?: emptyList()

            VisionAnalysisResult(
                description = description,
                labels = labelDescriptions,
                width = width,
                height = height,
                dominantColors = dominantColors,
            )
        }

    private fun decodePalette(uri: Uri): Triple<Int, Int, List<String>>? =
        context.contentResolver.openInputStream(uri)?.use { inputStream ->
            val bitmap = BitmapFactory.decodeStream(inputStream) ?: return@use null
            val palette = Palette.from(bitmap).clearFilters().generate()
            val colors =
                palette.swatches
                    .sortedByDescending { it.population }
                    .take(3)
                    .map { swatch ->
                        val hex = String.format("#%02X%02X%02X", swatch.rgb.red, swatch.rgb.green, swatch.rgb.blue)
                        "$hex"
                    }
            Triple(bitmap.width, bitmap.height, colors)
        }
}
