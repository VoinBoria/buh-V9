package com.serhio.homeaccountingapp

import android.annotation.SuppressLint
import android.app.DatePickerDialog
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.annotation.RequiresApi
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Sort
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.paint
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Color.Companion.Gray
import androidx.compose.ui.graphics.Color.Companion.White
import androidx.compose.ui.graphics.Color.Companion.Black
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.serhio.homeaccountingapp.ui.theme.HomeAccountingAppTheme
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.*
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.ui.input.pointer.pointerInput
class AllTransactionExpenseActivity : ComponentActivity() {
    private val viewModel: ExpenseViewModel by viewModels { ExpenseViewModelFactory(application) }
    private lateinit var updateReceiver: BroadcastReceiver

    private fun <T> navigateToActivity(activityClass: Class<T>) {
        val intent = Intent(this, activityClass)
        startActivity(intent)
    }

    @RequiresApi(Build.VERSION_CODES.O)
    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            HomeAccountingAppTheme {
                val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
                val scope = rememberCoroutineScope()

                ModalNavigationDrawer(
                    drawerState = drawerState,
                    drawerContent = {
                        DrawerContent(
                            onNavigateToMainActivity = {
                                val intent = Intent(this@AllTransactionExpenseActivity, MainActivity::class.java).apply {
                                    putExtra("SHOW_SPLASH_SCREEN", false)
                                }
                                startActivity(intent)
                            },
                            onNavigateToIncomes = { navigateToActivity(IncomeActivity::class.java) },
                            onNavigateToExpenses = { navigateToActivity(ExpenseActivity::class.java) },
                            onNavigateToIssuedOnLoan = { navigateToActivity(IssuedOnLoanActivity::class.java) },
                            onNavigateToBorrowed = { navigateToActivity(BorrowedActivity::class.java) },
                            onNavigateToAllTransactionIncome = { navigateToActivity(AllTransactionIncomeActivity::class.java) },
                            onNavigateToAllTransactionExpense = { navigateToActivity(AllTransactionExpenseActivity::class.java) },
                            onNavigateToBudgetPlanning = { navigateToActivity(BudgetPlanningActivity::class.java) },
                            onNavigateToTaskActivity = { navigateToActivity(TaskActivity::class.java) }
                        )
                    }
                ) {
                    Scaffold(
                        topBar = {
                            TopAppBar(
                                title = { Text("Всі транзакції витрат", color = White) },
                                navigationIcon = {
                                    IconButton(onClick = { scope.launch { drawerState.open() } }) {
                                        Icon(Icons.Default.Menu, contentDescription = "Меню", tint = White)
                                    }
                                },
                                actions = {
                                    SortMenu(viewModel)
                                },
                                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color(0xFF121212))
                            )
                        },
                        content = { innerPadding ->
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .paint(
                                        painter = painterResource(id = R.drawable.background_app),
                                        contentScale = ContentScale.Crop
                                    )
                                    .padding(innerPadding)
                            ) {
                                AllTransactionExpenseScreen(viewModel)
                                ExpensePeriodButton(viewModel, Modifier.align(Alignment.BottomStart).padding(16.dp))
                            }
                        }
                    )
                }
            }
        }

        // Ініціалізація BroadcastReceiver для оновлення даних
        updateReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                if (intent.action == "com.example.homeaccountingapp.UPDATE_EXPENSES") {
                    viewModel.loadData()
                }
            }
        }
        val filter = IntentFilter("com.example.homeaccountingapp.UPDATE_EXPENSES")
        LocalBroadcastManager.getInstance(this).registerReceiver(updateReceiver, filter)
    }

    override fun onDestroy() {
        super.onDestroy()
        LocalBroadcastManager.getInstance(this).unregisterReceiver(updateReceiver)
    }
}
@RequiresApi(Build.VERSION_CODES.O)
@Composable
fun ExpensePeriodButton(viewModel: ExpenseViewModel, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val dialogState = remember { mutableStateOf(false) }
    val dateFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")
    val startDate = remember { mutableStateOf(LocalDate.now()) }
    val endDate = remember { mutableStateOf(LocalDate.now()) }

    OutlinedButton(
        onClick = { dialogState.value = true },
        modifier = modifier.size(100.dp, 40.dp),
        border = BorderStroke(1.dp, Color.Gray),
        colors = ButtonDefaults.outlinedButtonColors(
            contentColor = Color.White,
            containerColor = Color.Transparent // Прозорий фон
        )
    ) {
        Text("Період", fontWeight = FontWeight.Bold, color = Color.White)
    }

    if (dialogState.value) {
        AlertDialog(
            onDismissRequest = { dialogState.value = false },
            title = {
                Text("Вибір періоду", style = TextStyle(color = Color.White, fontWeight = FontWeight.Bold))
            },
            text = {
                Column {
                    ExpenseDatePickerField(
                        label = "Початкова дата",
                        date = startDate.value,
                        onDateSelected = { date -> startDate.value = date }
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    ExpenseDatePickerField(
                        label = "Кінцева дата",
                        date = endDate.value,
                        onDateSelected = { date -> endDate.value = date }
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.filterByPeriod(startDate.value, endDate.value)
                        dialogState.value = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF388E3C)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text("Зберегти", style = MaterialTheme.typography.bodyLarge)
                }
            },
            dismissButton = {
                TextButton(onClick = { dialogState.value = false }) {
                    Text("Скасувати", style = TextStyle(color = Color.White, fontWeight = FontWeight.Bold))
                }
            },
            containerColor = Color.DarkGray
        )
    }
}
@RequiresApi(Build.VERSION_CODES.O)
@Composable
fun ExpenseDatePickerField(label: String, date: LocalDate, onDateSelected: (LocalDate) -> Unit) {
    val context = LocalContext.current
    OutlinedButton(onClick = {
        showExpenseDatePickerDialog(context, date, onDateSelected)
    }) {
        Text(text = "$label: ${date.format(DateTimeFormatter.ofPattern("yyyy-MM-dd"))}", style = TextStyle(color = White, fontWeight = FontWeight.Bold))
    }
}

@RequiresApi(Build.VERSION_CODES.O)
fun showExpenseDatePickerDialog(context: Context, initialDate: LocalDate, onDateSelected: (LocalDate) -> Unit) {
    val calendar = Calendar.getInstance()
    calendar.set(initialDate.year, initialDate.monthValue - 1, initialDate.dayOfMonth)
    DatePickerDialog(
        context,
        { _, selectedYear, selectedMonth, selectedDay ->
            val selectedDate = LocalDate.of(selectedYear, selectedMonth + 1, selectedDay)
            onDateSelected(selectedDate)
        },
        calendar.get(Calendar.YEAR),
        calendar.get(Calendar.MONTH),
        calendar.get(Calendar.DAY_OF_MONTH)
    ).show()
}

@Composable
fun SortMenu(viewModel: ExpenseViewModel) {
    var expanded by remember { mutableStateOf(false) }

    Box {
        IconButton(onClick = { expanded = true }) {
            Icon(Icons.Default.Sort, contentDescription = "Сортувати за", tint = White)
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier.background(Black) // Зміна фону меню на чорний
        ) {
            DropdownMenuItem(
                text = { Text("За датою", color = White) },
                onClick = {
                    viewModel.sortTransactions(ExpenseSortType.DATE)
                    expanded = false
                }
            )
            DropdownMenuItem(
                text = { Text("За сумою", color = White) },
                onClick = {
                    viewModel.sortTransactions(ExpenseSortType.AMOUNT)
                    expanded = false
                }
            )
            DropdownMenuItem(
                text = { Text("За категорією", color = White) },
                onClick = {
                    viewModel.sortTransactions(ExpenseSortType.CATEGORY)
                    expanded = false
                }
            )
        }
    }
}
@SuppressLint("UnusedBoxWithConstraintsScope")
@RequiresApi(Build.VERSION_CODES.O)
@Composable
fun AllTransactionExpenseScreen(
    viewModel: ExpenseViewModel = viewModel(),
    modifier: Modifier = Modifier
) {
    var selectedTransaction by remember { mutableStateOf<Transaction?>(null) }
    var showMenuDialog by remember { mutableStateOf(false) }
    var showEditDialog by remember { mutableStateOf(false) }

    BoxWithConstraints {
        val screenWidth = maxWidth
        val fontSize = if (screenWidth < 360.dp) 14.sp else 18.sp
        val padding = if (screenWidth < 360.dp) 8.dp else 16.dp

        val expenseTransactions by viewModel.sortedTransactions.collectAsState()

        Box(
            modifier = Modifier
                .fillMaxSize()
        ) {
            LazyColumn(
                modifier = modifier
                    .fillMaxSize()
                    .padding(start = padding, end = padding, top = padding, bottom = 70.dp)
                    .background(Color.Transparent)
            ) {
                when (viewModel.sortType) {
                    ExpenseSortType.DATE -> {
                        val groupedTransactions = expenseTransactions.groupBy { it.date }
                        groupedTransactions.toSortedMap(compareByDescending { it }).forEach { (date, transactions) ->
                            items(transactions) { transaction ->
                                AllExpenseTransactionItem(
                                    transaction = transaction,
                                    onClick = {
                                        selectedTransaction = transaction
                                        showMenuDialog = true
                                    }
                                )
                            }
                            item {
                                val totalGroupExpense = transactions.sumOf { it.amount }
                                Text(
                                    text = "Сума за $date: $totalGroupExpense грн",
                                    style = TextStyle(fontSize = fontSize, fontWeight = FontWeight.Normal, color = Color.White),
                                    modifier = Modifier.padding(padding)
                                )
                            }
                        }
                    }
                    ExpenseSortType.AMOUNT -> {
                        val sortedTransactions = expenseTransactions.sortedBy { it.amount }
                        items(sortedTransactions) { transaction ->
                            AllExpenseTransactionItem(
                                transaction = transaction,
                                onClick = {
                                    selectedTransaction = transaction
                                    showMenuDialog = true
                                }
                            )
                        }
                    }
                    ExpenseSortType.CATEGORY -> {
                        val groupedTransactions = expenseTransactions.groupBy { it.category }
                        groupedTransactions.forEach { (_, transactions) ->
                            items(transactions) { transaction ->
                                AllExpenseTransactionItem(
                                    transaction = transaction,
                                    onClick = {
                                        selectedTransaction = transaction
                                        showMenuDialog = true
                                    }
                                )
                            }
                            item {
                                val totalGroupExpense = transactions.sumOf { it.amount }
                                Text(
                                    text = "Всього витрат по категорії: $totalGroupExpense грн",
                                    style = TextStyle(fontSize = fontSize, fontWeight = FontWeight.Normal, color = Color.White),
                                    modifier = Modifier.padding(padding)
                                )
                            }
                        }
                    }
                }
            }

            if (showMenuDialog && selectedTransaction != null) {
                EditDeleteDialog(
                    transaction = selectedTransaction!!,
                    onDismiss = { showMenuDialog = false },
                    onEdit = {
                        showMenuDialog = false
                        showEditDialog = true
                    },
                    onDelete = {
                        viewModel.deleteTransaction(selectedTransaction!!)
                        showMenuDialog = false
                    }
                )
            }
            if (showEditDialog && selectedTransaction != null) {
                EditTransactionDialog(
                    transaction = selectedTransaction!!,
                    onDismiss = { showEditDialog = false },
                    onSave = { updatedTransaction ->
                        viewModel.updateTransaction(updatedTransaction)
                        showEditDialog = false
                    }
                )
            }
        }
    }
}
@SuppressLint("UnusedBoxWithConstraintsScope")
@Composable
fun AllExpenseTransactionItem(
    transaction: Transaction,
    onClick: () -> Unit
) {
    BoxWithConstraints {
        val screenWidth = maxWidth
        val fontSize = if (screenWidth < 360.dp) 14.sp else 18.sp
        val padding = if (screenWidth < 360.dp) 4.dp else 8.dp // Зменшення відступу

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(padding)
                .clip(RoundedCornerShape(8.dp))
                .background(
                    brush = Brush.horizontalGradient(
                        colors = listOf(
                            Color.DarkGray.copy(alpha = 0.9f), // Темно-сірий зліва
                            Color.DarkGray.copy(alpha = 0.1f)  // Майже прозорий справа
                        ),
                        startX = 0.0f,
                        endX = screenWidth.value / 2 // Градієнт до середини
                    )
                )
                .clickable { onClick() } // Виклик onClick у контексті click
                .padding(padding)
        ) {
            Column {
                Text(
                    text = "Категорія: ${transaction.category}", // Додавання назви категорії
                    style = MaterialTheme.typography.bodyLarge.copy(color = Color.Red, fontSize = fontSize),
                    modifier = Modifier.padding(bottom = 4.dp)
                )
                Text(
                    text = "Сума: ${if (transaction.amount < 0) "" else "-"}${transaction.amount} грн",
                    style = MaterialTheme.typography.bodyLarge.copy(color = Color.Red, fontSize = fontSize),
                    modifier = Modifier.padding(bottom = 4.dp)
                )
                Text(
                    text = "Дата: ${transaction.date}",
                    style = MaterialTheme.typography.bodyMedium.copy(color = Color.Red, fontSize = fontSize),
                    modifier = Modifier.padding(bottom = 4.dp)
                )
                if (!transaction.comments.isNullOrEmpty()) {
                    Text(
                        text = "Коментар: ${transaction.comments}",
                        style = MaterialTheme.typography.bodySmall.copy(color = Color.Red, fontSize = fontSize)
                    )
                }
            }
        }
    }
}
