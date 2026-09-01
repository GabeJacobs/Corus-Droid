package fm.corus.android.ui.screens.messaging

import fm.corus.android.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class GroupSystemMessagesTest {

    @Test
    fun parseNamedAndLeft() {
        val named = GroupSystemMessages.parse("John Farley named the group basica")
        assertEquals(GroupSystemMessages.Parsed.Event.NAMED, named?.event)
        assertEquals("John Farley", named?.actor)
        assertEquals("basica", named?.groupName)

        val left = GroupSystemMessages.parse("clifton left")
        assertEquals(GroupSystemMessages.Parsed.Event.LEFT, left?.event)
        assertEquals("clifton", left?.actor)
    }

    @Test
    fun parseEveryBackendEvent() {
        assertEquals(GroupSystemMessages.Parsed.Event.CREATED, GroupSystemMessages.parse("Gabe created the group")?.event)
        assertEquals("Maya and Alex", GroupSystemMessages.parse("Gabe added Maya and Alex")?.targets)
        assertEquals("Maya", GroupSystemMessages.parse("Gabe removed Maya")?.targets)
        assertEquals(GroupSystemMessages.Parsed.Event.RENAMED, GroupSystemMessages.parse("Gabe changed the group name")?.event)
        assertEquals(GroupSystemMessages.Parsed.Event.PHOTO, GroupSystemMessages.parse("Gabe changed the group photo")?.event)
        assertNull(GroupSystemMessages.parse("hey what's up"))
        assertNull(GroupSystemMessages.parse(""))
    }

    @Test
    fun formatUsesLocalizedTemplates() {
        val table = mapOf(
            R.string.messaging_group_sys_left to "%s saiu",
            R.string.messaging_group_sys_named to "%s nomeou o grupo %s",
            R.string.messaging_group_sys_added to "%s adicionou %s",
            R.string.messaging_group_sys_and to "%s e %s",
            R.string.notif_favorite_someone to "Alguém",
        )
        val getString: (Int, Array<out Any>) -> String = { id, args ->
            var out = table.getValue(id)
            args.forEach { arg -> out = out.replaceFirst("%s", arg.toString()) }
            out
        }
        assertEquals(
            "Gabe Jacobs saiu",
            GroupSystemMessages.format(GroupSystemMessages.parse("Gabe Jacobs left")!!, getString),
        )
        assertEquals(
            "John Farley nomeou o grupo basica",
            GroupSystemMessages.format(GroupSystemMessages.parse("John Farley named the group basica")!!, getString),
        )
        assertEquals(
            "Gabe adicionou Maya e Alex",
            GroupSystemMessages.format(GroupSystemMessages.parse("Gabe added Maya and Alex")!!, getString),
        )
    }
}
