with open('app/src/main/java/com/example/ui/components/PayLaterBillReviewDialog.kt', 'r', encoding='utf-8') as f:
    text = f.read()

# 1. Define syncCurrentItem
old_build_final = """            val buildFinalResult: () -> PayLaterBillScanResult = {"""

# We can define syncCurrentItem right before navigation
old_nav_block = """            // Batch Navigation Bar (if multiple items)
            if (itemsList.size > 1) {
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedButton(
                        onClick = { if (activeIndex > 0) activeIndex-- },
                        enabled = activeIndex > 0,
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Sebelumnya", modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Sebelumnya", fontSize = 12.sp)
                    }
                    Text(
                        text = "Item ${activeIndex + 1}/${itemsList.size}",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    OutlinedButton(
                        onClick = { if (activeIndex < itemsList.size - 1) activeIndex++ },
                        enabled = activeIndex < itemsList.size - 1,
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text("Berikutnya", fontSize = 12.sp)
                        Spacer(modifier = Modifier.width(4.dp))
                        Icon(Icons.Default.ArrowForward, contentDescription = "Berikutnya", modifier = Modifier.size(16.dp))
                    }
                }
            }"""

new_nav_block = """            val syncCurrentItem: () -> Unit = {
                if (activeIndex in itemsList.indices) {
                    val curInstallment = installmentCurrentText.toIntOrNull()
                    val pCurrentBill = ReceiptCalculator.parseAmountSafe(currentBillText)
                    val pTotalDebt = ReceiptCalculator.parseAmountSafe(totalDebtText)
                    val updated = currentItem.copy(
                        providerName = providerName.trim(),
                        productName = productName.trim().ifBlank { null },
                        currentBillAmount = pCurrentBill,
                        totalRemainingAmount = pTotalDebt,
                        installmentCurrent = curInstallment,
                        installmentTotal = installmentTotalText.toIntOrNull(),
                        dueDate = formattedIsoDate,
                        dueDay = parsedDay,
                        dueMonth = parsedMonth,
                        dueYear = parsedYear,
                        dueIsoDate = formattedIsoDate.takeIf { isDateValid },
                        isDateAmbiguous = if (isDateConfirmed) false else isCurrentItemDateAmbiguous,
                        dateConfidenceStatus = if (isDateConfirmed) "USER_CONFIRMED" else currentItem.dateConfidenceStatus
                    )
                    itemsList[activeIndex] = updated
                }
            }

            // Batch Navigation Bar & Platform Groups (if multiple items)
            if (itemsList.size > 1) {
                Spacer(modifier = Modifier.height(6.dp))
                androidx.compose.foundation.lazy.LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    items(itemsList.size) { idx ->
                        val itm = itemsList[idx]
                        val isCurrent = idx == activeIndex
                        val pLabel = itm.providerName.ifBlank { "Item ${idx + 1}" }
                        FilterChip(
                            selected = isCurrent,
                            onClick = {
                                syncCurrentItem()
                                activeIndex = idx
                            },
                            label = {
                                Text(
                                    text = "${idx + 1}. $pLabel",
                                    fontSize = 11.sp,
                                    fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal
                                )
                            }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedButton(
                        onClick = {
                            syncCurrentItem()
                            if (activeIndex > 0) activeIndex--
                        },
                        enabled = activeIndex > 0,
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Sebelumnya", modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Sebelumnya", fontSize = 12.sp)
                    }
                    Text(
                        text = "Item ${activeIndex + 1}/${itemsList.size} (${currentItem.providerName.ifBlank { "Penyedia Tidak Dikenal" }})",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    OutlinedButton(
                        onClick = {
                            syncCurrentItem()
                            if (activeIndex < itemsList.size - 1) activeIndex++
                        },
                        enabled = activeIndex < itemsList.size - 1,
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text("Berikutnya", fontSize = 12.sp)
                        Spacer(modifier = Modifier.width(4.dp))
                        Icon(Icons.Default.ArrowForward, contentDescription = "Berikutnya", modifier = Modifier.size(16.dp))
                    }
                }
            }"""

text = text.replace('\r\n', '\n')
old_nav_block = old_nav_block.replace('\r\n', '\n')
new_nav_block = new_nav_block.replace('\r\n', '\n')

assert old_nav_block in text, "old_nav_block not found"
text = text.replace(old_nav_block, new_nav_block)

# 2. Add Save All Batch button if itemsList.size > 1
old_save_row = """                Button(
                    onClick = {
                        if (!canSave) {
                            if (!isDateValid) {
                                Toast.makeText(context, "Periksa kembali tanggal jatuh tempo yang valid.", Toast.LENGTH_SHORT).show()
                            } else if (!isDateConfirmed) {
                                Toast.makeText(context, "Harap centang konfirmasi tanggal jatuh tempo sebelum menyimpan.", Toast.LENGTH_SHORT).show()
                            } else if (providerName.isBlank()) {
                                Toast.makeText(context, "Nama penyedia layanan wajib diisi.", Toast.LENGTH_SHORT).show()
                            }
                            return@Button
                        }
                        val finalResult = buildFinalResult()
                        val debtToSave = finalResult.toDebtItem(userId = currentUserId, customCategory = selectedCategory)
                        onConfirmSave(debtToSave)
                        Toast.makeText(context, "Tagihan '${debtToSave.name}' disimpan.", Toast.LENGTH_SHORT).show()
                        val removedId = currentItem.id
                        itemsList.removeAt(activeIndex)
                        itemConfirmationMap.remove(removedId)
                        if (itemsList.isEmpty()) onDismiss()
                    },
                    enabled = canSave,
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (totInstallment != null && totInstallment > 1) MaterialTheme.colorScheme.secondary else FinSmartPrimary
                    ),
                    modifier = Modifier.weight(1.5f).testTag("btn_save_paylater_bill")
                ) {
                    Text(if (itemsList.size > 1) "Simpan Item Ini" else "Simpan Tagihan")
                }
            }"""

new_save_row = """                Button(
                    onClick = {
                        if (!canSave) {
                            if (!isDateValid) {
                                Toast.makeText(context, "Periksa kembali tanggal jatuh tempo yang valid.", Toast.LENGTH_SHORT).show()
                            } else if (!isDateConfirmed) {
                                Toast.makeText(context, "Harap centang konfirmasi tanggal jatuh tempo sebelum menyimpan.", Toast.LENGTH_SHORT).show()
                            } else if (providerName.isBlank()) {
                                Toast.makeText(context, "Nama penyedia layanan wajib diisi.", Toast.LENGTH_SHORT).show()
                            }
                            return@Button
                        }
                        val finalResult = buildFinalResult()
                        val debtToSave = finalResult.toDebtItem(userId = currentUserId, customCategory = selectedCategory)
                        onConfirmSave(debtToSave)
                        Toast.makeText(context, "Tagihan '${debtToSave.name}' disimpan.", Toast.LENGTH_SHORT).show()
                        val removedId = currentItem.id
                        itemsList.removeAt(activeIndex)
                        itemConfirmationMap.remove(removedId)
                        if (itemsList.isEmpty()) onDismiss()
                    },
                    enabled = canSave,
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (totInstallment != null && totInstallment > 1) MaterialTheme.colorScheme.secondary else FinSmartPrimary
                    ),
                    modifier = Modifier.weight(1.5f).testTag("btn_save_paylater_bill")
                ) {
                    Text(if (itemsList.size > 1) "Simpan Item Ini" else "Simpan Tagihan")
                }
            }

            if (itemsList.size > 1) {
                Spacer(modifier = Modifier.height(10.dp))
                Button(
                    onClick = {
                        syncCurrentItem()
                        val unconfirmed = itemsList.firstOrNull { it.isDateAmbiguous && itemConfirmationMap[it.id] != true }
                        if (unconfirmed != null) {
                            Toast.makeText(context, "Item '${unconfirmed.providerName.ifBlank { "Tagihan" }}' memerlukan konfirmasi tanggal jatuh tempo.", Toast.LENGTH_LONG).show()
                            return@Button
                        }
                        val blankProvider = itemsList.firstOrNull { it.providerName.isBlank() }
                        if (blankProvider != null) {
                            Toast.makeText(context, "Penyedia layanan untuk salah satu tagihan belum diisi.", Toast.LENGTH_LONG).show()
                            return@Button
                        }

                        val debtsToSave = mutableListOf<DebtItem>()
                        val plansToSave = mutableListOf<Pair<InstallmentPlanItem, List<InstallmentPeriod>>>()

                        itemsList.forEach { itm ->
                            if (itm.installmentTotal != null && itm.installmentTotal > 1) {
                                val plan = itm.toInstallmentPlanItem(userId = currentUserId)
                                if (plan != null) {
                                    val periods = itm.toInstallmentPeriods(plan.id, currentUserId ?: 1L)
                                    plansToSave.add(Pair(plan, periods))
                                } else {
                                    debtsToSave.add(itm.toDebtItem(userId = currentUserId))
                                }
                            } else {
                                debtsToSave.add(itm.toDebtItem(userId = currentUserId))
                            }
                        }

                        if (onSaveBatch != null) {
                            onSaveBatch(debtsToSave, plansToSave)
                        } else {
                            debtsToSave.forEach { onConfirmSave(it) }
                            plansToSave.forEach { (plan, periods) ->
                                onSaveAsInstallmentPlanWithPeriods?.invoke(plan, periods) ?: onSaveAsInstallmentPlan?.invoke(plan)
                            }
                        }
                        Toast.makeText(context, "Semua ${itemsList.size} tagihan berhasil disimpan!", Toast.LENGTH_SHORT).show()
                        itemsList.clear()
                        onDismiss()
                    },
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = FinSmartPrimary),
                    modifier = Modifier.fillMaxWidth().testTag("btn_save_all_batch")
                ) {
                    Text("Simpan Semua (${itemsList.size} Tagihan)", fontWeight = FontWeight.Bold)
                }
            }"""

old_save_row = old_save_row.replace('\r\n', '\n')
new_save_row = new_save_row.replace('\r\n', '\n')

assert old_save_row in text, "old_save_row not found"
text = text.replace(old_save_row, new_save_row)

with open('app/src/main/java/com/example/ui/components/PayLaterBillReviewDialog.kt', 'w', encoding='utf-8') as f:
    f.write(text.replace('\n', '\r\n'))

print("PayLaterBillReviewDialog.kt updated successfully")
