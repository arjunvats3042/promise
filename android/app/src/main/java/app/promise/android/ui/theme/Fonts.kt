package app.promise.android.ui.theme

import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import app.promise.android.R

/**
 * Candidate typeface. Bundled for Pixel comparison only.
 * [PromiseFontDecision.useCandidateFont] gates whether it becomes the default.
 */
val PromiseJakartaFamily = FontFamily(
    Font(R.font.plus_jakarta_sans_regular, FontWeight.Normal),
    Font(R.font.plus_jakarta_sans_medium, FontWeight.Medium),
    Font(R.font.plus_jakarta_sans_semibold, FontWeight.SemiBold),
)
