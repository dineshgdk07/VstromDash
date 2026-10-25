package dev.gdk.bumblebee.ride

/** What a crash text can say, filled in when it is sent. */
enum class SosField(val token: String, val label: String, val required: Boolean, val sample: String) {
    NAME("{name}", "Rider name", false, "Dinesh"),
    LOCATION("{location}", "Map link", true, "https://maps.google.com/?q=12.9716,77.5946"),
    TIME("{time}", "Time", true, "7:42 pm"),
    SPEED("{speed}", "Speed before", false, "54 km/h"),
    BIKE("{bike}", "Bike", false, "V-Strom 250 SX (Champion Yellow)"),
    REG("{reg}", "Reg no", false, "KA 01 AB 1234"),
    BLOOD("{blood}", "Blood group", false, "O+"),
    NOTE("{note}", "Medical note", false, "Diabetic"),
}

/** Why a template can't be saved, or null when it can. */
sealed class SosProblem(val message: String) {
    class Missing(val fields: List<SosField>) :
        SosProblem("Must include " + fields.joinToString(" and ") { it.token } + " so they can find you")
    class Unknown(val tokens: List<String>) :
        SosProblem("Unknown " + tokens.joinToString(", ") + ". Use the chips to insert fields")
    object TooLong : SosProblem("Keep it under ${SosTemplate.MAX_CHARS} characters")
}

/** The rider's crash text: free wording, with {fields} that are filled in when it is sent. */
object SosTemplate {
    const val MAX_CHARS = 480

    val DEFAULT = "Possible crash: {name} may need help. Location: {location} at {time}. " +
        "Bike: {bike} {reg}. Speed before: {speed}. Blood: {blood}. {note} Sent automatically by Bumblebee."

    private val TOKEN = Regex("\\{[a-zA-Z]+\\}")

    fun problems(template: String): List<SosProblem> {
        val out = mutableListOf<SosProblem>()
        val missing = SosField.entries.filter { it.required && !template.contains(it.token) }
        if (missing.isNotEmpty()) out += SosProblem.Missing(missing)
        val known = SosField.entries.map { it.token }.toSet()
        val unknown = TOKEN.findAll(template).map { it.value }.filter { it !in known }.distinct().toList()
        if (unknown.isNotEmpty()) out += SosProblem.Unknown(unknown)
        if (template.length > MAX_CHARS) out += SosProblem.TooLong
        return out
    }

    /**
     * Fills in the fields. Empty values drop out cleanly: "Blood: {blood}." with no blood group
     * loses the whole "Blood: ." phrase rather than leaving it hanging.
     */
    fun render(template: String, values: Map<SosField, String?>): String {
        var text = template
        for (field in SosField.entries) {
            val value = values[field]?.trim().orEmpty()
            if (value.isNotEmpty()) {
                text = text.replace(field.token, value)
                continue
            }
            // "Label: {field}." or "Label: {field}" → gone.
            text = text.replace(Regex("[A-Za-z ]+:\\s*" + Regex.escape(field.token) + "\\.?"), "")
            text = text.replace(field.token, "")
        }
        return text
            .replace(Regex("\\(\\s*\\)"), "")
            .replace(Regex(" +([.,])"), "$1")
            .replace(Regex("\\.{2,}"), ".")
            .replace(Regex("\\s{2,}"), " ")
            .trim()
    }

    fun preview(template: String): String = render(template, SosField.entries.associateWith { it.sample })
}

/** Indian registration numbers: KA 01 AB 1234, and BH series 22 BH 1234 AA. */
object RegNo {
    private val STATE = Regex("^([A-Z]{2})(\\d{1,2})([A-Z]{0,3})(\\d{1,4})$")
    private val BHARAT = Regex("^(\\d{2})(BH)(\\d{4})([A-Z]{1,2})$")

    /** Upper case, letters and digits only. */
    fun clean(raw: String): String = raw.uppercase().filter { it.isLetterOrDigit() }

    fun isValid(raw: String): Boolean {
        val c = clean(raw)
        return BHARAT.matches(c) || STATE.matchEntire(c)?.let { it.groupValues[4].isNotEmpty() } == true
    }

    /** "ka01ab1234" → "KA 01 AB 1234". Unrecognised input comes back cleaned. */
    fun format(raw: String): String {
        val c = clean(raw)
        BHARAT.matchEntire(c)?.let { m -> return m.groupValues.drop(1).joinToString(" ") }
        STATE.matchEntire(c)?.let { m ->
            val (state, district, series, number) = m.destructured
            return listOf(state, district.padStart(2, '0'), series, number).filter { it.isNotEmpty() }.joinToString(" ")
        }
        return c
    }
}
