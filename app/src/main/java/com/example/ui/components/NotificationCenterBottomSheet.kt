package com.example.ui.components

import com.example.util.AppStrings



import androidx.compose.foundation.background

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

import androidx.compose.foundation.rememberScrollState

import androidx.compose.foundation.shape.CircleShape

import androidx.compose.foundation.shape.RoundedCornerShape

import androidx.compose.foundation.verticalScroll

import androidx.compose.material.icons.Icons

import androidx.compose.material.icons.filled.Close

import androidx.compose.material.icons.filled.Notifications

import androidx.compose.material.icons.filled.NotificationsActive

import androidx.compose.material.icons.filled.Stars

import androidx.compose.material.icons.filled.Warning

import androidx.compose.material3.Card

import androidx.compose.material3.CardDefaults

import androidx.compose.material3.ExperimentalMaterial3Api

import androidx.compose.material3.Icon

import androidx.compose.material3.IconButton

import androidx.compose.material3.MaterialTheme

import androidx.compose.material3.ModalBottomSheet

import androidx.compose.material3.Surface

import androidx.compose.material3.Text

import androidx.compose.material3.rememberModalBottomSheetState

import androidx.compose.runtime.Composable

import androidx.compose.ui.Alignment

import androidx.compose.ui.Modifier

import androidx.compose.ui.graphics.Color

import androidx.compose.ui.platform.testTag

import androidx.compose.ui.text.font.FontWeight

import androidx.compose.ui.unit.dp

import androidx.compose.ui.unit.sp

import com.example.ui.theme.ExpenseRed

import com.example.ui.theme.FinSmartPrimary

import com.example.ui.theme.GoldAccent



data class NotificationItem(

    val title: String,

    val message: String,

    val timeAgo: String,

    val isWarning: Boolean = false

)



@OptIn(ExperimentalMaterial3Api::class)

@Composable

fun NotificationCenterBottomSheet(

    notifications: List<NotificationItem>,

    onDismiss: () -> Unit

) {

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)



    ModalBottomSheet(

        onDismissRequest = onDismiss,

        sheetState = sheetState,

        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),

        containerColor = MaterialTheme.colorScheme.surface,

        modifier = Modifier.testTag("notification_center_modal")

    ) {

        Column(

            modifier = Modifier

                .fillMaxWidth()

                .padding(horizontal = 20.dp)

                .padding(bottom = 32.dp)

                .verticalScroll(rememberScrollState()),

            horizontalAlignment = Alignment.CenterHorizontally

        ) {

            // Header

            Row(

                modifier = Modifier.fillMaxWidth(),

                horizontalArrangement = Arrangement.SpaceBetween,

                verticalAlignment = Alignment.CenterVertically

            ) {

                Row(verticalAlignment = Alignment.CenterVertically) {

                    Box(

                        modifier = Modifier

                            .size(36.dp)

                            .background(FinSmartPrimary.copy(alpha = 0.15f), CircleShape),

                        contentAlignment = Alignment.Center

                    ) {

                        Icon(

                            imageVector = Icons.Default.NotificationsActive,

                            contentDescription = null,

                            tint = FinSmartPrimary,

                            modifier = Modifier.size(20.dp)

                        )

                    }

                    Spacer(modifier = Modifier.width(10.dp))

                    Column {

                        Text(

                            text = AppStrings.t("Pusat Notifikasi DompetNalar", "DompetNalar Notification Center"),

                            fontWeight = FontWeight.Bold,

                            fontSize = 15.sp,

                            color = MaterialTheme.colorScheme.onSurface

                        )

                        Text(

                            text = AppStrings.t("Riwayat peringatan & pengingat otomatis", "History of automated alerts & reminders"),

                            fontSize = 11.sp,

                            color = MaterialTheme.colorScheme.onSurfaceVariant

                        )

                    }

                }



                IconButton(onClick = onDismiss) {

                    Icon(Icons.Default.Close, contentDescription = "Tutup")

                }

            }



            Spacer(modifier = Modifier.height(16.dp))



            if (notifications.isEmpty()) {

                Card(

                    modifier = Modifier.fillMaxWidth(),

                    shape = RoundedCornerShape(16.dp),

                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))

                ) {

                    Column(

                        modifier = Modifier

                            .fillMaxWidth()

                            .padding(24.dp),

                        horizontalAlignment = Alignment.CenterHorizontally

                    ) {

                        Icon(Icons.Default.Notifications, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(32.dp))

                        Spacer(modifier = Modifier.height(8.dp))

                        Text("Belum Ada Notifikasi Baru", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)

                    }

                }

            } else {

                notifications.forEach { item ->

                    Card(

                        modifier = Modifier

                            .fillMaxWidth()

                            .padding(vertical = 4.dp),

                        shape = RoundedCornerShape(14.dp),

                        colors = CardDefaults.cardColors(

                            containerColor = if (item.isWarning) ExpenseRed.copy(alpha = 0.08f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)

                        )

                    ) {

                        Row(

                            modifier = Modifier.padding(12.dp),

                            verticalAlignment = Alignment.Top

                        ) {

                            Box(

                                modifier = Modifier

                                    .size(32.dp)

                                    .background(

                                        if (item.isWarning) ExpenseRed.copy(alpha = 0.2f) else FinSmartPrimary.copy(alpha = 0.15f),

                                        CircleShape

                                    ),

                                contentAlignment = Alignment.Center

                            ) {

                                Icon(

                                    imageVector = if (item.isWarning) Icons.Default.Warning else Icons.Default.Stars,

                                    contentDescription = null,

                                    tint = if (item.isWarning) ExpenseRed else FinSmartPrimary,

                                    modifier = Modifier.size(16.dp)

                                )

                            }



                            Spacer(modifier = Modifier.width(10.dp))



                            Column(modifier = Modifier.weight(1f)) {

                                Row(

                                    modifier = Modifier.fillMaxWidth(),

                                    horizontalArrangement = Arrangement.SpaceBetween,

                                    verticalAlignment = Alignment.Top

                                ) {

                                    Text(

                                        text = item.title,

                                        fontWeight = FontWeight.Bold,

                                        fontSize = 13.sp,

                                        color = if (item.isWarning) ExpenseRed else MaterialTheme.colorScheme.onSurface,

                                        modifier = Modifier.weight(1f, fill = false)

                                    )

                                    Spacer(modifier = Modifier.width(8.dp))

                                    Text(

                                        text = item.timeAgo,

                                        fontSize = 10.sp,

                                        maxLines = 1,

                                        softWrap = false,

                                        color = MaterialTheme.colorScheme.onSurfaceVariant

                                    )

                                }

                                Spacer(modifier = Modifier.height(2.dp))

                                Text(

                                    text = item.message,

                                    fontSize = 11.sp,

                                    color = MaterialTheme.colorScheme.onSurfaceVariant

                                )

                            }

                        }

                    }

                }

            }

        }

    }

}

