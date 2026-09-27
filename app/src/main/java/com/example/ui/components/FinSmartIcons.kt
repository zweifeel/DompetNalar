package com.example.ui.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.CardGiftcard
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.DirectionsBus
import androidx.compose.material.icons.filled.LocalAtm
import androidx.compose.material.icons.filled.MedicalServices
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.Receipt
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.ShoppingBag
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material.icons.filled.Store
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.Wallet
import androidx.compose.material.icons.filled.Work
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector

object FinSmartIcons {

    fun getCategoryIcon(name: String): ImageVector {
        return when (name.lowercase()) {
            "restaurant", "food", "makanan & minuman", "makanan" -> Icons.Default.Restaurant
            "commute", "transport", "transportasi & bensin", "bensin" -> Icons.Default.DirectionsBus
            "shopping_bag", "shopping", "belanja & hobi", "belanja" -> Icons.Default.ShoppingBag
            "receipt", "bills", "tagihan & utilitas", "tagihan" -> Icons.Default.Receipt
            "medical_services", "health", "kesehatan" -> Icons.Default.MedicalServices
            "school", "education", "pendidikan" -> Icons.Default.School
            "movie", "entertainment", "hiburan & langganan" -> Icons.Default.Movie
            "payments", "salary", "gaji pokok", "gaji" -> Icons.Default.Payments
            "card_giftcard", "bonus", "bonus & tunjangan" -> Icons.Default.CardGiftcard
            "trending_up", "investasi & dividen", "investasi" -> Icons.AutoMirrored.Filled.TrendingUp
            "store", "penjualan / omset", "penjualan" -> Icons.Default.Store
            "swap_horiz", "transfer" -> Icons.Default.SwapHoriz
            else -> Icons.Default.Category
        }
    }

    fun getWalletIcon(name: String): ImageVector {
        return when (name.lowercase()) {
            "cash", "tunai" -> Icons.Default.LocalAtm
            "gopay", "ovo", "dana", "shopeepay", "linkaja", "ewallet" -> Icons.Default.PhoneAndroid
            "bca", "bri", "mandiri", "bni", "cimb", "jago", "jenius", "bank" -> Icons.Default.AccountBalance
            else -> Icons.Default.AccountBalanceWallet
        }
    }

    fun getWalletPresetColor(name: String): Color {
        return when (name.lowercase()) {
            "bca" -> Color(0xFF005DAA)
            "bri" -> Color(0xFF00529C)
            "mandiri" -> Color(0xFF003D79)
            "bni" -> Color(0xFFE55300)
            "gopay" -> Color(0xFF00AA13)
            "dana" -> Color(0xFF118EEA)
            "ovo" -> Color(0xFF4C2A86)
            "shopeepay" -> Color(0xFFEE4D2D)
            "cash", "tunai" -> Color(0xFF10B981)
            else -> Color(0xFF0B57D0)
        }
    }
}
