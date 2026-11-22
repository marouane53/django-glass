package com.macedonga.visor.core.settings

import com.google.flatbuffers.ArrayReadWriteBuf
import com.google.flatbuffers.FlexBuffers
import org.junit.Assert.assertEquals
import org.junit.Test

class ContactsTest {
    @Test fun contactSyncPutsTheDialableNumberBeforeTheLabel() {
        val contact = GlassesContact(
            id = "42",
            name = "Test contact",
            numbers = listOf(
                GlassesContact.Number(2, "1", "Cellulare", "+12025550100"),
                GlassesContact.Number(1, "1", "Home", "+12025550101"),
                GlassesContact.Number(0, "1", "Work", "+12025550102"),
                GlassesContact.Number(3, "1", "Desk, \"west\"; floor 2", "+12025550103"),
            ),
        )

        val payload = contactObject(contact).data
        val record = FlexBuffers.getRoot(ArrayReadWriteBuf(payload, payload.size)).asMap()
        assertEquals(
            "CELL,1,+12025550100,,Cellulare;HOME,1,+12025550101,,Home;WORK,1,+12025550102,,Work;" +
                "CUSTOM,1,+12025550103,,\"Desk, \"\"west\"\"; floor 2\"",
            record["phoneNumbers"].asString(),
        )
    }
}
