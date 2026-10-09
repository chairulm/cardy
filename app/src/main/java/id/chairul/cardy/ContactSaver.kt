package id.chairul.cardy

import android.content.ContentValues
import android.content.Intent
import android.provider.ContactsContract
import android.provider.ContactsContract.CommonDataKinds

/**
 * Opens the system "New contact" screen pre-filled with the card data.
 * No WRITE_CONTACTS permission needed; the user picks the account (Google, phone, SIM) and confirms.
 */
object ContactSaver {

    fun intent(c: CardData): Intent =
        Intent(ContactsContract.Intents.Insert.ACTION).apply {
            type = ContactsContract.RawContacts.CONTENT_TYPE
            putExtra(ContactsContract.Intents.Insert.NAME, c.name)
            putExtra(ContactsContract.Intents.Insert.COMPANY, c.company)
            putExtra(ContactsContract.Intents.Insert.JOB_TITLE, c.title)
            putExtra(ContactsContract.Intents.Insert.EMAIL, c.email)
            putExtra(ContactsContract.Intents.Insert.EMAIL_TYPE, CommonDataKinds.Email.TYPE_WORK)
            putExtra(ContactsContract.Intents.Insert.POSTAL, c.address)
            putExtra(ContactsContract.Intents.Insert.POSTAL_TYPE, CommonDataKinds.StructuredPostal.TYPE_WORK)

            val phones = buildList {
                if (c.mobile.isNotBlank()) add(c.mobile to CommonDataKinds.Phone.TYPE_MOBILE)
                if (c.phone.isNotBlank()) add(c.phone to CommonDataKinds.Phone.TYPE_WORK)
                if (c.fax.isNotBlank()) add(c.fax to CommonDataKinds.Phone.TYPE_FAX_WORK)
            }
            val keys = listOf(
                ContactsContract.Intents.Insert.PHONE to ContactsContract.Intents.Insert.PHONE_TYPE,
                ContactsContract.Intents.Insert.SECONDARY_PHONE to ContactsContract.Intents.Insert.SECONDARY_PHONE_TYPE,
                ContactsContract.Intents.Insert.TERTIARY_PHONE to ContactsContract.Intents.Insert.TERTIARY_PHONE_TYPE,
            )
            phones.forEachIndexed { i, (num, t) ->
                putExtra(keys[i].first, num)
                putExtra(keys[i].second, t)
            }

            // Website has no dedicated extra; pass it as a data row
            if (c.website.isNotBlank()) {
                val rows = arrayListOf(ContentValues().apply {
                    put(ContactsContract.Data.MIMETYPE, CommonDataKinds.Website.CONTENT_ITEM_TYPE)
                    put(CommonDataKinds.Website.URL, c.website)
                    put(CommonDataKinds.Website.TYPE, CommonDataKinds.Website.TYPE_WORK)
                })
                putParcelableArrayListExtra(ContactsContract.Intents.Insert.DATA, rows)
            }
            putExtra("finishActivityOnSaveCompleted", true)
        }

    /** vCard 3.0 text, for sharing via WhatsApp / email. */
    fun toVCard(c: CardData): String = buildString {
        fun esc(s: String) = s.replace("\\", "\\\\").replace(",", "\\,").replace(";", "\\;")
        appendLine("BEGIN:VCARD")
        appendLine("VERSION:3.0")
        appendLine("FN:${esc(c.name)}")
        val parts = c.name.trim().split(Regex("""\s+"""))
        val last = if (parts.size > 1) parts.last() else ""
        val first = if (parts.size > 1) parts.dropLast(1).joinToString(" ") else c.name
        appendLine("N:${esc(last)};${esc(first)};;;")
        if (c.company.isNotBlank()) appendLine("ORG:${esc(c.company)}")
        if (c.title.isNotBlank()) appendLine("TITLE:${esc(c.title)}")
        if (c.mobile.isNotBlank()) appendLine("TEL;TYPE=CELL:${c.mobile}")
        if (c.phone.isNotBlank()) appendLine("TEL;TYPE=WORK,VOICE:${c.phone}")
        if (c.fax.isNotBlank()) appendLine("TEL;TYPE=WORK,FAX:${c.fax}")
        if (c.email.isNotBlank()) appendLine("EMAIL;TYPE=WORK:${c.email}")
        if (c.website.isNotBlank()) appendLine("URL:${c.website}")
        if (c.address.isNotBlank()) appendLine("ADR;TYPE=WORK:;;${esc(c.address)};;;;")
        append("END:VCARD")
    }
}
