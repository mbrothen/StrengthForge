package com.example.data.analytics

import java.util.Locale

data class PlateCount(
    val weightLbs: Double,
    val countPerSide: Int
)

data class PlateCalculationResult(
    val targetWeightLbs: Double,
    val barWeightLbs: Double,
    val achievableWeightLbs: Double,
    val platesPerSide: List<PlateCount>,
    val remainderLbs: Double
) {
    fun formatPerSideSummary(): String {
        if (targetWeightLbs <= barWeightLbs) return "Empty bar (${formatPlate(barWeightLbs)} lbs)"
        if (platesPerSide.isEmpty()) return "Empty bar (${formatPlate(barWeightLbs)} lbs)"
        return platesPerSide.joinToString(" + ") { pc ->
            if (pc.countPerSide > 1) "${pc.countPerSide}×${formatPlate(pc.weightLbs)}" else formatPlate(pc.weightLbs)
        } + " per side"
    }

    companion object {
        fun formatPlate(w: Double): String {
            return if (w % 1.0 == 0.0) w.toInt().toString() else String.format(Locale.US, "%.1f", w)
        }
    }
}

object PlateCalculator {
    val STANDARD_PLATES = listOf(45.0, 35.0, 25.0, 10.0, 5.0, 2.5)

    fun calculatePlates(
        targetWeightLbs: Double,
        barWeightLbs: Double = 45.0,
        availablePlates: List<Double> = STANDARD_PLATES
    ): PlateCalculationResult {
        val safeBar = barWeightLbs.coerceAtLeast(0.0)
        val safeTarget = targetWeightLbs.coerceAtLeast(0.0)
        if (safeTarget <= safeBar) {
            return PlateCalculationResult(
                targetWeightLbs = safeTarget,
                barWeightLbs = safeBar,
                achievableWeightLbs = safeBar,
                platesPerSide = emptyList(),
                remainderLbs = 0.0
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
        val remainder = Math.round((safeTarget - loadedTotal) * 10.0) / 10.0
        return PlateCalculationResult(
            targetWeightLbs = safeTarget,
            barWeightLbs = safeBar,
            achievableWeightLbs = Math.round(loadedTotal * 10.0) / 10.0,
            platesPerSide = perSide,
            remainderLbs = remainder
        )
    }
}
