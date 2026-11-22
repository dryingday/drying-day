package io.github.dryingday

import androidx.compose.runtime.Composable
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import io.github.dryingday.shared.resources.Res
import io.github.dryingday.shared.resources.big_shoulders_display_bold
import io.github.dryingday.shared.resources.big_shoulders_display_extrabold
import io.github.dryingday.shared.resources.public_sans_bold
import io.github.dryingday.shared.resources.public_sans_regular
import io.github.dryingday.shared.resources.public_sans_semibold
import org.jetbrains.compose.resources.Font

// The two typefaces and the named sizes of docs/bolts/09-washing-line.md. Sizes are in sp so they
// follow the font scale. Not applied to any screen yet. Font(Res.font...) is composable, so each
// of these is too.

/** Big Shoulders Display: the verdict, the day names, the section headings. */
@Composable
fun bigShouldersFamily(): FontFamily = FontFamily(
    Font(Res.font.big_shoulders_display_bold, FontWeight.Bold),
    Font(Res.font.big_shoulders_display_extrabold, FontWeight.ExtraBold),
)

/** Public Sans: everything else. */
@Composable
fun publicSansFamily(): FontFamily = FontFamily(
    Font(Res.font.public_sans_regular, FontWeight.Normal),
    Font(Res.font.public_sans_semibold, FontWeight.SemiBold),
    Font(Res.font.public_sans_bold, FontWeight.Bold),
)

/** The open cloth's verdict: 40 sp ExtraBold, line height 1.02. */
@Composable
fun verdictStyle(): TextStyle = TextStyle(
    fontFamily = bigShouldersFamily(),
    fontWeight = FontWeight.ExtraBold,
    fontSize = 40.sp,
    lineHeight = (40 * 1.02f).sp,
)

/** The day names on the cloths: 15 sp Bold. */
@Composable
fun dayNameStyle(): TextStyle =
    TextStyle(fontFamily = bigShouldersFamily(), fontWeight = FontWeight.Bold, fontSize = 15.sp)

/** Section headings: 22 sp Bold. */
@Composable
fun headingStyle(): TextStyle =
    TextStyle(fontFamily = bigShouldersFamily(), fontWeight = FontWeight.Bold, fontSize = 22.sp)

/** Sentences, tips and times: 15 sp Regular. */
@Composable
fun bodyStyle(): TextStyle =
    TextStyle(fontFamily = publicSansFamily(), fontWeight = FontWeight.Normal, fontSize = 15.sp)

/** Labels: 13 sp SemiBold. */
@Composable
fun labelStyle(): TextStyle =
    TextStyle(fontFamily = publicSansFamily(), fontWeight = FontWeight.SemiBold, fontSize = 13.sp)

/** Buttons: 14 sp Bold. */
@Composable
fun buttonStyle(): TextStyle =
    TextStyle(fontFamily = publicSansFamily(), fontWeight = FontWeight.Bold, fontSize = 14.sp)
