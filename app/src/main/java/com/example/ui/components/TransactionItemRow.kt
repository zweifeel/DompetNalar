package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.TransactionItem
import com.example.data.model.TransactionType
import com.example.ui.theme.ExpenseRed
import com.example.ui.theme.ExpenseRedDark
import com.example.ui.theme.FinSmartPrimary
import com.example.ui.theme.IncomeGreen
import com.example.ui.theme.IncomeGreenDark
import com.example.ui.theme.TransferBlue
import com.example.util.CurrencyFormatter
import androidx.compose.ui.text.style.TextOverflow

@Composable
fun TransactionItemRow(
    transaction: TransactionItem,
    isPrivacyMode: Boolean,
    onDelete: (TransactionItem) -> Unit,
    onClick: (TransactionItem) -> Unit = {},
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .clickable { onClick(transaction) }
            .testTag("tx_item_${transaction.id}"),
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(16.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Category Icon Circle
            val iconBg = when (transaction.type) {
                TransactionType.INCOME -> IncomeGreen.copy(alpha = 0.15f)
                TransactionType.EXPENSE -> ExpenseRed.copy(alpha = 0.15f)
                TransactionType.TRANSFER -> TransferBlue.copy(alpha = 0.15f)
            }
            val iconTint = when (transaction.type) {
                TransactionType.INCOME -> IncomeGreenDark
                TransactionType.EXPENSE -> ExpenseRedDark
                TransactionType.TRANSFER -> TransferBlue
            }

            Box(
                modifier = Modifier
                    .size(44.dp)
                    .background(iconBg, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (transaction.type == TransactionType.TRANSFER) {
                        Icons.Default.SwapHoriz
                    } else {
                        FinSmartIcons.getCategoryIcon(transaction.categoryIcon)
                    },
                    contentDescription = transaction.category,
                    tint = iconTint,
                    modifier = Modifier.size(22.dp)
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            // Center details column with flex weight
            Column(
                modifier = Modifier.weight(1f, fill = false)
            ) {
                Text(
                    text = transaction.title,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(2.dp))
                val subtitle = if (transaction.type == TransactionType.TRANSFER) {
                    "${transaction.walletName} → ${transaction.targetWalletName ?: "Dompet"} • ${CurrencyFormatter.formatRelativeDate(transaction.timestamp)}"
                } else {
                    "${transaction.category} • ${CurrencyFormatter.formatRelativeDate(transaction.timestamp)}"
                }
                Text(
                    text = subtitle,
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                if (transaction.note.isNotBlank() || transaction.tags.isNotBlank()) {
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = listOfNotNull(
                            transaction.note.takeIf { it.isNotBlank() },
                            transaction.tags.takeIf { it.isNotBlank() }
                        ).joinToString(" "),
                        fontSize = 10.sp,
                        color = MaterialTheme.colorScheme.primary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Spacer(modifier = Modifier.width(8.dp))

            // Right: Amount & Delete
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(
                    horizontalAlignment = Alignment.End
                ) {
                    val prefix = when (transaction.type) {
                        TransactionType.INCOME -> "+ "
                        TransactionType.EXPENSE -> "- "
                        TransactionType.TRANSFER -> "↔ "
                    }
                    val amountColor = when (transaction.type) {
                        TransactionType.INCOME -> IncomeGreen
                        TransactionType.EXPENSE -> ExpenseRed
                        TransactionType.TRANSFER -> FinSmartPrimary
                    }

                    Text(
                        text = if (isPrivacyMode) "Rp •••••" else prefix + CurrencyFormatter.formatRupiah(transaction.amount),
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        color = amountColor,
                        maxLines = 1,
                        softWrap = false
                    )
                    Text(
                        text = transaction.walletName,
                        fontSize = 10.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Spacer(modifier = Modifier.width(4.dp))

                IconButton(
                    onClick = { onDelete(transaction) },
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "Hapus Transaksi",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
    }
}
