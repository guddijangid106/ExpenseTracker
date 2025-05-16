package com.example.expensetracker

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.*
import androidx.navigation.NavHostController
import androidx.navigation.compose.*
import com.example.expensetracker.screens.*
import com.example.expensetracker.ui.screens.WelcomeScreen
import com.example.expensetracker.ui.theme.ExpenseTrackerTheme
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        FirebaseApp.initializeApp(this)
        window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
        
        setContent {
            ExpenseTrackerTheme {
                val navController = rememberNavController()
                val auth = FirebaseAuth.getInstance()
                val db = FirebaseFirestore.getInstance()

                NavHost(navController = navController, startDestination = "welcome") {
                    composable("welcome") {
                        WelcomeScreen(
                            onStartClick = {
                                navController.navigate("login")
                            }
                        )
                    }

                    composable("login") {
                        LoginScreen(navController, auth)
                    }

                    composable("register") {
                        RegisterScreen(navController, auth, db)
                    }

                    composable("home") {
                        HomeScreen(navController, auth, db)
                    }

                    composable("add_income") {
                        AddIncomeScreen(navController, auth)
                    }

                    composable("add_expense") {
                        AddExpenseScreen(navController, auth)
                    }

                    composable("transactions") {
                        TransactionScreen(navController, auth)
                    }

                    composable("profile") {
                        ProfileScreen(
                            navController = navController, 
                            auth = auth
                        )
                    }

                    composable("visual_insights") {
                        VisualInsightsScreen(navController, auth)
                    }
                }
            }
        }
    }
}
