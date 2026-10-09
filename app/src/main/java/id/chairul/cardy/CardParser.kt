package id.chairul.cardy

/**
 * One OCR'd line plus its on-image height (px). Height helps pick the name,
 * which is usually the largest text on a business card.
 */
data class OcrLine(val text: String, val height: Int = 0)

data class CardData(
    val name: String = "",
    val title: String = "",
    val company: String = "",
    val mobile: String = "",
    val phone: String = "",
    val fax: String = "",
    val email: String = "",
    val website: String = "",
    val address: String = "",
)

/** Heuristic business-card field extractor. Pure Kotlin, no Android deps. */
object CardParser {

    private val EMAIL = Regex("""[A-Za-z0-9._%+\-]+@[A-Za-z0-9.\-]+\.[A-Za-z]{2,}""")
    private val URL = Regex("""(?i)\b((https?://)?(www\.)?[a-z0-9\-]+(\.[a-z0-9\-]+)*\.(com|id|co\.id|net|org|io|ai|my|sg|au|com\.au|com\.my|com\.sg|biz|info|tech|asia|go\.id|ac\.id|or\.id)(/[^\s]*)?)\b""")
    private val PHONE = Regex("""(\+?\(?\d[\d\s().\-/]{6,}\d)""")

    private val MOBILE_LABEL = Regex("""(?i)\b(m|mob|mobile|hp|cell|handphone|wa|whatsapp|hand ?phone)\b\.?\s*[:.]?""")
    private val FAX_LABEL = Regex("""(?i)\b(f|fax|facs?imile)\b\.?\s*[:.]?""")
    private val PHONE_LABEL = Regex("""(?i)\b(t|tel|telp|phone|ph|p|o|office|direct|d)\b\.?\s*[:.]?""")

    private val TITLE_WORDS = listOf(
        "ceo", "cto", "cfo", "coo", "cio", "ciso", "founder", "co-founder", "president", "vice president", "vp",
        "director", "manager", "head", "lead", "engineer", "consultant", "architect", "officer", "executive",
        "specialist", "analyst", "sales", "presales", "account", "partner", "owner", "principal", "chairman",
        "commissioner", "associate", "advisor", "adviser", "representative", "supervisor", "coordinator",
        "administrator", "developer", "designer", "marketing", "business development", "gm", "general manager",
        "direktur", "manajer", "kepala", "komisaris", "staff", "team leader", "senior", "assistant", "secretary",
    )
    private val COMPANY_WORDS = listOf(
        "pt", "pt.", "cv", "cv.", "tbk", "inc", "inc.", "ltd", "ltd.", "llc", "corp", "corp.", "corporation",
        "company", "co.", "group", "gmbh", "pte", "sdn", "bhd", "plc", "limited", "solutions", "solusi",
        "technologies", "technology", "teknologi", "systems", "consulting", "indonesia", "international",
        "persero", "holdings", "bank", "university", "universitas", "foundation", "yayasan",
    )
    private val ADDRESS_WORDS = listOf(
        "jl", "jl.", "jln", "jalan", "street", "st.", "road", "rd", "rd.", "avenue", "ave", "blvd", "floor", "fl.",
        "lt", "lt.", "lantai", "tower", "building", "gedung", "blok", "block", "kav", "kav.", "rt", "rw", "no.",
        "suite", "unit", "kel.", "kec.", "kelurahan", "kecamatan", "jakarta", "bandung", "surabaya", "tangerang",
        "bekasi", "bogor", "depok", "medan", "bali", "semarang", "yogyakarta", "singapore", "kuala lumpur",
        "penang", "sydney", "indonesia", "malaysia", "australia", "plaza", "komplek", "ruko", "district",
    )
    private val POSTCODE = Regex("""\b\d{4,6}\b""")

    fun parse(rawLines: List<OcrLine>): CardData {
        val lines = rawLines.map { it.copy(text = it.text.trim()) }.filter { it.text.isNotEmpty() }
        val used = BooleanArray(lines.size)

        var email = ""
        var website = ""
        var mobile = ""
        var phone = ""
        var fax = ""

        // 1) Emails / websites / phones (may share a line)
        lines.forEachIndexed { i, l ->
            val t = l.text
            EMAIL.find(t)?.let { if (email.isEmpty()) email = it.value; used[i] = true }

            val noEmail = EMAIL.replace(t, " ")
            URL.find(noEmail)?.let { m ->
                if (website.isEmpty() && !m.value.contains('@')) website = m.value.trimEnd('.', ',')
                used[i] = true
            }

            // A line can hold several numbers: "T +62 21 555 1234  F +62 21 555 9999"
            val segments = splitByLabels(noEmail)
            for (seg in segments) {
                val num = PHONE.find(seg)?.value ?: continue
                val digits = num.count { it.isDigit() }
                if (digits < 7 || digits > 15) continue
                if (looksLikeDateOrPostcode(num)) continue
                val clean = normalizePhone(num)
                when {
                    FAX_LABEL.containsMatchIn(seg.substringBefore(num)) -> if (fax.isEmpty()) fax = clean
                    MOBILE_LABEL.containsMatchIn(seg.substringBefore(num)) || isMobileNumber(clean) ->
                        if (mobile.isEmpty()) mobile = clean else if (phone.isEmpty()) phone = clean
                    else -> if (phone.isEmpty()) phone = clean else if (mobile.isEmpty()) mobile = clean
                }
                used[i] = true
            }
        }

        // 2) Address (can be multi-line; collect consecutive address-looking lines)
        val addrParts = mutableListOf<String>()
        lines.forEachIndexed { i, l ->
            if (used[i]) return@forEachIndexed
            if (addressScore(l.text) >= 2) {
                addrParts += l.text; used[i] = true
            }
        }
        val address = addrParts.joinToString(", ")

        // 3) Company
        var company = ""
        var bestC = 0
        lines.forEachIndexed { i, l ->
            if (used[i]) return@forEachIndexed
            val s = keywordScore(l.text, COMPANY_WORDS)
            if (s > bestC) { bestC = s; company = l.text }
        }
        if (company.isNotEmpty()) used[lines.indexOfFirst { it.text == company }] = true

        // 4) Title
        var title = ""
        var bestT = 0
        lines.forEachIndexed { i, l ->
            if (used[i]) return@forEachIndexed
            val s = keywordScore(l.text, TITLE_WORDS)
            if (s > bestT) { bestT = s; title = l.text }
        }
        if (title.isNotEmpty()) used[lines.indexOfFirst { it.text == title }] = true

        // 5) Name: best remaining name-like line
        val emailTokens = email.substringBefore('@').lowercase()
            .split('.', '_', '-').filter { it.length >= 2 }.toSet()
        val maxH = (lines.maxOfOrNull { it.height } ?: 1).coerceAtLeast(1)
        var name = ""
        var bestN = Double.NEGATIVE_INFINITY
        lines.forEachIndexed { i, l ->
            if (used[i]) return@forEachIndexed
            if (!isNameLike(l.text)) return@forEachIndexed
            var score = 0.0
            score += 3.0 * l.height / maxH                       // bigger text
            score += 1.0 - i.toDouble() / lines.size              // nearer the top
            val words = l.text.lowercase().split(Regex("""\s+"""))
            if (words.any { w -> emailTokens.any { t -> w.startsWith(t) || t.startsWith(w) } }) score += 3.0
            if (l.text.split(' ').all { it.firstOrNull()?.isUpperCase() == true }) score += 0.5
            if (score > bestN) { bestN = score; name = l.text }
        }

        // Fallback: derive from email like "john.doe@" -> "John Doe"
        if (name.isEmpty() && emailTokens.size >= 2) {
            name = email.substringBefore('@').split('.', '_', '-')
                .filter { it.isNotBlank() && it.none(Char::isDigit) }
                .joinToString(" ") { it.replaceFirstChar(Char::uppercase) }
        }

        return CardData(
            name = prettifyName(name), title = title, company = company,
            mobile = mobile, phone = phone, fax = fax,
            email = email, website = website, address = address,
        )
    }

    // ---- helpers ----

    private fun splitByLabels(t: String): List<String> {
        // Split before a label that is followed by a digit/+ so each number keeps its label.
        val parts = t.split(Regex("""(?i)(?=\b(?:t|tel|telp|phone|ph|p|o|m|mob|mobile|hp|cell|wa|f|fax|d|direct)\b\.?\s*[:.]?\s*[+(\d])"""))
        return parts.filter { it.isNotBlank() }.ifEmpty { listOf(t) }
    }

    private fun looksLikeDateOrPostcode(s: String): Boolean {
        val d = s.filter(Char::isDigit)
        return Regex("""^\d{1,2}[/.\-]\d{1,2}[/.\-]\d{2,4}$""").matches(s.trim()) || (d.length <= 6 && !s.contains('+'))
    }

    fun normalizePhone(s: String): String =
        s.trim().replace(Regex("""[^\d+()\s\-]"""), "").replace(Regex("""\s+"""), " ").trim()

    private fun isMobileNumber(p: String): Boolean {
        val d = p.filter { it.isDigit() || it == '+' }
        // Indonesia (08xx / +628xx), Malaysia (01x / +601x), Australia (04xx / +614xx)
        return d.startsWith("08") || d.startsWith("+628") || d.startsWith("628") ||
            d.startsWith("+601") || d.startsWith("+614") || Regex("""^04\d{8}$""").matches(d)
    }

    private fun tokens(t: String) = t.lowercase().split(Regex("""[\s,]+""")).filter { it.isNotEmpty() }

    private fun keywordScore(t: String, words: List<String>): Int {
        val low = " " + t.lowercase() + " "
        val toks = tokens(t).toSet()
        return words.count { w -> if (w.contains(' ')) low.contains(" $w ") else w in toks || w.trimEnd('.') in toks }
    }

    private fun addressScore(t: String): Int {
        var s = keywordScore(t, ADDRESS_WORDS)
        if (POSTCODE.containsMatchIn(t)) s += 1
        if (t.count { it == ',' } >= 2) s += 1
        if (t.any(Char::isDigit) && t.length > 15) s += 1
        return s
    }

    private fun isNameLike(t: String): Boolean {
        if (t.any(Char::isDigit)) return false
        if (t.contains('@') || t.contains("www", ignoreCase = true)) return false
        val words = t.split(Regex("""\s+""")).filter { it.isNotEmpty() }
        if (words.size !in 1..5) return false
        if (t.length > 40) return false
        val letters = t.count { it.isLetter() }
        if (letters < t.replace(" ", "").length * 0.8) return false
        if (keywordScore(t, COMPANY_WORDS) > 0 || keywordScore(t, TITLE_WORDS) > 0) return false
        // single-word names are allowed but must look like a word, not an acronym logo
        if (words.size == 1 && (t.length < 3 || t.all { it.isUpperCase() })) return false
        return true
    }

    private fun prettifyName(n: String): String {
        if (n.isEmpty()) return n
        // ALL CAPS -> Title Case (keep degree suffixes like "S.T." / "MBA" as-is)
        return n.split(' ').joinToString(" ") { w ->
            if (w.length > 3 && w.all { !it.isLetter() || it.isUpperCase() } && !w.contains('.'))
                w.lowercase().replaceFirstChar(Char::uppercase)
            else w
        }
    }
}
