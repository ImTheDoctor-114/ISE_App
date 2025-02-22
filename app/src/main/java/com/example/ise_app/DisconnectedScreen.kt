package com.example.ise_app

import android.util.Log
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import java.time.ZoneOffset
import java.time.ZonedDateTime

@Composable
fun DisconnectedScreen(navController: NavController) {
    var isConnected by remember { mutableStateOf(false) }
    var hasNavigated by remember { mutableStateOf(false) }  // Prevents multiple navigation calls
    val database = FirebaseDatabase.getInstance()
    val deviceStatusRef = database.getReference("/deviceStatus/lastSeen")

    LaunchedEffect(Unit) {
        hasNavigated = false  // Reset navigation flag on screen entry
    }

    DisposableEffect(Unit) {
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val lastSeenTimestamp = snapshot.getValue(Long::class.java) ?: 0L
                val currentTimestampInIST = ZonedDateTime.now(ZoneOffset.ofHoursMinutes(5, 30)).toEpochSecond()

                // ✅ Corrected Disconnection Logic
                val timeDifference = currentTimestampInIST - lastSeenTimestamp
                isConnected = timeDifference <= 3  // ✅ ESP32 is connected if lastSeen is recent

                Log.d(
                    "DisconnectedScreen",
                    "Last seen (IST): $lastSeenTimestamp, Current (IST): $currentTimestampInIST, Diff: $timeDifference, Connected: $isConnected"
                )

                // ✅ Navigate back to home if reconnected (Prevent Infinite Loop)
                if (isConnected && !hasNavigated) {
                    hasNavigated = true  // ✅ Set flag first
                    Log.d("DisconnectedScreen", "✅ Device Reconnected - Navigating to Home")

                    deviceStatusRef.removeEventListener(this)  // ✅ Remove listener before navigating

                    navController.navigate("home") {
                        popUpTo("disconnected") { inclusive = true }
                    }
                }
            }

            override fun onCancelled(error: DatabaseError) {
                Log.e("DisconnectedScreen", "Firebase error: ${error.message}")
            }
        }

        deviceStatusRef.addValueEventListener(listener)

        onDispose {
            deviceStatusRef.removeEventListener(listener)  // Ensure listener cleanup
        }
    }

    // UI: Display Disconnected Message & Retry Button
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(text = "Device Disconnected", fontSize = 24.sp, color = MaterialTheme.colorScheme.error)
        Text("Please reconnect the device to continue.", fontSize = 16.sp)

        Button(
            onClick = {
                if (isConnected) {
                    Log.d("DisconnectedScreen", "✅ Device reconnected - Navigating to Home")
                    navController.navigate("home") {
                        popUpTo("disconnected") { inclusive = true }
                    }
                } else {
                    Log.d("DisconnectedScreen", "❌ Device still disconnected.")
                }
            }
        ) {
            Text("Retry Connection")
        }
    }
}
