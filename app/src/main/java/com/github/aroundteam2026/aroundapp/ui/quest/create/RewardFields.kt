// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.github.aroundteam2026.aroundapp.ui.quest.create

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.github.aroundteam2026.aroundapp.R
import com.github.aroundteam2026.aroundapp.model.quest.DiscountUnit
import com.github.aroundteam2026.aroundapp.model.quest.QuestLimits
import com.github.aroundteam2026.aroundapp.resources.C
import com.github.aroundteam2026.aroundapp.ui.quest.currentLocale
import com.github.aroundteam2026.aroundapp.ui.quest.formatRewardAmountInput
import com.github.aroundteam2026.aroundapp.ui.theme.AroundCorners
import com.github.aroundteam2026.aroundapp.ui.theme.AroundSpacing
import com.github.aroundteam2026.aroundapp.ui.theme.AroundTextStyles
import com.github.aroundteam2026.aroundapp.ui.theme.AroundTheme
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/** What the reward fields do when the venue changes them; each does nothing by default. */
class QuestRewardActions(
    val onTypeSelect: (RewardType?) -> Unit = {},
    val onDiscountAmountChange: (String) -> Unit = {},
    val onDiscountUnitChange: (DiscountUnit) -> Unit = {},
    val onItemNameChange: (String) -> Unit = {},
    val onSpecificsChange: (String) -> Unit = {},
    val onDescriptionChange: (String) -> Unit = {},
    val onExpiryDateChange: (LocalDate?) -> Unit = {},
)

/**
 * The optional reward explorers get: no reward, or one option per [RewardType] with the picked
 * type's fields in a card.
 */
@Composable
fun RewardFields(reward: RewardFormState, actions: QuestRewardActions) {
  Column {
    HorizontalDivider(color = MaterialTheme.colorScheme.outline)
    Spacer(Modifier.height(AroundSpacing.SectionGap))
    OptionalLabel(stringResource(R.string.create_quest_reward_type))
    Spacer(Modifier.height(AroundSpacing.Small))
    NoRewardOption(selected = reward.type == null, onClick = { actions.onTypeSelect(null) })
    Spacer(Modifier.height(AroundSpacing.Small))
    RewardTypeOptions(reward.type, actions.onTypeSelect)
    reward.type?.let {
      Spacer(Modifier.height(AroundSpacing.Medium))
      RewardDetails(reward, it, actions)
    }
  }
}

/** A field label followed by a muted "Optional". */
@Composable
private fun OptionalLabel(text: String) {
  Row(
      horizontalArrangement = Arrangement.spacedBy(AroundSpacing.SegmentGap),
      verticalAlignment = Alignment.Bottom,
  ) {
    FieldLabel(text)
    Text(
        stringResource(R.string.create_quest_reward_optional),
        style = AroundTextStyles.Small,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
  }
}

/** The full-width option for a quest without a reward, with its trophy. */
@Composable
private fun NoRewardOption(selected: Boolean, onClick: () -> Unit) {
  SelectableOption(
      selected = selected,
      onClick = onClick,
      tag = C.Tag.CREATE_QUEST_REWARD_NONE,
      shape = MaterialTheme.shapes.small,
      padding =
          PaddingValues(horizontal = AroundSpacing.InsetPadding, vertical = AroundSpacing.Medium),
      modifier = Modifier.fillMaxWidth(),
  ) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(AroundSpacing.Medium),
        verticalAlignment = Alignment.CenterVertically,
    ) {
      Icon(
          painterResource(R.drawable.ic_trophy),
          contentDescription = null,
          tint =
              if (selected) AroundTheme.colors.accentText
              else MaterialTheme.colorScheme.onSurfaceVariant,
      )
      Column(verticalArrangement = Arrangement.spacedBy(OPTION_TEXT_GAP)) {
        OptionText(
            stringResource(R.string.create_quest_reward_none),
            stringResource(R.string.create_quest_reward_none_hint),
            selected,
        )
      }
    }
  }
}

/** One option per [RewardType], side by side. */
@Composable
private fun RewardTypeOptions(selected: RewardType?, onSelect: (RewardType?) -> Unit) {
  Row(
      Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.spacedBy(AroundSpacing.Small),
  ) {
    RewardType.entries.forEach { type ->
      val isSelected = type == selected
      val (title, tag) =
          when (type) {
            RewardType.DISCOUNT ->
                R.string.create_quest_reward_discount to C.Tag.CREATE_QUEST_REWARD_DISCOUNT
            RewardType.FREE_ITEM ->
                R.string.create_quest_reward_free_item to C.Tag.CREATE_QUEST_REWARD_FREE_ITEM
            RewardType.OTHER ->
                R.string.create_quest_reward_other to C.Tag.CREATE_QUEST_REWARD_OTHER
          }
      SelectableOption(
          selected = isSelected,
          onClick = { onSelect(type) },
          tag = tag,
          shape = MaterialTheme.shapes.small,
          padding = PaddingValues(vertical = AroundSpacing.Large),
          modifier = Modifier.weight(1f),
          horizontalAlignment = Alignment.CenterHorizontally,
      ) {
        Text(
            stringResource(title),
            style = AroundTextStyles.Subtitle,
            color =
                if (isSelected) AroundTheme.colors.accentText
                else MaterialTheme.colorScheme.onSurface,
        )
      }
    }
  }
}

/** The fields of the picked reward [type], on a panel card. */
@Composable
private fun RewardDetails(
    reward: RewardFormState,
    type: RewardType,
    actions: QuestRewardActions,
) {
  Surface(
      shape = MaterialTheme.shapes.small,
      color = AroundTheme.colors.panel,
      border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
  ) {
    Column(
        Modifier.fillMaxWidth().padding(AroundSpacing.InsetPadding),
        verticalArrangement = Arrangement.spacedBy(AroundSpacing.Medium),
    ) {
      when (type) {
        RewardType.DISCOUNT -> {
          DiscountAmount(reward, actions)
          RewardSpecifics(
              reward,
              stringResource(R.string.create_quest_reward_discount_specifics_hint),
              actions.onSpecificsChange,
          )
        }
        RewardType.FREE_ITEM -> {
          QuestTextField(
              label = stringResource(R.string.create_quest_reward_free_item),
              hint = stringResource(R.string.create_quest_reward_item_hint),
              value = reward.itemName,
              onValueChange = actions.onItemNameChange,
              error = reward.itemNameError,
              maxLength = QuestLimits.REWARD_ITEM_MAX_LENGTH,
              singleLine = true,
              tag = C.Tag.CREATE_QUEST_REWARD_ITEM,
              errorTag = C.Tag.CREATE_QUEST_REWARD_ITEM_ERROR,
              counterTag = C.Tag.CREATE_QUEST_REWARD_ITEM_COUNTER,
          )
          RewardSpecifics(
              reward,
              stringResource(R.string.create_quest_reward_specifics_hint),
              actions.onSpecificsChange,
          )
        }
        RewardType.OTHER ->
            QuestTextField(
                label = stringResource(R.string.create_quest_reward_description),
                hint = stringResource(R.string.create_quest_reward_description_hint),
                value = reward.description,
                onValueChange = actions.onDescriptionChange,
                error = reward.descriptionError,
                maxLength = QuestLimits.REWARD_DESCRIPTION_MAX_LENGTH,
                singleLine = false,
                tag = C.Tag.CREATE_QUEST_REWARD_DESCRIPTION,
                errorTag = C.Tag.CREATE_QUEST_REWARD_DESCRIPTION_ERROR,
                counterTag = C.Tag.CREATE_QUEST_REWARD_DESCRIPTION_COUNTER,
            )
      }
      RewardExpiry(reward.expiryDate, reward.expiryInPast, actions.onExpiryDateChange)
    }
  }
}

/** The discount amount beside its unit, with the amount's error below. */
@Composable
private fun DiscountAmount(reward: RewardFormState, actions: QuestRewardActions) {
  val label = stringResource(R.string.create_quest_reward_amount)
  val error = reward.discountAmountError
  Column(verticalArrangement = Arrangement.spacedBy(AroundSpacing.Small)) {
    FieldLabel(label)
    Row(
        horizontalArrangement = Arrangement.spacedBy(AroundSpacing.CompactGap),
        verticalAlignment = Alignment.CenterVertically,
    ) {
      QuestInputField(
          label = label,
          hint = stringResource(R.string.create_quest_reward_amount_hint),
          value = reward.discountAmount,
          onValueChange = actions.onDiscountAmountChange,
          isError = error != null,
          singleLine = true,
          keyboardOptions =
              KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Next),
          modifier = Modifier.weight(1f).testTag(C.Tag.CREATE_QUEST_REWARD_AMOUNT),
      )
      DiscountUnitToggle(reward.discountUnit, actions.onDiscountUnitChange)
    }
    error?.let {
      QuestFieldError(
          discountErrorText(it, reward.discountUnit),
          C.Tag.CREATE_QUEST_REWARD_AMOUNT_ERROR,
      )
    }
  }
}

/** What [error] tells the venue to fix in the discount amount, in [unit]. */
@Composable
private fun discountErrorText(error: DiscountAmountError, unit: DiscountUnit): String {
  // Written as the amount field accepts it, so the venue can type the limit back in
  val locale = currentLocale()
  return when (error) {
    DiscountAmountError.BLANK -> stringResource(R.string.create_quest_blank)
    DiscountAmountError.NOT_A_NUMBER ->
        stringResource(R.string.create_quest_reward_amount_not_a_number)
    DiscountAmountError.TOO_MANY_DECIMALS ->
        stringResource(
            R.string.create_quest_reward_amount_too_many_decimals,
            QuestLimits.MAX_DISCOUNT_DECIMALS.toString(),
        )
    DiscountAmountError.TOO_SMALL ->
        stringResource(
            R.string.create_quest_reward_amount_too_small,
            formatRewardAmountInput(QuestLimits.MIN_DISCOUNT_AMOUNT, unit, locale),
        )
    DiscountAmountError.TOO_LARGE ->
        stringResource(
            when (unit) {
              DiscountUnit.PERCENT -> R.string.create_quest_reward_amount_too_large_percent
              DiscountUnit.CHF -> R.string.create_quest_reward_amount_too_large_chf
            },
            formatRewardAmountInput(QuestLimits.maxDiscountAmount(unit), unit, locale),
        )
  }
}

/** Percent or francs, as a segmented control: the chosen unit is filled in purple. */
@Composable
private fun DiscountUnitToggle(selected: DiscountUnit, onSelect: (DiscountUnit) -> Unit) {
  Row(
      Modifier.background(MaterialTheme.colorScheme.surface, AroundCorners.InsetField)
          .border(1.dp, MaterialTheme.colorScheme.outline, AroundCorners.InsetField)
          .padding(AroundSpacing.ExtraSmall)
          .selectableGroup(),
      horizontalArrangement = Arrangement.spacedBy(AroundSpacing.ExtraSmall),
  ) {
    DiscountUnit.entries.forEach { unit ->
      val isSelected = unit == selected
      val (title, tag) =
          when (unit) {
            DiscountUnit.PERCENT ->
                R.string.create_quest_reward_unit_percent to C.Tag.CREATE_QUEST_REWARD_UNIT_PERCENT
            DiscountUnit.CHF ->
                R.string.create_quest_reward_unit_chf to C.Tag.CREATE_QUEST_REWARD_UNIT_CHF
          }
      Surface(
          selected = isSelected,
          onClick = { onSelect(unit) },
          shape = AroundCorners.SegmentedOption,
          color = if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent,
          modifier =
              Modifier.height(UNIT_HEIGHT).testTag(tag).semantics { role = Role.RadioButton },
      ) {
        Box(Modifier.padding(horizontal = AroundSpacing.InsetPadding), Alignment.Center) {
          Text(
              stringResource(title),
              style = AroundTextStyles.FieldLabel,
              color =
                  if (isSelected) MaterialTheme.colorScheme.onPrimary
                  else MaterialTheme.colorScheme.onSurface,
          )
        }
      }
    }
  }
}

/**
 * The optional last day of the reward: a field that opens a date picker, "Set date" while empty or
 * a button to clear it once set, and an error if the day has passed.
 */
@Composable
private fun RewardExpiry(date: LocalDate?, inPast: Boolean, onChange: (LocalDate?) -> Unit) {
  var picking by rememberSaveable { mutableStateOf(false) }
  val muted = MaterialTheme.colorScheme.onSurfaceVariant
  Column(verticalArrangement = Arrangement.spacedBy(AroundSpacing.Small)) {
    OptionalLabel(stringResource(R.string.create_quest_reward_expires))
    Surface(
        onClick = { picking = true },
        shape = MaterialTheme.shapes.small,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        modifier = Modifier.fillMaxWidth().testTag(C.Tag.CREATE_QUEST_REWARD_EXPIRY),
    ) {
      Row(
          Modifier.height(EXPIRY_HEIGHT)
              .padding(start = AroundSpacing.InsetPadding, end = AroundSpacing.Medium),
          horizontalArrangement = Arrangement.spacedBy(AroundSpacing.CompactGap),
          verticalAlignment = Alignment.CenterVertically,
      ) {
        Icon(
            painterResource(R.drawable.ic_calendar),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
        )
        Text(
            date?.format(
                DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(currentLocale())
            ) ?: stringResource(R.string.create_quest_reward_no_expiry),
            style = AroundTextStyles.Body,
            color = if (date == null) muted else MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
        )
        if (date == null) {
          Text(
              stringResource(R.string.create_quest_reward_set_date),
              style = AroundTextStyles.FieldLabel,
              color = AroundTheme.colors.accentText,
          )
        } else {
          Surface(
              onClick = { onChange(null) },
              shape = CircleShape,
              color = AroundTheme.colors.panel,
              modifier = Modifier.size(CLEAR_SIZE).testTag(C.Tag.CREATE_QUEST_REWARD_EXPIRY_CLEAR),
          ) {
            Box(contentAlignment = Alignment.Center) {
              Icon(
                  painterResource(R.drawable.ic_clear_small),
                  contentDescription = stringResource(R.string.create_quest_reward_expiry_clear),
                  tint = muted,
              )
            }
          }
        }
      }
    }
    if (inPast) {
      QuestFieldError(
          stringResource(R.string.create_quest_reward_expiry_past),
          C.Tag.CREATE_QUEST_REWARD_EXPIRY_ERROR,
      )
    }
  }
  if (picking) {
    ExpiryDatePicker(
        initial = date,
        onDismiss = { picking = false },
        onConfirm = {
          onChange(it)
          picking = false
        },
    )
  }
}

/** A date picker from today on, starting at [initial] or today. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ExpiryDatePicker(
    initial: LocalDate?,
    onDismiss: () -> Unit,
    onConfirm: (LocalDate) -> Unit,
) {
  val today = remember { LocalDate.now() }
  val state =
      rememberDatePickerState(
          initialSelectedDateMillis = (initial ?: today).toUtcMillis(),
          selectableDates = selectableFrom(today),
      )
  DatePickerDialog(
      onDismissRequest = onDismiss,
      confirmButton = {
        TextButton(
            onClick = { state.selectedDateMillis?.let { onConfirm(it.toUtcDate()) } },
            enabled = state.selectedDateMillis != null,
            modifier = Modifier.testTag(C.Tag.CREATE_QUEST_REWARD_EXPIRY_CONFIRM),
        ) {
          Text(stringResource(android.R.string.ok))
        }
      },
      dismissButton = {
        TextButton(onClick = onDismiss) { Text(stringResource(android.R.string.cancel)) }
      },
  ) {
    DatePicker(state)
  }
}

/** The days of the date picker from [today] on, and the years that contain them. */
@OptIn(ExperimentalMaterial3Api::class)
internal fun selectableFrom(today: LocalDate): SelectableDates =
    object : SelectableDates {
      override fun isSelectableDate(utcTimeMillis: Long) =
          !utcTimeMillis.toUtcDate().isBefore(today)

      override fun isSelectableYear(year: Int) = year >= today.year
    }

/** The date picker counts days as midnights in UTC. */
internal fun LocalDate.toUtcMillis() = atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()

private fun Long.toUtcDate(): LocalDate =
    Instant.ofEpochMilli(this).atZone(ZoneOffset.UTC).toLocalDate()

/** The optional details of the reward, with the [hint] of its type. */
@Composable
private fun RewardSpecifics(reward: RewardFormState, hint: String, onChange: (String) -> Unit) {
  QuestTextField(
      label = stringResource(R.string.create_quest_reward_specifics),
      hint = hint,
      value = reward.specifics,
      onValueChange = onChange,
      error = reward.specificsError,
      maxLength = QuestLimits.REWARD_SPECIFICS_MAX_LENGTH,
      singleLine = false,
      tag = C.Tag.CREATE_QUEST_REWARD_SPECIFICS,
      errorTag = C.Tag.CREATE_QUEST_REWARD_SPECIFICS_ERROR,
      counterTag = C.Tag.CREATE_QUEST_REWARD_SPECIFICS_COUNTER,
  )
}

private val UNIT_HEIGHT: Dp = 36.dp
private val EXPIRY_HEIGHT: Dp = 46.dp
private val CLEAR_SIZE: Dp = 28.dp
