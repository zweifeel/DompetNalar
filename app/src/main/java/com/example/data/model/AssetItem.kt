package com.example.data.model

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Ignore
import androidx.room.Index
import androidx.room.PrimaryKey
import com.squareup.moshi.JsonClass
import java.util.UUID

enum class AssetCategory(val displayName: String, val annualDepreciationPercent: Double) {
    ELECTRONICS("Elektronik / Gadget", 20.0),
    VEHICLE("Kendaraan Motor/Mobil", 15.0),
    PROPERTY("Properti / Rumah", -3.0),
    GOLD_INVESTMENT("Emas & Tabungan", -5.0)
}

/**
 * DEF-03 (Room v7): [userId] is the owning user. `NULL` means ownership is unknown
 * (legacy v6 data) and must never be implicitly assigned to any user.
 */
@JsonClass(generateAdapter = true)
@Entity(
    tableName = "assets",
    foreignKeys = [
        ForeignKey(
            entity = User::class,
            parentColumns = ["id"],
            childColumns = ["userId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["userId"])]
)
data class AssetItem(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val name: String,
    val initialValue: Double,
    val purchaseYear: Int,
    val category: AssetCategory,
    val createdAt: Long = System.currentTimeMillis(),
    val userId: Long? = null
) {
    @get:Ignore
    val currentValue: Double
        get() {
            val currentYear = 2026
            val yearsPassed = (currentYear - purchaseYear).coerceAtLeast(0)
            val depreciationRate = category.annualDepreciationPercent / 100.0
            var valCalc = initialValue
            for (i in 0 until yearsPassed) {
                valCalc -= valCalc * depreciationRate
            }
            return valCalc.coerceAtLeast(0.0)
        }
}
