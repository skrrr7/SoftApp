package com.example.softapp

import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.records.*
import androidx.health.connect.client.units.Mass
import androidx.health.connect.client.units.Energy
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZonedDateTime
import java.time.temporal.ChronoUnit
import kotlin.random.Random
import androidx.health.connect.client.records.metadata.Metadata

class MainActivity : AppCompatActivity() {

    private lateinit var healthClient: HealthConnectClient
    private lateinit var healthConnectManager: HealthConnectManager
    private lateinit var requestPermissionsLauncher: ActivityResultLauncher<Set<String>>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Initialize healthConnectManager before usage
        healthConnectManager = HealthConnectManager(this)

        requestPermissionsLauncher = registerForActivityResult(
            healthConnectManager.requestPermissionsActivityContract()
        ) { grantedPermissions ->
            if (grantedPermissions.containsAll(healthConnectManager.permissions)) {
                Toast.makeText(this, "✅ Permissions granted!", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "❌ Permissions denied!", Toast.LENGTH_SHORT).show()
            }
        }

        val availability = HealthConnectClient.getSdkStatus(this)

        if (availability == HealthConnectClient.SDK_AVAILABLE) {
            healthClient = HealthConnectClient.getOrCreate(this)
        } else {
            Toast.makeText(this, "❌ Health Connect is not available!", Toast.LENGTH_SHORT).show()
            return
        }

        val checkButton = findViewById<Button>(R.id.btnCheckHealthConnect)
        val readWeightButton = findViewById<Button>(R.id.btnReadWeight)
        val readExerciseButton = findViewById<Button>(R.id.btnReadExercise)
        val btnRecordWeight = findViewById<Button>(R.id.btnRecordWeight)

        checkButton.setOnClickListener { checkHealthConnectAvailability() }
        readWeightButton.setOnClickListener { readWeightData() }
        readExerciseButton.setOnClickListener { readExerciseData() }
        btnRecordWeight.setOnClickListener {
            val weightInput = 70.0
            checkPermissionsAndWriteWeight(weightInput)
        }
    }

    private fun checkHealthConnectAvailability() {
        val isAvailable = HealthConnectClient.getSdkStatus(this) == HealthConnectClient.SDK_AVAILABLE
        Toast.makeText(
            this,
            if (isAvailable) "✅ Health Connect is available!" else "❌ Health Connect is NOT available!",
            Toast.LENGTH_SHORT
        ).show()
    }

    private fun checkPermissionsAndWriteWeight(weightInput: Double) {
        lifecycleScope.launch {
            if (!healthConnectManager.hasAllPermissions()) {
                requestPermissionsLauncher.launch(healthConnectManager.permissions)
            } else {
                writeWeightInput(weightInput)
            }
        }
    }

    private suspend fun writeWeightInput(weightInput: Double) {
        val time = ZonedDateTime.now().withNano(0)
        val weightRecord = WeightRecord(
            metadata = Metadata(),
            weight = Mass.kilograms(weightInput),
            time = time.toInstant(),
            zoneOffset = time.offset
        )
        try {
            healthClient.insertRecords(listOf(weightRecord))
            Toast.makeText(this, "✅ Successfully recorded weight!", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(this, "❌ Error inserting weight: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun readWeightData() {
        lifecycleScope.launch {
            if (!healthConnectManager.hasAllPermissions()) {
                requestPermissionsLauncher.launch(healthConnectManager.permissions)
            } else {
                val start = Instant.now().minus(7, ChronoUnit.DAYS)
                val end = Instant.now()
                val weightRecords = healthConnectManager.readWeightInputs(start, end)

                if (weightRecords.isNotEmpty()) {
                    weightRecords.forEach { record ->
                        Log.d("HealthConnect", "Weight: ${record.weight.inKilograms} kg at ${record.time}")
                    }
                    Toast.makeText(this@MainActivity, "✅ Weight data retrieved!", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(this@MainActivity, "❌ No weight data found!", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun readExerciseData() {
        lifecycleScope.launch {
            if (!healthConnectManager.hasAllPermissions()) {
                requestPermissionsLauncher.launch(healthConnectManager.permissions)
            } else {
                val start = Instant.now().minusSeconds(86400)
                val end = Instant.now()
                val exerciseRecords = healthConnectManager.readExerciseSessions(start, end)

                if (exerciseRecords.isNotEmpty()) {
                    exerciseRecords.forEach { record ->
                        Log.d("HealthConnect", "Exercise: ${record.title} from ${record.startTime} to ${record.endTime}")
                    }
                    Toast.makeText(this@MainActivity, "✅ Exercise data retrieved!", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(this@MainActivity, "❌ No exercise records found!", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
}
