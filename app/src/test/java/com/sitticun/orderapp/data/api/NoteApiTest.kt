package com.sitticun.orderapp.data.api

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.sitticun.orderapp.data.local.PreferenceManager
import com.sitticun.orderapp.data.model.NoteModel
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class NoteApiTest {
    private lateinit var server: MockWebServer
    private lateinit var client: SupabaseClient
    private val note = NoteModel(id = "note-1", title = "title", content = "text", revision = 4)

    @Before fun setup() {
        server = MockWebServer().also { it.start() }
        val prefs = PreferenceManager(ApplicationProvider.getApplicationContext<Context>())
        prefs.clearSession()
        prefs.accessToken = "test-session"
        client = SupabaseClient(prefs, server.url("/").toString())
    }

    @After fun teardown() { server.shutdown() }

    @Test fun updateIncludesRevisionFilterAndRequestsReturnedRow() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(200).setBody("[{\"id\":\"note-1\"}]"))
        assertEquals(true, client.writeNoteIfUnchanged(note.id, "upsert", note, 4).getOrThrow())
        val request = server.takeRequest()
        assertEquals("PATCH", request.method)
        assertEquals("eq.note-1", request.requestUrl?.queryParameter("id"))
        assertEquals("eq.4", request.requestUrl?.queryParameter("revision"))
        assertEquals("return=representation", request.getHeader("Prefer"))
        assertEquals("Bearer test-session", request.getHeader("Authorization"))
    }

    @Test fun staleRevisionDoesNotReportSuccess() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(200).setBody("[]"))
        assertEquals(false, client.writeNoteIfUnchanged(note.id, "upsert", note, 4).getOrThrow())
    }

    @Test fun insertionDoesNotMergeExistingNotes() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(409).setBody("duplicate"))
        assertTrue(client.writeNoteIfUnchanged(note.id, "upsert", note, 0).isFailure)
        val request = server.takeRequest()
        assertEquals("POST", request.method)
        assertEquals("return=representation", request.getHeader("Prefer"))
    }

    @Test fun deletionRequiresSpecificRevisionAndCannotTargetWholeTable() = runBlocking {
        assertTrue(client.writeNoteIfUnchanged(note.id, "delete", null, 0).isFailure)
        assertEquals(0, server.requestCount)
        server.enqueue(MockResponse().setResponseCode(200).setBody("[{\"id\":\"note-1\"}]"))
        assertTrue(client.writeNoteIfUnchanged(note.id, "delete", null, 4).getOrThrow())
        val request = server.takeRequest()
        assertEquals("DELETE", request.method)
        assertEquals("eq.4", request.requestUrl?.queryParameter("revision"))
        assertEquals("eq.note-1", request.requestUrl?.queryParameter("id"))
    }
}
