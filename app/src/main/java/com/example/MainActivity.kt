package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.data.repository.WorkoutRepository
import com.example.ui.auth.AuthScreen
import com.example.ui.charts.GainsChartScreen
import com.example.ui.components.WorkoutSocialShareHostDialog
import com.example.ui.dashboard.DashboardScreen
import com.example.ui.history.WorkoutHistoryScreen
import com.example.ui.nutrition.NutritionScreen
import com.example.ui.theme.AmberGold
import com.example.ui.theme.BackgroundDark
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.theme.SurfaceCard
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.tracker.ActiveWorkoutScreen
import com.example.ui.tracker.WorkoutHubScreen
import com.example.ui.tracker.triggerRestEndAlert
import com.example.viewmodel.WorkoutViewModel
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MyApplicationTheme {
                AppRootNavigation()
            }
        }
    }
}

internal fun FirebaseAuth.authStateFlow(): Flow<FirebaseUser?> = callbackFlow {
    val listener = FirebaseAuth.AuthStateListener { auth ->
        trySend(auth.currentUser)
    }
    addAuthStateListener(listener)
    awaitClose { removeAuthStateListener(listener) }
}

@Composable
fun AppRootNavigation(auth: FirebaseAuth = Firebase.auth) {
    val currentUser by auth.authStateFlow().collectAsStateWithLifecycle(initialValue = auth.currentUser)
    val user = currentUser

    if (user == null) {
        AuthScreen(auth = auth)
    } else {
        StrengthForgeApp(currentUserId = user.uid)
    }
}

enum class NavDestination(val label: String, val icon: ImageVector) {
    DASHBOARD("Dashboard", Icons.Default.FitnessCenter),
    GAINS("Gains Chart", Icons.Default.TrendingUp),
    WORKOUT("Workout", Icons.Default.PlayArrow),
    NUTRITION("Macros", Icons.Default.Restaurant),
    HISTORY("History", Icons.Default.History)
}

class WorkoutViewModelFactory(
    private val repository: WorkoutRepository,
    private val userId: String,
    private val appContext: android.content.Context
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
        return WorkoutViewModel(
            repository = repository,
            currentUserId = userId,
            appContext = appContext
        ) as T
    }
}

internal fun pushNavDestination(backStack: MutableList<Int>, targetOrdinal: Int) {
    if (backStack.lastOrNull() != targetOrdinal) {
        backStack.add(targetOrdinal)
        if (backStack.size > 32) {
            backStack.removeAt(0)
        }
    }
}

internal fun popNavDestination(backStack: MutableList<Int>): Boolean {
    return if (backStack.size > 1) {
        backStack.removeAt(backStack.lastIndex)
        true
    } else {
        false
    }
}

@Composable
fun StrengthForgeApp(currentUserId: String) {
    val context = LocalContext.current
    val viewModel: WorkoutViewModel = viewModel(
        key = currentUserId,
        factory = WorkoutViewModelFactory(
            WorkoutRepository(context),
            currentUserId,
            context.applicationContext
        )
    )

    val navigationBackStack = remember { mutableStateListOf(NavDestination.DASHBOARD.ordinal) }
    val currentDestination = navigationBackStack.lastOrNull() ?: NavDestination.DASHBOARD.ordinal
    val isWorkoutActive by viewModel.isWorkoutActive.collectAsState()

    BackHandler(enabled = navigationBackStack.size > 1) {
        popNavDestination(navigationBackStack)
    }

    LaunchedEffect(viewModel) {
        viewModel.restTimerCompletedEvent.collect {
            launch { triggerRestEndAlert(context) }
        }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = BackgroundDark,
        bottomBar = {
            NavigationBar(
                containerColor = Color(0xFF0F172A),
                contentColor = TextPrimary,
                modifier = Modifier.navigationBarsPadding()
            ) {
                NavDestination.entries.forEach { destination ->
                    val selected = currentDestination == destination.ordinal
                    NavigationBarItem(
                        selected = selected,
                        onClick = { pushNavDestination(navigationBackStack, destination.ordinal) },
                        icon = {
                            Icon(
                                imageVector = destination.icon,
                                contentDescription = destination.label,
                                modifier = Modifier.size(22.dp)
                            )
                        },
                        label = {
                            Text(
                                text = destination.label,
                                fontSize = 11.sp,
                                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
                            )
                        },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = Color.Black,
                            selectedTextColor = AmberGold,
                            indicatorColor = AmberGold,
                            unselectedIconColor = TextSecondary,
                            unselectedTextColor = TextSecondary
                        ),
                        modifier = Modifier.testTag("nav_tab_${destination.name.lowercase()}")
                    )
                }
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            Crossfade(targetState = currentDestination, label = "ScreenTransition") { targetScreen ->
                when (NavDestination.entries[targetScreen]) {
                    NavDestination.DASHBOARD -> DashboardScreen(
                        viewModel = viewModel,
                        onNavigateToWorkout = {
                            pushNavDestination(navigationBackStack, NavDestination.WORKOUT.ordinal)
                        },
                        onNavigateToAnalytics = {
                            pushNavDestination(navigationBackStack, NavDestination.GAINS.ordinal)
                        },
                        onNavigateToNutrition = {
                            pushNavDestination(navigationBackStack, NavDestination.NUTRITION.ordinal)
                        }
                    )
                    NavDestination.GAINS -> GainsChartScreen(viewModel = viewModel)
                    NavDestination.WORKOUT -> {
                        if (isWorkoutActive) {
                            ActiveWorkoutScreen(
                                viewModel = viewModel,
                                onWorkoutFinished = {
                                    pushNavDestination(navigationBackStack, NavDestination.HISTORY.ordinal)
                                },
                                onWorkoutDiscarded = {
                                    pushNavDestination(navigationBackStack, NavDestination.WORKOUT.ordinal)
                                }
                            )
                        } else {
                            WorkoutHubScreen(
                                viewModel = viewModel,
                                onOpenHistoryTab = {
                                    pushNavDestination(navigationBackStack, NavDestination.HISTORY.ordinal)
                                }
                            )
                        }
                    }
                    NavDestination.NUTRITION -> NutritionScreen(viewModel = viewModel)
                    NavDestination.HISTORY -> WorkoutHistoryScreen(
                        viewModel = viewModel,
                        onSelectExerciseForChart = { exerciseId ->
                            viewModel.setSelectedExercise(exerciseId)
                            pushNavDestination(navigationBackStack, NavDestination.GAINS.ordinal)
                        },
                        onStartRoutine = { routine ->
                            viewModel.startWorkoutWithRoutine(routine)
                            pushNavDestination(navigationBackStack, NavDestination.WORKOUT.ordinal)
                        },
                        onOpenWorkoutTab = {
                            pushNavDestination(navigationBackStack, NavDestination.WORKOUT.ordinal)
                        }
                    )
                }
            }
        }
    }

    WorkoutSocialShareHostDialog(viewModel = viewModel)
}
