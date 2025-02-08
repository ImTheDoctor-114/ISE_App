package com.example.ise_app

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
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
                val navController = rememberNavController()
                NavHost(navController, startDestination = "home") {
                    composable("home") { HomeScreen(navController) }
                    composable("calibration") { CalibrationScreen(navController) }
                    composable("history") { HistoryScreen(navController) }
                }
            }
        }
    }
}

@Composable
fun HomeScreen(navController: NavController) {
    val database = FirebaseDatabase.getInstance().reference.child("sensorData/latest")
    var chlorine by remember { mutableStateOf(0.0) }
    var ammonia by remember { mutableStateOf(0.0) }
    var nitrate by remember { mutableStateOf(0.0) }
    var temperature by remember { mutableStateOf(0.0) }

    LaunchedEffect(Unit) {
        database.addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                chlorine = snapshot.child("chlorine").getValue(Double::class.java) ?: 0.0
                ammonia = snapshot.child("ammonia").getValue(Double::class.java) ?: 0.0
                nitrate = snapshot.child("nitrate").getValue(Double::class.java) ?: 0.0
                temperature = snapshot.child("temperature").getValue(Double::class.java) ?: 0.0
            }

            override fun onCancelled(error: DatabaseError) {
                // Handle error
            }
        })
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Text("Water Quality Monitoring", style = MaterialTheme.typography.headlineLarge)
        Spacer(Modifier.height(16.dp))

        Text("Chlorine: $chlorine V", style = MaterialTheme.typography.bodyLarge)
        Text("Ammonia: $ammonia V", style = MaterialTheme.typography.bodyLarge)
        Text("Nitrate: $nitrate V", style = MaterialTheme.typography.bodyLarge)
        Text("Temperature: $temperature °C", style = MaterialTheme.typography.bodyLarge)

        Spacer(Modifier.height(24.dp))

        Button(onClick = { navController.navigate("calibration") }, modifier = Modifier.fillMaxWidth()) {
            Text("Go to Calibration")
        }

        Spacer(Modifier.height(16.dp))

        Button(onClick = { navController.navigate("history") }, modifier = Modifier.fillMaxWidth()) {
            Text("View History")
        }

        Spacer(Modifier.height(16.dp))

        val context = LocalContext.current
        Button(
            onClick = {
                shareData(context, chlorine, ammonia, nitrate, temperature)
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Share Data")
        }
    }
}

fun shareData(context: android.content.Context, chlorine: Double, ammonia: Double, nitrate: Double, temperature: Double) {
    val shareIntent = Intent().apply {
        action = Intent.ACTION_SEND
        putExtra(Intent.EXTRA_TEXT, "Water Quality Data:\nChlorine: $chlorine V\nAmmonia: $ammonia V\nNitrate: $nitrate V\nTemperature: $temperature °C")
        type = "text/plain"
    }
    context.startActivity(Intent.createChooser(shareIntent, "Share via"))
}

@Composable
fun CalibrationScreen(navController: NavController) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Text("Calibration Mode", style = MaterialTheme.typography.headlineLarge)
        Spacer(Modifier.height(16.dp))

        Text("Adjust Calibration Here...", style = MaterialTheme.typography.bodyLarge)

        Spacer(Modifier.height(24.dp))
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

                    val entry = "[$timestamp]\nChlorine: $chlorine V, Ammonia: $ammonia V, Nitrate: $nitrate V, Temperature: $temperature °C"
                    tempList.add(entry)
                }
                historyList = tempList.reversed() // Show newest first
            }

            override fun onCancelled(error: DatabaseError) {
                // Handle error
            }
        })
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Text("Sensor Data History", style = MaterialTheme.typography.headlineLarge)
        Spacer(Modifier.height(16.dp))

        LazyColumn {
            items(historyList) { entry ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(8.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(entry, style = MaterialTheme.typography.bodyLarge)
                    }
                }
            }
        }

        Spacer(Modifier.height(24.dp))
        Button(onClick = { navController.navigateUp() }, modifier = Modifier.fillMaxWidth()) {
            Text("Back")
        }
    }
}
