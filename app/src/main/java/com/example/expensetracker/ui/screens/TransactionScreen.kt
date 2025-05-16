package com.example.expensetracker

import android.app.DatePickerDialog
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import java.text.SimpleDateFormat
import java.util.*

data class Transaction(
    val id: String = "",
    val amount: Double = 0.0,
    val category: String = "",
    val description: String = "",
    val type: String = "",
    val date: String = ""
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TransactionScreen(navController: NavController, auth: FirebaseAuth) {
    val db = FirebaseFirestore.getInstance()
    val userId = auth.currentUser?.uid ?: ""
    val context = LocalContext.current

    val sdf = remember { SimpleDateFormat("dd MMM yyyy", Locale.getDefault()) }
    val inputSdf = remember { SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()) }

    var transactions by remember { mutableStateOf(listOf<Transaction>()) }
    var filteredTransactions by remember { mutableStateOf(listOf<Transaction>()) }
    var loading by remember { mutableStateOf(true) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var transactionToDelete by remember { mutableStateOf<Transaction?>(null) }

    var selectedDate by remember { mutableStateOf<Long?>(null) }
    var selectedPeriod by remember { mutableStateOf<String?>(null) }
    var selectedType by remember { mutableStateOf("All") }
    var showDatePicker by remember { mutableStateOf(false) }

    val periodOptions = listOf("This Week", "This Month", "Last 7 Days", "Last 30 Days")
    val typeOptions = listOf("All", "Income", "Expense")

    // Fetch Transactions from Firestore
    LaunchedEffect(userId) {
        if (userId.isNotEmpty()) {
            db.collection("transactions")
                .whereEqualTo("userId", userId)
                .addSnapshotListener { snapshot, error ->
                    if (error != null) {
                        Toast.makeText(context, "Error: ${error.message}", Toast.LENGTH_SHORT).show()
                        return@addSnapshotListener
                    }
                    if (snapshot != null) {
                        transactions = snapshot.documents.mapNotNull { doc ->
                            Transaction(
                                id = doc.id,
                                amount = doc.getDouble("amount") ?: 0.0,
                                category = doc.getString("category") ?: "",
                                description = doc.getString("title") ?: "",
                                type = doc.getString("type") ?: "",
                                date = doc.getString("date") ?: inputSdf.format(Date())
                            )
                        }.sortedByDescending { 
                            inputSdf.parse(it.date)?.time ?: System.currentTimeMillis() 
                        }
                        applyFilters(transactions, selectedDate, selectedPeriod, selectedType) {
                            filteredTransactions = it
                        }
                    }
                    loading = false
                }
        }
    }

    fun showDatePicker() {
        val calendar = Calendar.getInstance()
        DatePickerDialog(
            context,
            { _, year, month, dayOfMonth ->
                val selectedCal = Calendar.getInstance().apply {
                    set(year, month, dayOfMonth, 0, 0, 0)
                }
                selectedDate = selectedCal.timeInMillis
                selectedPeriod = null // disable period filter
                applyFilters(transactions, selectedDate, null, selectedType) {
                    filteredTransactions = it
                }
            },
            calendar.get(Calendar.YEAR),
            calendar.get(Calendar.MONTH),
            calendar.get(Calendar.DAY_OF_MONTH)
        ).show()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Transactions", style = MaterialTheme.typography.titleLarge) },
                navigationIcon = {
                    IconButton(onClick = { navController.navigateUp() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color(0xFF7C4DFF),
                    titleContentColor = Color.White
                )
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFFF5F5F5))
                .padding(paddingValues)
        ) {
            // Filter Section
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Date Picker and Period Filter in same row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // Date Picker Button
                        Row(
                            modifier = Modifier.weight(1f)
                        ) {
                            OutlinedButton(
                                onClick = { showDatePicker() },
                                modifier = Modifier.fillMaxWidth(),
                                colors = ButtonDefaults.outlinedButtonColors(
                                    contentColor = Color(0xFF7C4DFF)
                                )
                            ) {
                                Icon(Icons.Default.DateRange, contentDescription = "Date")
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(selectedDate?.let { sdf.format(Date(it)) } ?: "Pick Date")
                            }
                        }

                        // Period Filter
                        var showPeriodDropdown by remember { mutableStateOf(false) }
                        Row(
                            modifier = Modifier.weight(1f)
                        ) {
                            Box {
                                OutlinedButton(
                                    onClick = { showPeriodDropdown = true },
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = ButtonDefaults.outlinedButtonColors(
                                        contentColor = Color(0xFF7C4DFF)
                                    )
                                ) {
                                    Icon(Icons.Default.Menu, contentDescription = "Filter")
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(selectedPeriod ?: "Periods")
                                }
                                
                                DropdownMenu(
                                    expanded = showPeriodDropdown,
                                    onDismissRequest = { showPeriodDropdown = false }
                                ) {
                                    periodOptions.forEach { period ->
                                        DropdownMenuItem(
                                            text = { Text(period) },
                                            onClick = {
                                                selectedPeriod = period
                                                selectedDate = null // disable date filter
                                                applyFilters(transactions, null, period, selectedType) {
                                                    filteredTransactions = it
                                                }
                                                showPeriodDropdown = false
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // Type Filter
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        typeOptions.forEach { type ->
                            FilterChip(
                                selected = selectedType == type,
                                onClick = {
                                    selectedType = type
                                    applyFilters(transactions, selectedDate, selectedPeriod, type) {
                                        filteredTransactions = it
                                    }
                                },
                                label = { Text(type) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = when (type) {
                                        "Income" -> Color(0xFF4CAF50)
                                        "Expense" -> Color(0xFFF44336)
                                        else -> Color(0xFF7C4DFF)
                                    }
                                )
                            )
                        }
                    }
                }
            }

            // Transactions List
            if (loading) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = Color(0xFF7C4DFF))
                }
            } else if (filteredTransactions.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "No transactions found",
                        style = MaterialTheme.typography.bodyLarge,
                        color = Color.Gray
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(filteredTransactions) { transaction ->
                        TransactionItem(
                            transaction = transaction,
                            onLongPress = {
                                transactionToDelete = transaction
                                showDeleteDialog = true
                            }
                        )
                    }
                }
            }
        }

        // Date Picker Dialog
        if (showDatePicker) {
            DatePickerDialog(
                onDismissRequest = { showDatePicker = false },
                confirmButton = {
                    TextButton(
                        onClick = {
                            showDatePicker = false
                            applyFilters(transactions, selectedDate, selectedPeriod, selectedType) {
                                filteredTransactions = it
                            }
                        }
                    ) {
                        Text("OK")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showDatePicker = false }) {
                        Text("Cancel")
                    }
                }
            ) {
                DatePicker(
                    state = rememberDatePickerState(
                        initialSelectedDateMillis = selectedDate ?: System.currentTimeMillis()
                    ),
                    colors = DatePickerDefaults.colors(
                        selectedDayContainerColor = Color(0xFF7C4DFF),
                        todayDateBorderColor = Color(0xFF7C4DFF)
                    ),
                    showModeToggle = false
                )
            }
        }

        // Delete Confirmation Dialog
        if (showDeleteDialog && transactionToDelete != null) {
            AlertDialog(
                onDismissRequest = { showDeleteDialog = false },
                title = { Text("Delete Transaction") },
                text = { Text("Are you sure you want to delete this transaction?") },
                confirmButton = {
                    TextButton(
                        onClick = {
                            db.collection("transactions")
                                .document(transactionToDelete!!.id)
                                .delete()
                                .addOnSuccessListener {
                                    Toast.makeText(context, "Transaction deleted", Toast.LENGTH_SHORT).show()
                                    showDeleteDialog = false
                                }
                                .addOnFailureListener {
                                    Toast.makeText(context, "Failed to delete: ${it.message}", Toast.LENGTH_SHORT).show()
                                }
                        }
                    ) {
                        Text("Delete")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showDeleteDialog = false }) {
                        Text("Cancel")
                    }
                }
            )
        }
    }
}

@Composable
fun TransactionItem(transaction: Transaction, onLongPress: () -> Unit) {
    val sdf = remember { SimpleDateFormat("dd MMM yyyy", Locale.getDefault()) }
    val inputSdf = remember { SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()) }
    
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .pointerInput(Unit) {
                detectTapGestures(
                    onLongPress = { onLongPress() }
                )
            },
        colors = CardDefaults.cardColors(
            containerColor = if (transaction.type == "income") Color(0xFF4CAF50) else Color(0xFFF44336)
        ),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = transaction.description,
                    color = Color.White,
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                )
                Text(
                    text = "₹${transaction.amount}",
                    color = Color.White,
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = transaction.category,
                    color = Color.White.copy(alpha = 0.7f),
                    style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp)
                )
                Text(
                    text = inputSdf.parse(transaction.date)?.let { sdf.format(it) } ?: "",
                    color = Color.White.copy(alpha = 0.7f),
                    style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp)
                )
            }
        }
    }
}

fun applyFilters(
    transactions: List<Transaction>,
    date: Long?,
    period: String?,
    type: String,
    onFiltered: (List<Transaction>) -> Unit
) {
    val now = Calendar.getInstance()
    val inputSdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
    
    val filtered = transactions.filter { txn ->
        val matchType = when (type) {
            "Income" -> txn.type == "income"
            "Expense" -> txn.type == "expense"
            else -> true
        }
        
        val transactionDate = inputSdf.parse(txn.date)?.time ?: System.currentTimeMillis()
        
        val matchDate = date?.let {
            val cal = Calendar.getInstance().apply { timeInMillis = transactionDate }
            val selected = Calendar.getInstance().apply { timeInMillis = it }
            cal.get(Calendar.YEAR) == selected.get(Calendar.YEAR) &&
                    cal.get(Calendar.DAY_OF_YEAR) == selected.get(Calendar.DAY_OF_YEAR)
        } ?: true

        val matchPeriod = period?.let {
            val cal = Calendar.getInstance()
            cal.timeInMillis = transactionDate
            when (it) {
                "This Week" -> {
                    val currentWeek = now.get(Calendar.WEEK_OF_YEAR)
                    cal.get(Calendar.WEEK_OF_YEAR) == currentWeek && cal.get(Calendar.YEAR) == now.get(Calendar.YEAR)
                }
                "This Month" -> cal.get(Calendar.MONTH) == now.get(Calendar.MONTH) && cal.get(Calendar.YEAR) == now.get(Calendar.YEAR)
                "Last 7 Days" -> transactionDate >= now.timeInMillis - 7 * 24 * 60 * 60 * 1000
                "Last 30 Days" -> transactionDate >= now.timeInMillis - 30 * 24 * 60 * 60 * 1000
                else -> true
            }
        } ?: true

        matchType && matchDate && matchPeriod
    }
    onFiltered(filtered)
}
