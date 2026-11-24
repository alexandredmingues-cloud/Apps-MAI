package com.example.data

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.OpenableColumns
import androidx.core.content.FileProvider
import com.example.data.model.PropertyItem
import com.example.data.model.SellerDocumentCatalog
import com.example.data.model.SellerPropertyDossier
import com.example.data.model.SellerSaleDocument
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

data class VaultStoredFileInfo(
    val fileName: String,
    val mimeType: String,
    val sizeBytes: Long,
    val relativeVaultPath: String
)

object SellerDocumentVault {

    private const val MAX_FILE_BYTES = 15L * 1024L * 1024L

    fun importDocumentFromUri(
        context: Context,
        propertyId: String,
        sourceUri: Uri
    ): Result<VaultStoredFileInfo> {
        return runCatching {
            val resolver = context.contentResolver
            val mime = resolver.getType(sourceUri) ?: "application/octet-stream"
            var rawName = "document_vente"
            var sizeHint = -1L

            resolver.query(sourceUri, null, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val nameIdx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    val sizeIdx = cursor.getColumnIndex(OpenableColumns.SIZE)
                    if (nameIdx >= 0) {
                        rawName = cursor.getString(nameIdx) ?: rawName
                    }
                    if (sizeIdx >= 0 && !cursor.isNull(sizeIdx)) {
                        sizeHint = cursor.getLong(sizeIdx)
                    }
                }
            }

            if (sizeHint > MAX_FILE_BYTES) {
                error("Fichier trop volumineux (> 15 Mo).")
            }

            val safePropertyId = sanitizeSegment(propertyId.ifBlank { "bien" })
            val safeFileName = sanitizeFileName(rawName)
            val vaultDir = File(context.filesDir, "seller_vault/$safePropertyId").apply { mkdirs() }
            val destFile = File(vaultDir, "${System.currentTimeMillis()}_$safeFileName")

            var totalWritten = 0L
            resolver.openInputStream(sourceUri)?.use { input ->
                FileOutputStream(destFile).use { output ->
                    val buffer = ByteArray(8192)
                    while (true) {
                        val read = input.read(buffer)
                        if (read <= 0) break
                        totalWritten += read
                        if (totalWritten > MAX_FILE_BYTES) {
                            destFile.delete()
                            error("Fichier supérieur à la limite de 15 Mo.")
                        }
                        output.write(buffer, 0, read)
                    }
                }
            } ?: error("Impossible de lire le fichier sélectionné.")

            val relativePath = "seller_vault/$safePropertyId/${destFile.name}"
            VaultStoredFileInfo(
                fileName = safeFileName,
                mimeType = mime,
                sizeBytes = totalWritten,
                relativeVaultPath = relativePath
            )
        }
    }

    fun resolveVaultFile(context: Context, relativePath: String): File? {
        if (relativePath.isBlank()) return null
        val root = File(context.filesDir, "seller_vault").canonicalFile
        val target = File(context.filesDir, relativePath).canonicalFile
        if (!target.path.startsWith(root.path)) return null
        return target.takeIf { it.exists() && it.isFile }
    }

    fun importListingPhotoFromUri(
        context: Context,
        propertyId: String,
        sourceUri: Uri
    ): Result<VaultStoredFileInfo> {
        return runCatching {
            val resolver = context.contentResolver
            val mime = resolver.getType(sourceUri) ?: "image/jpeg"
            if (!mime.startsWith("image/", ignoreCase = true)) {
                error("Format photo non supporté ($mime).")
            }
            var rawName = "photo_bien.jpg"
            resolver.query(sourceUri, null, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val nameIdx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (nameIdx >= 0) {
                        rawName = cursor.getString(nameIdx) ?: rawName
                    }
                }
            }
            val safePropertyId = sanitizeSegment(propertyId.ifBlank { "bien" })
            val safeFileName = sanitizeFileName(rawName.substringBeforeLast('.') + ".jpg")
            val photoDir = File(context.filesDir, "seller_vault/photos/$safePropertyId").apply { mkdirs() }
            val destFile = File(photoDir, "${System.currentTimeMillis()}_$safeFileName")

            val original = resolver.openInputStream(sourceUri)?.use { BitmapFactory.decodeStream(it) }
                ?: error("Impossible de décoder l'image sélectionnée.")
            val maxDim = maxOf(original.width, original.height)
            val scaled = if (maxDim > 1400) {
                val ratio = 1400f / maxDim.toFloat()
                Bitmap.createScaledBitmap(
                    original,
                    (original.width * ratio).toInt().coerceAtLeast(1),
                    (original.height * ratio).toInt().coerceAtLeast(1),
                    true
                ).also {
                    if (it != original) original.recycle()
                }
            } else {
                original
            }
            FileOutputStream(destFile).use { out ->
                scaled.compress(Bitmap.CompressFormat.JPEG, 82, out)
            }
            scaled.recycle()

            val relativePath = "seller_vault/photos/$safePropertyId/${destFile.name}"
            VaultStoredFileInfo(
                fileName = safeFileName,
                mimeType = "image/jpeg",
                sizeBytes = destFile.length(),
                relativeVaultPath = relativePath
            )
        }
    }

    fun loadDownscaledPhotoBitmap(
        context: Context,
        relativePath: String,
        maxDimPx: Int = 1024
    ): Bitmap? {
        val file = resolveVaultFile(context, relativePath) ?: return null
        return runCatching {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(file.absolutePath, bounds)
            var sampleSize = 1
            val longest = maxOf(bounds.outWidth, bounds.outHeight)
            while (longest / sampleSize > maxDimPx) {
                sampleSize *= 2
            }
            val opts = BitmapFactory.Options().apply { inSampleSize = sampleSize.coerceAtLeast(1) }
            BitmapFactory.decodeFile(file.absolutePath, opts)
        }.getOrNull()
    }

    fun openDocumentWithViewer(
        context: Context,
        document: SellerSaleDocument
    ): Result<Unit> {
        return runCatching {
            val file = resolveVaultFile(context, document.localVaultPath)
                ?: error("Le fichier local n'est pas présent sur cet appareil (métadonnée synchronisée).")
            val authority = "${context.packageName}.fileprovider"
            val contentUri = FileProvider.getUriForFile(context, authority, file)
            val viewIntent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(contentUri, document.mimeType.ifBlank { "*/*" })
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(Intent.createChooser(viewIntent, "Ouvrir ${document.title}"))
        }
    }

    fun buildBuyerPackZipBytes(
        context: Context,
        dossier: SellerPropertyDossier,
        property: PropertyItem?
    ): ByteArray {
        val baos = java.io.ByteArrayOutputStream()
        ZipOutputStream(baos).use { zip ->
            val summaryText = buildString {
                appendLine(SellerDocumentCatalog.buildFreeBuyerSummary(dossier, property))
                appendLine()
                appendLine("=== INVENTAIRE COMPLET DES PIÈCES ===")
                dossier.documents.forEachIndexed { idx, doc ->
                    appendLine("${idx + 1}. [${doc.category}] ${doc.title} — Statut : ${doc.status}")
                    if (doc.summaryText.isNotBlank()) {
                        appendLine("   Résumé : ${doc.summaryText}")
                    }
                    if (doc.fileName.isNotBlank()) {
                        appendLine("   Fichier : ${doc.fileName} (${formatBytes(doc.sizeBytes)}) • Déposé par : ${doc.uploadedByRole}")
                    }
                }
            }
            zip.putNextEntry(ZipEntry("00_SYNTHESE_DOSSIER_ET_ALUR.txt"))
            zip.write(summaryText.toByteArray(Charsets.UTF_8))
            zip.closeEntry()

            val usedEntryNames = mutableSetOf("00_SYNTHESE_DOSSIER_ET_ALUR.txt")
            dossier.documents.forEachIndexed { idx, doc ->
                val vaultFile = resolveVaultFile(context, doc.localVaultPath)
                if (vaultFile != null) {
                    val baseName = "${(idx + 1).toString().padStart(2, '0')}_${sanitizeFileName(doc.fileName.ifBlank { vaultFile.name })}"
                    val entryName = if (usedEntryNames.add(baseName)) baseName else "${idx}_${baseName}"
                    zip.putNextEntry(ZipEntry(entryName))
                    vaultFile.inputStream().use { input -> input.copyTo(zip) }
                    zip.closeEntry()
                }
            }
        }
        return baos.toByteArray()
    }

    fun shareBuyerPackZip(
        context: Context,
        dossier: SellerPropertyDossier,
        property: PropertyItem?
    ): Result<Unit> {
        return runCatching {
            val zipBytes = buildBuyerPackZipBytes(context, dossier, property)
            val exportDir = File(context.cacheDir, "exports").apply { mkdirs() }
            val safeTitle = sanitizeSegment(dossier.propertyTitle.ifBlank { "bien" })
            val zipFile = File(exportDir, "pack_documents_${safeTitle}.zip")
            zipFile.writeBytes(zipBytes)

            val authority = "${context.packageName}.fileprovider"
            val uri = FileProvider.getUriForFile(context, authority, zipFile)
            val sendIntent = Intent(Intent.ACTION_SEND).apply {
                type = "application/zip"
                putExtra(Intent.EXTRA_SUBJECT, "Dossier complet & Documents ALUR — ${dossier.propertyTitle}")
                putExtra(
                    Intent.EXTRA_TEXT,
                    "Veuillez trouver ci-joint le pack complet des documents de vente (${dossier.documents.count { it.isProvided }} pièces)."
                )
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(Intent.createChooser(sendIntent, "Recevoir / Envoyer le pack documents"))
        }
    }

    fun formatBytes(bytes: Long): String {
        return when {
            bytes <= 0L -> "0 o"
            bytes < 1024L -> "$bytes o"
            bytes < 1024L * 1024L -> "${bytes / 1024L} Ko"
            else -> String.format(java.util.Locale.FRANCE, "%.1f Mo", bytes / (1024.0 * 1024.0))
        }
    }

    private fun sanitizeSegment(raw: String): String {
        return raw.replace(Regex("[^a-zA-Z0-9_\\-]"), "_").take(40).ifBlank { "bien" }
    }

    private fun sanitizeFileName(raw: String): String {
        return raw.replace(Regex("[^a-zA-Z0-9._\\-]"), "_").take(100).ifBlank { "document.pdf" }
    }
}
