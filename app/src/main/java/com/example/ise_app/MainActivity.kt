package com.example.ise_app

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.ise_app.ui.theme.ISEAppTheme
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener

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
                title = { Text("ISE Parameters", color = MaterialTheme.colorScheme.onPrimary) },
                colors = TopAppBarDefaults.mediumTopAppBarColors(containerColor = MaterialTheme.colorScheme.primary)
            )
        }
    ) { paddingValues ->
        Box(modifier = Modifier.padding(paddingValues)) {
            NavHost(navController, startDestination = "home") {
                composable("home") { HomeScreen(navController) }
                composable("calibration") { CalibrationScreen(navController) }
                composable("history") { HistoryScreen(navController) }
            }
        }
    }
}

fun shareData(context: Context, chlorine: Double, ammonia: Double, nitrate: Double, temperature: Double) {
    val shareText = """
        ISE Sensor Data:
        - Chlorine: $chlorine ppm
        - Ammonia: $ammonia ppm
        - Nitrate: $nitrate ppm
        - Temperature: $temperature°C
    """.trimIndent()

    val sendIntent = Intent().apply {
        action = Intent.ACTION_SEND
        putExtra(Intent.EXTRA_TEXT, shareText)
        type = "text/plain"
    }

    val shareIntent = Intent.createChooser(sendIntent, "Share ISE Data")
    context.startActivity(shareIntent)
}

@Composable
fun HomeScreen(navController: NavController) {
    val database = FirebaseDatabase.getInstance().reference.child("sensorData/latest")
    val calibrationRef = FirebaseDatabase.getInstance().reference.child("calibration")

    var chlorine by remember { mutableDoubleStateOf(0.0) }
    var ammonia by remember { mutableDoubleStateOf(0.0) }
    var nitrate by remember { mutableDoubleStateOf(0.0) }
    var temperature by remember { mutableDoubleStateOf(0.0) }

    LaunchedEffect(Unit) {
        database.addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                chlorine = snapshot.child("chlorine").getValue(Double::class.java) ?: 0.0
                ammonia = snapshot.child("ammonia").getValue(Double::class.java) ?: 0.0
                nitrate = snapshot.child("nitrate").getValue(Double::class.java) ?: 0.0
                temperature = snapshot.child("temperature").getValue(Double::class.java) ?: 0.0
            }

            override fun onCancelled(error: DatabaseError) {}
        })

        calibrationRef.addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val chlorineOffset = snapshot.child("chlorineOffset").getValue(Double::class.java) ?: 0.0
                val ammoniaOffset = snapshot.child("ammoniaOffset").getValue(Double::class.java) ?: 0.0
                val nitrateOffset = snapshot.child("nitrateOffset").getValue(Double::class.java) ?: 0.0

                chlorine += chlorineOffset
                ammonia += ammoniaOffset
                nitrate += nitrateOffset
            }

            override fun onCancelled(error: DatabaseError) {}
        })
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.height(16.dp))

        Text("ISE Parameters", style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.onBackground)

        Spacer(Modifier.height(16.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(Modifier.padding(16.dp)) {
                Text("Chlorine: $chlorine ppm", style = MaterialTheme.typography.bodyLarge)
                Text("Ammonia: $ammonia ppm", style = MaterialTheme.typography.bodyLarge)
                Text("Nitrate: $nitrate ppm", style = MaterialTheme.typography.bodyLarge)
                Text("Temperature: $temperature °C", style = MaterialTheme.typography.bodyLarge)
            }
        }

        Spacer(Modifier.height(24.dp))

        Button(onClick = { navController.navigate("calibration") }, modifier = Modifier.fillMaxWidth()) {
            Text("Calibration Mode")
        }

        Spacer(Modifier.height(16.dp))

        Button(onClick = { navController.navigate("history") }, modifier = Modifier.fillMaxWidth()) {
            Text("View History")
        }

        Spacer(Modifier.height(16.dp))

        val context = LocalContext.current
        Button(
            onClick = { shareData(context, chlorine, ammonia, nitrate, temperature) },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Share Data")
        }
    }
}

@Composable
fun CalibrationScreen(navController: NavController) {
    val database = FirebaseDatabase.getInstance().reference.child("calibration")
    var chlorineOffset by remember { mutableStateOf("0.0") }
    var ammoniaOffset by remember { mutableStateOf("0.0") }
    var nitrateOffset by remember { mutableStateOf("0.0") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("Calibration Mode", style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.onBackground)

        Spacer(Modifier.height(16.dp))

        OutlinedTextField(
            value = chlorineOffset,
            onValueChange = { chlorineOffset = it },
            label = { Text("Chlorine Offset (ppm)") },
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(Modifier.height(8.dp))

        OutlinedTextField(
            value = ammoniaOffset,
            onValueChange = { ammoniaOffset = it },
            label = { Text("Ammonia Offset (ppm)") },
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(Modifier.height(8.dp))

        OutlinedTextField(
            value = nitrateOffset,
            onValueChange = { nitrateOffset = it },
            label = { Text("Nitrate Offset (ppm)") },
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(Modifier.height(16.dp))

        Button(
            onClick = {
                database.child("chlorineOffset").setValue(chlorineOffset.toDoubleOrNull() ?: 0.0)
                database.child("ammoniaOffset").setValue(ammoniaOffset.toDoubleOrNull() ?: 0.0)
                database.child("nitrateOffset").setValue(nitrateOffset.toDoubleOrNull() ?: 0.0)
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Save Calibration")
        }

        Spacer(Modifier.height(16.dp))

        Button(onClick = { navController.navigateUp() }, modifier = Modifier.fillMaxWidth()) {
            Text("Back")
        }
    }
}

@Composable
fun HistoryScreen(navController: NavController) {
    val database = FirebaseDatabase.getInstance().reference.child("sensorData")
    var historyList by remember { mutableStateOf(listOf<String>()) }

    LaunchedEffect(Unit) {
        database.orderByKey().limitToLast(10).addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val tempList = mutableListOf<String>()
                for (child in snapshot.children) {
                    val timestamp = child.key ?: "Unknown Time"
                    val chlorine = child.child("chlorine").getValue(Double::class.java) ?: 0.0
                    val ammonia = child.child("ammonia").getValue(Double::class.java) ?: 0.0
                    val nitrate = child.child("nitrate").getValue(Double::class.java) ?: 0.0
                    val temperature = child.child("temperature").getValue(Double::class.java) ?: 0.0

                    val entry = """
                        Time: $timestamp
                        - Chlorine: $chlorine ppm
                        - Ammonia: $ammonia ppm
                        - Nitrate: $nitrate ppm
                        - Temperature: $temperature °C
                    """.trimIndent()
                    tempList.add(entry)
                }
                historyList = tempList.reversed() // Show newest first
            }

            override fun onCancelled(error: DatabaseError) {}
        })
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Text("History", style = MaterialTheme.typography.headlineMedium, color = Color.Black)

        Spacer(Modifier.height(16.dp))

        if (historyList.isEmpty()) {
            Text("No history data available.", style = MaterialTheme.typography.bodyLarge)
        } else {
            LazyColumn {
                items(historyList) { entry ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        colors = CardDefaults.cardColors(containerColor = Color.White)
                    ) {
                        Text(entry, modifier = Modifier.padding(16.dp), style = MaterialTheme.typography.bodyLarge)
                    }
                }
            }
        }

        Spacer(Modifier.height(16.dp))

        Button(onClick = { navController.navigateUp() }, modifier = Modifier.fillMaxWidth()) {
            Text("Back")
        }
    }
}

