// Co-authored-by: OpenAI Codex <noreply@openai.com>
// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.github.aroundteam2026.aroundapp.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.TextFieldColors
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/**
 * Shared outlined-field colours for auth, quest creation, and venue setup: the surface inside, the
 * outline around, and the primary colour once focused.
 *
 * @param disabledContainerColor The inside of a disabled field; Material's default unless given.
 * @param errorContainerColor The inside of a field with an error; the surface, like the others.
 * @param focusedBorderColor The border while the field is focused; venue setup uses its text
 *   colour.
 * @param cursorColor The cursor; Material's default unless given. Venue setup matches it to its
 *   focused border.
 */
@Composable
fun aroundFieldColors(
    disabledContainerColor: Color = Color.Unspecified,
    errorContainerColor: Color = MaterialTheme.colorScheme.surface,
    focusedBorderColor: Color = MaterialTheme.colorScheme.primary,
    cursorColor: Color = Color.Unspecified,
): TextFieldColors =
    OutlinedTextFieldDefaults.colors(
        focusedContainerColor = MaterialTheme.colorScheme.surface,
        unfocusedContainerColor = MaterialTheme.colorScheme.surface,
        disabledContainerColor = disabledContainerColor,
        errorContainerColor = errorContainerColor,
        focusedBorderColor = focusedBorderColor,
        unfocusedBorderColor = MaterialTheme.colorScheme.outline,
        cursorColor = cursorColor,
    )
