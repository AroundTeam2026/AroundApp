// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.github.aroundteam2026.aroundapp.model.connectivity

import android.app.Application
import android.content.Context
import android.content.ContextWrapper
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.NetworkCapabilities.NET_CAPABILITY_INTERNET
import android.net.NetworkCapabilities.NET_CAPABILITY_VALIDATED
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.shadows.ShadowNetwork
import org.robolectric.shadows.ShadowNetworkCapabilities

/**
 * Tests [ConnectivityManagerRepository] on Robolectric's [ConnectivityManager]: each test sets the
 * default network's state, then calls the registered callback as Android would.
 */
@RunWith(AndroidJUnit4::class)
class ConnectivityManagerRepositoryTest {
  private val app: Application = ApplicationProvider.getApplicationContext()
  private val manager = app.getSystemService(ConnectivityManager::class.java)
  private val repository = ConnectivityManagerRepository(app)
  private val wifi = ShadowNetwork.newInstance(1)
  private val mobile = ShadowNetwork.newInstance(2)

  private fun capabilities(vararg capabilities: Int): NetworkCapabilities =
      ShadowNetworkCapabilities.newInstance().also { created ->
        capabilities.forEach { shadowOf(created).addCapability(it) }
      }

  private fun validated() = capabilities(NET_CAPABILITY_INTERNET, NET_CAPABILITY_VALIDATED)

  private fun defaultNetworkHas(capabilities: NetworkCapabilities) =
      shadowOf(manager).setNetworkCapabilities(manager.activeNetwork, capabilities)

  private fun noNetwork() = shadowOf(manager).setActiveNetworkInfo(null)

  /** The callback the repository registered, which Android calls on each change. */
  private fun callback() = shadowOf(manager).networkCallbacks.single()

  /** Collects every state [watched] reports for the rest of the test. */
  private fun TestScope.watch(watched: ConnectivityRepository = repository): List<Boolean> {
    val seen = mutableListOf<Boolean>()
    backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
      watched.isOnline.collect { seen += it }
    }
    return seen
  }

  @Test
  fun startsOnline_whenTheDefaultNetworkReachesTheInternet() = runTest {
    defaultNetworkHas(validated())

    assertTrue(repository.isOnline.first())
  }

  @Test
  fun startsOffline_withoutANetwork() = runTest {
    noNetwork()

    assertFalse(repository.isOnline.first())
  }

  @Test
  fun startsOffline_behindACaptivePortal() = runTest {
    // Connected, but the system couldn't reach the internet through it yet
    defaultNetworkHas(capabilities(NET_CAPABILITY_INTERNET))

    assertFalse(repository.isOnline.first())
  }

  @Test
  fun startsOffline_whenValidatedWithoutInternet() = runTest {
    defaultNetworkHas(capabilities(NET_CAPABILITY_VALIDATED))

    assertFalse(repository.isOnline.first())
  }

  @Test
  fun reportsEachChange() = runTest {
    noNetwork()
    val seen = watch()

    callback().onCapabilitiesChanged(wifi, validated())
    callback().onCapabilitiesChanged(wifi, capabilities(NET_CAPABILITY_INTERNET))
    callback().onCapabilitiesChanged(wifi, validated())
    callback().onLost(wifi)

    assertEquals(listOf(false, true, false, true, false), seen)
  }

  @Test
  fun doesNotRepeatAnUnchangedState() = runTest {
    defaultNetworkHas(validated())
    val seen = watch()

    callback().onCapabilitiesChanged(wifi, validated())
    callback().onCapabilitiesChanged(mobile, validated())

    assertEquals(listOf(true), seen)
  }

  @Test
  fun keepsACallbackThatComesBeforeTheFirstRead() = runTest {
    // Android may call back on its own thread before the repository reads the state: here the
    // network is lost while the callback registers, and the read still sees it
    val losing =
        mockk<ConnectivityManager>(relaxUnitFun = true) {
          every { activeNetwork } returns wifi
          every { getNetworkCapabilities(wifi) } returns validated()
          every {
            registerDefaultNetworkCallback(any<ConnectivityManager.NetworkCallback>())
          } answers { firstArg<ConnectivityManager.NetworkCallback>().onLost(wifi) }
        }
    val context =
        object : ContextWrapper(app) {
          override fun getApplicationContext() = this

          override fun getSystemService(name: String): Any? =
              if (name == Context.CONNECTIVITY_SERVICE) losing else super.getSystemService(name)
        }

    assertEquals(listOf(false), watch(ConnectivityManagerRepository(context)))
  }

  @Test
  fun stopsListening_whenNoOneCollects() = runTest {
    val watching =
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
          repository.isOnline.collect {}
        }
    assertEquals(1, shadowOf(manager).networkCallbacks.size)

    watching.cancel()

    assertTrue(shadowOf(manager).networkCallbacks.isEmpty())
  }
}
