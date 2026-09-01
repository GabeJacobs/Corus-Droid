package fm.corus.android.ui.screens.messaging

import android.content.Context
import fm.corus.android.R

/**
 * Backend writes group lifecycle rows in English ("Gabe left", "Maya named the
 * group Trip"). Clients re-render that stored text in the user's language.
 */
object GroupSystemMessages {
    fun localize(text: String, context: Context): String {
        val parsed = parse(text) ?: return text
        return format(parsed) { id, args ->
            if (args.isEmpty()) context.getString(id) else context.getString(id, *args)
        }
    }

    internal data class Parsed(
        val event: Event,
        val actor: String,
        val targets: String? = null,
        val groupName: String? = null,
    ) {
        enum class Event { CREATED, ADDED, REMOVED, LEFT, NAMED, RENAMED, PHOTO }
    }

    internal fun parse(text: String): Parsed? {
        val t = text.trim()
        if (t.isEmpty()) return null

        val namedSep = " named the group "
        val namedAt = t.indexOf(namedSep)
        if (namedAt > 0) {
            return Parsed(Parsed.Event.NAMED, t.substring(0, namedAt), groupName = t.substring(namedAt + namedSep.length))
        }
        actorBeforeSuffix(t, " changed the group name")?.let {
            return Parsed(Parsed.Event.RENAMED, it)
        }
        actorBeforeSuffix(t, " changed the group photo")?.let {
            return Parsed(Parsed.Event.PHOTO, it)
        }
        actorBeforeSuffix(t, " created the group")?.let {
            return Parsed(Parsed.Event.CREATED, it)
        }
        actorBeforeSuffix(t, " left")?.let {
            return Parsed(Parsed.Event.LEFT, it)
        }
        val addedAt = t.indexOf(" added ")
        if (addedAt > 0) {
            val targets = t.substring(addedAt + " added ".length)
            if (targets.isNotEmpty()) return Parsed(Parsed.Event.ADDED, t.substring(0, addedAt), targets = targets)
        }
        val removedAt = t.indexOf(" removed ")
        if (removedAt > 0) {
            val targets = t.substring(removedAt + " removed ".length)
            if (targets.isNotEmpty()) return Parsed(Parsed.Event.REMOVED, t.substring(0, removedAt), targets = targets)
        }
        return null
    }

    private fun actorBeforeSuffix(text: String, suffix: String): String? {
        if (!text.endsWith(suffix)) return null
        val actor = text.substring(0, text.length - suffix.length)
        return actor.takeIf { it.isNotEmpty() }
    }

    internal fun format(parsed: Parsed, getString: (Int, Array<out Any>) -> String): String {
        val actor = localizePerson(parsed.actor, getString)
        return when (parsed.event) {
            Parsed.Event.CREATED ->
                getString(R.string.messaging_group_sys_created, arrayOf(actor))
            Parsed.Event.ADDED ->
                getString(R.string.messaging_group_sys_added, arrayOf(actor, localizeNameList(parsed.targets.orEmpty(), getString)))
            Parsed.Event.REMOVED ->
                getString(R.string.messaging_group_sys_removed, arrayOf(actor, localizeNameList(parsed.targets.orEmpty(), getString)))
            Parsed.Event.LEFT ->
                getString(R.string.messaging_group_sys_left, arrayOf(actor))
            Parsed.Event.NAMED ->
                getString(R.string.messaging_group_sys_named, arrayOf(actor, parsed.groupName.orEmpty()))
            Parsed.Event.RENAMED ->
                getString(R.string.messaging_group_sys_renamed, arrayOf(actor))
            Parsed.Event.PHOTO ->
                getString(R.string.messaging_group_sys_photo, arrayOf(actor))
        }
    }

    private fun localizePerson(name: String, getString: (Int, Array<out Any>) -> String): String {
        if (name.equals("someone", ignoreCase = true)) {
            return getString(R.string.notif_favorite_someone, emptyArray())
        }
        return name
    }

    private val othersPattern = Regex("""^(.+), (.+) and (\d+) others?$""")

    private fun localizeNameList(raw: String, getString: (Int, Array<out Any>) -> String): String {
        if (raw.equals("someone", ignoreCase = true)) {
            return getString(R.string.notif_favorite_someone, emptyArray())
        }
        othersPattern.matchEntire(raw)?.let { match ->
            val a = localizePerson(match.groupValues[1], getString)
            val b = localizePerson(match.groupValues[2], getString)
            val count = match.groupValues[3].toInt()
            val id = if (count == 1) R.string.messaging_group_sys_and_n_other else R.string.messaging_group_sys_and_n_others
            return getString(id, arrayOf(a, b, count))
        }
        val andAt = raw.indexOf(" and ")
        if (andAt > 0 && !raw.contains(",")) {
            val a = localizePerson(raw.substring(0, andAt), getString)
            val b = localizePerson(raw.substring(andAt + " and ".length), getString)
            return getString(R.string.messaging_group_sys_and, arrayOf(a, b))
        }
        return raw
    }
}
