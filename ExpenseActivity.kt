@file:OptIn(ExperimentalMaterial3Api::class)
package com.serhio.homeaccountingapp;
import android.annotation.SuppressLint
import android.app.Application
import android.app.DatePickerDialog
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.annotation.RequiresApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.layout.ContentScale
import com.serhio.homeaccountingapp.ui.theme.HomeAccountingAppTheme
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.util.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Menu
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter

private const val TAG = "ExpenseActivity"

enum class ExpenseSortType {
    DATE, AMOUNT, CATEGORY
}
class ExpenseViewModelFactory(
    private val application: Application
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(ExpenseViewModel::class.java)) {
            return ExpenseViewModel(application) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}

class ExpenseActivity : ComponentActivity() {
    private lateinit var sharedPreferences: SharedPreferences
    private lateinit var transactionResultLauncher: ActivityResultLauncher<Intent>
    private val gson by lazy { Gson() }
    private val viewModel: ExpenseViewModel by viewModels {
        ExpenseViewModelFactory(application)
    }
    private lateinit var updateReceiver: BroadcastReceiver
    private fun <T> navigateToActivity(activityClass: Class<T>) {
        val intent = Intent(this, activityClass)
        startActivity(intent)
    }

    @RequiresApi(Build.VERSION_CODES.O)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        sharedPreferences = getSharedPreferences("ExpensePrefs", Context.MODE_PRIVATE)

        loadExpensesFromSharedPreferences()
        loadCategoriesFromSharedPreferences() // Завантаження категорій

        transactionResultLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == RESULT_OK) {
                viewModel.loadData()
                sendUpdateBroadcast()
            }
        }

        setContent {
            HomeAccountingAppTheme {
                val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
                val scope = rememberCoroutineScope()

                ModalNavigationDrawer(
                    drawerState = drawerState,
                    drawerContent = {
                        DrawerContent(
                            onNavigateToMainActivity = {
                                val intent = Intent(this@ExpenseActivity, MainActivity::class.java).apply {
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
                                title = { Text("Витрати", color = Color.White) },
                                navigationIcon = {
                                    IconButton(onClick = { scope.launch { drawerState.open() } }) {
                                        Icon(Icons.Default.Menu, contentDescription = "Меню", tint = Color.White)
                                    }
                                },
                                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color(0xFF121212))
                            )
                        },
                        content = { innerPadding ->
                            ExpenseScreen(
                                viewModel = viewModel,
                                onOpenTransactionScreen = { categoryName, transactionsJson ->
                                    val intent = Intent(this, ExpenseTransactionActivity::class.java).apply {
                                        putExtra("categoryName", categoryName)
                                        putExtra("transactionsJson", transactionsJson)
                                    }
                                    transactionResultLauncher.launch(intent)
                                },
                                onDeleteCategory = { category ->
                                    viewModel.deleteCategory(category)
                                    sendUpdateBroadcast()
                                },
                                modifier = Modifier.padding(innerPadding) // Додаємо innerPadding
                            )
                        }
                    )
                }
            }
        }

        viewModel.setSendUpdateBroadcast { sendUpdateBroadcast() }

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

    private fun sendUpdateBroadcast() {
        val updateIntent = Intent("com.example.homeaccountingapp.UPDATE_EXPENSES")
        LocalBroadcastManager.getInstance(this).sendBroadcast(updateIntent)
    }

    private fun loadExpensesFromSharedPreferences() {
        val expensesJson = sharedPreferences.getString("expenses", null)
        val expenseMap: Map<String, Double> = if (expensesJson != null) {
            Gson().fromJson(expensesJson, object : TypeToken<Map<String, Double>>() {}.type)
        } else {
            emptyMap()
        }
        viewModel.updateExpenses(expenseMap)
    }

    private fun loadCategoriesFromSharedPreferences() {
        val categoriesJson = sharedPreferences.getString("categories", null)
        val categoriesList: List<String> = if (categoriesJson != null) {
            Gson().fromJson(categoriesJson, object : TypeToken<List<String>>() {}.type)
        } else {
            val defaultCategories = viewModel.getStandardCategories()
            viewModel.saveCategories(defaultCategories)
            defaultCategories
        }
        viewModel.updateCategories(categoriesList)
    }

    override fun onDestroy() {
        super.onDestroy()
        LocalBroadcastManager.getInstance(this).unregisterReceiver(updateReceiver)
    }

    companion object {
        private const val TAG = "ExpenseActivity"
    }
}

class ExpenseViewModel(application: Application) : AndroidViewModel(application) {
    private val sharedPreferences = application.getSharedPreferences("ExpensePrefs", Context.MODE_PRIVATE)
    private val gson = Gson()
    var categories by mutableStateOf<List<String>>(emptyList())
    private val _transactions = MutableStateFlow<List<Transaction>>(emptyList())
    val transactions: StateFlow<List<Transaction>> = _transactions
    var categoryExpenses by mutableStateOf<Map<String, Double>>(emptyMap())
    var totalExpense by mutableStateOf(0.0)
    var sortType by mutableStateOf(ExpenseSortType.DATE)
    private val _sortedTransactions = MutableStateFlow<List<Transaction>>(emptyList())
    val sortedTransactions: StateFlow<List<Transaction>> = _sortedTransactions

    // Створення властивості для зберігання функції
    private var sendUpdateBroadcast: (() -> Unit)? = null

    init {
        loadData()
    }

    // Метод для встановлення функції
    fun setSendUpdateBroadcast(sendUpdateBroadcast: () -> Unit) {
        this.sendUpdateBroadcast = sendUpdateBroadcast
    }

    // Додайте цю функцію для відправки broadcast
    private fun sendUpdateBroadcast() {
        sendUpdateBroadcast?.invoke()
    }

    // Завантаження стандартних категорій
    fun loadStandardCategories() {
        categories = listOf("Аренда", "Комунальні послуги", "Транспорт", "Розваги", "Продукти", "Одяг", "Здоров'я", "Освіта", "Подарунки", "Хобі", "Благодійність", "Спорт", "Техніка")
        saveCategories(categories)
    }

    // Отримання стандартних категорій
    fun getStandardCategories(): List<String> {
        return listOf("Аренда", "Комунальні послуги", "Транспорт", "Розваги", "Продукти", "Одяг", "Здоров'я", "Освіта", "Подарунки", "Хобі", "Благодійність", "Спорт", "Техніка")
    }

    // Функція для завантаження даних
    fun loadData() {
        categories = loadCategories()
        _transactions.value = loadTransactions()
        sortTransactions(sortType)  // Сортування транзакцій після завантаження даних
        updateExpenses()  // Оновлення витрат при завантаженні даних
    }

    @RequiresApi(Build.VERSION_CODES.O)
    fun filterTransactionsByPeriod(startDate: LocalDate, endDate: LocalDate) {
        _sortedTransactions.value = _transactions.value.filter { transaction ->
            val transactionDate = LocalDate.parse(transaction.date, DateTimeFormatter.ISO_DATE)
            transactionDate in startDate..endDate
        }.sortedByDescending { it.date } // Завжди сортувати у спадному порядку за датою після фільтрації
    }

    // Функція для сортування транзакцій
    fun sortTransactions(sortType: ExpenseSortType) {
        this.sortType = sortType
        _sortedTransactions.value = when (sortType) {
            ExpenseSortType.DATE -> _transactions.value.sortedByDescending { it.date }
            ExpenseSortType.AMOUNT -> _transactions.value.sortedBy { it.amount }
            ExpenseSortType.CATEGORY -> _transactions.value.sortedBy { it.category }
        }
    }

    // Публічна функція для оновлення витрат
    fun updateExpenses(expenses: Map<String, Double> = emptyMap()) {
        categoryExpenses = expenses.takeIf { it.isNotEmpty() }
            ?: categories.associateWith { category ->
                _transactions.value.filter { it.category == category }.sumOf { it.amount }
            }
        totalExpense = _transactions.value.sumOf { it.amount }
        // Оновлення витрат у SharedPreferences
        saveExpensesToSharedPreferences(categoryExpenses)
    }

    fun updateCategories(newCategories: List<String>) {
        categories = newCategories
        saveCategories(categories)
        updateExpenses()  // Оновлення витрат після зміни категорій
        sendUpdateBroadcast() // Виклик функції для відправки broadcast
    }

    // Функція для оновлення транзакцій
    fun updateTransactions(newTransactions: List<Transaction>) {
        _transactions.value = newTransactions
        sortTransactions(sortType)  // Сортування транзакцій після їх оновлення
        updateExpenses()
        sendUpdateBroadcast()
    }

    // Нова функція для фільтрації транзакцій за періодом
    @RequiresApi(Build.VERSION_CODES.O)
    fun filterByPeriod(startDate: LocalDate, endDate: LocalDate) {
        _sortedTransactions.value = _transactions.value.filter { transaction ->
            val transactionDate = LocalDate.parse(transaction.date, DateTimeFormatter.ISO_DATE)
            transactionDate in startDate..endDate
        }.sortedByDescending { it.date } // Завжди сортувати у спадному порядку за датою після фільтрації
    }

    fun deleteCategory(category: String) {
        categories = categories.filter { it != category }
        _transactions.value = _transactions.value.filter { it.category != category }
        saveCategories(categories) // Збереження категорій
        saveTransactions(_transactions.value) // Збереження транзакцій
        sortTransactions(sortType)  // Сортування транзакцій після видалення категорії
        updateExpenses()  // Оновлення витрат після видалення категорії
        sendUpdateBroadcast()
    }

    fun editCategory(oldCategory: String, newCategory: String) {
        categories = categories.map { if (it == oldCategory) newCategory else it }
        _transactions.value = _transactions.value.map {
            if (it.category == oldCategory) it.copy(category = newCategory) else it
        }
        saveCategories(categories)
        saveTransactions(_transactions.value)
        sortTransactions(sortType)  // Сортування транзакцій після зміни категорії
        updateExpenses()  // Оновлення витрат після зміни категорії
        sendUpdateBroadcast()
    }
    fun addCategory(newCategory: String) {
        if (newCategory !in categories) {
            categories = categories + newCategory
            saveCategories(categories)
            updateExpenses()
            sendUpdateBroadcast()
        }
    }

    fun saveCategories(categories: List<String>) {
        Log.d(TAG, "Saving categories: $categories")  // Логування перед збереженням
        sharedPreferences.edit().putString("categories", gson.toJson(categories)).apply()
        sendUpdateBroadcast()
    }
    fun addTransaction(newTransaction: Transaction) {
        viewModelScope.launch {
            val currentTransactions = loadTransactions().toMutableList()
            currentTransactions.add(newTransaction)
            _transactions.value = currentTransactions
            saveTransactions(currentTransactions)
            updateExpenses()
            sortTransactions(sortType)
            sendUpdateBroadcast()
        }
    }

    // Функція для збереження транзакцій
    private fun saveTransactions(transactions: List<Transaction>) {
        Log.d(TAG, "Saving transactions: $transactions")
        sharedPreferences.edit().putString("transactions", gson.toJson(transactions)).apply()
        sendUpdateBroadcast()
    }

    private fun loadCategories(): List<String> {
        val json = sharedPreferences.getString("categories", null)
        Log.d(TAG, "Loaded categories: $json")  // Логування при завантаженні
        return if (json != null) {
            gson.fromJson(json, object : TypeToken<List<String>>() {}.type)
        } else {
            emptyList()
        }
    }

    private fun loadTransactions(): List<Transaction> {
        val json = sharedPreferences.getString("transactions", null)
        Log.d(TAG, "Loaded transactions: $json")
        return if (json != null) {
            gson.fromJson<List<Transaction>>(json, object : TypeToken<List<Transaction>>() {}.type)
                .map { transaction ->
                    if (transaction.amount > 0) transaction.copy(amount = -transaction.amount)
                    else transaction
                }
        } else {
            emptyList()
        }
    }

    private fun saveExpensesToSharedPreferences(expenses: Map<String, Double>) {
        val editor = sharedPreferences.edit()
        val expensesJson = gson.toJson(expenses)
        editor.putString("expenses", expensesJson)
        editor.apply()
    }

    // Функція для оновлення транзакції
    fun updateTransaction(updatedTransaction: Transaction) {
        _transactions.value = _transactions.value.map {
            if (it.id == updatedTransaction.id) updatedTransaction else it
        }
        saveTransactions(_transactions.value)
        sortTransactions(sortType)  // Сортування транзакцій після їх оновлення
        updateExpenses()
        sendUpdateBroadcast()
    }

    // Функція для видалення транзакції
    fun deleteTransaction(transaction: Transaction) {
        _transactions.value = _transactions.value.filter { it.id != transaction.id }
        saveTransactions(_transactions.value)
        sortTransactions(sortType)  // Сортування транзакцій після їх видалення
        updateExpenses()
        sendUpdateBroadcast()
    }

    companion object {
        private const val TAG = "ExpenseViewModel"
    }
}
@RequiresApi(Build.VERSION_CODES.O)
@SuppressLint("UnusedBoxWithConstraintsScope")
@Composable
fun ExpenseScreen(
    viewModel: ExpenseViewModel,
    onOpenTransactionScreen: (String, String) -> Unit,
    onDeleteCategory: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    BoxWithConstraints {
        val screenWidth = maxWidth
        val fontSize = if (screenWidth < 360.dp) 14.sp else 18.sp
        val padding = if (screenWidth < 360.dp) 8.dp else 16.dp

        val categories = viewModel.categories
        val transactions = viewModel.transactions.collectAsState().value
        val categoryExpenses = viewModel.categoryExpenses
        val totalExpense = viewModel.totalExpense

        var showEditCategoryDialog by remember { mutableStateOf(false) }
        var categoryToEdit by remember { mutableStateOf<String?>(null) }
        var showMenu by remember { mutableStateOf(false) }
        var showAddTransactionDialog by remember { mutableStateOf(false) }
        var showAddCategoryDialog by remember { mutableStateOf(false) }
        var showDeleteConfirmationDialog by remember { mutableStateOf(false) }
        var categoryToDelete by remember { mutableStateOf<String?>(null) }

        Box(modifier = Modifier.fillMaxSize()) {
            Image(
                painter = painterResource(id = R.drawable.background_app),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(start = padding, end = padding, top = 50.dp)
            ) {
                Spacer(modifier = Modifier.height(50.dp))
                Box(
                    modifier = Modifier.weight(1f)
                ) {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 24.dp, bottom = 100.dp)
                    ) {
                        items(categories) { category ->
                            CategoryRow(
                                category = category,
                                expenseAmount = categoryExpenses[category] ?: 0.0,
                                onClick = {
                                    onOpenTransactionScreen(category, Gson().toJson(transactions))
                                },
                                onDelete = {
                                    categoryToDelete = category
                                    showDeleteConfirmationDialog = true
                                },
                                onEdit = {
                                    categoryToEdit = category
                                    showEditCategoryDialog = true
                                }
                            )
                        }
                    }
                }
            }
            FloatingActionButton(
                onClick = { showMenu = !showMenu },
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(16.dp),
                containerColor = Color(0xFFDC143C)
            ) {
                Text("+", color = Color.White, style = MaterialTheme.typography.bodyLarge)
            }
            if (showMenu) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.6f))
                        .clickable { showMenu = false }
                ) {
                    Column(
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(16.dp)
                            .widthIn(max = if (screenWidth < 360.dp) 200.dp else 250.dp)
                    ) {
                        MenuButton(
                            text = "Додати транзакцію",
                            backgroundColors = listOf(
                                Color(0xFF8B0000).copy(alpha = 0.7f),
                                Color(0xFFDC143C).copy(alpha = 0.1f)
                            ),
                            onClick = {
                                showAddTransactionDialog = true
                                showMenu = false
                            }
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        MenuButton(
                            text = "Додати категорію",
                            backgroundColors = listOf(
                                Color(0xFF00008B).copy(alpha = 0.7f),
                                Color(0xFF4682B4).copy(alpha = 0.1f)
                            ),
                            onClick = {
                                showAddCategoryDialog = true
                                showMenu = false
                            }
                        )
                    }
                }
            }
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomCenter)
                    .padding(30.dp)
            ) {
                Text(
                    text = "Загальні витрати:",
                    style = MaterialTheme.typography.titleMedium,
                    color = Color.White,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                Text(
                    text = "${totalExpense.formatAmount(2)} грн",
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                    color = Color.White
                )
            }
        }
        if (showDeleteConfirmationDialog) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.6f))
                    .clickable { showDeleteConfirmationDialog = false }
            ) {
                Column(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .background(Color(0xFF1A1A1A).copy(alpha = 0.9f), shape = RoundedCornerShape(12.dp))
                        .padding(16.dp)
                        .widthIn(max = if (screenWidth < 360.dp) 250.dp else 300.dp)
                ) {
                    Text(
                        text = "Ви впевнені, що хочете видалити цю категорію?",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.White,
                        modifier = Modifier.padding(bottom = 16.dp)
                    )
                    Row(
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Button(
                            onClick = {
                                showDeleteConfirmationDialog = false
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4682B4))
                        ) {
                            Text("Ні", color = Color.White)
                        }
                        Spacer(modifier = Modifier.width(16.dp))
                        Button(
                            onClick = {
                                categoryToDelete?.let {
                                    viewModel.deleteCategory(it)
                                }
                                showDeleteConfirmationDialog = false
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF8B0000))
                        ) {
                            Text("Так", color = Color.White)
                        }
                    }
                }
            }
        }
        if (showAddTransactionDialog) {
            AddTransactionDialog(
                categories = categories,
                onDismiss = { showAddTransactionDialog = false },
                onSave = { transaction ->
                    viewModel.addTransaction(transaction)
                    showAddTransactionDialog = false
                },
                onAddCategory = { newCategory ->
                    viewModel.addCategory(newCategory) // Виклик методу для додавання нової категорії
                }
            )
        }
        if (showAddCategoryDialog) {
            AddCategoryDialog(
                onDismiss = { showAddCategoryDialog = false },
                onSave = { newCategory ->
                    viewModel.updateCategories(categories + newCategory)
                    showAddCategoryDialog = false
                }
            )
        }
        if (showEditCategoryDialog) {
            categoryToEdit?.let { oldCategory ->
                EditCategoryDialog(
                    oldCategoryName = oldCategory,
                    onDismiss = { showEditCategoryDialog = false },
                    onSave = { newCategory ->
                        viewModel.editCategory(oldCategory, newCategory)
                        showEditCategoryDialog = false
                    }
                )
            }
        }
    }
}
@SuppressLint("UnusedBoxWithConstraintsScope")
@Composable
fun CategoryRow(
    category: String,
    expenseAmount: Double,
    onClick: () -> Unit,
    onDelete: () -> Unit,
    onEdit: () -> Unit
) {
    BoxWithConstraints {
        val screenWidth = maxWidth
        val fontSize = if (screenWidth < 360.dp) 14.sp else 18.sp
        val padding = if (screenWidth < 360.dp) 8.dp else 16.dp

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp)
                .background(
                    brush = Brush.horizontalGradient(
                        colors = listOf(
                            Color(0xFF2E2E2E).copy(alpha = 1f),
                            Color(0xFF2E2E2E).copy(alpha = 0f)
                        )
                    ),
                    shape = MaterialTheme.shapes.medium
                )
                .clickable { onClick() }
                .padding(padding),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = category,
                style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold, fontSize = fontSize),
                color = Color.White,
                modifier = Modifier.weight(1f)
            )
            Text(
                text = "${expenseAmount.formatAmount(2)} грн",
                style = MaterialTheme.typography.bodyLarge.copy(fontSize = fontSize),
                color = Color.White,
                modifier = Modifier.padding(end = 8.dp)
            )
            Row {
                IconButton(onClick = onEdit) {
                    Icon(
                        imageVector = Icons.Default.Edit,
                        contentDescription = "Edit Category",
                        tint = Color.White
                    )
                }
                IconButton(onClick = onDelete) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "Delete Category",
                        tint = Color.White
                    )
                }
            }
        }
    }
}

fun Double.formatAmount(digits: Int): String {
    return "%.${digits}f".format(this)
}


@Composable
fun EditCategoryDialog(
    oldCategoryName: String,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit
) {
    var newCategoryName by remember { mutableStateOf(oldCategoryName) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "Редагувати категорію",
                style = MaterialTheme.typography.titleLarge,
                color = Color.White
            )
        },
        text = {
            Column {
                OutlinedTextField(
                    value = newCategoryName,
                    onValueChange = { newCategoryName = it },
                    label = { Text("Нова назва категорії", color = Color.Gray) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(8.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Color.White,
                        unfocusedBorderColor = Color.Gray,
                        cursorColor = Color.White,
                        focusedLabelColor = Color.White,
                        unfocusedLabelColor = Color.Gray
                    ),
                    textStyle = MaterialTheme.typography.bodyLarge.copy(color = Color.White),
                    singleLine = true
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (newCategoryName.isNotBlank()) {
                        onSave(newCategoryName.trim())
                        onDismiss()
                    }
                }
            ) {
                Text("Зберегти", color = Color.White)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Закрити", color = Color.Gray)
            }
        },
        containerColor = Color(0xFF2B2B2B)
    )
}

@Composable
fun MenuButton(text: String, backgroundColors: List<Color>, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        modifier = Modifier
            .width(220.dp)
            .height(60.dp)
            .padding(8.dp),
        shape = RoundedCornerShape(12.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = Color.Transparent
        ),
        contentPadding = PaddingValues(0.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    brush = Brush.horizontalGradient(colors = backgroundColors)
                ),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = text,
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = 20.sp
            )
        }
    }
}
@Composable
fun AddCategoryDialog(
    onDismiss: () -> Unit,
    onSave: (String) -> Unit
) {
    var categoryName by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "Додати категорію",
                style = MaterialTheme.typography.titleLarge,
                color = Color.White
            )
        },
        text = {
            Column {
                OutlinedTextField(
                    value = categoryName,
                    onValueChange = { categoryName = it },
                    label = { Text("Назва категорії", color = Color.Gray) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(8.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Color.White,
                        unfocusedBorderColor = Color.Gray,
                        cursorColor = Color.White,
                        focusedLabelColor = Color.White,
                        unfocusedLabelColor = Color.Gray
                    ),
                    textStyle = MaterialTheme.typography.bodyLarge.copy(color = Color.White),
                    singleLine = true
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (categoryName.isNotBlank()) {
                        onSave(categoryName.trim())
                        onDismiss()
                    }
                }
            ) {
                Text("Зберегти", color = Color.White)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Закрити", color = Color.Gray)
            }
        },
        containerColor = Color(0xFF2B2B2B)
    )
}
@RequiresApi(Build.VERSION_CODES.O)
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddTransactionDialog(
    categories: List<String>,
    onDismiss: () -> Unit,
    onSave: (Transaction) -> Unit, // Явно вказуємо тип параметра
    onAddCategory: (String) -> Unit // Колбек для додавання нової категорії
) {
    val today = remember {
        val calendar = Calendar.getInstance()
        "${calendar.get(Calendar.DAY_OF_MONTH)}/${calendar.get(Calendar.MONTH) + 1}/${calendar.get(Calendar.YEAR)}"
    }
    var amount by remember { mutableStateOf("") }
    var date by remember { mutableStateOf(today) }
    var selectedCategory by remember { mutableStateOf(categories.firstOrNull() ?: "") }
    var comment by remember { mutableStateOf("") }
    var showDatePicker by remember { mutableStateOf(false) }
    var isDropdownExpanded by remember { mutableStateOf(false) }
    var showAddCategoryDialog by remember { mutableStateOf(false) } // Додано стан для показу діалогу додавання категорії

    val context = LocalContext.current

    if (showDatePicker) {
        val calendar = Calendar.getInstance()
        DatePickerDialog(
            context,
            { _, year, month, dayOfMonth ->
                date = "$dayOfMonth/${month + 1}/$year"
                showDatePicker = false
            },
            calendar.get(Calendar.YEAR),
            calendar.get(Calendar.MONTH),
            calendar.get(Calendar.DAY_OF_MONTH)
        ).show()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.8f))
            .clickable(enabled = true, onClick = {})
            .zIndex(1f),
    ) {
        Column(
            modifier = Modifier
                .align(Alignment.Center)
                .padding(16.dp)
                .border(2.dp, Color.White, RoundedCornerShape(8.dp))
                .background(
                    brush = Brush.verticalGradient(
                        colors = listOf(Color.Gray.copy(alpha = 0.8f), Color.Black.copy(alpha = 0.8f))
                    ),
                    shape = RoundedCornerShape(8.dp)
                )
                .widthIn(max = 300.dp)
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "Додати витрату",
                style = MaterialTheme.typography.titleLarge.copy(
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp)
            )
            OutlinedTextField(
                value = amount,
                onValueChange = { newValue ->
                    if (newValue.all { it.isDigit() || it == '.' }) {
                        amount = newValue
                    }
                },
                label = { Text("Сума", color = Color.White) },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                singleLine = true,
                colors = TextFieldDefaults.outlinedTextFieldColors(
                    focusedBorderColor = Color.White,
                    unfocusedBorderColor = Color.Gray,
                    cursorColor = Color.White,
                    focusedLabelColor = Color.White,
                    unfocusedLabelColor = Color.Gray,
                    containerColor = Color.Transparent,
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White
                ),
                textStyle = MaterialTheme.typography.bodyLarge.copy(color = Color.White, fontWeight = FontWeight.Bold)
            )
            Button(
                onClick = { showDatePicker = true },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp)
                    .clip(RoundedCornerShape(4.dp)),
                colors = ButtonDefaults.buttonColors(containerColor = Color.Yellow)
            ) {
                Text(
                    text = "Дата: $date",
                    color = Color.Black,
                    style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold)
                )
            }
            ExposedDropdownMenuBox(
                expanded = isDropdownExpanded,
                onExpandedChange = { isDropdownExpanded = !isDropdownExpanded }
            ) {
                OutlinedTextField(
                    value = selectedCategory,
                    onValueChange = {},
                    label = { Text("Категорія") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .menuAnchor(),
                    readOnly = true,
                    trailingIcon = {
                        ExposedDropdownMenuDefaults.TrailingIcon(expanded = isDropdownExpanded)
                    },
                    colors = TextFieldDefaults.outlinedTextFieldColors(
                        focusedBorderColor = Color.White,
                        unfocusedBorderColor = Color.Gray,
                        focusedLabelColor = Color.White,
                        unfocusedLabelColor = Color.Gray,
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        containerColor = Color.Transparent
                    ),
                    textStyle = MaterialTheme.typography.bodyLarge.copy(color = Color.White, fontWeight = FontWeight.Bold)
                )
                ExposedDropdownMenu(
                    expanded = isDropdownExpanded,
                    onDismissRequest = { isDropdownExpanded = false },
                    modifier = Modifier.background(Color.DarkGray)
                ) {
                    categories.forEach { category ->
                        DropdownMenuItem(
                            text = {
                                Text(
                                    text = category,
                                    color = Color.White
                                )
                            },
                            onClick = {
                                selectedCategory = category
                                isDropdownExpanded = false
                            }
                        )
                    }
                    DropdownMenuItem(
                        text = {
                            Text(
                                text = "Додати категорію",
                                color = Color.White
                            )
                        },
                        onClick = {
                            isDropdownExpanded = false
                            showAddCategoryDialog = true // Показ діалогу додавання категорії
                        }
                    )
                }
            }
            OutlinedTextField(
                value = comment,
                onValueChange = { comment = it },
                label = { Text("Коментар", color = Color.White) },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                singleLine = false,
                colors = TextFieldDefaults.outlinedTextFieldColors(
                    focusedBorderColor = Color.White,
                    unfocusedBorderColor = Color.Gray,
                    cursorColor = Color.White,
                    focusedLabelColor = Color.White,
                    unfocusedLabelColor = Color.Gray,
                    containerColor = Color.Transparent,
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White
                ),
                textStyle = MaterialTheme.typography.bodyLarge.copy(color = Color.White, fontWeight = FontWeight.Bold)
            )
            Row(
                modifier = Modifier
                    .padding(top = 16.dp)
                    .fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                Button(
                    onClick = {
                        if (amount.isNotBlank() && selectedCategory.isNotBlank()) {
                            onSave(
                                Transaction(
                                    id = UUID.randomUUID().toString(),
                                    amount = amount.toDouble(),
                                    category = selectedCategory,
                                    date = date,
                                    comments = comment.takeIf { it.isNotBlank() } // Використання takeIf для comments
                                )
                            )
                            onDismiss()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color.Green)
                ) {
                    Text("Зберегти", color = Color.White)
                }
                Button(
                    onClick = onDismiss,
                    colors = ButtonDefaults.buttonColors(containerColor = Color.Red)
                ) {
                    Text("Скасувати", color = Color.White)
                }
            }
        }
    }

    if (showAddCategoryDialog) {
        AddCategoryDialog(
            onDismiss = { showAddCategoryDialog = false },
            onSave = { newCategory ->
                onAddCategory(newCategory)
                selectedCategory = newCategory
                showAddCategoryDialog = false
            }
        )
    }
}
data class Transaction(
    val id: String = UUID.randomUUID().toString(), // Додаємо унікальний ідентифікатор
    val category: String,
    val amount: Double,
    val date: String,
    val comments: String? = null
)
