package com.viewgrow.app

import android.Manifest
import android.content.ContentProviderOperation
import android.content.ContentUris
import android.content.Context
import android.content.pm.PackageManager
import android.provider.ContactsContract
import androidx.core.content.ContextCompat

// Saves the contacts from the database's file to the phone. Nothing goes to any Google account:
// they stay on this phone, tagged VG (company name), and keep the database's own note
// "ViewGrow-<your id>", which is how Delete My Contacts finds them later.
object ContactsSaver {
    const val TAG = "VG"

    class Card(val name: String, val number: String, val note: String)

    private val markerName = Regex("^ViewGrow \\d+$")

    // The name shown in the phone: "Team Limitless" becomes "Team Limitless VG". Done in two places on purpose
    // (name ending + company field) so a VG contact is easy to spot. Not added twice if the name already ends in
    // VG, and not added to the member's own marker contact ("ViewGrow <id>") or "ViewGrow Admin".
    fun displayName(name: String): String {
        val n = name.trim()
        if (n.isEmpty() || markerName.matches(n) || n == "ViewGrow Admin") return n
        return if (n.endsWith(" $TAG", ignoreCase = false)) n else "$n $TAG"
    }

    fun hasPermission(ctx: Context): Boolean =
        ContextCompat.checkSelfPermission(ctx, Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED &&
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.WRITE_CONTACTS) == PackageManager.PERMISSION_GRANTED

    private fun unescape(s: String): String =
        s.replace("\\n", "\n").replace("\\N", "\n").replace("\\,", ",").replace("\\;", ";").replace("\\\\", "\\")

    // Reads the .vcf text the database sends: one card per person.
    fun parse(vcf: String): List<Card> {
        val out = ArrayList<Card>()
        var name = ""
        var tel = ""
        var note = ""
        var inside = false
        for (raw in vcf.split("\n")) {
            val line = raw.trim()
            if (line.equals("BEGIN:VCARD", ignoreCase = true)) {
                inside = true; name = ""; tel = ""; note = ""
            } else if (line.equals("END:VCARD", ignoreCase = true)) {
                if (inside && tel.isNotEmpty()) out.add(Card(if (name.isEmpty()) tel else name, tel, note))
                inside = false
            } else if (inside) {
                if (line.startsWith("FN:", ignoreCase = true)) {
                    name = unescape(line.substring(3)).trim()
                } else if (line.startsWith("TEL", ignoreCase = true)) {
                    val i = line.indexOf(':')
                    if (i >= 0) tel = line.substring(i + 1).trim()
                } else if (line.startsWith("NOTE:", ignoreCase = true)) {
                    note = unescape(line.substring(5)).trim()
                }
            }
        }
        return out
    }

    // The last 10 digits, so 0801 234 5678 and +234 801 234 5678 count as the same number.
    private fun key(number: String): String = number.filter { it.isDigit() }.takeLast(10)

    // Contacts that still exist. A contact deleted in the phone's Contacts app can linger, flagged as
    // deleted, for a while and its number can still show up in phone-number queries. Without this check
    // the sync would think that number is still saved and never put it back. Null if the phone could not
    // answer, in which case nothing is filtered out.
    private fun liveRawIds(ctx: Context): Set<Long>? {
        return try {
            val ids = HashSet<Long>()
            val c = ctx.contentResolver.query(
                ContactsContract.RawContacts.CONTENT_URI,
                arrayOf(ContactsContract.RawContacts._ID),
                ContactsContract.RawContacts.DELETED + " = 0",
                null, null
            ) ?: return null
            c.use { while (it.moveToNext()) ids.add(it.getLong(0)) }
            ids
        } catch (e: Exception) {
            null
        }
    }

    private fun existingKeys(ctx: Context): HashSet<String> {
        val set = HashSet<String>()
        try {
            val live = liveRawIds(ctx)
            ctx.contentResolver.query(
                ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                arrayOf(
                    ContactsContract.CommonDataKinds.Phone.RAW_CONTACT_ID,
                    ContactsContract.CommonDataKinds.Phone.NUMBER
                ),
                null, null, null
            )?.use { c ->
                while (c.moveToNext()) {
                    if (live != null && c.getLong(0) !in live) continue
                    val k = key(c.getString(1) ?: "")
                    if (k.isNotEmpty()) set.add(k)
                }
            }
        } catch (e: Exception) {
            // Could not read: nothing is skipped, so a few duplicates are possible but nothing is lost.
        }
        return set
    }

    // Saves every card whose number is not in the phone yet. Returns false if the phone refused.
    fun save(ctx: Context, cards: List<Card>): Boolean = saveCounting(ctx, cards) >= 0

    // Same as save, but returns how many of the member's contacts were actually put in (the member's own
    // "ViewGrow <id>" marker card is not counted), or -1 if the phone refused.
    fun saveCounting(ctx: Context, cards: List<Card>): Int {
        val have = existingKeys(ctx)
        val todo = cards.filter { have.add(key(it.number)) }
        var i = 0
        while (i < todo.size) {
            val end = minOf(i + 50, todo.size)
            val ops = ArrayList<ContentProviderOperation>()
            for (k in i until end) addCard(ops, todo[k])
            try {
                ctx.contentResolver.applyBatch(ContactsContract.AUTHORITY, ops)
            } catch (e: Exception) {
                return -1
            }
            i = end
        }
        val marker = Regex("^ViewGrow \\d+$")
        return todo.count { !marker.matches(it.name.trim()) }
    }

    private fun addCard(ops: ArrayList<ContentProviderOperation>, c: Card) {
        val at = ops.size
        ops.add(
            ContentProviderOperation.newInsert(ContactsContract.RawContacts.CONTENT_URI)
                .withValue(ContactsContract.RawContacts.ACCOUNT_TYPE, null)
                .withValue(ContactsContract.RawContacts.ACCOUNT_NAME, null)
                .build()
        )
        ops.add(
            ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI)
                .withValueBackReference(ContactsContract.Data.RAW_CONTACT_ID, at)
                .withValue(ContactsContract.Data.MIMETYPE, ContactsContract.CommonDataKinds.StructuredName.CONTENT_ITEM_TYPE)
                .withValue(ContactsContract.CommonDataKinds.StructuredName.DISPLAY_NAME, displayName(c.name))
                .build()
        )
        ops.add(
            ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI)
                .withValueBackReference(ContactsContract.Data.RAW_CONTACT_ID, at)
                .withValue(ContactsContract.Data.MIMETYPE, ContactsContract.CommonDataKinds.Phone.CONTENT_ITEM_TYPE)
                .withValue(ContactsContract.CommonDataKinds.Phone.NUMBER, c.number)
                .withValue(ContactsContract.CommonDataKinds.Phone.TYPE, ContactsContract.CommonDataKinds.Phone.TYPE_MOBILE)
                .build()
        )
        ops.add(
            ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI)
                .withValueBackReference(ContactsContract.Data.RAW_CONTACT_ID, at)
                .withValue(ContactsContract.Data.MIMETYPE, ContactsContract.CommonDataKinds.Organization.CONTENT_ITEM_TYPE)
                .withValue(ContactsContract.CommonDataKinds.Organization.COMPANY, TAG)
                .build()
        )
        if (c.note.isNotEmpty()) {
            ops.add(
                ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI)
                    .withValueBackReference(ContactsContract.Data.RAW_CONTACT_ID, at)
                    .withValue(ContactsContract.Data.MIMETYPE, ContactsContract.CommonDataKinds.Note.CONTENT_ITEM_TYPE)
                    .withValue(ContactsContract.CommonDataKinds.Note.NOTE, c.note)
                    .build()
            )
        }
    }

    // Delete My Contacts: removes every contact the app saved (found by the database's "ViewGrow-<id>" note).
    // The member's own marker contact ("ViewGrow <id>") stays. Blocking. Returns how many were removed, or -1 on failure.
    fun deleteMine(ctx: Context): Int {
        if (!hasPermission(ctx)) return -1
        return try {
            val resolver = ctx.contentResolver
            val ids = ArrayList<Long>()
            resolver.query(
                ContactsContract.Data.CONTENT_URI,
                arrayOf(ContactsContract.Data.RAW_CONTACT_ID),
                ContactsContract.Data.MIMETYPE + "=? AND " + ContactsContract.CommonDataKinds.Note.NOTE + " LIKE ?",
                arrayOf(ContactsContract.CommonDataKinds.Note.CONTENT_ITEM_TYPE, "ViewGrow-%"),
                null
            )?.use { c -> while (c.moveToNext()) ids.add(c.getLong(0)) }

            // Which of them is the member's own marker contact: named "ViewGrow" and a number.
            val keep = HashSet<Long>()
            val marker = Regex("^ViewGrow \\d+$")
            var i = 0
            while (i < ids.size) {
                val part = ids.subList(i, minOf(i + 400, ids.size))
                val marks = part.joinToString(",") { "?" }
                val args = ArrayList<String>()
                args.add(ContactsContract.CommonDataKinds.StructuredName.CONTENT_ITEM_TYPE)
                part.forEach { args.add(it.toString()) }
                resolver.query(
                    ContactsContract.Data.CONTENT_URI,
                    arrayOf(ContactsContract.Data.RAW_CONTACT_ID, ContactsContract.CommonDataKinds.StructuredName.DISPLAY_NAME),
                    ContactsContract.Data.MIMETYPE + "=? AND " + ContactsContract.Data.RAW_CONTACT_ID + " IN (" + marks + ")",
                    args.toTypedArray(),
                    null
                )?.use { c ->
                    while (c.moveToNext()) {
                        if (marker.matches(c.getString(1)?.trim() ?: "")) keep.add(c.getLong(0))
                    }
                }
                i += 400
            }

            val remove = ids.filter { it !in keep }.distinct()
            var k = 0
            while (k < remove.size) {
                val end = minOf(k + 100, remove.size)
                val ops = ArrayList<ContentProviderOperation>()
                for (n in k until end) {
                    ops.add(ContentProviderOperation.newDelete(ContentUris.withAppendedId(ContactsContract.RawContacts.CONTENT_URI, remove[n])).build())
                }
                resolver.applyBatch(ContactsContract.AUTHORITY, ops)
                k = end
            }
            remove.size
        } catch (e: Exception) {
            -1
        }
    }
}
