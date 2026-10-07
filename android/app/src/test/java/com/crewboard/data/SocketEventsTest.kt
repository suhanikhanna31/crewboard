package com.crewboard.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SocketEventsTest {
    @Test
    fun parsesTaskAssigned() {
        val json = """{"type":"task_assigned","task":{"id":7,"title":"Tie rebar","status":"assigned","assigned_to":2,"required_skill":"rebar","zone":"A","priority":3,"created_at":"2026-01-01T00:00:00"}}"""
        val event = parseEvent(json) as SocketEvent.TaskAssigned
        assertEquals(7, event.task.id)
        assertEquals(2, event.task.assignedTo)
        assertEquals("rebar", event.task.requiredSkill)
    }

    @Test
    fun parsesTelemetry() {
        val event = parseEvent("""{"type":"telemetry","resource_id":4,"battery":61.5,"x":0,"y":0,"state":"idle"}""")
        assertTrue(event is SocketEvent.Telemetry)
        assertEquals(61.5, (event as SocketEvent.Telemetry).battery, 0.0)
    }

    @Test
    fun ignoresUnknownAndGarbage() {
        assertNull(parseEvent("""{"type":"pong"}"""))
        assertNull(parseEvent("not json"))
    }

    @Test
    fun backoffDoublesThenCaps() {
        assertEquals(1_000L, backoffMs(0))
        assertEquals(2_000L, backoffMs(1))
        assertEquals(30_000L, backoffMs(10))
    }
}
