package app.promise.android.core.speech

sealed interface SpeechRecognitionState {
    data object Idle : SpeechRecognitionState
    data object PermissionRequired : SpeechRecognitionState
    data object Ready : SpeechRecognitionState
    data class Listening(
        val rmsNormalized: Float = 0f,
        val partialText: String = "",
    ) : SpeechRecognitionState
    data class Finished(
        val finalText: String,
    ) : SpeechRecognitionState
    data class Error(
        val errorCode: Int,
        val message: String,
    ) : SpeechRecognitionState
}
