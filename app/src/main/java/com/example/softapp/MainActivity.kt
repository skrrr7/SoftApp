package com.example.softapp

import androidx.health.connect.client.time.TimeRangeFilter
import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.records.WeightRecord
import androidx.health.connect.client.records.ExerciseSessionRecord
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.records.TotalCaloriesBurnedRecord
import androidx.health.connect.client.records.HeartRateRecord
import androidx.health.connect.client.units.Mass
import androidx.health.connect.client.units.Energy
import androidx.health.connect.client.request.ReadRecordsRequest
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZonedDateTime
import java.time.temporal.ChronoUnit
import kotlin.random.Random
import androidx.health.connect.client.records.metadata.Metadata



class MainActivity : AppCompatActivity() {

    private lateinit var healthClient: HealthConnectClient
    private lateinit var healthConnectManager: HealthConnectManager

    private val requestPermissions =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissions ->
            if (permissions.all { it.value }) {
                Toast.makeText(this, "✅ Health Connect permissions granted!", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "❌ Permissions denied!", Toast.LENGTH_SHORT).show()
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val availability = HealthConnectClient.getSdkStatus(this)

        if (availability == HealthConnectClient.SDK_AVAILABLE) {
            healthClient = HealthConnectClient.getOrCreate(this)
        } else {
            Toast.makeText(this, "❌ Health Connect is not available!", Toast.LENGTH_SHORT).show()
        }
        healthConnectManager = HealthConnectManager(this)

        val checkButton = findViewById<Button>(R.id.btnCheckHealthConnect)
        val insertExerciseButton = findViewById<Button>(R.id.btnInsertExercise) // Added new button
        val readWeightButton = findViewById<Button>(R.id.btnReadWeight)   // New button
        val readExerciseButton = findViewById<Button>(R.id.btnReadExercise) // New button

        checkButton.setOnClickListener { checkHealthConnectAvailability() }
        insertExerciseButton.setOnClickListener { requestPermissionAndInsertExerciseSession() } // Added click event
        readWeightButton.setOnClickListener { readWeightData() }  // Handle weight data retrieval
        readExerciseButton.setOnClickListener { readExerciseData() } // Handle exercise session retrieval
    }

    private fun checkHealthConnectAvailability() {
        val isAvailable = HealthConnectClient.getSdkStatus(this) == HealthConnectClient.SDK_AVAILABLE
        Toast.makeText(
            this,
            if (isAvailable) "✅ Health Connect is available!" else "❌ Health Connect is NOT available!",
            Toast.LENGTH_SHORT
        ).show()
    }

    private fun requestPermissionAndInsertExerciseSession() {
        lifecycleScope.launch {
            if (!healthConnectManager.hasAllPermissions()) {
                requestPermissions.launch(healthConnectManager.permissions.toTypedArray())
            } else {
                val startTime = ZonedDateTime.now().minusMinutes(30)
                val endTime = ZonedDateTime.now()
                writeExerciseSession(startTime, endTime)
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
        val records = listOf(weightRecord)
        try {
            healthClient.insertRecords(records)
            Toast.makeText(this, "✅ Successfully recorded weight!", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(this, "❌ Error inserting weight: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }


    private suspend fun writeExerciseSession(start: ZonedDateTime, end: ZonedDateTime) {
        try {
            healthClient.insertRecords(
                listOf(
                    ExerciseSessionRecord(
                        metadata = Metadata(), // Added metadata
                        startTime = start.toInstant(),
                        startZoneOffset = start.offset,
                        endTime = end.toInstant(),
                        endZoneOffset = end.offset,
                        exerciseType = ExerciseSessionRecord.EXERCISE_TYPE_RUNNING,
                        title = "My Run #${Random.nextInt(0, 60)}"
                    ),
                    StepsRecord(
                        metadata = Metadata(), // Added metadata
                        startTime = start.toInstant(),
                        startZoneOffset = start.offset,
                        endTime = end.toInstant(),
                        endZoneOffset = end.offset,
                        count = (1000 + 1000 * Random.nextInt(3)).toLong()
                    ),
                    TotalCaloriesBurnedRecord(
                        metadata = Metadata(), // Added metadata
                        startTime = start.toInstant(),
                        startZoneOffset = start.offset,
                        endTime = end.toInstant(),
                        endZoneOffset = end.offset,
                        energy = Energy.calories((140 + Random.nextInt(20)) * 0.01)
                    )
                ) + buildHeartRateSeries(start, end)
            )
            Toast.makeText(this, "✅ Successfully inserted exercise session!", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(this, "❌ Error inserting exercise session: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }


    private fun buildHeartRateSeries(
        sessionStartTime: ZonedDateTime,
        sessionEndTime: ZonedDateTime,
    ): HeartRateRecord {
        val samples = mutableListOf<HeartRateRecord.Sample>()
        var time = sessionStartTime
        while (time.isBefore(sessionEndTime)) {
            samples.add(
                HeartRateRecord.Sample(
                    time = time.toInstant(),
                    beatsPerMinute = (80 + Random.nextInt(80)).toLong()
                )
            )
            time = time.plusSeconds(30)
        }
        return HeartRateRecord(
            metadata = Metadata(), // Added metadata
            startTime = sessionStartTime.toInstant(),
            startZoneOffset = sessionStartTime.offset,
            endTime = sessionEndTime.toInstant(),
            endZoneOffset = sessionEndTime.offset,
            samples = samples
        )
    }private fun readWeightData() {
        lifecycleScope.launch {
            if (!healthConnectManager.hasAllPermissions()) {
                requestPermissions.launch(healthConnectManager.permissions.toTypedArray())
            } else {
                val start = Instant.now().minus(7, ChronoUnit.DAYS) // Last 7 days
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
                requestPermissions.launch(healthConnectManager.permissions.toTypedArray())
            } else {
                val start = Instant.now().minus(7, ChronoUnit.DAYS) // Last 7 days
                val end = Instant.now()
                val exerciseRecords = healthConnectManager.readExerciseSessions(start, end)

                if (exerciseRecords.isNotEmpty()) {
                    exerciseRecords.forEach { record ->
                        Log.d("HealthConnect", "Exercise: ${record.title} from ${record.startTime} to ${record.endTime}")
                    }
                    Toast.makeText(this@MainActivity, "✅ Exercise data retrieved!", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(this@MainActivity, "❌ No exercise data found!", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }


}
