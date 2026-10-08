// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.github.aroundteam2026.aroundapp.ui.map

import android.content.res.Resources
import androidx.annotation.RawRes
import com.github.aroundteam2026.aroundapp.R
import org.json.JSONArray

/**
 * The map's Google Maps style for the light or dark theme, as the JSON the Maps SDK reads: the
 * theme's colours, then the rules hiding Google's place, business and road icons, which look like
 * pins and would sit under the quest pins. Both themes share one copy of those rules.
 */
fun mapStyle(resources: Resources, darkTheme: Boolean): String {
  val palette = if (darkTheme) R.raw.map_style_dark else R.raw.map_style_light
  val style = JSONArray()
  // The Maps SDK applies rules in order, so the hiding comes last, where no colour rule undoes it
  for (rules in listOf(palette, R.raw.map_style_hidden)) {
    val array = resources.readJsonArray(rules)
    for (i in 0 until array.length()) style.put(array.getJSONObject(i))
  }
  return style.toString()
}

private fun Resources.readJsonArray(@RawRes id: Int) =
    openRawResource(id).bufferedReader().use { JSONArray(it.readText()) }
