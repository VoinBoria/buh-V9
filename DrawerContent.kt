// DrawerContent.kt
package com.serhio.homeaccountingapp

import android.annotation.SuppressLint
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun DrawerContent(
    onNavigateToMainActivity: () -> Unit,
    onNavigateToIncomes: () -> Unit,
    onNavigateToExpenses: () -> Unit,
    onNavigateToIssuedOnLoan: () -> Unit,
    onNavigateToBorrowed: () -> Unit,
    onNavigateToAllTransactionIncome: () -> Unit,
    onNavigateToAllTransactionExpense: () -> Unit,
    onNavigateToBudgetPlanning: () -> Unit,
    onNavigateToTaskActivity: () -> Unit
) {
    val configuration = LocalConfiguration.current
    val screenWidthDp = configuration.screenWidthDp.dp
    val iconSize = when {
        screenWidthDp < 360.dp -> 20.dp  // Small screens
        screenWidthDp < 600.dp -> 24.dp  // Normal screens
        else -> 28.dp  // Large screens
    }
    val textSize = if (screenWidthDp < 360.dp) 14.sp else 18.sp  // Adjust text size for small screens
    val paddingSize = if (screenWidthDp < 360.dp) 8.dp else 16.dp  // Adjust padding for small screens

    val drawerWidth = when {
        screenWidthDp < 600.dp -> screenWidthDp * 0.8f
        else -> 240.dp  // Set a fixed width for larger screens
    }

    Column(
        modifier = Modifier
            .fillMaxHeight()
            .width(drawerWidth)
            .background(Color(0xFF1E1E1E).copy(alpha = 0.9f))
            .padding(paddingSize)
            .verticalScroll(rememberScrollState())  // Enable vertical scrolling
    ) {
        Text(
            text = "Меню",
            style = MaterialTheme.typography.titleLarge.copy(color = Color.White, fontSize = textSize)
        )
        Spacer(modifier = Modifier.height(20.dp))  // Adjust space for small screens
        Column(modifier = Modifier.fillMaxWidth()) {
            CategoryItem(
                text = "Головне меню",
                icon = {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_home),
                        contentDescription = "Іконка головного меню",
                        tint = Color.White,
                        modifier = Modifier.size(iconSize)
                    )
                },
                onClick = onNavigateToMainActivity,
                gradientColors = listOf(
                    Color(0xFF000000).copy(alpha = 0.7f),
                    Color(0xFF2E2E2E).copy(alpha = 0.7f)
                )
            )
            Spacer(modifier = Modifier.height(8.dp))
            CategoryItem(
                text = "Доходи",
                icon = {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_income),
                        contentDescription = "Іконка доходів",
                        tint = Color.White,
                        modifier = Modifier.size(iconSize)
                    )
                },
                onClick = onNavigateToIncomes,
                gradientColors = listOf(
                    Color(0xFF000000).copy(alpha = 0.7f),
                    Color(0xFF2E2E2E).copy(alpha = 0.7f)
                )
            )
            Spacer(modifier = Modifier.height(8.dp))
            CategoryItem(
                text = "Витрати",
                icon = {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_expense),
                        contentDescription = "Іконка витрат",
                        tint = Color.White,
                        modifier = Modifier.size(iconSize)
                    )
                },
                onClick = onNavigateToExpenses,
                gradientColors = listOf(
                    Color(0xFF000000).copy(alpha = 0.7f),
                    Color(0xFF2E2E2E).copy(alpha = 0.7f)
                )
            )
            Spacer(modifier = Modifier.height(8.dp))
            CategoryItem(
                text = "Всі транзакції доходів",
                icon = {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_all_income_transactions),
                        contentDescription = "Іконка всіх транзакцій доходів",
                        tint = Color.White,
                        modifier = Modifier.size(iconSize)
                    )
                },
                onClick = onNavigateToAllTransactionIncome,
                gradientColors = listOf(
                    Color(0xFF000000).copy(alpha = 0.7f),
                    Color(0xFF2E2E2E).copy(alpha = 0.7f)
                )
            )
            Spacer(modifier = Modifier.height(8.dp))
            CategoryItem(
                text = "Всі транзакції витрат",
                icon = {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_all_expense_transactions),
                        contentDescription = "Іконка всіх транзакцій витрат",
                        tint = Color.White,
                        modifier = Modifier.size(iconSize)
                    )
                },
                onClick = onNavigateToAllTransactionExpense,
                gradientColors = listOf(
                    Color(0xFF000000).copy(alpha = 0.7f),
                    Color(0xFF2E2E2E).copy(alpha = 0.7f)
                )
            )
            Spacer(modifier = Modifier.height(8.dp))
            CategoryItem(
                text = "Видано в борг",
                icon = {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_loan_issued),
                        contentDescription = "Іконка виданих боргів",
                        tint = Color.White,
                        modifier = Modifier.size(iconSize)
                    )
                },
                onClick = onNavigateToIssuedOnLoan,
                gradientColors = listOf(
                    Color(0xFF000000).copy(alpha = 0.7f),
                    Color(0xFF2E2E2E).copy(alpha = 0.7f)
                )
            )
            Spacer(modifier = Modifier.height(8.dp))
            CategoryItem(
                text = "Отримано в борг",
                icon = {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_loan_borrowed),
                        contentDescription = "Іконка отриманих боргів",
                        tint = Color.White,
                        modifier = Modifier.size(iconSize)
                    )
                },
                onClick = onNavigateToBorrowed,
                gradientColors = listOf(
                    Color(0xFF000000).copy(alpha = 0.7f),
                    Color(0xFF2E2E2E).copy(alpha = 0.7f)
                )
            )
            Spacer(modifier = Modifier.height(8.dp))
            CategoryItem(
                text = "Планування бюджету",
                icon = {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_budget_planning),
                        contentDescription = "Іконка планування бюджету",
                        tint = Color.White,
                        modifier = Modifier.size(iconSize)
                    )
                },
                onClick = onNavigateToBudgetPlanning,
                gradientColors = listOf(
                    Color(0xFF000000).copy(alpha = 0.7f),
                    Color(0xFF2E2E2E).copy(alpha = 0.7f)
                )
            )
            Spacer(modifier = Modifier.height(8.dp))
            CategoryItem(
                text = "Задачник",
                icon = {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_task),
                        contentDescription = "Іконка задачника",
                        tint = Color.White,
                        modifier = Modifier.size(iconSize)
                    )
                },
                onClick = onNavigateToTaskActivity,
                gradientColors = listOf(
                    Color(0xFF000000).copy(alpha = 0.7f),
                    Color(0xFF2E2E2E).copy(alpha = 0.7f)
                )
            )
        }
    }
}