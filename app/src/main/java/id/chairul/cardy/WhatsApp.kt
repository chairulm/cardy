package id.chairul.cardy

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast

/** User settings (SharedPreferences). */
class Prefs(context: Context) {
    private val sp = context.getSharedPreferences("cardy", Context.MODE_PRIVATE)

    var countryCode: String
        get() = sp.getString("cc", "62") ?: "62"
        set(v) = sp.edit().putString("cc", v.filter(Char::isDigit)).apply()

    var template: String
        get() = sp.getString("tpl", DEFAULT_TEMPLATE) ?: DEFAULT_TEMPLATE
        set(v) = sp.edit().putString("tpl", v).apply()

    companion object {
        const val DEFAULT_TEMPLATE = "Hi {name}, great to meet you! Saving your contact — let's keep in touch."
    }
}

object WhatsApp {

    /** "0812-3456-7890" + cc 62 -> "6281234567890"; "+60 12 345 6789" -> "60123456789". */
    fun normalize(phone: String, countryCode: String): String? {
        var d = phone.trim().filter { it.isDigit() || it == '+' }
        d = when {
            d.startsWith("+") -> d.drop(1)
            d.startsWith("00") -> d.drop(2)
            d.startsWith("0") -> countryCode + d.drop(1)
            else -> d
        }
        d = d.filter(Char::isDigit)
        return d.takeIf { it.length >= 8 }
    }

    fun fill(template: String, c: CardData): String =
        template.replace("{name}", c.name.split(" ").firstOrNull().orEmpty().ifBlank { "there" })
            .replace("{fullname}", c.name)
            .replace("{company}", c.company)

    fun open(context: Context, phone: String, message: String, countryCode: String): Boolean {
        val n = normalize(phone, countryCode)
        if (n == null) {
            Toast.makeText(context, "Invalid phone number", Toast.LENGTH_SHORT).show()
            return false
        }
        val url = "https://wa.me/$n" + if (message.isNotBlank()) "?text=" + Uri.encode(message) else ""
        return try {
            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
            true
        } catch (e: Exception) {
            Toast.makeText(context, "WhatsApp not available", Toast.LENGTH_SHORT).show()
            false
        }
    }
}
