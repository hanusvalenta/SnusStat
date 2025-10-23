package com.example.snusstat

import android.app.Application
import android.os.Bundle
import android.content.Context
import androidx.activity.ComponentActivity
import androidx.activity.viewModels
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.snusstat.ui.theme.SnusStatTheme
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.WeekFields
import java.util.Locale

class SnusViewModel(application: Application) : AndroidViewModel(application) {
    private val _uiState = MutableStateFlow(SnusUiState())
    val uiState: StateFlow<SnusUiState> = _uiState.asStateFlow()

    private val prefs = application.getSharedPreferences("snus_tracker_prefs", Context.MODE_PRIVATE)
    private val snusEvents = mutableListOf<Long>()

    companion object {
        private const val SNUS_EVENTS_KEY = "snus_events"
        private const val STORAGE_COUNT_KEY = "storage_count"
    }

    init {
        loadSnusEvents()
        updateCounts()
        loadStorageCount()
    }

    private fun loadSnusEvents() {
        val savedEvents = prefs.getStringSet(SNUS_EVENTS_KEY, emptySet()) ?: emptySet()
        snusEvents.clear()
        snusEvents.addAll(savedEvents.map { it.toLong() }.sorted())
    }
    
    private fun loadStorageCount() {
        val savedCount = prefs.getInt(STORAGE_COUNT_KEY, 0)
        _uiState.update { it.copy(storageCount = savedCount) }
    }

    private fun saveSnusEvents() {
        val editor = prefs.edit()
        val eventsAsStringSet = snusEvents.map { it.toString() }.toSet()
        editor.putStringSet(SNUS_EVENTS_KEY, eventsAsStringSet).apply()
    }

    private fun saveStorageCount() {
        prefs.edit().putInt(STORAGE_COUNT_KEY, _uiState.value.storageCount).apply()
    }

    fun addSnusEvent() {
        viewModelScope.launch {
            val now = Instant.now().toEpochMilli()
            snusEvents.add(now)
            saveSnusEvents()
            updateCounts()
            if (_uiState.value.storageCount > 0) {
                _uiState.update { it.copy(storageCount = it.storageCount - 1) }
                saveStorageCount()
            }
        }
    }

    fun resetCounts() {
        viewModelScope.launch {
            snusEvents.clear()
            saveSnusEvents()
            updateCounts()
        }
    }

    fun addOneToStorage() {
        _uiState.update { it.copy(storageCount = it.storageCount + 1) }
        saveStorageCount()
    }

    fun removeOneFromStorage() {
        _uiState.update {
            val newCount = if (it.storageCount > 0) it.storageCount - 1 else 0
            it.copy(storageCount = newCount)
        }
        saveStorageCount()
    }

    fun addTwentyToStorage() {
        _uiState.update { it.copy(storageCount = it.storageCount + 20) }
        saveStorageCount()
    }

    private fun updateCounts() {
        val now = LocalDate.now()
        val zoneId = ZoneId.systemDefault()

        val todayCount = snusEvents.count {
            Instant.ofEpochMilli(it).atZone(zoneId).toLocalDate().isEqual(now)
        }

        val weekFields = WeekFields.of(Locale.getDefault())
        val currentWeek = now.get(weekFields.weekOfWeekBasedYear())
        val currentYear = now.year
        val weekCount = snusEvents.count {
            val eventDate = Instant.ofEpochMilli(it).atZone(zoneId).toLocalDate()
            eventDate.get(weekFields.weekOfWeekBasedYear()) == currentWeek && eventDate.year == currentYear
        }

        val monthCount = snusEvents.count {
            val eventDate = Instant.ofEpochMilli(it).atZone(zoneId).toLocalDate()
            eventDate.month == now.month && eventDate.year == now.year
        }

        _uiState.update {
            it.copy(
                todayCount = todayCount,
                weekCount = weekCount,
                monthCount = monthCount
            )
        }
    }
}

data class SnusUiState(
    val todayCount: Int = 0,
    val weekCount: Int = 0,
    val monthCount: Int = 0,
    val storageCount: Int = 0
)

class MainActivity : ComponentActivity() {
    private val viewModel: SnusViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            SnusStatTheme {
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    val uiState by viewModel.uiState.collectAsState()
                    SnusTrackerScreen(
                        uiState = uiState,
                        onSnusTaken = { viewModel.addSnusEvent() },
                        onReset = { viewModel.resetCounts() },
                        onAddOne = { viewModel.addOneToStorage() },
                        onRemoveOne = { viewModel.removeOneFromStorage() },
                        onAddTwenty = { viewModel.addTwentyToStorage() }
                    )
                }
            }
        }
    }
}

@Composable
fun SnusTrackerScreen(
    uiState: SnusUiState,
    onSnusTaken: () -> Unit,
    onReset: () -> Unit,
    onAddOne: () -> Unit,
    onRemoveOne: () -> Unit,
    onAddTwenty: () -> Unit,
    modifier: Modifier = Modifier) {
    Box(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier.align(Alignment.Center),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("Snus taken today >> ${uiState.todayCount}", style = MaterialTheme.typography.headlineMedium)
            Text("Snus taken this week >> ${uiState.weekCount}", style = MaterialTheme.typography.headlineMedium, modifier = Modifier.padding(top = 8.dp))
            Text("Snus taken this month >> ${uiState.monthCount}", style = MaterialTheme.typography.headlineMedium, modifier = Modifier.padding(top = 8.dp))
            Button(onClick = onSnusTaken, modifier = Modifier.padding(top = 32.dp)) {
                Text("I had a snus")
            }

            Text("Snus in storage >> ${uiState.storageCount}", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.padding(top = 32.dp))
            Row(modifier = Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onAddOne) { Text("+") }
                Button(onClick = onRemoveOne) { Text("-") }
                Button(onClick = onAddTwenty) { Text("+20") }
            }
            Button(onClick = onReset, modifier = Modifier.padding(top = 8.dp)) {
                Text("Reset")
            }
        }
        Text(
            text = "Made with 🤍 by Hanuš Valenta",
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.align(Alignment.BottomCenter).padding(16.dp),
        )
    }
}