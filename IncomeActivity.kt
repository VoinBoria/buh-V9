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
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
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

private const val TAG = "IncomeActivity"

enum class SortType {
    DATE, AMOUNT, CATEGORY
}

class IncomeViewModelFactory(
    private val application: Application
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(IncomeViewModel::class.java)) {
            return IncomeViewModel(application) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}

class IncomeActivity : ComponentActivity() {
    private lateinit var incomesharedPreferences: SharedPreferences
    private lateinit var IncomeTransactionResultLauncher: ActivityResultLauncher<Intent>
    private val gson by lazy { Gson() }
    private val viewModel: IncomeViewModel by viewModels {
        IncomeViewModelFactory(application)
    }
    private lateinit var updateReceiver: BroadcastReceiver
    private fun <T> navigateToActivity(activityClass: Class<T>) {
        val intent = Intent(this, activityClass)
        startActivity(intent)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        incomesharedPreferences = getSharedPreferences("IncomePrefs", Context.MODE_PRIVATE)

        loadIncomesFromSharedPreferences()
        loadCategoriesFromSharedPreferences() // Завантаження категорій

        IncomeTransactionResultLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == RESULT_OK) {
                viewModel.loadDataIncome()
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
                                val intent = Intent(this@IncomeActivity, MainActivity::class.java).apply {
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
                                title = { Text("Доходи", color = Color.White) },
                                navigationIcon = {
                                    IconButton(onClick = { scope.launch { drawerState.open() } }) {
                                        Icon(Icons.Default.Menu, contentDescription = "Меню", tint = Color.White)
                                    }
                                },
                                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color(0xFF121212))
                            )
                        },
                        content = { innerPadding ->
                            IncomeScreen(
                                viewModel = viewModel,
                                onOpenIncomeTransactionScreen = { categoryName, _ ->
                                    val intent = Intent(this, IncomeTransactionActivity::class.java).apply {
                                        putExtra("categoryName", categoryName)
                                    }
                                    IncomeTransactionResultLauncher.launch(intent)
                                },
                                onincomeDeleteCategory = { category ->
                                    viewModel.incomeDeleteCategory(category)
                                    sendUpdateBroadcast()
                                },
                                modifier = Modifier.padding(innerPadding) // Додаємо innerPadding
                            )
                        }
                    )
                }
            }
        }

        // Встановлення функції після створення ViewModel
        viewModel.setSendUpdateBroadcast { sendUpdateBroadcast() }

        updateReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                if (intent.action == "com.example.homeaccountingapp.UPDATE_INCOME") {
                    viewModel.loadDataIncome()
                }
            }
        }
        val filter = IntentFilter("com.example.homeaccountingapp.UPDATE_INCOME")
        LocalBroadcastManager.getInstance(this).registerReceiver(updateReceiver, filter)
    }

    private fun sendUpdateBroadcast() {
        val updateIntent = Intent("com.example.homeaccountingapp.UPDATE_INCOME")
        LocalBroadcastManager.getInstance(getApplication()).sendBroadcast(updateIntent)
    }

    private fun loadIncomesFromSharedPreferences() {
        val incomesJson = incomesharedPreferences.getString("incomes", null)
        val incomeMap: Map<String, Double> = if (incomesJson != null) {
            Gson().fromJson(incomesJson, object : TypeToken<Map<String, Double>>() {}.type)
        } else {
            emptyMap()
        }
        viewModel.updateIncomes(incomeMap)
    }

    private fun loadCategoriesFromSharedPreferences() {
        val categoriesJson = incomesharedPreferences.getString("categories", null)
        val categoriesList: List<String> = if (categoriesJson != null) {
            Gson().fromJson(categoriesJson, object : TypeToken<List<String>>() {}.type)
        } else {
            val defaultCategories = viewModel.getStandardCategories()
            viewModel.saveCategories(defaultCategories)
            defaultCategories
        }
        viewModel.updateCategories(categoriesList)  // Оновлення категорій в ViewModel
    }

    override fun onDestroy() {
        super.onDestroy()
        LocalBroadcastManager.getInstance(this).unregisterReceiver(updateReceiver)
    }

    companion object {
        private const val TAG = "IncomeActivity"
    }
}
class IncomeViewModel(application: Application) : AndroidViewModel(application) {
    private val incomesharedPreferences = application.getSharedPreferences("IncomePrefs", Context.MODE_PRIVATE)
    private val gson = Gson()
    var categories by mutableStateOf<List<String>>(emptyList())
    var IncomeTransactions by mutableStateOf<List<IncomeTransaction>>(emptyList())
    var categoryIncomes by mutableStateOf<Map<String, Double>>(emptyMap())
    var totalIncome by mutableStateOf(0.0)
    var sortType by mutableStateOf(SortType.DATE)
    private val _sortedTransactions = MutableStateFlow<List<IncomeTransaction>>(emptyList())
    val sortedTransactions: StateFlow<List<IncomeTransaction>> = _sortedTransactions
    private val _filteredTransactions = MutableStateFlow<List<IncomeTransaction>>(emptyList())
    val filteredTransactions: StateFlow<List<IncomeTransaction>> = _filteredTransactions

    private var sendUpdateBroadcast: (() -> Unit)? = null

    init {
        loadDataIncome()
    }

    fun setSendUpdateBroadcast(sendUpdateBroadcast: () -> Unit) {
        this.sendUpdateBroadcast = sendUpdateBroadcast
    }

    fun deleteTransaction(transaction: IncomeTransaction) {
        viewModelScope.launch {
            val currentTransactions = IncomeTransactions.toMutableList()
            currentTransactions.remove(transaction)
            IncomeTransactions = currentTransactions
            saveTransactionsToPreferences(currentTransactions)
            sortTransactions(sortType)
            sendUpdateBroadcast?.invoke() // Використання ?.invoke() для безпечного виклику
        }
    }

    fun updateTransaction(updatedTransaction: IncomeTransaction) {
        viewModelScope.launch {
            val currentTransactions = IncomeTransactions.toMutableList()
            val index = currentTransactions.indexOfFirst { it.id == updatedTransaction.id }
            if (index != -1) {
                currentTransactions[index] = updatedTransaction
            }
            IncomeTransactions = currentTransactions
            saveTransactionsToPreferences(currentTransactions)
            sortTransactions(sortType)
            sendUpdateBroadcast?.invoke() // Використання ?.invoke() для безпечного виклику
        }
    }

    private fun saveTransactionsToPreferences(transactions: List<IncomeTransaction>) {
        val json = gson.toJson(transactions)
        incomesharedPreferences.edit().putString("IncomeTransactions", json).apply()
        sendUpdateBroadcast?.invoke()
    }

    fun loadStandardCategories() {
        categories = listOf("Зарплата", "Премія", "Подарунки", "Пасивний дохід")
        saveCategories(categories)
    }

    fun getStandardCategories(): List<String> {
        return listOf("Зарплата", "Премія", "Подарунки", "Пасивний дохід")
    }

    fun loadDataIncome() {
        categories = loadCategories()
        IncomeTransactions = loadIncomeTransactions()
        updateIncomes()
        sortTransactions(sortType)
    }

    @RequiresApi(Build.VERSION_CODES.O)
    fun filterByPeriodForTransactions(startDate: LocalDate, endDate: LocalDate, categoryName: String? = null) {
        _sortedTransactions.value = IncomeTransactions.filter {
            val transactionDate = LocalDate.parse(it.date, DateTimeFormatter.ISO_DATE)
            transactionDate in startDate..endDate && (categoryName == null || it.category == categoryName)
        }.sortedByDescending { it.date }
    }

    @RequiresApi(Build.VERSION_CODES.O)
    fun filterByPeriodForAllTransactions(startDate: LocalDate, endDate: LocalDate) {
        _sortedTransactions.value = IncomeTransactions.filter {
            val transactionDate = LocalDate.parse(it.date, DateTimeFormatter.ISO_DATE)
            transactionDate in startDate..endDate
        }.sortedByDescending { it.date }
    }

    @RequiresApi(Build.VERSION_CODES.O)
    fun filterByPeriodForCategory(startDate: LocalDate, endDate: LocalDate, categoryName: String) {
        _filteredTransactions.value = IncomeTransactions.filter {
            val transactionDate = LocalDate.parse(it.date, DateTimeFormatter.ISO_DATE)
            it.category == categoryName && transactionDate in startDate..endDate
        }.sortedByDescending { it.date }
    }

    fun getTotalIncomeForFilteredTransactions(): Double {
        return _filteredTransactions.value.sumOf { it.amount }
    }

    fun filterByCategory(categoryName: String) {
        _filteredTransactions.value = IncomeTransactions.filter {
            it.category == categoryName
        }.sortedByDescending { it.date }
    }

    fun sortTransactions(sortType: SortType) {
        this.sortType = sortType
        _sortedTransactions.value = when (sortType) {
            SortType.DATE -> IncomeTransactions.sortedByDescending { it.date }
            SortType.AMOUNT -> IncomeTransactions.sortedByDescending { it.amount }
            SortType.CATEGORY -> IncomeTransactions.sortedBy { it.category }
        }
    }

    fun updateIncomes(Incomes: Map<String, Double> = emptyMap()) {
        categoryIncomes = Incomes.takeIf { it.isNotEmpty() }
            ?: categories.associateWith { category ->
                IncomeTransactions.filter { it.category == category }.sumOf { it.amount }
            }
        totalIncome = IncomeTransactions.sumOf { it.amount }
        saveIncomesToSharedPreferences(categoryIncomes)
    }

    fun updateCategories(newCategories: List<String>) {
        categories = newCategories
        saveCategories(categories)
        updateIncomes()
        sendUpdateBroadcast?.invoke()
    }

    fun updateIncomeTransactions(newTransaction: IncomeTransaction) {
        viewModelScope.launch {
            val currentTransactions = loadIncomeTransactions().toMutableList()
            currentTransactions.add(newTransaction)
            IncomeTransactions = currentTransactions
            saveTransactionsToPreferences(currentTransactions)
            updateIncomes()
            sortTransactions(sortType)
            sendUpdateBroadcast?.invoke()
        }
    }

    fun incomeDeleteCategory(category: String) {
        viewModelScope.launch {
            categories = categories.filter { it != category }
            IncomeTransactions = IncomeTransactions.filter { it.category != category }
            saveCategories(categories)
            saveTransactionsToPreferences(IncomeTransactions)
            updateIncomes()
            sortTransactions(sortType)
            sendUpdateBroadcast?.invoke()
        }
    }

    fun IncomeEditCategory(oldCategory: String, newCategory: String) {
        viewModelScope.launch {
            categories = categories.map { if (it == oldCategory) newCategory else it }
            IncomeTransactions = IncomeTransactions.map {
                if (it.category == oldCategory) it.copy(category = newCategory) else it
            }
            saveCategories(categories)
            saveTransactionsToPreferences(IncomeTransactions)
            updateIncomes()
            sortTransactions(sortType)
            sendUpdateBroadcast?.invoke()
        }
    }

    fun saveCategories(categories: List<String>) {
        incomesharedPreferences.edit().putString("categories", gson.toJson(categories)).apply()
        sendUpdateBroadcast?.invoke()
    }

    private fun saveIncomeTransactions(IncomeTransactions: List<IncomeTransaction>) {
        incomesharedPreferences.edit().putString("IncomeTransactions", gson.toJson(IncomeTransactions)).apply()
        sendUpdateBroadcast?.invoke()
    }

    private fun loadCategories(): List<String> {
        val json = incomesharedPreferences.getString("categories", null)
        return if (json != null) {
            gson.fromJson(json, object : TypeToken<List<String>>() {}.type)
        } else {
            emptyList()
        }
    }

    fun loadIncomeTransactions(): List<IncomeTransaction> {
        val json = incomesharedPreferences.getString("IncomeTransactions", null)
        return if (json != null) {
            gson.fromJson(json, object : TypeToken<List<IncomeTransaction>>() {}.type)
        } else {
            emptyList()
        }
    }

    private fun saveIncomesToSharedPreferences(incomes: Map<String, Double>) {
        val incomesJson = gson.toJson(incomes)
        incomesharedPreferences.edit().putString("incomes", incomesJson).apply()
    }

    companion object {
        private const val TAG = "IncomeViewModel"
    }
}
@SuppressLint("UnusedBoxWithConstraintsScope")
@Composable
fun IncomeScreen(
    viewModel: IncomeViewModel,
    onOpenIncomeTransactionScreen: (String, String) -> Unit,
    onincomeDeleteCategory: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    BoxWithConstraints {
        val screenWidth = maxWidth
        val fontSize = if (screenWidth < 360.dp) 14.sp else 18.sp
        val padding = if (screenWidth < 360.dp) 8.dp else 16.dp

        val categories = viewModel.categories
        val IncomeTransactions = viewModel.IncomeTransactions
        val categoryIncomes = viewModel.categoryIncomes
        val totalIncome = viewModel.totalIncome

        var showIncomeEditCategoryDialog by remember { mutableStateOf(false) }
        var categoryToEdit by remember { mutableStateOf<String?>(null) }
        var showMenu by remember { mutableStateOf(false) }
        var showIncomeAddIncomeTransactionDialog by remember { mutableStateOf(false) }
        var showIncomeAddCategoryDialog by remember { mutableStateOf(false) }
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
                            IncomeCategoryRow(
                                category = category,
                                IncomeAmount = categoryIncomes[category] ?: 0.0,
                                onClick = {
                                    onOpenIncomeTransactionScreen(category, Gson().toJson(IncomeTransactions))
                                },
                                onDelete = {
                                    categoryToDelete = category
                                    showDeleteConfirmationDialog = true
                                },
                                onEdit = {
                                    categoryToEdit = category
                                    showIncomeEditCategoryDialog = true
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
                containerColor = Color(0xFF228B22)
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
                        IncomeMenuButton(
                            text = "Додати транзакцію",
                            backgroundColors = listOf(
                                Color(0xFF006400).copy(alpha = 0.7f),
                                Color(0xFF228B22).copy(alpha = 0.1f)
                            ),
                            onClick = {
                                showIncomeAddIncomeTransactionDialog = true
                                showMenu = false
                            }
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        IncomeMenuButton(
                            text = "Додати категорію",
                            backgroundColors = listOf(
                                Color(0xFF00008B).copy(alpha = 0.7f),
                                Color(0xFF4682B4).copy(alpha = 0.1f)
                            ),
                            onClick = {
                                showIncomeAddCategoryDialog = true
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
                    text = "Загальні Доходи:",
                    style = MaterialTheme.typography.titleMedium,
                    color = Color.White,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                Text(
                    text = "${totalIncome.incomeFormatAmount(2)} грн",
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
                                    viewModel.incomeDeleteCategory(it)
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
        if (showIncomeAddIncomeTransactionDialog) {
            IncomeAddIncomeTransactionDialog(
                categories = categories,
                onDismiss = { showIncomeAddIncomeTransactionDialog = false },
                onSave = { IncomeTransaction ->
                    viewModel.updateIncomeTransactions(IncomeTransaction)
                    showIncomeAddIncomeTransactionDialog = false
                }
            )
        }
        if (showIncomeAddCategoryDialog) {
            IncomeAddCategoryDialog(
                onDismiss = { showIncomeAddCategoryDialog = false },
                onSave = { newCategory ->
                    viewModel.updateCategories(categories + newCategory)
                    showIncomeAddCategoryDialog = false
                }
            )
        }
        if (showIncomeEditCategoryDialog) {
            categoryToEdit?.let { oldCategory ->
                IncomeEditCategoryDialog(
                    oldCategoryName = oldCategory,
                    onDismiss = { showIncomeEditCategoryDialog = false },
                    onSave = { newCategory ->
                        viewModel.IncomeEditCategory(oldCategory, newCategory)
                        showIncomeEditCategoryDialog = false
                    }
                )
            }
        }
    }
}
@SuppressLint("UnusedBoxWithConstraintsScope")
@Composable
fun IncomeCategoryRow(
    category: String,
    IncomeAmount: Double,
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
                text = "${IncomeAmount.incomeFormatAmount(2)} грн",
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

fun Double.incomeFormatAmount(digits: Int): String {
    return "%.${digits}f".format(this)
}
@Composable
fun IncomeEditCategoryDialog(
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
fun IncomeMenuButton(text: String, backgroundColors: List<Color>, onClick: () -> Unit) {
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
fun IncomeAddCategoryDialog(
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
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun IncomeAddIncomeTransactionDialog(
    categories: List<String>,
    onDismiss: () -> Unit,
    onSave: (IncomeTransaction) -> Unit
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
            .background(Color.Black.copy(alpha = 0.8f)) // Більш прозорий чорний фон
            .clickable(enabled = true, onClick = {})
            .zIndex(1f),
    ) {
        Column(
            modifier = Modifier
                .align(Alignment.Center)
                .padding(16.dp)
                .border(2.dp, Color.White, RoundedCornerShape(8.dp)) // Додаємо білу рамку
                .background(
                    brush = Brush.verticalGradient(
                        colors = listOf(Color.Gray.copy(alpha = 0.8f), Color.Black.copy(alpha = 0.8f))
                    ),
                    shape = RoundedCornerShape(8.dp)
                )
                .widthIn(max = 300.dp) // Зробити меню вужчим
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "Додати дохід",
                style = MaterialTheme.typography.titleLarge.copy(
                    fontWeight = FontWeight.Bold,
                    color = Color.Green // Зелений заголовок для кращого контрасту
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
                    containerColor = Color.Transparent, // Прозорий фон для поля вводу
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White
                ),
                textStyle = MaterialTheme.typography.bodyLarge.copy(color = Color.White, fontWeight = FontWeight.Bold), // Білий і жирний текст
                keyboardOptions = KeyboardOptions.Default.copy(
                    keyboardType = KeyboardType.Decimal
                )
            )
            Button(
                onClick = { showDatePicker = true },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp)
                    .clip(RoundedCornerShape(4.dp)),
                colors = ButtonDefaults.buttonColors(containerColor = Color.Yellow) // Жовта кнопка вибору дати
            ) {
                Text(
                    text = "Дата: $date",
                    color = Color.Black,
                    style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold) // Чорний жирний текст
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
                        containerColor = Color.Transparent // Прозорий фон для поля вводу
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
                            },
                            modifier = Modifier.background(Color.DarkGray)
                        )
                    }
                    DropdownMenuItem(
                        text = {
                            Text(
                                text = "Додати категорію",
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 18.sp // Збільшення розміру шрифту для кращої читабельності
                            )
                        },
                        modifier = Modifier
                            .background(Color.DarkGray)
                            .border(2.dp, Color.White, RoundedCornerShape(8.dp)) // Додаємо білу рамку
                            .padding(8.dp),
                        onClick = {}
                    )
                }
            }
            OutlinedTextField(
                value = comment,
                onValueChange = { comment = it.takeIf { it.isNotBlank() } ?: "" },
                label = { Text("Коментар", color = Color.Gray) },
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
                    containerColor = Color.Transparent, // Прозорий фон для поля вводу
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White
                ),
                textStyle = MaterialTheme.typography.bodyLarge.copy(color = Color.White, fontWeight = FontWeight.Bold) // Білий і жирний текст
            )
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                TextButton(
                    onClick = onDismiss,
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Скасувати", color = Color.Red, fontWeight = FontWeight.Bold) // Білий жирний текст
                }
                Spacer(modifier = Modifier.width(8.dp))
                TextButton(
                    onClick = {
                        val transaction = IncomeTransaction(
                            id = UUID.randomUUID().toString(),
                            amount = amount.toDoubleOrNull() ?: 0.0,
                            date = date,
                            category = selectedCategory,
                            comments = comment.takeIf { it.isNotBlank() } // Використання takeIf для comments
                        )
                        onSave(transaction)
                    },
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Зберегти", color = Color.Green, fontWeight = FontWeight.Bold) // Білий жирний текст
                }
            }
        }
    }
}
data class IncomeTransaction(
    val id: String = UUID.randomUUID().toString(),
    val category: String,
    val amount: Double,
    val date: String,
    val comments: String? = null
)
