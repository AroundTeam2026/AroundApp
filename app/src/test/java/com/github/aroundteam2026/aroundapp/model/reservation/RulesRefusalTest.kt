// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.github.aroundteam2026.aroundapp.model.reservation

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.storage.StorageException
import java.io.IOException
import java.util.concurrent.TimeoutException
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RulesRefusalTest {

  @Test
  fun storageForbidden_isARefusal() {
    assertTrue(isRulesRefusal(StorageException.fromExceptionAndHttpCode(null, FORBIDDEN)))
  }

  @Test
  fun otherStorageFailure_isNotARefusal() {
    assertFalse(isRulesRefusal(StorageException.fromExceptionAndHttpCode(null, NOT_FOUND)))
  }

  @Test
  fun firestorePermissionDenied_isARefusal() {
    assertTrue(
        isRulesRefusal(
            FirebaseFirestoreException("denied", FirebaseFirestoreException.Code.PERMISSION_DENIED)
        )
    )
  }

  @Test
  fun otherFirestoreFailure_isNotARefusal() {
    assertFalse(
        isRulesRefusal(
            FirebaseFirestoreException("offline", FirebaseFirestoreException.Code.UNAVAILABLE)
        )
    )
  }

  @Test
  fun failuresThatCanPass_areNotRefusals() {
    assertFalse(isRulesRefusal(IOException("offline")))
    assertFalse(isRulesRefusal(TimeoutException("slow")))
  }

  private companion object {
    const val FORBIDDEN = 403
    const val NOT_FOUND = 404
  }
}
