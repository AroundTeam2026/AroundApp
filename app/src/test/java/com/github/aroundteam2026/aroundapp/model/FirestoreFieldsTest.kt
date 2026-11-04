// Co-authored-by: Claude Sonnet 5.5 <noreply@anthropic.com>
package com.github.aroundteam2026.aroundapp.model

import com.google.firebase.firestore.DocumentSnapshot
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Test

/** Tests [required], which every Firestore mapper reads its mandatory fields with. */
class FirestoreFieldsTest {
  private val document =
      mockk<DocumentSnapshot> {
        every { getString("name") } returns "Café"
        every { getString("absent") } returns null
      }

  @Test
  fun returnsTheValueReadForTheField() {
    assertEquals("Café", document.required("name") { getString(it) })
  }

  @Test
  fun failsNamingTheFieldWhenTheValueIsAbsent() {
    val error =
        assertThrows(IllegalArgumentException::class.java) {
          document.required<String>("absent") { getString(it) }
        }

    assertEquals("Missing field absent", error.message)
  }

  @Test
  fun handsTheFieldNameToTheReader() {
    val given = mutableListOf<String>()

    document.required("name") {
      given += it
      getString(it)
    }

    assertEquals(listOf("name"), given)
  }

  @Test
  fun readsFromTheSnapshotItIsCalledOn() {
    assertSame(document, document.required("name") { this })
  }
}
