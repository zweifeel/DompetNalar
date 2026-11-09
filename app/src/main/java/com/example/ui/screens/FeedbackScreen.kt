package com.example.ui.screens
import com.example.util.AppStrings

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Feedback
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.data.model.User
import com.example.ui.theme.FinSmartPrimary
import com.example.ui.theme.GoldAccent
import com.example.ui.viewmodel.FeedbackViewModel
import com.example.util.LocaleHelper

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FeedbackScreen(
    currentUser: User?,
    onNavigateBack: () -> Unit,
    feedbackViewModel: FeedbackViewModel = viewModel(),
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var rating by remember { mutableIntStateOf(5) }
    var selectedCategory by remember { mutableStateOf("Saran Fitur") }
    var feedbackMessage by remember { mutableStateOf("") }

    val isSubmitting by feedbackViewModel.isSubmitting.collectAsState()
    val submissionMessage by feedbackViewModel.submissionSuccess.collectAsState()

    val isEnglish = LocaleHelper.isEnglish()
    val categories = listOf(AppStrings.t("Bug / Kendala", "Bug / Issue"), AppStrings.t("Saran Fitur", "Feature Suggestion"), AppStrings.t("Desain / UI", "Design / UI"), AppStrings.t("Lainnya", "Other"))

    val userName = currentUser?.username ?: if (isEnglish) "DompetNalar User" else "Pengguna DompetNalar"
    val userEmail = currentUser?.email ?: "guest@dompetnalar.local"

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = if (isEnglish) "Send Feedback & Suggestions" else "Kirim Masukan & Saran",
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp
                    )
                },
                navigationIcon = {
                    IconButton(
                        onClick = onNavigateBack,
                        modifier = Modifier.testTag("btn_back_feedback")
                    ) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = if (isEnglish) "Back" else "Kembali"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.onBackground
                )
            )
        },
        modifier = modifier
            .fillMaxSize()
            .testTag("feedback_screen")
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Header card
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = FinSmartPrimary.copy(alpha = 0.10f))
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .background(FinSmartPrimary, CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Feedback,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(14.dp))
                        Column {
                            Text(
                                text = if (isEnglish) "Help Us Improve DompetNalar" else "Bantu Kami Menyempurnakan DompetNalar",
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = if (isEnglish) "Critiques, suggestions, and bug reports are reviewed directly by developers." else "Kritik, saran, maupun laporan kendala Anda dibaca langsung oleh developer.",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            // User Info Preview
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Text(
                            text = AppStrings.t("Pengirim Masukan", "Feedback Sender"),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "$userName • $userEmail",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }

            // Rating Stars
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = AppStrings.t("Bagaimana pengalaman Anda menggunakan DompetNalar?", "How was your experience using DompetNalar?"),
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            (1..5).forEach { star ->
                                val isSelected = star <= rating
                                Icon(
                                    imageVector = if (isSelected) Icons.Default.Star else Icons.Default.StarBorder,
                                    contentDescription = "Rating $star",
                                    tint = if (isSelected) GoldAccent else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                                    modifier = Modifier
                                        .size(36.dp)
                                        .clickable { rating = star }
                                        .testTag("star_rating_$star")
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = when (rating) {
                                5 -> "⭐⭐⭐⭐⭐ " + AppStrings.t("Luar Biasa!", "Outstanding!")
                                4 -> "⭐⭐⭐⭐ " + AppStrings.t("Sangat Bagus", "Very Good")
                                3 -> "⭐⭐⭐ " + AppStrings.t("Cukup Baik", "Good")
                                2 -> "⭐⭐ " + AppStrings.t("Perlu Peningkatan", "Needs Improvement")
                                else -> "⭐ " + AppStrings.t("Kurang Memuaskan", "Poor")
                            },
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = GoldAccent
                        )
                    }
                }
            }

            // Category Chips
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = AppStrings.t("Kategori Masukan", "Feedback Category"),
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            categories.forEach { cat ->
                                val isSelected = cat == selectedCategory
                                FilterChip(
                                    selected = isSelected,
                                    onClick = { selectedCategory = cat },
                                    label = { Text(cat, fontSize = 11.sp, fontWeight = FontWeight.SemiBold) },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = FinSmartPrimary,
                                        selectedLabelColor = Color.White
                                    )
                                )
                            }
                        }
                    }
                }
            }

            // Text Message Field
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = if (isEnglish) "Message, Suggestion, or Bug Details" else "Pesan, Saran, atau Detail Kendala",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "${feedbackMessage.length}/1000",
                                fontSize = 11.sp,
                                color = if (feedbackMessage.length >= 1000) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedTextField(
                            value = feedbackMessage,
                            onValueChange = { if (it.length <= 1000) feedbackMessage = it },
                            placeholder = {
                                Text(
                                    if (isEnglish)
                                        "Please provide detailed suggestions, bug reports, or feedback..."
                                    else
                                        "Tuliskan kritik, saran, atau kendala yang Anda alami secara detail di sini..."
                                )
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(130.dp)
                                .testTag("input_feedback_message"),
                            shape = RoundedCornerShape(12.dp)
                        )
                    }
                }
            }

            // Submit Button
            item {
                Button(
                    onClick = {
                        if (feedbackMessage.isBlank()) {
                            Toast.makeText(
                                context,
                                if (isEnglish) "Please enter your feedback message first" else "Silakan isi pesan masukan terlebih dahulu",
                                Toast.LENGTH_SHORT
                            ).show()
                            return@Button
                        }
                        feedbackViewModel.submitFeedback(
                            userName = userName,
                            userEmail = userEmail,
                            rating = rating,
                            category = selectedCategory,
                            message = feedbackMessage,
                            onResult = { isCloudSynced ->
                                if (isCloudSynced) {
                                    Toast.makeText(
                                        context,
                                        if (isEnglish) "Feedback sent and synchronized successfully." else "Feedback berhasil dikirim dan tersinkronisasi.",
                                        Toast.LENGTH_LONG
                                    ).show()
                                } else {
                                    Toast.makeText(
                                        context,
                                        if (isEnglish) "Feedback saved locally. Will be synchronized once network is available." else "Feedback tersimpan di perangkat, tetapi belum berhasil dikirim ke server. Silakan coba sinkronisasi kembali.",
                                        Toast.LENGTH_LONG
                                    ).show()
                                }
                                onNavigateBack()
                            },
                            onError = { _ ->
                                Toast.makeText(
                                    context,
                                    if (isEnglish) "Failed to save feedback. Please try again." else "Feedback gagal disimpan. Silakan coba lagi.",
                                    Toast.LENGTH_LONG
                                ).show()
                            }
                        )
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp)
                        .testTag("btn_submit_feedback"),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = FinSmartPrimary),
                    enabled = !isSubmitting
                ) {
                    if (isSubmitting) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp), color = Color.White, strokeWidth = 2.dp)
                    } else {
                        Icon(Icons.Default.Send, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (isEnglish) "Submit Feedback Now" else "Kirim Masukan Sekarang",
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        )
                    }
                }
                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }
}
