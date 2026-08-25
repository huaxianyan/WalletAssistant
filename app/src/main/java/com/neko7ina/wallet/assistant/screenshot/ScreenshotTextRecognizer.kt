package com.neko7ina.wallet.assistant.screenshot

import android.content.Context
import android.net.Uri
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.chinese.ChineseTextRecognizerOptions

sealed interface ScreenshotRecognitionResult {
    data object Processing : ScreenshotRecognitionResult
    data class Success(val text: String) : ScreenshotRecognitionResult
    data object Cancelled : ScreenshotRecognitionResult
    data object Failed : ScreenshotRecognitionResult
}

class ScreenshotTextRecognizer {
    private val recognizer = TextRecognition.getClient(
        ChineseTextRecognizerOptions.Builder().build(),
    )

    fun recognize(
        context: Context,
        uri: Uri,
        onResult: (ScreenshotRecognitionResult) -> Unit,
    ) {
        val image = runCatching { InputImage.fromFilePath(context, uri) }
            .getOrElse {
                onResult(ScreenshotRecognitionResult.Failed)
                return
            }
        recognizer.process(image)
            .addOnSuccessListener { result ->
                val text = result.text.trim()
                onResult(
                    if (text.isEmpty()) {
                        ScreenshotRecognitionResult.Failed
                    } else {
                        ScreenshotRecognitionResult.Success(text)
                    },
                )
            }
            .addOnFailureListener { onResult(ScreenshotRecognitionResult.Failed) }
    }

    fun close() {
        recognizer.close()
    }
}
