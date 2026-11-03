package fm.corus.android.ui.screens.map

import org.junit.Assert.*
import org.junit.Test

class MapPayloadGateTest {
    @Test fun identicalRecompositionsSkipBridgeCallsButChangedDataIsDelivered() {
        val gate = MapPayloadGate()
        assertTrue(gate.shouldSend("document", "cities"))
        repeat(30) { assertFalse(gate.shouldSend("document", "cities")) }
        assertTrue(gate.shouldSend("document", "new focus"))
        assertTrue(gate.shouldSend("document", "new avatars"))
    }

    @Test fun pageFinishedReplaysDataThatMayHaveArrivedBeforeTheJavascriptBridge() {
        val gate = MapPayloadGate()
        assertTrue(gate.shouldSend("document", "cities"))
        assertTrue(gate.shouldSend("document", "cities", force = true))
        assertFalse(gate.shouldSend("document", "cities"))
    }

    @Test fun retryAndTokenRotationCannotInheritTheOldDocumentsDeliveryState() {
        val gate = MapPayloadGate()
        assertTrue(gate.shouldSend("token:0", "cities"))
        assertTrue(gate.shouldSend("token:1", "cities"))
        assertTrue(gate.shouldSend("rotated-token:0", "cities"))
        assertTrue(gate.shouldSend("rotated-token:0", "cities", force = true))
    }
}
