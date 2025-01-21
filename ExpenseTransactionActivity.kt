package com.serhio.homeaccountingapp;

import android.app.DatePickerDialog
import android.content.Context
import android.content.Intent
import android.icu.util.Calendar
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.annotation.RequiresApi
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.serhio.homeaccountingapp.ui.theme.HomeAccountingAppTheme
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.draw.paint
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import java.text.SimpleDateFormat
import java.util.*
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter

class ExpenseTransactionActivity : ComponentActivity() {
    private val viewModel: ExpenseViewModel by viewModels { ExpenseViewModelFactory(application) }
    private lateinit var transactions: MutableList<Transaction>

    @RequiresApi(Build.VERSION_CODES.O)
    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val categoryName = intent.getStringExtra("categoryName") ?: "Категорія"
        val gson = Gson()
        val sharedPreferences = getSharedPreferences("ExpensePrefs", MODE_PRIVATE)
        val transactionsJson = sharedPreferences.getString("transactions", "[]") ?: "[]"
        val type = object : TypeToken<List<Transaction>>() {}.type
        val transactions = gson.fromJson<List<Transaction>>(transactionsJson, type).toMutableList()
        val filteredTransactions = transactions.filter { it.category == categoryName }

        setContent {
            HomeAccountingAppTheme {
                val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
                val scope = rememberCoroutineScope()

                ModalNavigationDrawer(
                    drawerState = drawerState,
                    drawerContent = {
                        DrawerContent(
                            onNavigateToMainActivity = {
                                val intent = Intent(this@ExpenseTransactionActivity, MainActivity::class.java).apply {
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
                                title = { Text(categoryName, color = Color.White) },
                                navigationIcon = {
                                    IconButton(onClick = { scope.launch { drawerState.open() } }) {
                                        Icon(Icons.Default.Menu, contentDescription = "Меню", tint = Color.White)
                                    }
                                },
                                actions = {
                                    ExpenseTransactionPeriodButton(viewModel, Modifier.width(120.dp))
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
                                ExpenseTransactionScreen(
                                    categoryName = categoryName,
                                    initialTransactions = filteredTransactions,
                                    onUpdateTransactions = { updatedTransactions ->
                                        viewModel.updateTransactions(updatedTransactions)
                                        saveTransactionsToStorage(updatedTransactions)
                                    }
                                )
                            }
                        }
                    )
                }
            }
        }
    }

    private fun navigateToActivity(activityClass: Class<*>) {
        val intent = Intent(this, activityClass)
        startActivity(intent)
    }

    private fun saveTransactionsToStorage(updatedTransactions: List<Transaction>) {
        val sharedPreferences = getSharedPreferences("ExpensePrefs", MODE_PRIVATE)
        val gson = Gson()
        val existingTransactionsJson = sharedPreferences.getString("transactions", "[]") ?: "[]"
        val type = object : TypeToken<List<Transaction>>() {}.type
        val existingTransactions: List<Transaction> = gson.fromJson(existingTransactionsJson, type)

        // Оновлюємо список транзакцій, зберігаючи всі існуючі, які не були оновлені
        val updatedList = existingTransactions.map { existingTransaction ->
            updatedTransactions.find { it.id == existingTransaction.id } ?: existingTransaction
        }.toMutableList()

        // Додаємо нові транзакції, які не були в існуючих
        updatedTransactions.filter { updatedTransaction ->
            existingTransactions.none { it.id == updatedTransaction.id }
        }.forEach { updatedList.add(it) }

        val newTransactionsJson = gson.toJson(updatedList)

        // Зберігаємо транзакції в SharedPreferences
        sharedPreferences.edit().putString("transactions", newTransactionsJson).apply()

        // Надсилаємо Broadcast для оновлення даних
        val updateIntent = Intent("com.example.homeaccountingapp.UPDATE_EXPENSES")
        LocalBroadcastManager.getInstance(this).sendBroadcast(updateIntent)
    }
}
@RequiresApi(Build.VERSION_CODES.O)
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExpenseTransactionScreen(
    categoryName: String,
    initialTransactions: List<Transaction>,
    onUpdateTransactions: (List<Transaction>) -> Unit,
    viewModel: ExpenseViewModel = viewModel()
) {
    var transactions by remember { mutableStateOf(initialTransactions.toMutableList()) }
    var selectedTransaction by remember { mutableStateOf<Transaction?>(null) }
    var showEditDialog by remember { mutableStateOf(false) }
    var showMenuDialog by remember { mutableStateOf(false) }
    var showAddDialog by remember { mutableStateOf(false) }

    val filteredTransactions by viewModel.sortedTransactions.collectAsState()
    val totalExpenseForFilteredTransactions = filteredTransactions.filter { it.category == categoryName }.sumOf { it.amount }

    Box(modifier = Modifier.fillMaxSize()) {
        Image(
            painter = painterResource(id = R.drawable.background_app),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .width(96.dp)
                .align(Alignment.CenterEnd)
                .background(
                    brush = Brush.horizontalGradient(
                        colors = listOf(
                            Color(0x00000000), // Transparent on the left
                            Color(0x99000000)  // Black on the right
                        )
                    )
                )
        )
        Column(modifier = Modifier.fillMaxSize()) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(bottom = 80.dp) // Додаємо відступ знизу, щоб не накладатися на заголовок
            ) {
                items(filteredTransactions.filter { it.category == categoryName }) { transaction ->
                    TransactionItem(
                        transaction = transaction,
                        onClick = {
                            selectedTransaction = transaction
                            showMenuDialog = true
                        }
                    )
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
        FloatingActionButton(
            onClick = { showAddDialog = true },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp),
            containerColor = Color(0xFF228B22) // Semi-transparent green button
        ) {
            Icon(Icons.Default.Add, contentDescription = "Add Transaction", tint = Color.White)
        }
        if (showAddDialog) {
            AddTransactionDialog(
                onDismiss = { showAddDialog = false },
                onSave = { newTransaction ->
                    transactions = (transactions + newTransaction).toMutableList()
                    onUpdateTransactions(transactions)
                    viewModel.updateTransactions(transactions)
                    showAddDialog = false
                },
                categoryName = categoryName
            )
        }

        // Додаємо текст "Загальні витрати" та суму витрат
        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(16.dp)
        ) {
            Spacer(modifier = Modifier.height(8.dp)) // Додати відступ між кнопкою та текстом
            Text(
                text = "Загальні витрати:",
                style = MaterialTheme.typography.titleMedium,
                color = Color.White
            )
            Text(
                text = "${totalExpenseForFilteredTransactions.formatAmount(2)} грн",
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                color = Color.Red // Червоний колір для загальної суми
            )
        }
    }
}
@RequiresApi(Build.VERSION_CODES.O)
@Composable
fun ExpenseTransactionPeriodButton(viewModel: ExpenseViewModel, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val dialogState = remember { mutableStateOf(false) }
    val dateFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")
    val startDate = remember { mutableStateOf(LocalDate.now()) }
    val endDate = remember { mutableStateOf(LocalDate.now()) }

    OutlinedButton(
        onClick = { dialogState.value = true },
        modifier = modifier.size(120.dp, 40.dp), // Збільшуємо ширину кнопки
        border = BorderStroke(1.dp, Color.Gray),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White)
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
                    ExpenseTransactionDatePickerField(
                        label = "Початкова дата",
                        date = startDate.value,
                        onDateSelected = { date -> startDate.value = date }
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    ExpenseTransactionDatePickerField(
                        label = "Кінцева дата",
                        date = endDate.value,
                        onDateSelected = { date -> endDate.value = date }
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.filterTransactionsByPeriod(startDate.value, endDate.value)
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
fun ExpenseTransactionDatePickerField(label: String, date: LocalDate, onDateSelected: (LocalDate) -> Unit) {
    val context = LocalContext.current
    OutlinedButton(onClick = {
        showExpenseTransactionDatePickerDialog(context, date, onDateSelected)
    }) {
        Text(text = "$label: ${date.format(DateTimeFormatter.ofPattern("yyyy-MM-dd"))}", style = TextStyle(color = Color.White, fontWeight = FontWeight.Bold))
    }
}

@RequiresApi(Build.VERSION_CODES.O)
fun showExpenseTransactionDatePickerDialog(context: Context, initialDate: LocalDate, onDateSelected: (LocalDate) -> Unit) {
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
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddTransactionDialog(
    onDismiss: () -> Unit,
    onSave: (Transaction) -> Unit,
    categoryName: String
) {
    var amount by remember { mutableStateOf("") }
    var date by remember { mutableStateOf(DateUtils.getCurrentDate()) } // Сьогоднішня дата за замовчуванням
    var comment by remember { mutableStateOf("") }
    var showDatePickerDialog by remember { mutableStateOf(false) }

    if (showDatePickerDialog) {
        DatePickerDialogComponent(
            onDateSelected = { selectedDate ->
                date = DateUtils.formatDate(selectedDate, "dd/MM/yyyy", "yyyy-MM-dd") // Оновлення дати після вибору
                showDatePickerDialog = false
            }
        )
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "Додавання нової транзакції",
                style = TextStyle(color = Color.White, fontWeight = FontWeight.Bold)
            )
        },
        text = {
            Column {
                TextField(
                    value = amount,
                    onValueChange = { amount = it },
                    label = { Text("Сума", style = TextStyle(color = Color.White)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    textStyle = TextStyle(color = Color.White, fontWeight = FontWeight.Bold),
                    colors = TextFieldDefaults.textFieldColors(
                        cursorColor = Color.White,
                        focusedIndicatorColor = Color.White,
                        unfocusedIndicatorColor = Color.White,
                        focusedLabelColor = Color.White,
                        unfocusedLabelColor = Color.White,
                        containerColor = Color.Transparent
                    )
                )
                Spacer(modifier = Modifier.height(8.dp))

                OutlinedButton(
                    onClick = { showDatePickerDialog = true },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White)
                ) {
                    Text(
                        text = date, // Відображення поточної або вибраної дати
                        style = TextStyle(color = Color.White)
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))

                TextField(
                    value = comment,
                    onValueChange = { comment = it },
                    label = { Text("Коментар", style = TextStyle(color = Color.White)) },
                    textStyle = TextStyle(color = Color.White, fontWeight = FontWeight.Bold), // Білий шрифт для введення коментаря
                    colors = TextFieldDefaults.textFieldColors(
                        cursorColor = Color.White,
                        focusedIndicatorColor = Color.White,
                        unfocusedIndicatorColor = Color.White,
                        focusedLabelColor = Color.White,
                        unfocusedLabelColor = Color.White,
                        containerColor = Color.Transparent
                    )
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val amountValue = amount.toDoubleOrNull()
                    if (amountValue != null && date.isNotBlank()) {
                        // Переконайтеся, що сума завжди негативна
                        onSave(
                            Transaction(
                                category = categoryName,
                                amount = -amountValue, // Ensure the amount is negative
                                date = date,
                                comments = comment
                            )
                        )
                        onDismiss() // Закриваємо діалог після збереження
                    }
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
            TextButton(onClick = onDismiss) {
                Text("Скасувати", color = Color.White)
            }
        },
        containerColor = Color.DarkGray
    )
}

fun getCurrentDate(): String {
    val formatter = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
    return formatter.format(Date())
}
// Функція для отримання дат останнього тижня у форматі "yyyy-MM-dd"
fun getPastWeekExpenseDates(): List<String> {
    val formatter = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault())
    val calendar = java.util.Calendar.getInstance()
    val dates = mutableListOf<String>()
    for (i in 0..6) {
        dates.add(formatter.format(calendar.time))
        calendar.add(java.util.Calendar.DAY_OF_YEAR, -1)
    }
    return dates
}
// Використовуємо стандартний DatePickerDialog для вибору дати
@Composable
fun DatePickerDialogComponent(onDateSelected: (String) -> Unit) {
    val context = LocalContext.current
    val calendar = Calendar.getInstance()
    val year = calendar.get(Calendar.YEAR)
    val month = calendar.get(Calendar.MONTH)
    val day = calendar.get(Calendar.DAY_OF_MONTH)
    val datePickerDialog = DatePickerDialog(
        context,
        { _, selectedYear, selectedMonth, selectedDay ->
            val formattedDate = String.format("%04d-%02d-%02d", selectedYear, selectedMonth + 1, selectedDay)
            onDateSelected(formattedDate)
        },
        year, month, day
    )
    LaunchedEffect(Unit) {
        datePickerDialog.show()
    }
}
@Composable
fun TransactionItem(
    transaction: Transaction,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(8.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(
                brush = Brush.horizontalGradient(
                    colors = listOf(
                        Color.DarkGray.copy(alpha = 0.9f), // Темно-сірий зліва
                        Color.DarkGray.copy(alpha = 0.1f)  // Майже прозорий справа
                    ),
                    startX = 0f,
                    endX = 300f  // Налаштовано так, щоб градієнт став майже прозорим з половини
                )
            )
            .clickable(onClick = onClick)
            .padding(16.dp) // Внутрішній відступ
    ) {
        Column {
            Text(
                text = "Сума: ${transaction.amount} грн",
                style = MaterialTheme.typography.bodyLarge.copy(color = Color.Red),
                modifier = Modifier.padding(bottom = 4.dp)
            )
            Text(
                text = "Дата: ${transaction.date}",
                style = MaterialTheme.typography.bodyMedium.copy(color = Color.Red),
                modifier = Modifier.padding(bottom = 4.dp)
            )
            if (!transaction.comments.isNullOrEmpty()) {
                Text(
                    text = "Коментар: ${transaction.comments}",
                    style = MaterialTheme.typography.bodySmall.copy(color = Color.Red)
                )
            }
        }
    }
}
@Composable
fun EditDeleteDialog(
    transaction: Transaction,
    onDismiss: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.6f))
            .clickable(enabled = true, onClick = onDismiss)
    ) {
        Column(
            modifier = Modifier
                .align(Alignment.Center)
                .padding(16.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(
                    brush = Brush.verticalGradient(
                        colors = listOf(Color.Black.copy(alpha = 0.9f), Color.Transparent)
                    )
                )
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "Дії з транзакцією",
                style = MaterialTheme.typography.titleMedium.copy(color = Color.White),
                modifier = Modifier.padding(bottom = 8.dp)
            )
            Button(
                onClick = onEdit,
                modifier = Modifier.fillMaxWidth().padding(8.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color.Gray)
            ) {
                Text("Редагувати", color = Color.White)
            }
            Button(
                onClick = onDelete,
                modifier = Modifier.fillMaxWidth().padding(8.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color.Red)
            ) {
                Text("Видалити", color = Color.White)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditTransactionDialog(
    transaction: Transaction,
    onDismiss: () -> Unit,
    onSave: (Transaction) -> Unit
) {
    var updatedAmount by remember { mutableStateOf(transaction.amount.toString()) }
    var updatedDate by remember { mutableStateOf(transaction.date) }
    var updatedComment by remember { mutableStateOf(transaction.comments ?: "") }
    // Для кнопки вибору дати
    val datePickerState = remember { mutableStateOf(false) }
    if (datePickerState.value) {
        // Показуємо діалог для вибору дати
        DatePickerDialogComponent(
            onDateSelected = { selectedDate ->
                updatedDate = DateUtils.formatDate(selectedDate, "dd/MM/yyyy", "yyyy-MM-dd") // Оновлення дати після вибору
                datePickerState.value = false
            }
        )
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "Редагування транзакції",
                style = TextStyle(color = Color.White, fontWeight = FontWeight.Bold)
            )
        },
        text = {
            Column {
                // Поле суми з білим жирним шрифтом
                TextField(
                    value = updatedAmount,
                    onValueChange = { updatedAmount = it },
                    label = { Text("Сума", style = TextStyle(color = Color.White)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    textStyle = TextStyle(color = Color.White, fontWeight = FontWeight.Bold), // Білий жирний шрифт
                    colors = TextFieldDefaults.textFieldColors(
                        cursorColor = Color.White,
                        focusedIndicatorColor = Color.White,
                        unfocusedIndicatorColor = Color.White,
                        focusedLabelColor = Color.White,
                        unfocusedLabelColor = Color.White,
                        containerColor = Color.Transparent
                    )
                )
                Spacer(modifier = Modifier.height(8.dp))
                // Кнопка для вибору дати
                Button(
                    onClick = { datePickerState.value = true },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp)
                        .clip(RoundedCornerShape(8.dp)),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF616161))
                ) {
                    Text(
                        text = if (updatedDate.isBlank()) "Вибрати дату" else "Дата: $updatedDate",
                        color = Color.White,
                        style = MaterialTheme.typography.bodyLarge
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                TextField(
                    value = updatedComment,
                    onValueChange = { updatedComment = it },
                    label = { Text("Коментар", style = TextStyle(color = Color.White)) },
                    colors = TextFieldDefaults.textFieldColors(
                        cursorColor = Color.White,
                        focusedIndicatorColor = Color.White,
                        unfocusedIndicatorColor = Color.White,
                        focusedLabelColor = Color.White,
                        unfocusedLabelColor = Color.White,
                        containerColor = Color.Transparent
                    )
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val amountValue = updatedAmount.toDoubleOrNull()
                    if (amountValue != null) {
                        // Переконайтеся, що сума завжди негативна
                        onSave(transaction.copy(amount = -amountValue, date = updatedDate, comments = updatedComment)) // Ensure the amount is negative
                    }
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF388E3C), // Темнозелений колір
                    contentColor = Color.White // Білий текст
                ),
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                shape = RoundedCornerShape(8.dp) // Округлені краї
            ) {
                Text("Зберегти", style = MaterialTheme.typography.bodyLarge)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Скасувати", color = Color.White)
            }
        },
        containerColor = Color.DarkGray
    )
}