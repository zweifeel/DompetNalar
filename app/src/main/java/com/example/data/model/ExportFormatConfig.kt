package com.example.data.model

data class ExportFormatConfig(
    val id: String,
    val name: String,
    val type: String, // "PDF" or "CSV"
    val isBuiltIn: Boolean = false,
    val delimiter: String = ",", // ",", ";", "\t", "|"
    val dateFormat: String = "yyyy-MM-dd HH:mm:ss",
    val columns: List<String> = DEFAULT_COLUMNS,
    val includeHeader: Boolean = true
) {
    companion object {
        val ALL_AVAILABLE_COLUMNS = listOf(
            "ID",
            "Tanggal",
            "Tipe",
            "Judul",
            "Kategori",
            "Sumber Dompet",
            "Tujuan Dompet",
            "Nominal",
            "Catatan",
            "Tag",
            "Mode"
        )

        val DEFAULT_COLUMNS = ALL_AVAILABLE_COLUMNS

        val BUILTIN_PDF = ExportFormatConfig(
            id = "builtin_pdf",
            name = "Dokumen PDF Standar",
            type = "PDF",
            isBuiltIn = true,
            columns = listOf("Tanggal", "Tipe", "Judul", "Kategori", "Nominal")
        )

        val BUILTIN_CSV = ExportFormatConfig(
            id = "builtin_csv",
            name = "Excel Spreadsheet Standar (CSV)",
            type = "CSV",
            isBuiltIn = true,
            delimiter = ",",
            dateFormat = "yyyy-MM-dd HH:mm:ss",
            columns = DEFAULT_COLUMNS
        )
    }
}
