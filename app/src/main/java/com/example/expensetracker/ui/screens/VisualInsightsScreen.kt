package com.example.expensetracker

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import java.text.SimpleDateFormat
import java.util.*
import androidx.compose.foundation.Canvas
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.platform.LocalContext
import kotlin.math.*

data class InsightData(
    val totalSpent: Double = 0.0,
    val totalIncome: Double = 0.0,
    val categoryBreakdown: Map<String, Double> = emptyMap(),
    val weeklyData: List<Double> = emptyList(),
    val monthlyData: List<Double> = emptyList(),
    val savingsRate: Double = 0.0,
    val topCategory: String = "",
    val topCategoryAmount: Double = 0.0,
    val weeklyIncomeData: List<Double> = emptyList(),
    val monthlyIncomeData: List<Double> = emptyList()
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VisualInsightsScreen(navController: NavController, auth: FirebaseAuth) {
    val db = FirebaseFirestore.getInstance()
    val userId = auth.currentUser?.uid ?: ""
    val context = LocalContext.current

    var insightData by remember { mutableStateOf(InsightData()) }
    var selectedPeriod by remember { mutableStateOf("This Month") }
    var loading by remember { mutableStateOf(true) }
    var showComparisonDialog by remember { mutableStateOf(false) }

    val periodOptions = listOf("This Week", "This Month")
    
    val chartColors = listOf(
        Color(0xFF7C4DFF),
        Color(0xFF4CAF50),
        Color(0xFFFF9800),
        Color(0xFFF44336),
        Color(0xFF2196F3)
    )

    // Fetch and process data
    LaunchedEffect(userId, selectedPeriod) {
        if (userId.isNotEmpty()) {
            loading = true
            db.collection("transactions")
                .whereEqualTo("userId", userId)
                .addSnapshotListener { snapshot, error ->
                    if (error != null) {
                        // Handle error
                        loading = false
                        return@addSnapshotListener
                    }
                    
                    if (snapshot != null) {
                        val transactions = snapshot.documents.mapNotNull { doc ->
                            Transaction(
                                id = doc.id,
                                amount = doc.getDouble("amount") ?: 0.0,
                                category = doc.getString("category") ?: "",
                                description = doc.getString("title") ?: "",
                                type = doc.getString("type") ?: "",
                                date = doc.getString("date") ?: SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
                            )
                        }

                        // Process data based on selected period
                        val now = Calendar.getInstance()
                        val filteredTransactions = transactions.filter { txn ->
                            val txnDate = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).parse(txn.date)?.time ?: 0L
                            when (selectedPeriod) {
                                "This Week" -> {
                                    val weekStart = now.clone() as Calendar
                                    weekStart.set(Calendar.DAY_OF_WEEK, weekStart.firstDayOfWeek)
                                    txnDate >= weekStart.timeInMillis
                                }
                                "This Month" -> {
                                    val monthStart = now.clone() as Calendar
                                    monthStart.set(Calendar.DAY_OF_MONTH, 1)
                                    txnDate >= monthStart.timeInMillis
                                }
                                else -> true
                            }
                        }

                        // Calculate insights
                        val totalSpent = filteredTransactions.filter { it.type == "expense" }.sumOf { it.amount }
                        val totalIncome = filteredTransactions.filter { it.type == "income" }.sumOf { it.amount }
                        
                        val categoryBreakdown = filteredTransactions
                            .filter { it.type == "expense" }
                            .groupBy { it.category }
                            .mapValues { it.value.sumOf { txn -> txn.amount } }

                        // Find top category
                        val topCategory = categoryBreakdown.maxByOrNull { it.value }
                        val topCategoryName = topCategory?.key ?: ""
                        val topCategoryAmount = topCategory?.value ?: 0.0

                        // Calculate savings rate
                        val savingsRate = if (totalIncome > 0) {
                            ((totalIncome - totalSpent) / totalIncome) * 100
                        } else {
                            0.0
                        }

                        // Generate weekly/monthly data
                        val weeklyData = List(7) { day ->
                            filteredTransactions
                                .filter { txn ->
                                    val txnDate = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).parse(txn.date)?.time ?: 0L
                                    val dayStart = now.clone() as Calendar
                                    dayStart.add(Calendar.DAY_OF_YEAR, -day)
                                    dayStart.set(Calendar.HOUR_OF_DAY, 0)
                                    dayStart.set(Calendar.MINUTE, 0)
                                    dayStart.set(Calendar.SECOND, 0)
                                    val dayEnd = dayStart.clone() as Calendar
                                    dayEnd.add(Calendar.DAY_OF_YEAR, 1)
                                    txnDate in dayStart.timeInMillis until dayEnd.timeInMillis
                                }
                                .filter { it.type == "expense" }
                                .sumOf { it.amount }
                        }.reversed()

                        val monthlyData = List(30) { day ->
                            filteredTransactions
                                .filter { txn ->
                                    val txnDate = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).parse(txn.date)?.time ?: 0L
                                    val dayStart = now.clone() as Calendar
                                    dayStart.add(Calendar.DAY_OF_YEAR, -day)
                                    dayStart.set(Calendar.HOUR_OF_DAY, 0)
                                    dayStart.set(Calendar.MINUTE, 0)
                                    dayStart.set(Calendar.SECOND, 0)
                                    val dayEnd = dayStart.clone() as Calendar
                                    dayEnd.add(Calendar.DAY_OF_YEAR, 1)
                                    txnDate in dayStart.timeInMillis until dayEnd.timeInMillis
                                }
                                .filter { it.type == "expense" }
                                .sumOf { it.amount }
                        }.reversed()
                        
                        // Generate income data for the same periods
                        val weeklyIncomeData = List(7) { day ->
                            filteredTransactions
                                .filter { txn ->
                                    val txnDate = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).parse(txn.date)?.time ?: 0L
                                    val dayStart = now.clone() as Calendar
                                    dayStart.add(Calendar.DAY_OF_YEAR, -day)
                                    dayStart.set(Calendar.HOUR_OF_DAY, 0)
                                    dayStart.set(Calendar.MINUTE, 0)
                                    dayStart.set(Calendar.SECOND, 0)
                                    val dayEnd = dayStart.clone() as Calendar
                                    dayEnd.add(Calendar.DAY_OF_YEAR, 1)
                                    txnDate in dayStart.timeInMillis until dayEnd.timeInMillis
                                }
                                .filter { it.type == "income" }
                                .sumOf { it.amount }
                        }.reversed()

                        val monthlyIncomeData = List(30) { day ->
                            filteredTransactions
                                .filter { txn ->
                                    val txnDate = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).parse(txn.date)?.time ?: 0L
                                    val dayStart = now.clone() as Calendar
                                    dayStart.add(Calendar.DAY_OF_YEAR, -day)
                                    dayStart.set(Calendar.HOUR_OF_DAY, 0)
                                    dayStart.set(Calendar.MINUTE, 0)
                                    dayStart.set(Calendar.SECOND, 0)
                                    val dayEnd = dayStart.clone() as Calendar
                                    dayEnd.add(Calendar.DAY_OF_YEAR, 1)
                                    txnDate in dayStart.timeInMillis until dayEnd.timeInMillis
                                }
                                .filter { it.type == "income" }
                                .sumOf { it.amount }
                        }.reversed()

                        insightData = InsightData(
                            totalSpent = totalSpent,
                            totalIncome = totalIncome,
                            categoryBreakdown = categoryBreakdown,
                            weeklyData = weeklyData,
                            monthlyData = monthlyData,
                            savingsRate = savingsRate,
                            topCategory = topCategoryName,
                            topCategoryAmount = topCategoryAmount,
                            weeklyIncomeData = weeklyIncomeData,
                            monthlyIncomeData = monthlyIncomeData
                        )
                        loading = false
                    }
                }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Financial Insights", style = MaterialTheme.typography.titleLarge) },
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
        if (loading) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = Color(0xFF7C4DFF))
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0xFFF5F5F5))
                    .padding(paddingValues),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Period Selector Card with horizontal scrolling
                item {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .shadow(4.dp, RoundedCornerShape(16.dp)),
                        colors = CardDefaults.cardColors(containerColor = Color.White),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp)
                        ) {
                            Text(
                                "Select Period",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            
                            // Period selector as a horizontally scrollable row
                            LazyRow(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                items(periodOptions) { period ->
                                    Button(
                                        onClick = { selectedPeriod = period },
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = if (selectedPeriod == period) 
                                                Color(0xFF7C4DFF) else Color(0xFFE0E0E0),
                                            contentColor = if (selectedPeriod == period) 
                                                Color.White else Color.Black
                                        ),
                                        modifier = Modifier.padding(horizontal = 4.dp)
                                    ) {
                                        Text(period)
                                    }
                                }
                            }
                        }
                    }
                }

                // Summary Cards Row
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // Total Spent Card
                        Card(
                            modifier = Modifier
                                .weight(1f)
                                .shadow(4.dp, RoundedCornerShape(16.dp)),
                            colors = CardDefaults.cardColors(containerColor = Color(0xFF7C4DFF)),
                            shape = RoundedCornerShape(16.dp)
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    "Total Spent",
                                    color = Color.White,
                                    style = MaterialTheme.typography.titleMedium
                                )
                                Text(
                                    "₹${insightData.totalSpent}",
                                    color = Color.White,
                                    style = MaterialTheme.typography.headlineMedium,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }

                        // Total Income Card
                        Card(
                            modifier = Modifier
                                .weight(1f)
                                .shadow(4.dp, RoundedCornerShape(16.dp)),
                            colors = CardDefaults.cardColors(containerColor = Color(0xFF4CAF50)),
                            shape = RoundedCornerShape(16.dp)
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    "Total Income",
                                    color = Color.White,
                                    style = MaterialTheme.typography.titleMedium
                                )
                                Text(
                                    "₹${insightData.totalIncome}",
                                    color = Color.White,
                                    style = MaterialTheme.typography.headlineMedium,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }

                // Savings Rate and Top Category Row
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // Savings Rate Card
                        Card(
                            modifier = Modifier
                                .weight(1f)
                                .shadow(4.dp, RoundedCornerShape(16.dp)),
                            colors = CardDefaults.cardColors(containerColor = Color(0xFF2196F3)),
                            shape = RoundedCornerShape(16.dp)
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    "Savings Rate",
                                    color = Color.White,
                                    style = MaterialTheme.typography.titleMedium
                                )
                                Text(
                                    "${String.format("%.1f", insightData.savingsRate)}%",
                                    color = Color.White,
                                    style = MaterialTheme.typography.headlineMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    if (insightData.savingsRate >= 20) "Great job!" else "Try to save more",
                                    color = Color.White,
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            }
                        }

                        // Top Category Card
                        Card(
                            modifier = Modifier
                                .weight(1f)
                                .shadow(4.dp, RoundedCornerShape(16.dp)),
                            colors = CardDefaults.cardColors(containerColor = Color(0xFFFF9800)),
                            shape = RoundedCornerShape(16.dp)
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    "Top Category",
                                    color = Color.White,
                                    style = MaterialTheme.typography.titleMedium
                                )
                                Text(
                                    insightData.topCategory,
                                    color = Color.White,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    "₹${insightData.topCategoryAmount}",
                                    color = Color.White,
                                    style = MaterialTheme.typography.bodyLarge
                                )
                            }
                        }
                    }
                }

                // Separate graphs for income and expenses with comparison dialog
                item {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .shadow(4.dp, RoundedCornerShape(16.dp)),
                        colors = CardDefaults.cardColors(containerColor = Color.White),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp)
                        ) {
                            Text(
                                "Financial Overview",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            
                            // Get data based on selected period
                            val expenseData = when (selectedPeriod) {
                                "This Week" -> insightData.weeklyData
                                "This Month" -> insightData.monthlyData
                                else -> insightData.monthlyData
                            }
                            
                            val incomeData = when (selectedPeriod) {
                                "This Week" -> insightData.weeklyIncomeData
                                "This Month" -> insightData.monthlyIncomeData
                                else -> insightData.monthlyIncomeData
                            }
                            
                            // Calculate max value for scaling
                            val maxExpense = expenseData.maxOrNull() ?: 0.0
                            val maxIncome = incomeData.maxOrNull() ?: 0.0
                            val maxValue = maxOf(maxExpense, maxIncome, 1.0) // Ensure at least 1.0 to avoid division by zero
                            
                            // For monthly view, we need to handle more data points
                            val isMonthlyView = selectedPeriod == "This Month"
                            
                            // For today view, we need special handling
                            val isTodayView = false
                            
                            // Combined Line Chart for Income and Expenses
                            Text(
                                "Income vs Expenses",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            
                            // Line chart with y-axis labels
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(200.dp)
                            ) {
                                // Y-axis labels
                                Column(
                                    modifier = Modifier
                                        .width(40.dp)
                                        .fillMaxHeight(),
                                    horizontalAlignment = Alignment.End,
                                    verticalArrangement = Arrangement.SpaceBetween
                                ) {
                                    // Calculate appropriate Y-axis values
                                    val yAxisValues = calculateYAxisValues(maxValue)
                                    
                                    // Display Y-axis values in reverse order (highest at top, 0 at bottom)
                                    yAxisValues.reversed().forEach { value ->
                                        Text(
                                            "₹${String.format("%.0f", value)}",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = Color.Gray
                                        )
                                    }
                                }
                                
                                Spacer(modifier = Modifier.width(8.dp))
                                
                                // Line chart
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .fillMaxHeight()
                                        .background(Color(0xFFF5F5F5), RoundedCornerShape(8.dp))
                                        .padding(8.dp)
                                ) {
                                    Canvas(modifier = Modifier.fillMaxSize()) {
                                        val width = size.width
                                        val height = size.height
                                        
                                        // Draw grid lines
                                        val gridColor = Color.Gray.copy(alpha = 0.2f)
                                        val gridStrokeWidth = 1f
                                        
                                        // Calculate Y-axis values for grid lines
                                        val yAxisValues = calculateYAxisValues(maxValue)
                                        
                                        // Horizontal grid lines
                                        yAxisValues.forEach { value ->
                                            val y = height * (1 - (value / maxValue).toFloat())
                                            drawLine(
                                                color = gridColor,
                                                start = Offset(0f, y),
                                                end = Offset(width, y),
                                                strokeWidth = gridStrokeWidth
                                            )
                                        }
                                        
                                        // Vertical grid lines
                                        val numVerticalLines = if (isMonthlyView) 6 else 4
                                        for (i in 0..numVerticalLines) {
                                            val x = width * (i / numVerticalLines.toFloat())
                                            drawLine(
                                                color = gridColor,
                                                start = Offset(x, 0f),
                                                end = Offset(x, height),
                                                strokeWidth = gridStrokeWidth
                                            )
                                        }
                                        
                                        // Draw expense line
                                        if (expenseData.isNotEmpty()) {
                                            val expensePath = Path()
                                            val stepX = width / (expenseData.size - 1).coerceAtLeast(1)
                                            
                                            expenseData.forEachIndexed { index, value ->
                                                val x = index * stepX
                                                val y = height * (1 - (value / maxValue).toFloat())
                                                
                                                if (index == 0) {
                                                    expensePath.moveTo(x, y)
                                                } else {
                                                    expensePath.lineTo(x, y)
                                                }
                                            }
                                            
                                            // Draw the line
                                            drawPath(
                                                path = expensePath,
                                                color = Color(0xFF7C4DFF),
                                                style = Stroke(
                                                    width = 3f,
                                                    cap = StrokeCap.Round,
                                                    join = androidx.compose.ui.graphics.StrokeJoin.Round
                                                )
                                            )
                                            
                                            // Draw data points
                                            expenseData.forEachIndexed { index, value ->
                                                val x = index * stepX
                                                val y = height * (1 - (value / maxValue).toFloat())
                                                
                                                drawCircle(
                                                    color = Color(0xFF7C4DFF),
                                                    radius = 4f,
                                                    center = Offset(x, y)
                                                )
                                            }
                                        }
                                        
                                        // Draw income line
                                        if (incomeData.isNotEmpty()) {
                                            val incomePath = Path()
                                            val stepX = width / (incomeData.size - 1).coerceAtLeast(1)
                                            
                                            incomeData.forEachIndexed { index, value ->
                                                val x = index * stepX
                                                val y = height * (1 - (value / maxValue).toFloat())
                                                
                                                if (index == 0) {
                                                    incomePath.moveTo(x, y)
                                                } else {
                                                    incomePath.lineTo(x, y)
                                                }
                                            }
                                            
                                            // Draw the line
                                            drawPath(
                                                path = incomePath,
                                                color = Color(0xFF4CAF50),
                                                style = Stroke(
                                                    width = 3f,
                                                    cap = StrokeCap.Round,
                                                    join = androidx.compose.ui.graphics.StrokeJoin.Round
                                                )
                                            )
                                            
                                            // Draw data points
                                            incomeData.forEachIndexed { index, value ->
                                                val x = index * stepX
                                                val y = height * (1 - (value / maxValue).toFloat())
                                                
                                                drawCircle(
                                                    color = Color(0xFF4CAF50),
                                                    radius = 4f,
                                                    center = Offset(x, y)
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                            
                            // X-axis labels
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(start = 48.dp), // Align with the chart
                                horizontalArrangement = Arrangement.SpaceEvenly
                            ) {
                                if (isMonthlyView) {
                                    // For monthly view, show week numbers
                                    val numLabels = 4 // Show 4 week labels
                                    val labelIndices = (0 until numLabels).map { it * 7 }
                                    
                                    labelIndices.forEach { index ->
                                        Text(
                                            text = "W${(index / 7) + 1}",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = Color.Gray
                                        )
                                    }
                                } else {
                                    // For weekly view, show day initials
                                    val numLabels = minOf(expenseData.size, 7)
                                    val labelIndices = if (expenseData.size <= 7) {
                                        expenseData.indices.toList()
                                    } else {
                                        val step = expenseData.size / (numLabels - 1)
                                        (0 until numLabels).map { it * step }
                                    }
                                    
                                    labelIndices.forEach { index ->
                                        val days = listOf("M", "T", "W", "T", "F", "S", "S")
                                        val label = days[index]
                                        Text(
                                            text = label,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = Color.Gray
                                        )
                                    }
                                }
                            }
                            
                            Spacer(modifier = Modifier.height(16.dp))
                            
                            // Add a legend for the graphs
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(end = 16.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(12.dp)
                                            .background(Color(0xFF7C4DFF), RoundedCornerShape(2.dp))
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Expenses", style = MaterialTheme.typography.bodySmall)
                                }
                                
                                Row(
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(12.dp)
                                            .background(Color(0xFF4CAF50), RoundedCornerShape(2.dp))
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Income", style = MaterialTheme.typography.bodySmall)
                                }
                            }
                            
                            Spacer(modifier = Modifier.height(16.dp))
                            
                            // Comparison button
                            Button(
                                onClick = { showComparisonDialog = true },
                                modifier = Modifier.fillMaxWidth(),
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF7C4DFF))
                            ) {
                                Text("Show Financial Comparison")
                            }
                        }
                    }
                }

                // Pie Chart for Transaction Insights
                item {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .shadow(4.dp, RoundedCornerShape(16.dp)),
                        colors = CardDefaults.cardColors(containerColor = Color.White),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp)
                        ) {
                            Text(
                                "Category Distribution",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            
                            // Pie chart with legend
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                // Pie Chart
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .aspectRatio(1f)
                                        .padding(8.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    val total = insightData.categoryBreakdown.values.sum()
                                    
                                    Canvas(modifier = Modifier.fillMaxSize()) {
                                        var startAngle = 0f
                                        insightData.categoryBreakdown.entries.forEachIndexed { index, (_, value) ->
                                            val sweepAngle = (value.toFloat() / total.toFloat() * 360f)
                                            drawArc(
                                                color = chartColors[index % chartColors.size],
                                                startAngle = startAngle,
                                                sweepAngle = sweepAngle,
                                                useCenter = true,
                                                size = Size(size.width, size.height)
                                            )
                                            startAngle += sweepAngle
                                        }
                                    }
                                }

                                // Legend
                                Column(
                                    modifier = Modifier
                                        .weight(1f)
                                        .padding(start = 16.dp)
                                ) {
                                    val pieChartTotal = insightData.categoryBreakdown.values.sum()
                                    insightData.categoryBreakdown.entries.forEachIndexed { index, (category, amount) ->
                                        val percentage = (amount / pieChartTotal) * 100
                                        
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(vertical = 4.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .size(12.dp)
                                                    .background(chartColors[index % chartColors.size], CircleShape)
                                            )
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text(
                                                category,
                                                style = MaterialTheme.typography.bodyMedium,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                                modifier = Modifier.weight(1f)
                                            )
                                            Text(
                                                "${String.format("%.1f", percentage)}%",
                                                style = MaterialTheme.typography.bodyMedium,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // Category Breakdown - Simplified
                item {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .shadow(4.dp, RoundedCornerShape(16.dp)),
                        colors = CardDefaults.cardColors(containerColor = Color.White),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp)
                        ) {
                            Text(
                                "Category Breakdown",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            
                            // Simple horizontal bar chart for categories
                            val total = insightData.categoryBreakdown.values.sum()
                            
                            insightData.categoryBreakdown.entries.forEachIndexed { index, (category, amount) ->
                                val percentage = (amount / total) * 100
                                
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 8.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            category,
                                            style = MaterialTheme.typography.bodyMedium,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier.weight(1f)
                                        )
                                        Text(
                                            "₹$amount (${String.format("%.1f", percentage)}%)",
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                    
                                    Spacer(modifier = Modifier.height(4.dp))
                                    
                                    // Progress bar
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(8.dp)
                                            .clip(RoundedCornerShape(4.dp))
                                            .background(Color(0xFFE0E0E0))
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .fillMaxWidth(percentage.toFloat() / 100f)
                                                .height(8.dp)
                                                .clip(RoundedCornerShape(4.dp))
                                                .background(chartColors[index % chartColors.size])
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
    
    // Financial Comparison Dialog
    if (showComparisonDialog) {
        AlertDialog(
            onDismissRequest = { showComparisonDialog = false },
            title = { Text("Financial Comparison") },
            text = {
                Column {
                    Text(
                        "Period: $selectedPeriod",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Total Income: ₹${String.format("%.2f", insightData.totalIncome)}")
                    Text("Total Expenses: ₹${String.format("%.2f", insightData.totalSpent)}")
                    Spacer(modifier = Modifier.height(8.dp))
                    val difference = insightData.totalIncome - insightData.totalSpent
                    val status = if (difference >= 0) "You saved" else "You overspent by"
                    Text(
                        "$status ₹${String.format("%.2f", abs(difference))}",
                        style = MaterialTheme.typography.titleMedium,
                        color = if (difference >= 0) Color(0xFF4CAF50) else Color(0xFFF44336),
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Savings Rate: ${String.format("%.1f", insightData.savingsRate)}%")
                }
            },
            confirmButton = {
                TextButton(onClick = { showComparisonDialog = false }) {
                    Text("Close")
                }
            }
        )
    }
}

private fun calculateYAxisValues(maxValue: Double): List<Double> {
    // Round up maxValue to the next hundred
    val roundedMax = ceil(maxValue / 100.0) * 100.0
    
    // Determine appropriate step size based on the maximum value
    val stepSize = when {
        roundedMax <= 500 -> 100.0
        roundedMax <= 1000 -> 200.0
        roundedMax <= 5000 -> 500.0
        else -> 1000.0
    }
    
    // Generate values from 0 to roundedMax with the determined step size
    val values = mutableListOf<Double>()
    var currentValue = 0.0
    while (currentValue <= roundedMax) {
        values.add(currentValue)
        currentValue += stepSize
    }
    
    return values
} 