package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.model.ExportFormatConfig
import com.example.util.ExportFormatManager
import com.example.util.InstallSourceHelper
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ExportFormatManagerTest {

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext<Context>()
        // Clear prefs before test
        context.getSharedPreferences("finsmart_export_formats", Context.MODE_PRIVATE).edit().clear().commit()
    }

    @Test
    fun testBuiltInFormatsAlwaysExistAndProtected() {
        val formats = ExportFormatManager.getAllFormats(context)
        assertTrue(formats.any { it.id == ExportFormatConfig.BUILTIN_PDF.id && it.isBuiltIn })
        assertTrue(formats.any { it.id == ExportFormatConfig.BUILTIN_CSV.id && it.isBuiltIn })

        // Attempting to delete built-in format must fail
        val deletedPdf = ExportFormatManager.deleteFormat(context, ExportFormatConfig.BUILTIN_PDF.id)
        assertFalse("Format built-in tidak boleh dihapus", deletedPdf)

        val deletedCsv = ExportFormatManager.deleteFormat(context, ExportFormatConfig.BUILTIN_CSV.id)
        assertFalse("Format built-in tidak boleh dihapus", deletedCsv)
    }

    @Test
    fun testAddEditDeleteCustomExportFormat() {
        val customFormat = ExportFormatConfig(
            id = "custom_test_1",
            name = "Laporan Pajak CSV",
            type = "CSV",
            isBuiltIn = false,
            delimiter = ";",
            dateFormat = "dd-MM-yyyy",
            columns = listOf("Tanggal", "Judul", "Nominal")
        )

        // Save
        val saved = ExportFormatManager.saveFormat(context, customFormat)
        assertTrue("Format kustom harus berhasil disimpan", saved)

        // Verify retrieval
        val allAfterSave = ExportFormatManager.getAllFormats(context)
        val retrieved = allAfterSave.find { it.id == "custom_test_1" }
        assertNotNull(retrieved)
        assertEquals("Laporan Pajak CSV", retrieved!!.name)
        assertEquals(";", retrieved.delimiter)
        assertEquals(3, retrieved.columns.size)

        // Switch active format
        ExportFormatManager.setActiveFormatId(context, "custom_test_1")
        assertEquals("custom_test_1", ExportFormatManager.getActiveFormatId(context))

        // Delete
        val deleted = ExportFormatManager.deleteFormat(context, "custom_test_1")
        assertTrue("Format kustom harus berhasil dihapus", deleted)

        // Verify deletion
        val allAfterDelete = ExportFormatManager.getAllFormats(context)
        assertFalse(allAfterDelete.any { it.id == "custom_test_1" })
        // Active format should reset to default built-in PDF
        assertEquals(ExportFormatConfig.BUILTIN_PDF.id, ExportFormatManager.getActiveFormatId(context))
    }

    @Test
    fun testInstallSourceDetectionExecution() {
        // Must execute cleanly without exception
        val isPlay = InstallSourceHelper.isInstalledFromGooglePlay(context)
        // In Robolectric without Play Store installer, it should be false
        assertFalse(isPlay)
    }
}
