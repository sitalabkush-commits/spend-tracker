package com.example.spendtracker

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.spendtracker.data.SpendEntity
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface(Modifier.fillMaxSize()) { SpendApp() }
            }
        }
    }
}

private val SMS_PERMISSIONS = arrayOf(Manifest.permission.RECEIVE_SMS, Manifest.permission.READ_SMS)

private fun hasSmsPermissions(ctx: Context) = SMS_PERMISSIONS.all {
    ContextCompat.checkSelfPermission(ctx, it) == PackageManager.PERMISSION_GRANTED
}

fun formatMoney(minor: Long): String =
    "₹" + String.format(Locale.getDefault(), "%,.2f", minor / 100.0)

private val dateFmt = DateTimeFormatter.ofPattern("dd MMM, hh:mm a", Locale.getDefault())
private fun formatTime(ts: Long): String =
    Instant.ofEpochMilli(ts).atZone(ZoneId.systemDefault()).format(dateFmt)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SpendApp(vm: MainViewModel = viewModel()) {
    val ctx = LocalContext.current
    var granted by remember { mutableStateOf(hasSmsPermissions(ctx)) }
    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { result -> granted = result.values.all { it } }

    val today by vm.todayTotal.collectAsStateWithLifecycle()
    val month by vm.monthTotal.collectAsStateWithLifecycle()
    val filter by vm.filter.collectAsStateWithLifecycle()
    val items by vm.transactions.collectAsStateWithLifecycle()
    val scanned by vm.lastScanCount.collectAsStateWithLifecycle()

    Scaffold(topBar = { CenterAlignedTopAppBar(title = { Text("Spend Tracker") }) }) { pad ->
        LazyColumn(
            modifier = Modifier.padding(pad).fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (!granted) {
                item {
                    Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("SMS permission needed", fontWeight = FontWeight.Bold)
                            Text("Everything stays on your phone. The app has no internet access.")
                            Button(onClick = { launcher.launch(SMS_PERMISSIONS) }) { Text("Grant permission") }
                        }
                    }
                }
            } else {
                item {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        OutlinedButton(onClick = { vm.scanInbox() }) { Text("Scan old SMS") }
                        scanned?.let { Text("$it new found") }
                    }
                }
            }

            item {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    SummaryCard("Today", formatMoney(today), Modifier.weight(1f))
                    SummaryCard("This Month", formatMoney(month), Modifier.weight(1f))
                }
            }

            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SpendFilter.values().forEach { f ->
                        FilterChip(
                            selected = f == filter,
                            onClick = { vm.setFilter(f) },
                            label = { Text(f.label) }
                        )
                    }
                }
            }

            if (items.isEmpty()) {
                item { Text("No transactions yet.", color = MaterialTheme.colorScheme.onSurfaceVariant) }
            } else {
                items(items, key = { it.id }) { TransactionRow(it) }
            }
        }
    }
}

@Composable
private fun SummaryCard(title: String, amount: String, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(title, style = MaterialTheme.typography.labelLarge)
            Text(amount, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun TransactionRow(t: SpendEntity) {
    Card(Modifier.fillMaxWidth()) {
        Row(
            Modifier.padding(16.dp).fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text(t.merchant, fontWeight = FontWeight.SemiBold)
                Text(
                    "${formatTime(t.timestamp)} • ${t.source}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Text("-" + formatMoney(t.amountMinor), fontWeight = FontWeight.Bold)
        }
    }
}
