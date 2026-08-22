package app.promise.android.ui.theme

import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import app.promise.android.R

/**
 * Editorial Serif font family (Newsreader, OFL by Production Type).
 * Used for major display titles, hero headings, and editorial goal titles.
 */
val PromiseSerifFamily = FontFamily(
    Font(R.font.newsreader_regular, FontWeight.Normal),
    Font(R.font.newsreader_medium, FontWeight.Medium),
    Font(R.font.newsreader_semibold, FontWeight.SemiBold),
    Font(R.font.newsreader_italic, FontWeight.Normal, FontStyle.Italic),
)

/**
 * Modern Sans-serif font family (Plus Jakarta Sans, OFL).
 * Used for UI, forms, buttons, tabs, labels, and supporting metadata.
 */
val PromiseSansFamily = FontFamily(
    Font(R.font.plus_jakarta_sans_regular, FontWeight.Normal),
    Font(R.font.plus_jakarta_sans_medium, FontWeight.Medium),
    Font(R.font.plus_jakarta_sans_semibold, FontWeight.SemiBold),
)

val PromiseJakartaFamily = PromiseSansFamily
