package com.github.aroundteam2026.aroundapp.worker

import android.content.Context
import android.util.Log
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.work.Configuration
import androidx.work.ListenableWorker
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.await
import androidx.work.testing.SynchronousExecutor
import androidx.work.testing.TestListenableWorkerBuilder
import androidx.work.testing.WorkManagerTestInitHelper
import androidx.work.workDataOf
import java.io.File
import java.io.IOException
import java.util.UUID
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ProofUploadWorkerTest {

  private class FakeUploader(private val failWith: Exception? = null) : ProofPhotoUploader {
    val uploaded = mutableListOf<String>()

    override suspend fun upload(file: File, remotePath: String): String {
      failWith?.let { throw it }
      uploaded += remotePath
      return remotePath
    }
  }

  private val remotePath = "proofs/user1/quest1.jpg"
  private val originalProvider = ProofUploadWorker.uploaderProvider
  private lateinit var context: Context
  private lateinit var photo: File

  @Before
  fun setUp() {
    context = ApplicationProvider.getApplicationContext()
    val config =
        Configuration.Builder()
            .setMinimumLoggingLevel(Log.DEBUG)
            .setExecutor(SynchronousExecutor())
            .build()
    WorkManagerTestInitHelper.initializeTestWorkManager(context, config)
    photo = File(context.filesDir, "proof-test.jpg").apply { writeBytes(byteArrayOf(1, 2, 3)) }
  }

  @After
  fun tearDown() {
    ProofUploadWorker.uploaderProvider = originalProvider
    photo.delete()
  }

  private fun buildWorker(
      localPath: String? = photo.absolutePath,
      remote: String? = remotePath,
      attempt: Int = 0,
  ): ProofUploadWorker {
    val input =
        workDataOf(
            ProofUploadWorker.KEY_LOCAL_PATH to localPath,
            ProofUploadWorker.KEY_REMOTE_PATH to remote,
        )
    return TestListenableWorkerBuilder<ProofUploadWorker>(context)
        .setInputData(input)
        .setRunAttemptCount(attempt)
        .build()
  }

  private suspend fun awaitState(wm: WorkManager, id: UUID, state: WorkInfo.State): WorkInfo =
      withTimeout(5_000) { wm.getWorkInfoByIdFlow(id).filterNotNull().first { it.state == state } }

  private suspend fun currentInfo(wm: WorkManager, id: UUID): WorkInfo =
      wm.getWorkInfoByIdFlow(id).filterNotNull().first()

  @Test
  fun waitsForNetworkThenUploads() = runBlocking {
    val fake = FakeUploader()
    ProofUploadWorker.uploaderProvider = { fake }
    val wm = WorkManager.getInstance(context)
    val request = ProofUploadWorker.buildRequest(photo.absolutePath, remotePath)

    wm.enqueue(request).await()
    assertEquals(WorkInfo.State.ENQUEUED, currentInfo(wm, request.id).state)
    assertTrue(fake.uploaded.isEmpty())

    WorkManagerTestInitHelper.getTestDriver(context)!!.setAllConstraintsMet(request.id)

    val info = awaitState(wm, request.id, WorkInfo.State.SUCCEEDED)
    assertEquals(WorkInfo.State.SUCCEEDED, info.state)
    assertEquals(remotePath, info.outputData.getString(ProofUploadWorker.KEY_REMOTE_PATH))
    assertEquals(listOf(remotePath), fake.uploaded)
  }

  @Test
  fun retriesWhenUploadFails() = runBlocking {
    ProofUploadWorker.uploaderProvider = { FakeUploader(IOException("offline")) }
    assertEquals(ListenableWorker.Result.retry(), buildWorker(attempt = 0).doWork())
  }

  @Test
  fun failsAfterLastAttempt() = runBlocking {
    ProofUploadWorker.uploaderProvider = { FakeUploader(IOException("offline")) }
    val result = buildWorker(attempt = ProofUploadWorker.MAX_ATTEMPTS - 1).doWork()
    assertTrue(result is ListenableWorker.Result.Failure)
  }

  @Test
  fun failsWhenFileIsMissing() = runBlocking {
    val fake = FakeUploader()
    ProofUploadWorker.uploaderProvider = { fake }
    val result = buildWorker(localPath = File(context.filesDir, "nope.jpg").absolutePath).doWork()
    assertEquals(
        ListenableWorker.Result.failure(
            workDataOf(ProofUploadWorker.KEY_ERROR to ProofUploadWorker.ERROR_FILE_MISSING)
        ),
        result,
    )
    assertTrue(fake.uploaded.isEmpty())
  }

  @Test
  fun failsWhenInputIsMissing() = runBlocking {
    ProofUploadWorker.uploaderProvider = { FakeUploader() }
    val result = buildWorker(remote = null).doWork()
    assertEquals(
        ListenableWorker.Result.failure(
            workDataOf(ProofUploadWorker.KEY_ERROR to ProofUploadWorker.ERROR_BAD_INPUT)
        ),
        result,
    )
  }

  @Test
  fun uniqueWorkNameDependsOnRemotePath() {
    assertEquals("proof-upload:a.jpg", ProofUploadWorker.uniqueWorkName("a.jpg"))
  }
}
