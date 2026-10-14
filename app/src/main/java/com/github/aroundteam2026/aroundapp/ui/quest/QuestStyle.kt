// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.github.aroundteam2026.aroundapp.ui.quest

import androidx.annotation.DrawableRes
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp

/**
 * One chip's colours.
 *
 * @property container Fills the chip.
 * @property content The chip's text.
 * @property outline Rings the chip, or null for none.
 */
@Immutable
data class ChipColors(val container: Color, val content: Color, val outline: Color? = null)

/**
 * How [QuestChips] look. Each screen that shows a quest builds one from its own style.
 *
 * @property reward The reward chip's colours.
 * @property party The party size chip's colours.
 * @property distance The distance chip's colours.
 * @property textStyle The chips' text.
 * @property shape Every chip's outline.
 * @property spacing Between chips, across and down when they wrap.
 * @property horizontalPadding Either side of a chip's text.
 * @property verticalPadding Above and below a chip's text.
 * @property outlineWidth How thick a chip's [ChipColors.outline] is.
 * @property freeItem The reward chip's colours when the reward is a free item.
 * @property rewardIcon Leads the reward chip's text, or null for none.
 */
@Immutable
data class QuestChipStyle(
    val reward: ChipColors,
    val party: ChipColors,
    val distance: ChipColors,
    val textStyle: TextStyle,
    val shape: Shape,
    val spacing: Dp,
    val horizontalPadding: Dp,
    val verticalPadding: Dp,
    val outlineWidth: Dp,
    val freeItem: ChipColors = reward,
    val rewardIcon: ChipIcon? = null,
)

/**
 * An icon before a chip's text, tinted like the text.
 *
 * @property icon The icon's drawable.
 * @property size The icon's width and height.
 * @property gap Between the icon and the text.
 * @property startPadding Before the icon, in place of [QuestChipStyle.horizontalPadding].
 */
@Immutable
data class ChipIcon(
    @param:DrawableRes val icon: Int,
    val size: Dp,
    val gap: Dp,
    val startPadding: Dp,
)

/**
 * How [QuestAvatar] looks.
 *
 * @property size The avatar's width and height, background included.
 * @property padding Between the background's edge and the avatar.
 * @property background Fills the avatar's background.
 * @property content The colour of single-colour avatars, such as initials.
 * @property shape The background's outline.
 * @property textStyle The initials' text.
 */
@Immutable
data class QuestAvatarStyle(
    val size: Dp,
    val padding: Dp,
    val background: Color,
    val content: Color,
    val shape: Shape,
    val textStyle: TextStyle,
)
