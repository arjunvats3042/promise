package app.promise.android.ui.haptics

class FakePromiseHaptics : PromiseHaptics {
    val events = mutableListOf<String>()

    override fun light() {
        events += "light"
    }

    override fun confirm() {
        events += "confirm"
    }

    override fun error() {
        events += "error"
    }
}
