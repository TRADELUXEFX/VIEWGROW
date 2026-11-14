package com.viewgrow.app

import android.Manifest
import android.content.ContentProviderOperation
import android.content.Context
import android.content.pm.PackageManager
import android.provider.ContactsContract
import androidx.core.content.ContextCompat

// Saves the contacts from the database's file to the phone. Nothing goes to any Google account:
// they stay on this phone, tagged VGC (company name), and keep the database's own note
// "ViewGrow-<your id>", which is how Delete My Contacts finds them later.
object ContactsSaver {
    const val TAG = "VGC"

    class Card(val name: String, val number: String, val note: String)

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

    private fun existingKeys(ctx: Context): HashSet<String> {
        val set = HashSet<String>()
        try {
            ctx.contentResolver.query(
                ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                arrayOf(ContactsContract.CommonDataKinds.Phone.NUMBER),
                null, null, null
            )?.use { c ->
                while (c.moveToNext()) {
                    val k = key(c.getString(0) ?: "")
                    if (k.isNotEmpty()) set.add(k)
                }
            }
        } catch (e: Exception) {
            // Could not read: nothing is skipped, so a few duplicates are possible but nothing is lost.
        }
        return set
    }

    // Saves every card whose number is not in the phone yet. Returns false if the phone refused.
    fun save(ctx: Context, cards: List<Card>): Boolean {
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
                return false
            }
            i = end
        }
        return true
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
                .withValue(ContactsContract.CommonDataKinds.StructuredName.DISPLAY_NAME, c.name)
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
}
