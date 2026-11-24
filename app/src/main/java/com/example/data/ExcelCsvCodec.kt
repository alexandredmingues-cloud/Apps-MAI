package com.example.data

import com.example.data.model.CrmCatalog
import com.example.data.model.CrmContact
import com.example.security.InputSanitizer
import org.w3c.dom.Element
import org.w3c.dom.Node
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.nio.charset.StandardCharsets
import java.text.Normalizer
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import javax.xml.XMLConstants
import javax.xml.parsers.DocumentBuilderFactory

enum class MontyImportField(val labelFr: String) {
    IGNORE("Ignorer"),
    FULL_NAME("Nom complet"),
    FIRST_NAME("Prénom"),
    LAST_NAME("Nom de famille"),
    PHONE("Téléphone"),
    EMAIL("Adresse e-mail"),
    POSTAL_ADDRESS("Adresse postale"),
    CONTACT_TYPE("Type de contact"),
    STATUS("Statut"),
    PRIORITY("Priorité"),
    BUDGET_MIN("Budget min (€)"),
    BUDGET_MAX("Budget max (€)"),
    SECTOR("Secteurs recherchés"),
    DESIRED_PROPERTY_TYPE("Bien recherché"),
    DESIRED_SURFACE("Surface min (m²)"),
    DESIRED_ROOMS("Chambres min"),
    FINANCING_MODE("Financement"),
    TIMELINE("Délai du projet"),
    SOURCE("Origine"),
    NOTES("Notes")
}

data class ParsedSpreadsheetTable(
    val formatDetected: String,
    val fileName: String,
    val headers: List<String>,
    val dataRows: List<List<String>>,
    val hasHeaderRow: Boolean,
    val suggestedMapping: Map<Int, MontyImportField>
)

data class ImportedContactDraft(
    val rowNumber: Int = 0,
    val firstName: String = "",
    val lastName: String = "",
    val fullName: String,
    val email: String = "",
    val phone: String = "",
    val postalAddress: String = "",
    val contactType: String = "Acquéreur",
    val status: String = "Nouveau",
    val priority: String = "Normal",
    val budgetMin: Long = 0L,
    val budgetMax: Long = 0L,
    val sector: String = "",
    val desiredPropertyType: String = "",
    val desiredSurfaceMin: Long = 0L,
    val desiredRoomsMin: Long = 0L,
    val financingMode: String = "",
    val projectTimeline: String = "",
    val source: String = "Import Excel",
    val notes: String = ""
)

data class ImportRowIssue(
    val rowNumber: Int,
    val label: String,
    val reason: String
)

data class ExcelImportPreview(
    val formatDetected: String,
    val validDrafts: List<ImportedContactDraft>,
    val duplicateRows: List<ImportRowIssue>,
    val errorRows: List<ImportRowIssue>
)

data class ExcelImportExecutionResult(
    val importedCount: Int,
    val duplicateSkippedCount: Int,
    val errorCount: Int
)

object ExcelCsvCodec {
    private const val MAX_IMPORT_ROWS = 100
    private val FORMULA_PREFIXES = setOf('=', '+', '-', '@', '\t', '\r')

    fun sanitizeSpreadsheetCell(value: String): String {
        val cleaned = InputSanitizer.sanitizeText(value, 500)
        if (cleaned.isEmpty()) return ""
        return if (cleaned.first() in FORMULA_PREFIXES) "'$cleaned" else cleaned
    }

    fun normalizePhoneKey(phone: String): String {
        val digits = phone.filter { it.isDigit() }
        return if (digits.length >= 9) digits.takeLast(9) else digits
    }

    fun normalizeEmailKey(email: String): String =
        InputSanitizer.sanitizeText(email, 160).lowercase(Locale.ROOT)

    fun exportContactsToCsv(contacts: List<CrmContact>): ByteArray {
        val sb = StringBuilder()
        sb.append('\uFEFF')
        sb.append(
            listOf(
                "Nom complet", "Prénom", "Nom", "Téléphone", "Email", "Adresse postale",
                "Type", "Statut", "Priorité", "Budget min (€)", "Budget max (€)",
                "Secteurs", "Bien recherché", "Surface min", "Chambres min",
                "Financement", "Délai", "Origine", "Agent responsable",
                "Bien associé", "Dernier contact", "Prochaine relance", "Notes",
                "Base légale RGPD", "Consentement RGPD", "Date consentement", "Échéance conservation"
            ).joinToString(";")
        ).append("\r\n")

        contacts.forEach { c ->
            val cols = contactToExportRow(c).map { raw ->
                val safe = sanitizeSpreadsheetCell(raw).replace("\"", "\"\"")
                "\"$safe\""
            }
            sb.append(cols.joinToString(";")).append("\r\n")
        }
        return sb.toString().toByteArray(StandardCharsets.UTF_8)
    }

    fun exportContactsToXlsx(contacts: List<CrmContact>, sheetTitle: String = "Contacts Monty"): ByteArray {
        val headers = listOf(
            "Nom complet", "Prénom", "Nom", "Téléphone", "Email", "Adresse postale",
            "Type", "Statut", "Priorité", "Budget min (€)", "Budget max (€)",
            "Secteurs", "Bien recherché", "Surface min", "Chambres min",
            "Financement", "Délai", "Origine", "Agent responsable",
            "Bien associé", "Dernier contact", "Prochaine relance", "Notes",
            "Base légale RGPD", "Consentement RGPD", "Date consentement", "Échéance conservation"
        )
        val rows = mutableListOf<List<String>>()
        rows.add(headers)
        contacts.forEach { c ->
            rows.add(contactToExportRow(c).map(::sanitizeSpreadsheetCell))
        }

        val baos = ByteArrayOutputStream()
        ZipOutputStream(baos).use { zos ->
            writeZipEntry(
                zos,
                "[Content_Types].xml",
                """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">
  <Default Extension="rels" ContentType="application/vnd.openxmlformats-officedocument.relationships+xml"/>
  <Default Extension="xml" ContentType="application/xml"/>
  <Override PartName="/xl/workbook.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml"/>
  <Override PartName="/xl/worksheets/sheet1.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"/>
</Types>"""
            )
            writeZipEntry(
                zos,
                "_rels/.rels",
                """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
  <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="xl/workbook.xml"/>
</Relationships>"""
            )
            val safeSheet = escapeXml(InputSanitizer.sanitizeText(sheetTitle, 30).ifBlank { "Contacts" })
            writeZipEntry(
                zos,
                "xl/workbook.xml",
                """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<workbook xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships">
  <sheets>
    <sheet name="$safeSheet" sheetId="1" r:id="rId1"/>
  </sheets>
</workbook>"""
            )
            writeZipEntry(
                zos,
                "xl/_rels/workbook.xml.rels",
                """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
  <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet" Target="worksheets/sheet1.xml"/>
</Relationships>"""
            )

            val numericColumns = setOf(9, 10, 13, 14)
            val sheetXml = buildString {
                append("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""")
                append("""<worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main"><sheetData>""")
                rows.forEachIndexed { rIdx, cols ->
                    val rowNum = rIdx + 1
                    append("""<row r="$rowNum">""")
                    cols.forEachIndexed { cIdx, cellVal ->
                        val colRef = columnLetters(cIdx) + rowNum
                        val numericVal = if (rIdx > 0 && cIdx in numericColumns) cellVal.toLongOrNull() else null
                        if (numericVal != null) {
                            append("""<c r="$colRef"><v>$numericVal</v></c>""")
                        } else {
                            append("""<c r="$colRef" t="inlineStr"><is><t>${escapeXml(cellVal)}</t></is></c>""")
                        }
                    }
                    append("</row>")
                }
                append("</sheetData></worksheet>")
            }
            writeZipEntry(zos, "xl/worksheets/sheet1.xml", sheetXml)
        }
        return baos.toByteArray()
    }

    private fun contactToExportRow(c: CrmContact): List<String> = listOf(
        c.fullName,
        c.firstName,
        c.lastName,
        c.phone,
        c.email,
        c.postalAddress,
        c.contactType,
        c.status,
        c.priority,
        if (c.budgetMin > 0) c.budgetMin.toString() else "",
        if (c.budgetMax > 0) c.budgetMax.toString() else "",
        c.sector,
        c.desiredPropertyType,
        if (c.desiredSurfaceMin > 0) c.desiredSurfaceMin.toString() else "",
        if (c.desiredRoomsMin > 0) c.desiredRoomsMin.toString() else "",
        c.financingMode,
        c.projectTimeline,
        c.source,
        c.assignedToName,
        c.linkedPropertyTitle,
        c.lastContactedAt,
        c.nextFollowUpAt,
        c.notes,
        c.rgpdLegalBasis,
        if (c.rgpdConsentGranted) "Oui" else "Non",
        c.rgpdConsentDate,
        c.rgpdRetentionUntil
    )

    fun inspectRawTable(bytes: ByteArray, fileNameHint: String = ""): ParsedSpreadsheetTable {
        val looksLikeZip = bytes.size >= 2 && bytes[0] == 'P'.code.toByte() && bytes[1] == 'K'.code.toByte()
        val (formatLabel, rawRows) = if (looksLikeZip || fileNameHint.endsWith(".xlsx", ignoreCase = true)) {
            "Excel (.xlsx)" to extractXlsxRows(ByteArrayInputStream(bytes))
        } else {
            val text = String(bytes, StandardCharsets.UTF_8).removePrefix("\uFEFF")
            val lines = text.replace("\r\n", "\n").replace("\r", "\n")
                .split('\n')
                .filter { it.isNotBlank() }
            if (lines.isEmpty()) {
                "CSV vide" to emptyList()
            } else {
                val delimiter = detectDelimiter(lines.first())
                "CSV (${if (delimiter == ';') "Excel ;" else delimiter.toString()})" to lines.map { splitDelimitedLine(it, delimiter) }
            }
        }

        if (rawRows.isEmpty()) {
            return ParsedSpreadsheetTable(formatLabel, fileNameHint, emptyList(), emptyList(), false, emptyMap())
        }

        val firstNorm = rawRows.first().map(::normalizeHeader)
        val hasHeader = firstNorm.any { h ->
            h.contains("nom") || h.contains("prenom") || h.contains("name") ||
                h.contains("email") || h.contains("mail") || h.contains("tel") ||
                h.contains("phone") || h.contains("budget") || h.contains("client") || h.contains("statut")
        }
        val headers = if (hasHeader) {
            rawRows.first().mapIndexed { idx, s -> s.ifBlank { "Colonne ${idx + 1}" } }
        } else {
            (0 until (rawRows.maxOfOrNull { it.size } ?: 0)).map { "Colonne ${it + 1}" }
        }
        val dataRows = if (hasHeader) rawRows.drop(1) else rawRows
        val suggested = if (hasHeader) suggestMappingFromHeaders(firstNorm) else defaultMapping(headers.size)

        return ParsedSpreadsheetTable(
            formatDetected = formatLabel,
            fileName = fileNameHint,
            headers = headers,
            dataRows = dataRows,
            hasHeaderRow = hasHeader,
            suggestedMapping = suggested
        )
    }

    fun buildImportPreview(
        table: ParsedSpreadsheetTable,
        mapping: Map<Int, MontyImportField>,
        existingContacts: List<CrmContact>
    ): ExcelImportPreview {
        val existingEmails = existingContacts.mapNotNull { c ->
            normalizeEmailKey(c.email).takeIf { it.isNotBlank() }
        }.toMutableSet()
        val existingPhones = existingContacts.mapNotNull { c ->
            normalizePhoneKey(c.phone).takeIf { it.length >= 6 }
        }.toMutableSet()

        val valid = mutableListOf<ImportedContactDraft>()
        val duplicates = mutableListOf<ImportRowIssue>()
        val errors = mutableListOf<ImportRowIssue>()

        table.dataRows.forEachIndexed { idx, row ->
            val rowNum = idx + if (table.hasHeaderRow) 2 else 1
            if (valid.size >= MAX_IMPORT_ROWS) {
                errors.add(ImportRowIssue(rowNum, row.firstOrNull().orEmpty().ifBlank { "Ligne $rowNum" }, "Limite de $MAX_IMPORT_ROWS contacts par import dépassée"))
                return@forEachIndexed
            }

            fun getField(field: MontyImportField): String {
                val colIdx = mapping.entries.firstOrNull { it.value == field }?.key ?: return ""
                return if (colIdx in row.indices) InputSanitizer.sanitizeText(row[colIdx], 500) else ""
            }

            val firstName = getField(MontyImportField.FIRST_NAME).take(80)
            val lastName = getField(MontyImportField.LAST_NAME).take(80)
            val explicitFull = getField(MontyImportField.FULL_NAME).take(140)
            val fullName = explicitFull.ifBlank {
                listOf(firstName, lastName).filter { it.isNotBlank() }.joinToString(" ")
            }.take(140)

            val email = getField(MontyImportField.EMAIL).take(160)
            val phone = getField(MontyImportField.PHONE).take(40)

            if (fullName.isBlank()) {
                errors.add(ImportRowIssue(rowNum, email.ifBlank { phone.ifBlank { "Ligne $rowNum" } }, "Nom manquant"))
                return@forEachIndexed
            }
            if (email.isNotBlank() && !email.contains("@")) {
                errors.add(ImportRowIssue(rowNum, fullName, "Adresse e-mail invalide ($email)"))
                return@forEachIndexed
            }

            val normEmail = normalizeEmailKey(email)
            val normPhone = normalizePhoneKey(phone)
            if (normEmail.isNotBlank() && normEmail in existingEmails) {
                duplicates.add(ImportRowIssue(rowNum, fullName, "Doublon e-mail ($email)"))
                return@forEachIndexed
            }
            if (normPhone.length >= 6 && normPhone in existingPhones) {
                duplicates.add(ImportRowIssue(rowNum, fullName, "Doublon téléphone ($phone)"))
                return@forEachIndexed
            }

            if (normEmail.isNotBlank()) existingEmails.add(normEmail)
            if (normPhone.length >= 6) existingPhones.add(normPhone)

            val rawType = getField(MontyImportField.CONTACT_TYPE)
            val contactType = CrmCatalog.CONTACT_TYPES.firstOrNull {
                rawType.contains(it.take(4), ignoreCase = true)
            } ?: when {
                rawType.contains("mix", ignoreCase = true) -> "Mixte"
                else -> "Acquéreur"
            }

            val rawStatus = getField(MontyImportField.STATUS)
            val status = CrmCatalog.CONTACT_STATUSES.firstOrNull {
                rawStatus.equals(it, ignoreCase = true)
            } ?: rawStatus.ifBlank { "Nouveau" }.take(60)

            val rawPriority = getField(MontyImportField.PRIORITY)
            val priority = CrmCatalog.PRIORITIES.firstOrNull {
                rawPriority.contains(it.take(4), ignoreCase = true)
            } ?: "Normal"

            val budgetMin = getField(MontyImportField.BUDGET_MIN).filter { it.isDigit() }.toLongOrNull()?.coerceIn(0L, 50_000_000L) ?: 0L
            val budgetMax = getField(MontyImportField.BUDGET_MAX).filter { it.isDigit() }.toLongOrNull()?.coerceIn(0L, 50_000_000L) ?: 0L
            val surf = getField(MontyImportField.DESIRED_SURFACE).filter { it.isDigit() }.toLongOrNull()?.coerceIn(0L, 5000L) ?: 0L
            val rooms = getField(MontyImportField.DESIRED_ROOMS).filter { it.isDigit() }.toLongOrNull()?.coerceIn(0L, 30L) ?: 0L

            valid.add(
                ImportedContactDraft(
                    rowNumber = rowNum,
                    firstName = firstName,
                    lastName = lastName,
                    fullName = fullName,
                    email = email,
                    phone = phone,
                    postalAddress = getField(MontyImportField.POSTAL_ADDRESS).take(220),
                    contactType = contactType,
                    status = status,
                    priority = priority,
                    budgetMin = budgetMin,
                    budgetMax = budgetMax,
                    sector = getField(MontyImportField.SECTOR).take(160),
                    desiredPropertyType = getField(MontyImportField.DESIRED_PROPERTY_TYPE).take(80),
                    desiredSurfaceMin = surf,
                    desiredRoomsMin = rooms,
                    financingMode = getField(MontyImportField.FINANCING_MODE).take(80),
                    projectTimeline = getField(MontyImportField.TIMELINE).take(80),
                    source = getField(MontyImportField.SOURCE).ifBlank { "Import Excel" }.take(60),
                    notes = getField(MontyImportField.NOTES).take(2000)
                )
            )
        }

        return ExcelImportPreview(
            formatDetected = table.formatDetected,
            validDrafts = valid,
            duplicateRows = duplicates,
            errorRows = errors
        )
    }

    // Convenience helper for direct parsing & unit tests
    fun parseBytes(bytes: ByteArray, fileNameHint: String = "", existingContacts: List<CrmContact> = emptyList()): ExcelImportPreview {
        val table = inspectRawTable(bytes, fileNameHint)
        return buildImportPreview(table, table.suggestedMapping, existingContacts)
    }

    private fun extractXlsxRows(input: InputStream): List<List<String>> {
        val entries = mutableMapOf<String, ByteArray>()
        ZipInputStream(input).use { zis ->
            var entry = zis.nextEntry
            var totalBytes = 0L
            while (entry != null) {
                if (!entry.isDirectory) {
                    val name = entry.name
                    if (name == "xl/sharedStrings.xml" || name.startsWith("xl/worksheets/sheet")) {
                        val content = zis.readBytes()
                        totalBytes += content.size
                        if (totalBytes <= 10 * 1024 * 1024L) {
                            entries[name] = content
                        }
                    }
                }
                zis.closeEntry()
                entry = zis.nextEntry
            }
        }
        val sharedStrings = entries["xl/sharedStrings.xml"]?.let(::parseSharedStrings).orEmpty()
        val sheetBytes = entries["xl/worksheets/sheet1.xml"]
            ?: entries.entries.firstOrNull { it.key.startsWith("xl/worksheets/sheet") }?.value
            ?: return emptyList()
        return parseWorksheetRows(sheetBytes, sharedStrings)
    }

    private fun defaultMapping(colCount: Int): Map<Int, MontyImportField> {
        val ordered = listOf(
            MontyImportField.FULL_NAME,
            MontyImportField.EMAIL,
            MontyImportField.PHONE,
            MontyImportField.CONTACT_TYPE,
            MontyImportField.STATUS,
            MontyImportField.BUDGET_MAX,
            MontyImportField.SECTOR,
            MontyImportField.NOTES
        )
        return (0 until colCount).associateWith { idx ->
            ordered.getOrElse(idx) { MontyImportField.IGNORE }
        }
    }

    private fun suggestMappingFromHeaders(headers: List<String>): Map<Int, MontyImportField> {
        val assignedFields = mutableSetOf<MontyImportField>()
        val result = mutableMapOf<Int, MontyImportField>()
        headers.forEachIndexed { idx, h ->
            val guess = when {
                h.contains("prenom") || h == "first name" || h == "firstname" -> MontyImportField.FIRST_NAME
                h == "nom" || h.contains("nom de famille") || h == "last name" || h == "lastname" -> MontyImportField.LAST_NAME
                h.contains("nom complet") || h.contains("full name") || h.contains("client") || h.contains("contact") -> MontyImportField.FULL_NAME
                h.contains("email") || h.contains("mail") || h.contains("courriel") -> MontyImportField.EMAIL
                h.contains("tel") || h.contains("phone") || h.contains("mobile") || h.contains("portable") -> MontyImportField.PHONE
                h.contains("adresse") || h.contains("address") -> MontyImportField.POSTAL_ADDRESS
                h.contains("priorit") -> MontyImportField.PRIORITY
                h.contains("budget min") -> MontyImportField.BUDGET_MIN
                h.contains("budget") || h.contains("montant") || h.contains("prix") -> MontyImportField.BUDGET_MAX
                h.contains("secteur") || h.contains("ville") || h.contains("quartier") || h.contains("zone") -> MontyImportField.SECTOR
                h.contains("surface") -> MontyImportField.DESIRED_SURFACE
                h.contains("chambre") || h.contains("pieces") -> MontyImportField.DESIRED_ROOMS
                h.contains("financement") -> MontyImportField.FINANCING_MODE
                h.contains("delai") || h.contains("horizon") -> MontyImportField.TIMELINE
                h.contains("origine") || h.contains("source") -> MontyImportField.SOURCE
                h.contains("statut") || h.contains("status") || h.contains("etape") -> MontyImportField.STATUS
                h.contains("bien") -> MontyImportField.DESIRED_PROPERTY_TYPE
                h.contains("type") || h.contains("profil") || h.contains("categorie") -> MontyImportField.CONTACT_TYPE
                h.contains("note") || h.contains("commentaire") -> MontyImportField.NOTES
                else -> MontyImportField.IGNORE
            }
            if (guess != MontyImportField.IGNORE && assignedFields.add(guess)) {
                result[idx] = guess
            } else if (guess == MontyImportField.LAST_NAME && MontyImportField.FULL_NAME !in assignedFields && MontyImportField.FIRST_NAME !in assignedFields) {
                assignedFields.add(MontyImportField.FULL_NAME)
                result[idx] = MontyImportField.FULL_NAME
            } else {
                result[idx] = MontyImportField.IGNORE
            }
        }
        // Fallback: if neither FULL_NAME nor FIRST_NAME+LAST_NAME was mapped, map col 0 to FULL_NAME
        if (MontyImportField.FULL_NAME !in result.values && MontyImportField.FIRST_NAME !in result.values && headers.isNotEmpty()) {
            result[0] = MontyImportField.FULL_NAME
        }
        return result
    }

    private fun normalizeHeader(raw: String): String {
        val decomposed = Normalizer.normalize(raw.lowercase().trim(), Normalizer.Form.NFD)
        return decomposed.replace(Regex("\\p{InCombiningDiacriticalMarks}+"), "")
    }

    private fun detectDelimiter(headerLine: String): Char {
        val candidates = listOf(';', ',', '\t')
        return candidates.maxByOrNull { ch -> headerLine.count { it == ch } } ?: ';'
    }

    private fun splitDelimitedLine(line: String, delimiter: Char): List<String> {
        val out = mutableListOf<String>()
        val current = StringBuilder()
        var inQuotes = false
        var i = 0
        while (i < line.length) {
            val ch = line[i]
            when {
                ch == '"' -> {
                    if (inQuotes && i + 1 < line.length && line[i + 1] == '"') {
                        current.append('"')
                        i++
                    } else {
                        inQuotes = !inQuotes
                    }
                }
                ch == delimiter && !inQuotes -> {
                    out.add(current.toString().trim())
                    current.clear()
                }
                else -> current.append(ch)
            }
            i++
        }
        out.add(current.toString().trim())
        return out
    }

    private fun secureXmlFactory(): DocumentBuilderFactory =
        DocumentBuilderFactory.newInstance().apply {
            isNamespaceAware = true
            runCatching { setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true) }
            runCatching { setFeature("http://apache.org/xml/features/disallow-doctype-decl", true) }
            runCatching { setFeature("http://xml.org/sax/features/external-general-entities", false) }
            runCatching { setFeature("http://xml.org/sax/features/external-parameter-entities", false) }
        }

    private fun parseSharedStrings(xmlBytes: ByteArray): List<String> {
        val doc = secureXmlFactory().newDocumentBuilder().parse(ByteArrayInputStream(xmlBytes))
        val siNodes = doc.getElementsByTagNameNS("*", "si")
        val result = ArrayList<String>(siNodes.length)
        for (i in 0 until siNodes.length) {
            val el = siNodes.item(i) as? Element ?: continue
            val tNodes = el.getElementsByTagNameNS("*", "t")
            val text = buildString {
                for (j in 0 until tNodes.length) append(tNodes.item(j).textContent.orEmpty())
            }
            result.add(text)
        }
        return result
    }

    private fun parseWorksheetRows(xmlBytes: ByteArray, sharedStrings: List<String>): List<List<String>> {
        val doc = secureXmlFactory().newDocumentBuilder().parse(ByteArrayInputStream(xmlBytes))
        val rowNodes = doc.getElementsByTagNameNS("*", "row")
        val rows = ArrayList<List<String>>(rowNodes.length)
        for (i in 0 until rowNodes.length) {
            val rowEl = rowNodes.item(i) as? Element ?: continue
            val cells = mutableMapOf<Int, String>()
            var maxCol = -1
            val children = rowEl.childNodes
            for (j in 0 until children.length) {
                val node = children.item(j)
                if (node.nodeType != Node.ELEMENT_NODE) continue
                val cEl = node as Element
                if (cEl.localName != "c") continue
                val ref = cEl.getAttribute("r")
                val colIndex = columnIndexFromRef(ref).takeIf { it >= 0 } ?: (maxCol + 1)
                if (colIndex > maxCol) maxCol = colIndex
                val type = cEl.getAttribute("t")
                val value = when (type) {
                    "s" -> {
                        val idx = firstChildText(cEl, "v")?.trim()?.toIntOrNull()
                        if (idx != null && idx in sharedStrings.indices) sharedStrings[idx] else ""
                    }
                    "inlineStr" -> {
                        val tNodes = cEl.getElementsByTagNameNS("*", "t")
                        buildString {
                            for (k in 0 until tNodes.length) append(tNodes.item(k).textContent.orEmpty())
                        }
                    }
                    else -> firstChildText(cEl, "v").orEmpty()
                }
                cells[colIndex] = value.trim()
            }
            if (maxCol >= 0) {
                val rowList = (0..maxCol).map { idx -> cells[idx].orEmpty() }
                if (rowList.any { it.isNotBlank() }) rows.add(rowList)
            }
        }
        return rows
    }

    private fun firstChildText(parent: Element, localName: String): String? {
        val nodes = parent.getElementsByTagNameNS("*", localName)
        return if (nodes.length > 0) nodes.item(0).textContent else null
    }

    private fun columnIndexFromRef(ref: String): Int {
        if (ref.isBlank()) return -1
        var col = 0
        var foundLetter = false
        for (ch in ref) {
            if (ch in 'A'..'Z' || ch in 'a'..'z') {
                foundLetter = true
                col = col * 26 + (ch.uppercaseChar() - 'A' + 1)
            } else break
        }
        return if (foundLetter) col - 1 else -1
    }

    private fun columnLetters(index: Int): String {
        var n = index
        val sb = StringBuilder()
        while (n >= 0) {
            sb.insert(0, ('A' + (n % 26)))
            n = (n / 26) - 1
        }
        return sb.toString()
    }

    private fun writeZipEntry(zos: ZipOutputStream, name: String, content: String) {
        zos.putNextEntry(ZipEntry(name))
        zos.write(content.toByteArray(StandardCharsets.UTF_8))
        zos.closeEntry()
    }

    private fun escapeXml(input: String): String =
        input.replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&apos;")
}
