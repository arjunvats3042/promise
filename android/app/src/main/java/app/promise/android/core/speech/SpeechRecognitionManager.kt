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

    private var isActivelyListening: Boolean = false
    private var accumulatedText: String = ""
    private var currentSegmentPartial: String = ""
    private var currentLocale: Locale = Locale.getDefault()

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

            currentLocale = locale
            isActivelyListening = true
            accumulatedText = ""
            currentSegmentPartial = ""
            _state.value = SpeechRecognitionState.Ready

            startListeningInternal()
        }
    }

    private fun startListeningInternal() {
        if (!isActivelyListening) return

        destroyRecognizer()

        try {
            speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context).apply {
                setRecognitionListener(createRecognitionListener())
            }

            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(
                    RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                    RecognizerIntent.LANGUAGE_MODEL_FREE_FORM,
                )
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, currentLocale.toLanguageTag())
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
                putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, context.packageName)

                // Generous 6-second silence threshold so the mic doesn't abruptly cut off between phrases
                putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 6000L)
                putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 5000L)
                putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_MINIMUM_LENGTH_MILLIS, 5000L)

                // Fallback OEM compatibility keys
                putExtra("android.speech.extras.SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS", 6000L)
                putExtra("android.speech.extras.SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS", 5000L)
                putExtra("android.speech.extras.SPEECH_INPUT_MINIMUM_LENGTH_MILLIS", 5000L)
                putExtra("android.speech.extra.DICTATION_MODE", true)
            }

            speechRecognizer?.startListening(intent)
        } catch (e: Exception) {
            if (isActivelyListening) {
                _state.value = SpeechRecognitionState.Error(
                    errorCode = -2,
                    message = e.localizedMessage ?: "Failed to start speech recognition.",
                )
                isActivelyListening = false
            }
        }
    }

    fun stopListening() {
        mainHandler.post {
            isActivelyListening = false
            try {
                speechRecognizer?.stopListening()
            } catch (_: Exception) {
            }

            val fullTranscript = getFullTranscript().trim()
            if (fullTranscript.isNotBlank()) {
                _state.value = SpeechRecognitionState.Finished(fullTranscript)
            } else {
                _state.value = SpeechRecognitionState.Idle
            }
            destroyRecognizer()
        }
    }

    fun cancelListening() {
        mainHandler.post {
            isActivelyListening = false
            try {
                speechRecognizer?.cancel()
            } catch (_: Exception) {
            }
            destroyRecognizer()
            accumulatedText = ""
            currentSegmentPartial = ""
            _state.value = SpeechRecognitionState.Idle
        }
    }

    fun reset() {
        mainHandler.post {
            isActivelyListening = false
            destroyRecognizer()
            accumulatedText = ""
            currentSegmentPartial = ""
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

    private fun getFullTranscript(): String {
        return when {
            accumulatedText.isBlank() -> currentSegmentPartial
            currentSegmentPartial.isBlank() -> accumulatedText
            else -> "$accumulatedText $currentSegmentPartial"
        }.trim()
    }

    private fun createRecognitionListener(): RecognitionListener {
        return object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {
                if (isActivelyListening) {
                    _state.value = SpeechRecognitionState.Listening(
                        rmsNormalized = 0f,
                        partialText = getFullTranscript(),
                    )
                }
            }

            override fun onBeginningOfSpeech() {
                if (isActivelyListening) {
                    _state.value = SpeechRecognitionState.Listening(
                        rmsNormalized = 0.1f,
                        partialText = getFullTranscript(),
                    )
                }
            }

            override fun onRmsChanged(rmsdB: Float) {
                if (isActivelyListening) {
                    val normalized = ((rmsdB + 2f) / 12f).coerceIn(0f, 1f)
                    val currentState = _state.value
                    if (currentState is SpeechRecognitionState.Listening) {
                        _state.value = currentState.copy(rmsNormalized = normalized)
                    }
                }
            }

            override fun onBufferReceived(buffer: ByteArray?) {}

            override fun onEndOfSpeech() {
                // Speech pause detected. Recognizer handles silence threshold or restarts for continuous capture.
            }

            override fun onError(error: Int) {
                if (!isActivelyListening) return

                // If it's just silence timeout or temporary pause between phrases, seamlessly restart listening
                if (error == SpeechRecognizer.ERROR_NO_MATCH || error == SpeechRecognizer.ERROR_SPEECH_TIMEOUT) {
                    if (isActivelyListening) {
                        mainHandler.postDelayed({
                            if (isActivelyListening) {
                                startListeningInternal()
                            }
                        }, 200)
                        return
                    }
                }

                if (error == SpeechRecognizer.ERROR_RECOGNIZER_BUSY || error == SpeechRecognizer.ERROR_CLIENT) {
                    mainHandler.postDelayed({
                        if (isActivelyListening) {
                            startListeningInternal()
                        }
                    }, 300)
                    return
                }

                val message = when (error) {
                    SpeechRecognizer.ERROR_AUDIO -> "Audio recording error. Please check your microphone."
                    SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Microphone permission is required."
                    SpeechRecognizer.ERROR_NETWORK -> "Network connection error."
                    SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "Network timeout. Please try again."
                    SpeechRecognizer.ERROR_SERVER -> "Server error. Please try again."
                    else -> "Speech recognition error ($error)."
                }

                val currentFull = getFullTranscript()
                if (currentFull.isNotBlank()) {
                    _state.value = SpeechRecognitionState.Finished(currentFull)
                } else {
                    _state.value = SpeechRecognitionState.Error(
                        errorCode = error,
                        message = message,
                    )
                }
                isActivelyListening = false
                destroyRecognizer()
            }

            override fun onResults(results: Bundle?) {
                if (!isActivelyListening) return

                val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                val text = matches?.firstOrNull()?.trim()
                if (!text.isNullOrBlank()) {
                    accumulatedText = if (accumulatedText.isBlank()) text else "$accumulatedText $text"
                    currentSegmentPartial = ""
                }

                val fullText = getFullTranscript()
                if (isActivelyListening) {
                    // Update listening state and keep the microphone open for further speech
                    _state.value = SpeechRecognitionState.Listening(
                        rmsNormalized = 0f,
                        partialText = fullText,
                    )
                    mainHandler.postDelayed({
                        if (isActivelyListening) {
                            startListeningInternal()
                        }
                    }, 150)
                } else {
                    if (fullText.isNotBlank()) {
                        _state.value = SpeechRecognitionState.Finished(fullText)
                    }
                    destroyRecognizer()
                }
            }

            override fun onPartialResults(partialResults: Bundle?) {
                if (!isActivelyListening) return

                val matches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                val text = matches?.firstOrNull()?.trim()
                if (!text.isNullOrBlank()) {
                    currentSegmentPartial = text
                    _state.value = SpeechRecognitionState.Listening(
                        rmsNormalized = 0.2f,
                        partialText = getFullTranscript(),
                    )
                }
            }

            override fun onEvent(eventType: Int, params: Bundle?) {}
        }
    }
}
