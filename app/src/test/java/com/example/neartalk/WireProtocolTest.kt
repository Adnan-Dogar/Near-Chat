package com.example.neartalk

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.util.UUID

class WireProtocolTest {
    private val id = UUID.randomUUID().toString()

    @Test fun helloRoundTrips() {
        val frame = WireProtocol.Frame.Hello(id, "My Device")
        assertEquals(frame, WireProtocol.decode(WireProtocol.encode(frame)))
    }

    @Test fun multilingualMessageRoundTrips() {
        val frame = WireProtocol.Frame.Message(id, "Hello 👋 مرحبا — سلام")
        assertEquals(frame, WireProtocol.decode(WireProtocol.encode(frame)))
    }

    @Test fun receiptsRoundTrip() {
        val frame = WireProtocol.Frame.Receipt(id)
        assertEquals(frame, WireProtocol.decode(WireProtocol.encode(frame)))
    }

    @Test fun badInputIsRejected() {
        assertNull(WireProtocol.decode(byteArrayOf(0, 1, 2)))
        val corrupt = WireProtocol.encode(WireProtocol.Frame.Message(id, "Hello"))
        corrupt[0] = 0
        assertNull(WireProtocol.decode(corrupt))
        assertNull(WireProtocol.decode(ByteArray(24_001)))
    }
}
