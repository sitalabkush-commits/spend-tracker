package com.example.spendtracker

import com.example.spendtracker.sms.SmsParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class SmsParserTest {

    @Test fun phonepePayment() {
        val r = SmsParser.parse("VM-PHONEPE", "Paid ₹250 to Swiggy. UPI Ref 123456789")
        assertNotNull(r)
        assertEquals(25000L, r!!.amountMinor)
        assertEquals("Swiggy", r.merchant)
        assertEquals("PhonePe", r.source)
    }

    @Test fun bankUpiDebit() {
        val r = SmsParser.parse(
            "AX-HDFCBK",
            "Rs 1,200.50 debited from A/c XX1234 on 01-01-24 to VPA shop@ybl (UPI Ref No 9999)."
        )
        assertNotNull(r)
        assertEquals(120050L, r!!.amountMinor)
        assertEquals("shop@ybl", r.merchant)
        assertEquals("Bank", r.source)
    }

    @Test fun cardSpend() {
        val r = SmsParser.parse("AX-ICICIB", "INR 499.00 spent on Card XX1234 at AMAZON on 01-Jan-24.")
        assertNotNull(r)
        assertEquals(49900L, r!!.amountMinor)
        assertEquals("AMAZON", r.merchant)
    }

    @Test fun creditIgnored() {
        assertNull(SmsParser.parse("AX-HDFCBK", "Rs 500 credited to your A/c XX1234 on 01-01-24"))
    }

    @Test fun otpIgnored() {
        assertNull(SmsParser.parse("AX-HDFCBK", "123456 is your OTP for payment of Rs 500. Do not share."))
    }
}
