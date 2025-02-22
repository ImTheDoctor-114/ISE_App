package com.example.ise_app

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.ise_app.ui.theme.ISEAppTheme
import com.example.ise_app.ui.theme.SplashScreen
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import java.time.ZoneOffset
import java.time.ZonedDateTime.now
import kotlin.math.log10
import kotlin.math.pow

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            ISEAppTheme {
                ISEParametersApp()
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ISEParametersApp() {
    val navController = rememberNavController()
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("AquaSense", color = MaterialTheme.colorScheme.onPrimary) },
                colors = TopAppBarDefaults.mediumTopAppBarColors(containerColor = MaterialTheme.colorScheme.primary)
            )
        }
    ) { paddingValues ->
        Box(modifier = Modifier.padding(paddingValues)) {
            NavHost(navController, startDestination = "splash") {
                composable("splash") { SplashScreen(navController) }  // ✅ Add splash screen
                composable("home") { HomeScreen(navController) }
                composable("calibration") { CalibrationScreen(navController) }
                composable("disconnected") { DisconnectedScreen(navController) }
            }


        }
    }
}

@Composable
fun DropdownMenuSelector(
    options: List<String>,
    selectedOption: String,
    onOptionSelected: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    var selectedText by remember { mutableStateOf(selectedOption) }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.LightGray, shape = RoundedCornerShape(8.dp))
            .clickable { expanded = true }
            .padding(16.dp)
    ) {
        Text(selectedText, modifier = Modifier.fillMaxWidth())

        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = { Text(option) },
                    onClick = {
                        selectedText = option
                        onOptionSelected(option) // 🔥 This properly updates the selected option
                        expanded = false
                    }
                )
            }
        }
    }
}
@Composable
fun SensorDataRow(
    compoundName: String,
    voltage: Double,
    concentration: Double,
    isSafe: Boolean,
    whoLimit: Double
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column {
            Text("$compoundName:", style = MaterialTheme.typography.bodyLarge)
            Text("Voltage: ${"%.4f".format(voltage)} V", style = MaterialTheme.typography.bodySmall)
            Text("Concentration: ${"%.4f".format(concentration)} µM", style = MaterialTheme.typography.bodySmall)
            Text("WHO Limit: ${"%.4f".format(whoLimit)} µM", style = MaterialTheme.typography.bodySmall)
        }
        Text(
            text = if (isSafe) "Safe" else "Unsafe",
            color = if (isSafe) Color.Green else Color.Red,
            style = MaterialTheme.typography.bodyLarge
        )
    }
}
@Composable
fun HomeScreen(navController: NavController) {
    val database = FirebaseDatabase.getInstance().reference
    val sensorDataRef = database.child("sensorData/latest")
    var calibrationPerformed by remember { mutableStateOf(false) }  // Tracks if calibration has been done
    val calibrationRef = database.child("calibration")
    val deviceStatusRef = database.child("deviceStatus/lastSeen")

    var showDialog by rememberSaveable { mutableStateOf(true) }
    var showStartScreen by remember { mutableStateOf(false) }
    var isMeasuring by remember { mutableStateOf(false) }
    var showDashboard by remember { mutableStateOf(false) }

    var useCalibration by rememberSaveable { mutableStateOf(false) }

    var chlorineVoltage by remember { mutableDoubleStateOf(0.0) }
    var ammoniaVoltage by remember { mutableDoubleStateOf(0.0) }
    var nitrateVoltage by remember { mutableDoubleStateOf(0.0) }
    var temperature by remember { mutableDoubleStateOf(0.0) }

    var chlorineConcentration by remember { mutableDoubleStateOf(0.0) }
    var ammoniaConcentration by remember { mutableDoubleStateOf(0.0) }
    var nitrateConcentration by remember { mutableDoubleStateOf(0.0) }

    var chlorineSafe by remember { mutableStateOf(true) }
    var ammoniaSafe by remember { mutableStateOf(true) }
    var nitrateSafe by remember { mutableStateOf(true) }

    var hasNavigated by remember { mutableStateOf(false) }  // ✅ Track ESP disconnection

    val WHO_LIMITS = mapOf(
        "Ammonia" to 83.0,
        "Chlorine" to 141.0,
        "Nitrate" to 806.0
    )

    // ✅ ESP Disconnection Handling
    DisposableEffect(Unit) {
        val lastSeenListener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val lastSeen = snapshot.getValue(Long::class.java) ?: 0L
                val currentTime = now(ZoneOffset.ofHoursMinutes(5, 30)).toEpochSecond()

                Log.d("HomeScreen", "🔹 Last Seen: $lastSeen, Current Time: $currentTime")

                if ((currentTime - lastSeen) > 5 && !hasNavigated) {
                    hasNavigated = true
                    Log.e("HomeScreen", "❌ ESP Disconnected! Navigating...")
                    navController.navigate("disconnected") {
                        popUpTo("home") { inclusive = true }
                    }
                }
            }

            override fun onCancelled(error: DatabaseError) {
                Log.e("HomeScreen", "🔥 Firebase error: ${error.message}")
            }
        }

        deviceStatusRef.addValueEventListener(lastSeenListener)

        onDispose {
            deviceStatusRef.removeEventListener(lastSeenListener)
        }
    }
    fun defaultRegression(compound: String, voltage: Double): Double {
        return when (compound) {
            "Nitrate" -> 10.0.pow((voltage - 0.17334) / -0.05446)
            "Chlorine" -> 10.0.pow((voltage - 0.22784) / -0.04381)
            "Ammonia" -> 10.0.pow((voltage + 0.29024) / 0.04874)
            else -> 0.0
        }
    }
    fun computeConcentration(compound: String, voltage: Double, e0: Double?, slope: Double?, useCalibration: Boolean): Double {
        if (voltage < 0 && compound != "Ammonia") {
            Log.e("HomeScreen", "⚠️ Skipping $compound: Negative voltage ($voltage V)")
            return Double.NaN  // ✅ Prevent calculations on negative voltage
        }

        return if (useCalibration && e0 != null && slope != null && slope != 0.0) {
            val calculatedValue = (voltage - e0) / slope
            if (calculatedValue.isNaN() || calculatedValue.isInfinite()) {
                Log.e("HomeScreen", "❌ Invalid concentration for $compound")
                Double.NaN
            } else {
                10.0.pow(calculatedValue)  // ✅ Correctly compute concentration
            }
        } else {
            defaultRegression(compound, voltage)  // ✅ Use default regression if calibration is missing
        }
    }
    // ✅ Calculate Concentration Based on Calibration
    fun calculateConcentration() {
        calibrationRef.addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val chlorineE0 = snapshot.child("chlorine/E0").getValue(Double::class.java)
                val chlorineSlope = snapshot.child("chlorine/Slope").getValue(Double::class.java)
                val ammoniaE0 = snapshot.child("ammonia/E0").getValue(Double::class.java)
                val ammoniaSlope = snapshot.child("ammonia/Slope").getValue(Double::class.java)
                val nitrateE0 = snapshot.child("nitrate/E0").getValue(Double::class.java)
                val nitrateSlope = snapshot.child("nitrate/Slope").getValue(Double::class.java)

                // Switch to calibration curves if available
                if (!calibrationPerformed && chlorineE0 != null && chlorineSlope != null) {
                    useCalibration = true
                    calibrationPerformed = true  // Mark calibration as completed
                }

                chlorineConcentration = computeConcentration("Chlorine", chlorineVoltage, chlorineE0, chlorineSlope, useCalibration)
                ammoniaConcentration = computeConcentration("Ammonia", ammoniaVoltage, ammoniaE0, ammoniaSlope, useCalibration)
                nitrateConcentration = computeConcentration("Nitrate", nitrateVoltage, nitrateE0, nitrateSlope, useCalibration)

                chlorineSafe = chlorineConcentration < (WHO_LIMITS["Chlorine"] ?: Double.MAX_VALUE)
                ammoniaSafe = ammoniaConcentration < (WHO_LIMITS["Ammonia"] ?: Double.MAX_VALUE)
                nitrateSafe = nitrateConcentration < (WHO_LIMITS["Nitrate"] ?: Double.MAX_VALUE)
            }

            override fun onCancelled(error: DatabaseError) {
                Log.e("HomeScreen", "❌ Error fetching calibration data: ${error.message}")
            }
        })
    }

    // ✅ Fetch Sensor Data & Calculate Concentration
    fun fetchSensorData() {
        isMeasuring = true
        showDashboard = false
        database.child("fetch").setValue(true)

        Handler(Looper.getMainLooper()).postDelayed({
            sensorDataRef.addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    chlorineVoltage = snapshot.child("chlorine").getValue(Double::class.java) ?: 0.0
                    ammoniaVoltage = snapshot.child("ammonia").getValue(Double::class.java) ?: 0.0
                    nitrateVoltage = snapshot.child("nitrate").getValue(Double::class.java) ?: 0.0
                    temperature = snapshot.child("temperature").getValue(Double::class.java) ?: 0.0

                    calculateConcentration()  // ✅ Now calls concentration calculation
                    isMeasuring = false
                    showDashboard = true
                }

                override fun onCancelled(error: DatabaseError) {
                    Log.e("HomeScreen", "❌ Error fetching sensor data: ${error.message}")
                    isMeasuring = false
                }
            })
        }, 30000)  // ✅ 30 sec delay for electrode stabilization
    }


    // ✅ Show Calibration Popup First
    if (showDialog) {
        CalibrationPromptDialog(
            onUseCalibration = {
                useCalibration = true  // Enable calibration mode
                showDialog = false
                navController.navigate("calibration")
            },
            onUseDefault = {
                useCalibration = false  // Keep using default curves
                showDialog = false
                showStartScreen = true  // Go to measurement mode
            }
        )

    }


    // ✅ Show Start Screen Next
    else if (showStartScreen) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text("Welcome to AquaSense", style = MaterialTheme.typography.headlineMedium)
            Spacer(Modifier.height(24.dp))
            Button(
                onClick = {
                    showStartScreen = false
                    fetchSensorData()
                }
            ) {
                Text("Start Measurement Mode")
            }
        }
    }
    // ✅ Show Loading Screen While Measuring
    else if (isMeasuring) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text("Measuring...", style = MaterialTheme.typography.headlineMedium)
            Spacer(Modifier.height(16.dp))
            CircularProgressIndicator()
        }
    }
    // ✅ Show Dashboard After Measurement
    else if (showDashboard) {
        HomeUI(
            navController,
            ::fetchSensorData,
            chlorineVoltage,
            ammoniaVoltage,
            nitrateVoltage,
            temperature,
            chlorineConcentration,
            ammoniaConcentration,
            nitrateConcentration,
            chlorineSafe,
            ammoniaSafe,
            nitrateSafe
        )
    }
}

@Composable
fun HomeUI(
    navController: NavController,
    fetchSensorData: () -> Unit,
    chlorineVoltage: Double,
    ammoniaVoltage: Double,
    nitrateVoltage: Double,
    temperature: Double,
    chlorineConcentration: Double,
    ammoniaConcentration: Double,
    nitrateConcentration: Double,
    chlorineSafe: Boolean,
    ammoniaSafe: Boolean,
    nitrateSafe: Boolean
) {
    val WHO_LIMITS = mapOf(
        "Ammonia" to 83.0,
        "Chlorine" to 141.0,
        "Nitrate" to 806.0
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp)) {
                SensorDataRow("Chlorine", chlorineVoltage, chlorineConcentration, chlorineSafe, WHO_LIMITS["Chlorine"] ?: 0.0)
                SensorDataRow("Ammonia", ammoniaVoltage, ammoniaConcentration, ammoniaSafe, WHO_LIMITS["Ammonia"] ?: 0.0)
                SensorDataRow("Nitrate", nitrateVoltage, nitrateConcentration, nitrateSafe, WHO_LIMITS["Nitrate"] ?: 0.0)
                Text("Temperature: $temperature °C")
            }
        }

        Spacer(Modifier.height(24.dp))

        Button(
            onClick = { fetchSensorData() },
            enabled = true
        ) {
            Text("Start Measurement Mode Again")
        }

        Spacer(Modifier.height(16.dp))

        Button(onClick = { navController.navigate("calibration") }) {
            Text("Calibration Mode")
        }

        Spacer(Modifier.height(24.dp)) // Add space before the images

        // **Images in a Row**
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center
        ) {
            Image(
                painter = painterResource(id = R.drawable.logo1),
                contentDescription = "Logo 1",
                modifier = Modifier
                    .size(150.dp) // Adjust size as needed
                    .padding(end = 8.dp) // Add space between images
            )
            Image(
                painter = painterResource(id = R.drawable.logo2),
                contentDescription = "Logo 2",
                modifier = Modifier
                    .size(150.dp) // Adjust size as needed
            )
        }
    }
}


@Composable
fun CalibrationScreen(navController: NavController) {
    val database = FirebaseDatabase.getInstance().reference
    var measuredVoltage by remember { mutableDoubleStateOf(0.0) }
    var selectedCompound by remember { mutableStateOf("Nitrate") }
    var selectedConcentration by remember { mutableStateOf("10uM") }
    var isLoading by remember { mutableStateOf(false) }

    var calibrationData by remember { mutableStateOf(emptyList<Pair<Double, Double>>()) }
    var fetchedE0 by remember { mutableDoubleStateOf(0.0) }
    var fetchedSlope by remember { mutableDoubleStateOf(0.0) }
    var calculatedConcentration by remember { mutableDoubleStateOf(0.0) }
    var isSafe by remember { mutableStateOf(true) }

    val WHO_LIMITS = mapOf(
        "Ammonia" to 83.0,
        "Chlorine" to 141.0,
        "Nitrate" to 806.0
    )

    // ✅ Update `concentrationOptions` dynamically when `selectedCompound` changes
    val concentrationOptions = remember(selectedCompound) {
        when (selectedCompound) {
            "Nitrate" -> listOf("10uM", "50uM", "100uM")
            "Ammonia" -> listOf("1uM", "10uM", "100uM")
            "Chlorine" -> listOf("10uM", "100uM", "1000uM")
            else -> listOf("10uM")
        }
    }

    fun defaultRegression(compound: String, voltage: Double): Double {
        return when (compound) {
            "Nitrate" -> 10.0.pow((voltage - 0.17334) / -0.05446)
            "Chlorine" -> 10.0.pow((voltage - 0.22784) / -0.04381)
            "Ammonia" -> 10.0.pow((voltage + 0.29024) / 0.04874)
            else -> 0.0
        }
    }

    fun performLinearRegression(data: List<Pair<Double, Double>>): Pair<Double, Double> {
        val n = data.size
        if (n < 3) {
            Log.e("CalibrationScreen", "❌ Not enough points for regression!")
            return 0.0 to -59.0
        }

        Log.d("CalibrationScreen", "📊 Calibration Data (log10[Conc] vs Voltage):")
        data.forEach { (logConc, voltage) ->
            Log.d("CalibrationScreen", "  logConc: $logConc, Voltage: $voltage")
        }

        val sumX = data.sumOf { it.first }
        val sumY = data.sumOf { it.second }
        val sumXY = data.sumOf { it.first * it.second }
        val sumX2 = data.sumOf { it.first * it.first }
        val denominator = (n * sumX2 - sumX * sumX)

        if (denominator == 0.0) {
            Log.e("CalibrationScreen", "❌ Regression failed (denominator = 0)!")
            return 0.0 to -59.0
        }

        val slope = (n * sumXY - sumX * sumY) / denominator
        val e0 = (sumY - slope * sumX) / n

        Log.d("CalibrationScreen", "✅ Computed Regression -> E₀: $e0, Slope: $slope")
        return e0 to slope
    }

    fun computeConcentration(compound: String, voltage: Double, e0: Double?, slope: Double?, useCalibration: Boolean): Double {
        if (voltage < 0 && compound != "Ammonia") {
            Log.e("HomeScreen", "⚠️ Skipping $compound: Negative voltage ($voltage V)")
            return Double.NaN  // ✅ Prevent calculations on negative voltage
        }

        return if (useCalibration && e0 != null && slope != null && slope != 0.0) {
            val calculatedValue = (voltage - e0) / slope
            if (calculatedValue.isNaN() || calculatedValue.isInfinite()) {
                Log.e("HomeScreen", "❌ Invalid concentration for $compound")
                Double.NaN
            } else {
                10.0.pow(calculatedValue)  // ✅ Correctly compute concentration
            }
        } else {
            defaultRegression(compound, voltage)  // ✅ Use default regression if calibration is missing
        }
    }

    fun fetchLatestVoltageForCompound(compound: String, concentration: String) {
        val compoundPath = compound.lowercase()
        database.child("sensorData/latest/$compoundPath")
            .addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    measuredVoltage = snapshot.getValue(Double::class.java) ?: 0.0
                    isLoading = false
                    Log.d("CalibrationScreen", "📌 Voltage for $compound: $measuredVoltage V")

                    val concentrationValue = try {
                        log10(concentration.replace("uM", "").toDouble())
                    } catch (e: Exception) {
                        Log.e("CalibrationScreen", "❌ Invalid concentration format: $concentration")
                        return
                    }

                    calibrationData = calibrationData + Pair(concentrationValue, measuredVoltage)
                    if (calibrationData.size > 3) calibrationData = calibrationData.drop(1)

                    if (calibrationData.size == 3) {
                        val (newE0, newSlope) = performLinearRegression(calibrationData)
                        if (newSlope != fetchedSlope || newE0 != fetchedE0) {
                            fetchedE0 = newE0
                            fetchedSlope = newSlope
                            database.child("calibration/${compound.lowercase()}").child("E0").setValue(newE0)
                            database.child("calibration/${compound.lowercase()}").child("Slope").setValue(newSlope)
                        }
                    }

                    calculatedConcentration = computeConcentration(selectedCompound, measuredVoltage, fetchedE0, fetchedSlope, true)
                    isSafe = !calculatedConcentration.isNaN() && calculatedConcentration < (WHO_LIMITS[selectedCompound] ?: Double.MAX_VALUE)
                }

                override fun onCancelled(error: DatabaseError) {
                    isLoading = false
                    Log.e("CalibrationScreen", "❌ Error fetching voltage: ${error.message}")
                }
            })
    }

    fun requestMeasurement(compound: String) {
        isLoading = true
        database.child("fetch").setValue(true)  // ✅ Request measurement for ALL compounds

        Handler(Looper.getMainLooper()).postDelayed({
            fetchLatestVoltageForCompound(compound, selectedConcentration)  // ✅ Read only selected compound after 30 sec
        }, 30000)  // ✅ Wait 30 seconds before fetching
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("Calibration Mode", style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(16.dp))

        DropdownMenuSelector(
            options = listOf("Nitrate", "Ammonia", "Chlorine"),
            selectedOption = selectedCompound,
            onOptionSelected = {
                selectedCompound = it
                selectedConcentration = concentrationOptions.first()
            }
        )

        Spacer(Modifier.height(16.dp))

        DropdownMenuSelector(
            options = concentrationOptions,
            selectedOption = selectedConcentration,
            onOptionSelected = { selectedConcentration = it }
        )

        Spacer(Modifier.height(16.dp))

        Button(
            onClick = {
                requestMeasurement(selectedCompound)
            },
            modifier = Modifier.fillMaxWidth(),
            enabled = !isLoading
        ) {
            Text(if (isLoading) "Measuring..." else "Fetch Measurement")
        }

        Spacer(Modifier.height(16.dp))

        Text("Measured Voltage: ${"%.3f".format(measuredVoltage)} V")
        Text("E₀: ${"%.3f".format(fetchedE0)} mV")
        Text("Slope: ${"%.3f".format(fetchedSlope)} mV/decade")
        Text("Concentration: ${"%.3f".format(calculatedConcentration)} µM")

        Spacer(Modifier.height(16.dp))

        Button(
            onClick = {
                navController.navigate("home") {
                    popUpTo("home") { inclusive = true }
                }
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Return to Home")
        }


    }
}

@Composable
fun CalibrationPromptDialog(
    onUseCalibration: () -> Unit,
    onUseDefault: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onUseDefault, // ✅ Default regression if dismissed
        title = { Text("Calibration Required") },
        text = {
            Column {
                Text("Would you like to calibrate your sensor?")
                Text("*Tip: Calibration is recommended for accurate measurements.*",
                    style = MaterialTheme.typography.bodySmall)
            }
        },
        confirmButton = {
            TextButton(onClick = onUseCalibration) {
                Text("Use Calibration")
            }
        },
        dismissButton = {
            TextButton(onClick = onUseDefault) {
                Text("Use Default")
            }
        }
    )
}
