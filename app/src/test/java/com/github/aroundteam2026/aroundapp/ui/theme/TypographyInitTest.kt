// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.github.aroundteam2026.aroundapp.ui.theme

import androidx.compose.material3.Typography as MaterialTypography
import org.junit.Assert.assertNotNull
import org.junit.Test

/**
 * Tests that the app's [Typography] is whole whichever of it and [AroundTextStyles] loads first.
 * They used to build each other: reading a text style before the theme ever ran built [Typography]
 * from styles that didn't exist yet, and every later screen in the process crashed.
 */
class TypographyInitTest {

  /** Loads the theme's classes afresh, so neither is set up yet, and the rest as usual. */
  private class FreshThemeLoader(parent: ClassLoader) : ClassLoader(parent) {
    override fun loadClass(name: String, resolve: Boolean): Class<*> {
      if (!name.startsWith(THEME_PACKAGE)) return super.loadClass(name, resolve)
      synchronized(this) {
        findLoadedClass(name)?.let {
          return it
        }
        val bytes =
            parent.getResourceAsStream(name.replace('.', '/') + ".class")!!.use { it.readBytes() }
        return defineClass(name, bytes, 0, bytes.size)
      }
    }
  }

  @Test
  fun readingATextStyleBeforeTheThemeLeavesTheTypographyWhole() {
    val loader = FreshThemeLoader(javaClass.classLoader!!)

    // The order that used to break: a screen reads a style before any theme is applied
    Class.forName("$THEME_PACKAGE.AroundTextStyles", true, loader)
    val typography =
        Class.forName("$THEME_PACKAGE.TypeKt", true, loader).getMethod("getTypography").invoke(null)
            as MaterialTypography

    listOf(
            typography.displayLarge,
            typography.headlineSmall,
            typography.titleLarge,
            typography.bodyLarge,
            typography.bodySmall,
            typography.labelLarge,
            typography.labelSmall,
        )
        .forEach { assertNotNull("a text style is missing", it) }
  }

  private companion object {
    const val THEME_PACKAGE = "com.github.aroundteam2026.aroundapp.ui.theme"
  }
}
