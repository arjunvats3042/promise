package app.promise.android.core.speech

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SpeechRecognitionStateTest {

    @Test
    fun `test speech recognition state types`() {
        val idle: SpeechRecognitionState = SpeechRecognitionState.Idle
        assertEquals(SpeechRecognitionState.Idle, idle)

        val perm: SpeechRecognitionState = SpeechRecognitionState.PermissionRequired
        assertEquals(SpeechRecognitionState.PermissionRequired, perm)

        val listening: SpeechRecognitionState = SpeechRecognitionState.Listening(
            rmsNormalized = 0.75f,
            partialText = "Call Sarah tomorrow",
        )
        assertTrue(listening is SpeechRecognitionState.Listening)
        val l = listening as SpeechRecognitionState.Listening
        assertEquals(0.75f, l.rmsNormalized, 0.001f)
        assertEquals("Call Sarah tomorrow", l.partialText)

        val finished: SpeechRecognitionState = SpeechRecognitionState.Finished(
            finalText = "Call Sarah tomorrow at 5pm",
        )
        assertTrue(finished is SpeechRecognitionState.Finished)
        assertEquals("Call Sarah tomorrow at 5pm", (finished as SpeechRecognitionState.Finished).finalText)

        val error: SpeechRecognitionState = SpeechRecognitionState.Error(
            errorCode = 7,
            message = "No speech detected",
        )
        assertTrue(error is SpeechRecognitionState.Error)
        val err = error as SpeechRecognitionState.Error
        assertEquals(7, err.errorCode)
        assertEquals("No speech detected", err.message)
    }
}
