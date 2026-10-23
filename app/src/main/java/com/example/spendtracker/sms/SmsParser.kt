package com.example.spendtracker.sms

import java.math.BigDecimal
import java.security.MessageDigest

data class ParsedSpend(val amountMinor: Long, val merchant: String, val source: String)

/** Pure Kotlin (no Android deps) so it can be unit-tested on the JVM. */
object SmsParser {

    // Rs. 500 / Rs 1,200.50 / INR 99 / ₹250 / ৳100 / BDT 100 / Tk. 50
    private val CURRENCY_AMOUNT = Regex(
        """(?:rs\.?|inr|₹|৳|bdt|tk\.?)\s*([0-9][0-9,]*(?:\.[0-9]{1,2})?)""",
        RegexOption.IGNORE_CASE
    )

    // "debited by 500.0" style (no currency symbol)
    private val BARE_AMOUNT = Regex(
        """\b(?:debited|paid|sent|spent)\s+(?:by\s+)?([0-9][0-9,]*(?:\.[0-9]{1,2})?)""",
        RegexOption.IGNORE_CASE
    )

    private val DEBIT = Regex(
        """\b(debited|paid|sent|spent|withdrawn|purchase|transferred)\b""",
        RegexOption.IGNORE_CASE
    )
    private val CREDIT = Regex(
        """\b(credited|received|refund(?:ed)?|reversed|cashback)\b""",
        RegexOption.IGNORE_CASE
    )
    private val OTP = Regex("""\b(otp|one time password|verification code)\b""", RegexOption.IGNORE_CASE)

    // "to Swiggy.", "to VPA shop@ybl (UPI...", "at AMAZON on 01-Jan"
    private val MERCHANT = Regex(
        """\b(?:to|at)\s+(?:vpa\s+)?([A-Za-z0-9@._&' -]{2,40}?)(?=\s*(?:\.|,|\(|\bon\b|\bvia\b|\busing\b|\bfrom\b|ref(?:no|\s|\.|:)|\bupi\b|$))""",
        RegexOption.IGNORE_CASE
    )

    fun parse(sender: String, body: String): ParsedSpend? {
        if (OTP.containsMatchIn(body)) return null

        val debit = DEBIT.find(body) ?: return null
        val credit = CREDIT.find(body)
        if (credit != null && credit.range.first < debit.range.first) return null

        val amountText = (CURRENCY_AMOUNT.find(body) ?: BARE_AMOUNT.find(body))
            ?.groupValues?.get(1)?.replace(",", "") ?: return null
        val minor = runCatching { BigDecimal(amountText).movePointRight(2).toLong() }.getOrNull() ?: return null
        if (minor <= 0) return null

        val merchant = MERCHANT.find(body)?.groupValues?.get(1)
            ?.trim()?.trimEnd('.', ' ')?.takeIf { it.isNotBlank() } ?: "Unknown"

        return ParsedSpend(minor, merchant, detectSource(sender, body))
    }

    private fun detectSource(sender: String, body: String): String {
        val s = sender.uppercase()
        val b = body.uppercase()
        return when {
            "PHONEPE" in s || "PHONEPE" in b -> "PhonePe"
            "PAYTM" in s || "PAYTM" in b -> "Paytm"
            else -> "Bank"
        }
    }

    fun fingerprint(sender: String, body: String): String =
        MessageDigest.getInstance("SHA-256")
            .digest("$sender|$body".toByteArray())
            .joinToString("") { "%02x".format(it) }
}
