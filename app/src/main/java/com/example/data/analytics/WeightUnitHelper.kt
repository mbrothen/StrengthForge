package com.example.data.analytics

import java.text.NumberFormat
import java.util.Locale
import kotlin.math.abs
import kotlin.math.round

enum class WeightUnit(
    val id: String,
    val label: String,
    val shortLabel: String,
    val defaultBarWeight: Double,
    val incrementStep: Double
) {
    LBS(
        id = "LBS",
        label = "Pounds (lbs)",
        shortLabel = "lbs",
        defaultBarWeight = 45.0,
        incrementStep = 5.0
    ),
    KG(
        id = "KG",
        label = "Kilograms (kg)",
        shortLabel = "kg",
        defaultBarWeight = 20.0,
        incrementStep = 2.5
    );

    companion object {
        fun fromString(value: String?): WeightUnit {
            return entries.firstOrNull { it.id.equals(value?.trim(), ignoreCase = true) } ?: LBS
        }
    }
}

object WeightUnitHelper {
    const val LBS_PER_KG = 2.20462262

    fun fromLbs(weightLbs: Double, unit: WeightUnit): Double {
        if (unit == WeightUnit.LBS) return weightLbs
        val rawKg = weightLbs / LBS_PER_KG
        val roundedTwo = round(rawKg * 100.0) / 100.0
        // Snap common metric increments cleanly so 60 kg -> lbs -> kg stays 60.0 instead of 59.99
        val quarterKg = round(rawKg * 4.0) / 4.0
        return if (abs(rawKg - quarterKg) < 0.03) {
            quarterKg
        } else {
            round(roundedTwo * 10.0) / 10.0
        }
    }

    fun toLbs(displayWeight: Double, unit: WeightUnit): Double {
        if (unit == WeightUnit.LBS) return displayWeight
        val rawLbs = displayWeight * LBS_PER_KG
        return round(rawLbs * 100.0) / 100.0
    }

    fun formatDisplayNumber(value: Double): String {
        val rounded = round(value * 100.0) / 100.0
        if (abs(rounded - rounded.toLong().toDouble()) < 0.005) {
            return rounded.toLong().toString()
        }
        val oneDecimal = round(rounded * 10.0) / 10.0
        return if (abs(rounded - oneDecimal) < 0.005) {
            String.format(Locale.US, "%.1f", oneDecimal)
        } else {
            String.format(Locale.US, "%.2f", rounded).trimEnd('0').trimEnd('.')
        }
    }

    fun formatWeightNumber(weightLbs: Double, unit: WeightUnit): String {
        val converted = fromLbs(weightLbs, unit)
        return formatDisplayNumber(converted)
    }

    fun formatWeightWithUnit(weightLbs: Double, unit: WeightUnit): String {
        return "${formatWeightNumber(weightLbs, unit)} ${unit.shortLabel}"
    }

    fun formatVolumeNumber(volumeLbs: Double, unit: WeightUnit): Int {
        return round(fromLbs(volumeLbs, unit)).toInt()
    }

    fun formatVolumeWithUnit(volumeLbs: Double, unit: WeightUnit): String {
        val fmt = NumberFormat.getNumberInstance(Locale.US)
        return "${fmt.format(formatVolumeNumber(volumeLbs, unit))} ${unit.shortLabel}"
    }
}
