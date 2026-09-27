package com.example.util

import androidx.compose.ui.graphics.Color

data class WalletBrandInfo(
    val brandName: String,
    val badgeLabel: String,
    val brandColor: Color,
    val textColor: Color = Color.White
)

object WalletBrandHelper {

    fun detectBrand(walletName: String): WalletBrandInfo {
        val lower = walletName.lowercase().trim()
        return when {
            lower.contains("bca") -> WalletBrandInfo("BCA", "BCA", Color(0xFF00529C))
            lower.contains("mandiri") -> WalletBrandInfo("Bank Mandiri", "MANDIRI", Color(0xFF003366))
            lower.contains("bni") -> WalletBrandInfo("BNI", "BNI", Color(0xFFE55300))
            lower.contains("bri") -> WalletBrandInfo("BRI", "BRI", Color(0xFF00529C))
            lower.contains("cimb") -> WalletBrandInfo("CIMB Niaga", "CIMB", Color(0xFF7A0000))
            lower.contains("jago") -> WalletBrandInfo("Bank Jago", "JAGO", Color(0xFFFF6600))
            lower.contains("seabank") -> WalletBrandInfo("SeaBank", "SEABANK", Color(0xFF009384))
            lower.contains("blu") -> WalletBrandInfo("blu by BCA Digital", "BLU", Color(0xFF00B2FF))

            lower.contains("gopay") || lower.contains("go-pay") -> WalletBrandInfo("GoPay", "GOPAY", Color(0xFF00AED6))
            lower.contains("dana") -> WalletBrandInfo("DANA", "DANA", Color(0xFF118EEA))
            lower.contains("ovo") -> WalletBrandInfo("OVO", "OVO", Color(0xFF4C2A86))
            lower.contains("shopee") || lower.contains("spay") -> WalletBrandInfo("ShopeePay", "SHOPEE", Color(0xFFEE4D2D))
            lower.contains("linkaja") || lower.contains("link aja") -> WalletBrandInfo("LinkAja", "LINKAJA", Color(0xFFE31E25))

            lower.contains("cash") || lower.contains("tunai") || lower.contains("dompet") -> WalletBrandInfo("Tunai / Cash", "CASH", Color(0xFF16A34A))
            lower.contains("invest") || lower.contains("saham") || lower.contains("crypto") -> WalletBrandInfo("Investasi", "INVEST", Color(0xFF8B5CF6))

            else -> WalletBrandInfo(walletName.take(12), "BANK", Color(0xFF0B57D0))
        }
    }

    val popularBrands = listOf(
        WalletBrandInfo("BCA", "BCA", Color(0xFF00529C)),
        WalletBrandInfo("Bank Mandiri", "MANDIRI", Color(0xFF003366)),
        WalletBrandInfo("BNI", "BNI", Color(0xFFE55300)),
        WalletBrandInfo("BRI", "BRI", Color(0xFF00529C)),
        WalletBrandInfo("Bank Jago", "JAGO", Color(0xFFFF6600)),
        WalletBrandInfo("GoPay", "GOPAY", Color(0xFF00AED6)),
        WalletBrandInfo("DANA", "DANA", Color(0xFF118EEA)),
        WalletBrandInfo("OVO", "OVO", Color(0xFF4C2A86)),
        WalletBrandInfo("ShopeePay", "SHOPEE", Color(0xFFEE4D2D)),
        WalletBrandInfo("LinkAja", "LINKAJA", Color(0xFFE31E25)),
        WalletBrandInfo("Tunai / Cash", "CASH", Color(0xFF16A34A))
    )
}
