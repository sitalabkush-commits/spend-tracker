package com.example.spendtracker.sms

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import com.example.spendtracker.data.AppDatabase
import com.example.spendtracker.data.SpendEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/** Receives every incoming SMS, parses it, and stores debit transactions locally. */
class SmsReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) return
        val parts = Telephony.Sms.Intents.getMessagesFromIntent(intent) ?: return

        // Long SMS arrive as multiple parts: merge them per sender.
        val bySender = parts.groupBy { it.originatingAddress.orEmpty() }
        val dao = AppDatabase.get(context).spendDao()
        val pending = goAsync() // keep receiver alive while we write to DB

        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                for ((sender, msgs) in bySender) {
                    val body = msgs.joinToString("") { it.messageBody.orEmpty() }
                    val parsed = SmsParser.parse(sender, body) ?: continue
                    dao.insert(
                        SpendEntity(
                            amountMinor = parsed.amountMinor,
                            merchant = parsed.merchant,
                            source = parsed.source,
                            timestamp = msgs.first().timestampMillis,
                            smsHash = SmsParser.fingerprint(sender, body)
                        )
                    )
                }
            } finally {
                pending.finish()
            }
        }
    }
}
