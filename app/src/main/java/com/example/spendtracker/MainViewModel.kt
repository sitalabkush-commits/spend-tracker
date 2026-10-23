package com.example.spendtracker

import android.app.Application
import android.provider.Telephony
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.spendtracker.data.AppDatabase
import com.example.spendtracker.data.SpendEntity
import com.example.spendtracker.sms.SmsParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters

enum class SpendFilter(val label: String) { TODAY("Today"), WEEK("This Week"), MONTH("This Month") }

@OptIn(ExperimentalCoroutinesApi::class)
class MainViewModel(app: Application) : AndroidViewModel(app) {

    private val dao = AppDatabase.get(app).spendDao()
    private val zone = ZoneId.systemDefault()

    private val _filter = MutableStateFlow(SpendFilter.TODAY)
    val filter: StateFlow<SpendFilter> = _filter.asStateFlow()

    private val _lastScanCount = MutableStateFlow<Int?>(null)
    val lastScanCount: StateFlow<Int?> = _lastScanCount.asStateFlow()

    val todayTotal: StateFlow<Long> = dao.observeTotalSince(startOf(SpendFilter.TODAY))
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0L)

    val monthTotal: StateFlow<Long> = dao.observeTotalSince(startOf(SpendFilter.MONTH))
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0L)

    val transactions: StateFlow<List<SpendEntity>> = _filter
        .flatMapLatest { dao.observeSince(startOf(it)) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun setFilter(f: SpendFilter) { _filter.value = f }

    private fun startOf(f: SpendFilter): Long {
        val today = LocalDate.now(zone)
        val day = when (f) {
            SpendFilter.TODAY -> today
            SpendFilter.WEEK -> today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
            SpendFilter.MONTH -> today.withDayOfMonth(1)
        }
        return day.atStartOfDay(zone).toInstant().toEpochMilli()
    }

    /** Parses existing inbox SMS (latest 1000) once permission is granted. Duplicates are ignored. */
    fun scanInbox() {
        viewModelScope.launch(Dispatchers.IO) {
            var added = 0
            try {
                getApplication<Application>().contentResolver.query(
                    Telephony.Sms.Inbox.CONTENT_URI,
                    arrayOf(Telephony.Sms.ADDRESS, Telephony.Sms.BODY, Telephony.Sms.DATE),
                    null, null, "${Telephony.Sms.DATE} DESC"
                )?.use { c ->
                    val iAddr = c.getColumnIndexOrThrow(Telephony.Sms.ADDRESS)
                    val iBody = c.getColumnIndexOrThrow(Telephony.Sms.BODY)
                    val iDate = c.getColumnIndexOrThrow(Telephony.Sms.DATE)
                    var rows = 0
                    while (c.moveToNext() && rows++ < 1000) {
                        val sender = c.getString(iAddr).orEmpty()
                        val body = c.getString(iBody).orEmpty()
                        val parsed = SmsParser.parse(sender, body) ?: continue
                        val id = dao.insert(
                            SpendEntity(
                                amountMinor = parsed.amountMinor,
                                merchant = parsed.merchant,
                                source = parsed.source,
                                timestamp = c.getLong(iDate),
                                smsHash = SmsParser.fingerprint(sender, body)
                            )
                        )
                        if (id != -1L) added++
                    }
                }
            } catch (_: SecurityException) {
                // permission missing: nothing to scan
            }
            _lastScanCount.value = added
        }
    }
}
