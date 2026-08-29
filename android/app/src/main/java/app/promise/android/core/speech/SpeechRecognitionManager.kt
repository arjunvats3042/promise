package app.promise.android.core.speech

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.core.content.ContextCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SpeechRecognitionManager @Inject constructor(
    @param:ApplicationContext private val context: Context,
) {
    private val mainHandler = Handler(Looper.getMainLooper())
    private var speechRecognizer: SpeechRecognizer? = null

    private val _state = MutableStateFlow<SpeechRecognitionState>(SpeechRecognitionState.Idle)
    val state: StateFlow<SpeechRecognitionState> = _state.asStateFlow()

    private var latestPartialText: String = ""

    fun hasRecordAudioPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.RECORD_AUDIO,
        ) == PackageManager.PERMISSION_GRANTED
    }

    fun isRecognitionAvailable(): Boolean {
        return SpeechRecognizer.isRecognitionAvailable(context)
    }

    fun startListening(locale: Locale = Locale.getDefault()) {
        mainHandler.post {
            if (!hasRecordAudioPermission()) {
                _state.value = SpeechRecognitionState.PermissionRequired
                return@post
            }

            if (!isRecognitionAvailable()) {
                _state.value = SpeechRecognitionState.Error(
                    errorCode = -1,
                    message = "Speech recognition is not available on this device.",
                )
                return@post
            }

            destroyRecognizer()

            latestPartialText = ""
            _state.value = SpeechRecognitionState.Ready

            try {
                speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context).apply {
                    setRecognitionListener(createRecognitionListener())
                }

                val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                    putExtra(
                        RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                        RecognizerIntent.LANGUAGE_MODEL_FREE_FORM,
                    )
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE, locale.toLanguageTag())
                    putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                    putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
                    putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, context.packageName)
                }

                speechRecognizer?.startListening(intent)
            } catch (e: Exception) {
                _state.value = SpeechRecognitionState.Error(
                    errorCode = -2,
                    message = e.localizedMessage ?: "Failed to start speech recognition.",
                )
            }
        }
    }

    fun stopListening() {
        mainHandler.post {
            try {
                speechRecognizer?.stopListening()
            } catch (_: Exception) {
            }
        }
    }

    fun cancelListening() {
        mainHandler.post {
            try {
                speechRecognizer?.cancel()
            } catch (_: Exception) {
            }
            destroyRecognizer()
            _state.value = SpeechRecognitionState.Idle
        }
    }

    fun reset() {
        mainHandler.post {
            destroyRecognizer()
            latestPartialText = ""
            _state.value = SpeechRecognitionState.Idle
        }
    }

    private fun destroyRecognizer() {
        try {
            speechRecognizer?.destroy()
        } catch (_: Exception) {
        }
        speechRecognizer = null
    }

    private fun createRecognitionListener(): RecognitionListener {
        return object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {
                _state.value = SpeechRecognitionState.Listening(
                    rmsNormalized = 0f,
                    partialText = latestPartialText,
                )
            }

            override fun onBeginningOfSpeech() {
                _state.value = SpeechRecognitionState.Listening(
                    rmsNormalized = 0.1f,
                    partialText = latestPartialText,
                )
            }

            override fun onRmsChanged(rmsdB: Float) {
                // rmsdB typically ranges from -2dB (silence) to ~10dB (loud speech).
                // Normalize smoothly to a 0.0f..1.0f range.
                val normalized = ((rmsdB + 2f) / 12f).coerceIn(0f, 1f)
                val currentState = _state.value
                if (currentState is SpeechRecognitionState.Listening) {
                    _state.value = currentState.copy(rmsNormalized = normalized)
                }
            }

            override fun onBufferReceived(buffer: ByteArray?) {}

            override fun onEndOfSpeech() {
                // Speech ended; waiting for final recognition results
            }

            override fun onError(error: Int) {
                val message = when (error) {
                    SpeechRecognizer.ERROR_AUDIO -> "Audio recording error. Please check your microphone."
                    SpeechRecognizer.ERROR_CLIENT -> "Client error occurred."
                    SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Microphone permission is required."
                    SpeechRecognizer.ERROR_NETWORK -> "Network connection error."
                    SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "Network timeout. Please try again."
                    SpeechRecognizer.ERROR_NO_MATCH -> {
                        if (latestPartialText.isNotBlank()) {
                            // If we caught partial results before NO_MATCH, treat partial text as final
                            _state.value = SpeechRecognitionState.Finished(latestPartialText.trim())
                            destroyRecognizer()
                            return
                        } else {
                            "No speech detected. Tap the mic to try again."
                        }
                    }
                    SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "Speech recognizer is busy. Retrying..."
                    SpeechRecognizer.ERROR_SERVER -> "Server error. Please try again."
                    SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> {
                        if (latestPartialText.isNotBlank()) {
                            _state.value = SpeechRecognitionState.Finished(latestPartialText.trim())
                            destroyRecognizer()
                            return
                        } else {
                            "No speech detected before timeout."
                        }
                    }
                    else -> "Speech recognition error ($error)."
                }

                _state.value = SpeechRecognitionState.Error(
                    errorCode = error,
                    message = message,
                )
                destroyRecognizer()
            }

            override fun onResults(results: Bundle?) {
                val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                val text = matches?.firstOrNull()?.takeIf { it.isNotBlank() } ?: latestPartialText
                if (text.isNotBlank()) {
                    _state.value = SpeechRecognitionState.Finished(text.trim())
                } else {
                    _state.value = SpeechRecognitionState.Error(
                        errorCode = SpeechRecognizer.ERROR_NO_MATCH,
                        message = "No speech detected. Tap the mic to try again.",
                    )
                }
                destroyRecognizer()
            }

            override fun onPartialResults(partialResults: Bundle?) {
                val matches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                val text = matches?.firstOrNull()
                if (!text.isNullOrBlank()) {
                    latestPartialText = text
                    val currentState = _state.value
                    if (currentState is SpeechRecognitionState.Listening) {
                        _state.value = currentState.copy(partialText = text)
                    } else {
                        _state.value = SpeechRecognitionState.Listening(
                            rmsNormalized = 0.2f,
                            partialText = text,
                        )
                    }
                }
            }

            override fun onEvent(eventType: Int, params: Bundle?) {}
        }
    }
}
