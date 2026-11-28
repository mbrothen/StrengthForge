package com.example.data.analytics

import java.util.Locale
import kotlin.math.abs
import kotlin.math.round

data class PlateCount(
    val weightLbs: Double,
    val countPerSide: Int
)

data class PlateCalculationResult(
    val targetWeightLbs: Double,
    val barWeightLbs: Double,
    val achievableWeightLbs: Double,
    val platesPerSide: List<PlateCount>,
    val remainderLbs: Double,
    val unit: WeightUnit = WeightUnit.LBS
) {
    fun formatPerSideSummary(): String {
        val unitSuffix = unit.shortLabel
        if (targetWeightLbs <= barWeightLbs) return "Empty bar (${formatPlate(barWeightLbs)} $unitSuffix)"
        if (platesPerSide.isEmpty()) return "Empty bar (${formatPlate(barWeightLbs)} $unitSuffix)"
        return platesPerSide.joinToString(" + ") { pc ->
            if (pc.countPerSide > 1) "${pc.countPerSide}×${formatPlate(pc.weightLbs)}" else formatPlate(pc.weightLbs)
        } + " per side"
    }

    companion object {
        fun formatPlate(w: Double): String {
            val rounded = round(w * 100.0) / 100.0
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
    }
}

object PlateCalculator {
    const val DEFAULT_BAR_LBS = 45.0
    const val DEFAULT_BAR_KG = 20.0

    val STANDARD_PLATES = listOf(45.0, 35.0, 25.0, 10.0, 5.0, 2.5)
    val STANDARD_PLATES_KG = listOf(25.0, 20.0, 15.0, 10.0, 5.0, 2.5, 1.25)

    fun availableBarsForUnit(unit: WeightUnit): List<Double> {
        return when (unit) {
            WeightUnit.LBS -> listOf(45.0, 35.0, 15.0, 0.0)
            WeightUnit.KG -> listOf(20.0, 15.0, 7.0, 0.0)
        }
    }

    fun calculatePlatesForUnit(
        targetDisplayWeight: Double,
        barDisplayWeight: Double = WeightUnit.KG.defaultBarWeight,
        unit: WeightUnit
    ): PlateCalculationResult {
        val plates = when (unit) {
            WeightUnit.LBS -> STANDARD_PLATES
            WeightUnit.KG -> STANDARD_PLATES_KG
        }
        return calculatePlates(
            targetWeightLbs = targetDisplayWeight,
            barWeightLbs = barDisplayWeight,
            availablePlates = plates,
            unit = unit
        )
    }

    fun calculatePlates(
        targetWeightLbs: Double,
        barWeightLbs: Double = 45.0,
        availablePlates: List<Double> = STANDARD_PLATES,
        unit: WeightUnit = WeightUnit.LBS
    ): PlateCalculationResult {
        val safeBar = barWeightLbs.coerceAtLeast(0.0)
        val safeTarget = targetWeightLbs.coerceAtLeast(0.0)
        if (safeTarget <= safeBar) {
            return PlateCalculationResult(
                targetWeightLbs = safeTarget,
                barWeightLbs = safeBar,
                achievableWeightLbs = safeBar,
                platesPerSide = emptyList(),
                remainderLbs = 0.0,
                unit = unit
            )
        }

        var remainingPerSide = (safeTarget - safeBar) / 2.0
        val sortedPlates = availablePlates.filter { it > 0.0 }.sortedDescending()
        val perSide = mutableListOf<PlateCount>()

        for (plate in sortedPlates) {
            val count = ((remainingPerSide + 1e-6) / plate).toInt()
            if (count > 0) {
                perSide.add(PlateCount(weightLbs = plate, countPerSide = count))
                remainingPerSide -= count * plate
            }
        }

        val loadedTotal = safeBar + (perSide.sumOf { it.weightLbs * it.countPerSide } * 2.0)
        val remainder = round((safeTarget - loadedTotal) * 100.0) / 100.0
        return PlateCalculationResult(
            targetWeightLbs = safeTarget,
            barWeightLbs = safeBar,
            achievableWeightLbs = round(loadedTotal * 100.0) / 100.0,
            platesPerSide = perSide,
            remainderLbs = remainder,
            unit = unit
        )
    }
}
