package com.iqsocial.iq_message.data

import androidx.compose.runtime.Immutable

/**
 * A dialling country.
 *
 * [iso] is the ISO-3166 alpha-2 code, which also produces the flag: the two letters
 * map to regional-indicator code points, so no flag assets ship with the app.
 */
@Immutable
data class Country(
    val iso: String,
    val name: String,
    val dialCode: String
) {
    val flag: String
        get() = buildString {
            for (c in iso.uppercase()) appendCodePoint(0x1F1E6 + (c - 'A'))
        }

    /** Dial code without the leading '+', e.g. "880". */
    val dialDigits: String get() = dialCode.filter(Char::isDigit)

    /**
     * Builds the value the API is given: E.164, e.g. `+8801792011775`.
     *
     * Verified against the live server, which matches `phone_number` exactly and does
     * not normalise. `+8801792011775` signs in; the same number as `01792011775`,
     * `8801792011775` or `1792011775` does not resolve to that account. (The local
     * repo's `normalisePhone`/`isValidPhone` describe the older national format used
     * by the deposit and recharge flows — not this one.)
     */
    fun composeForApi(typed: String): String {
        var digits = typed.filter(Char::isDigit)
        if (digits.isEmpty()) return ""

        // Tolerate a pasted country prefix, but only when it can't be mistaken for
        // the number itself: a one-digit code like +1 would otherwise eat the first
        // digit of any US number beginning with 1.
        if (dialDigits.length >= 2 &&
            digits.startsWith(dialDigits) &&
            digits.length > dialDigits.length + 5
        ) {
            digits = digits.removePrefix(dialDigits)
        }

        // National trunk prefix is dropped — it is not part of the E.164 form.
        return "+$dialDigits${digits.trimStart('0')}"
    }

    companion object {
        val BANGLADESH = Country("BD", "Bangladesh", "+880")

        /** Sorted by name; Bangladesh is the default rather than the first entry. */
        val ALL: List<Country> = listOf(
            Country("AF", "Afghanistan", "+93"),
            Country("AL", "Albania", "+355"),
            Country("DZ", "Algeria", "+213"),
            Country("AR", "Argentina", "+54"),
            Country("AM", "Armenia", "+374"),
            Country("AU", "Australia", "+61"),
            Country("AT", "Austria", "+43"),
            Country("AZ", "Azerbaijan", "+994"),
            Country("BH", "Bahrain", "+973"),
            BANGLADESH,
            Country("BY", "Belarus", "+375"),
            Country("BE", "Belgium", "+32"),
            Country("BT", "Bhutan", "+975"),
            Country("BR", "Brazil", "+55"),
            Country("BN", "Brunei", "+673"),
            Country("BG", "Bulgaria", "+359"),
            Country("KH", "Cambodia", "+855"),
            Country("CM", "Cameroon", "+237"),
            Country("CA", "Canada", "+1"),
            Country("CL", "Chile", "+56"),
            Country("CN", "China", "+86"),
            Country("CO", "Colombia", "+57"),
            Country("HR", "Croatia", "+385"),
            Country("CZ", "Czechia", "+420"),
            Country("DK", "Denmark", "+45"),
            Country("EG", "Egypt", "+20"),
            Country("ET", "Ethiopia", "+251"),
            Country("FI", "Finland", "+358"),
            Country("FR", "France", "+33"),
            Country("GE", "Georgia", "+995"),
            Country("DE", "Germany", "+49"),
            Country("GH", "Ghana", "+233"),
            Country("GR", "Greece", "+30"),
            Country("HK", "Hong Kong", "+852"),
            Country("HU", "Hungary", "+36"),
            Country("IS", "Iceland", "+354"),
            Country("IN", "India", "+91"),
            Country("ID", "Indonesia", "+62"),
            Country("IR", "Iran", "+98"),
            Country("IQ", "Iraq", "+964"),
            Country("IE", "Ireland", "+353"),
            Country("IL", "Israel", "+972"),
            Country("IT", "Italy", "+39"),
            Country("JP", "Japan", "+81"),
            Country("JO", "Jordan", "+962"),
            Country("KZ", "Kazakhstan", "+7"),
            Country("KE", "Kenya", "+254"),
            Country("KW", "Kuwait", "+965"),
            Country("KG", "Kyrgyzstan", "+996"),
            Country("LA", "Laos", "+856"),
            Country("LB", "Lebanon", "+961"),
            Country("LY", "Libya", "+218"),
            Country("MY", "Malaysia", "+60"),
            Country("MV", "Maldives", "+960"),
            Country("MT", "Malta", "+356"),
            Country("MX", "Mexico", "+52"),
            Country("MA", "Morocco", "+212"),
            Country("MM", "Myanmar", "+95"),
            Country("NP", "Nepal", "+977"),
            Country("NL", "Netherlands", "+31"),
            Country("NZ", "New Zealand", "+64"),
            Country("NG", "Nigeria", "+234"),
            Country("NO", "Norway", "+47"),
            Country("OM", "Oman", "+968"),
            Country("PK", "Pakistan", "+92"),
            Country("PS", "Palestine", "+970"),
            Country("PH", "Philippines", "+63"),
            Country("PL", "Poland", "+48"),
            Country("PT", "Portugal", "+351"),
            Country("QA", "Qatar", "+974"),
            Country("RO", "Romania", "+40"),
            Country("RU", "Russia", "+7"),
            Country("SA", "Saudi Arabia", "+966"),
            Country("SN", "Senegal", "+221"),
            Country("RS", "Serbia", "+381"),
            Country("SG", "Singapore", "+65"),
            Country("ZA", "South Africa", "+27"),
            Country("KR", "South Korea", "+82"),
            Country("ES", "Spain", "+34"),
            Country("LK", "Sri Lanka", "+94"),
            Country("SD", "Sudan", "+249"),
            Country("SE", "Sweden", "+46"),
            Country("CH", "Switzerland", "+41"),
            Country("SY", "Syria", "+963"),
            Country("TW", "Taiwan", "+886"),
            Country("TZ", "Tanzania", "+255"),
            Country("TH", "Thailand", "+66"),
            Country("TN", "Tunisia", "+216"),
            Country("TR", "Türkiye", "+90"),
            Country("UG", "Uganda", "+256"),
            Country("UA", "Ukraine", "+380"),
            Country("AE", "United Arab Emirates", "+971"),
            Country("GB", "United Kingdom", "+44"),
            Country("US", "United States", "+1"),
            Country("UZ", "Uzbekistan", "+998"),
            Country("VN", "Vietnam", "+84"),
            Country("YE", "Yemen", "+967"),
            Country("ZW", "Zimbabwe", "+263")
        ).sortedBy { it.name }

        fun search(query: String): List<Country> {
            val q = query.trim()
            if (q.isEmpty()) return ALL
            val digits = q.removePrefix("+")
            return ALL.filter {
                it.name.contains(q, ignoreCase = true) ||
                    it.iso.equals(q, ignoreCase = true) ||
                    it.dialDigits.startsWith(digits)
            }
        }
    }
}
