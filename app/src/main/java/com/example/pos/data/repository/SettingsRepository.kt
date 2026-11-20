package com.example.pos.data.repository

import androidx.room.withTransaction
import com.example.pos.core.error.PosError
import com.example.pos.core.error.PosResult
import com.example.pos.data.audit.AuditActionType
import com.example.pos.data.audit.AuditEntityType
import com.example.pos.data.audit.AuditSanitizer
import com.example.pos.data.db.AppSettingsDao
import com.example.pos.data.db.AppSettingsEntity
import com.example.pos.data.db.AuditLogDao
import com.example.pos.data.db.AuditLogEntity
import com.example.pos.data.db.OperatorDao
import com.example.pos.data.db.PosDatabase
import kotlinx.coroutines.flow.Flow
import java.util.UUID

interface SettingsRepository {
    val settingsFlow: Flow<AppSettingsEntity?>
    suspend fun getSettings(): AppSettingsEntity?
    suspend fun updateLanguage(lang: String): PosResult<Unit>
    suspend fun updateThemeMode(theme: String): PosResult<Unit>
    suspend fun updatePaperSize(paperSize: String): PosResult<Unit>
    suspend fun updateAutoCutter(enabled: Boolean): PosResult<Unit>
    suspend fun updateBluetoothPrinter(address: String?, name: String?): PosResult<Unit>
    suspend fun updateFooterNotes(l1: String, l2: String): PosResult<Unit>
}

class SettingsRepositoryImpl(
    private val appSettingsDao: AppSettingsDao,
    private val operatorDao: OperatorDao,
    private val auditLogDao: AuditLogDao,
    private val db: PosDatabase,
    private val timeProvider: () -> Long = { System.currentTimeMillis() }
) : SettingsRepository {

    override val settingsFlow: Flow<AppSettingsEntity?> = appSettingsDao.getSettingsFlow()

    override suspend fun getSettings(): AppSettingsEntity? = appSettingsDao.getSettings()

    override suspend fun updateLanguage(lang: String): PosResult<Unit> = withAuditedSettingsMutation(
        actionType = AuditActionType.APP_PREFERENCES_UPDATED,
        entityId = "APP-PREFERENCES",
        diffBuilder = { current ->
            val clean = lang.trim().lowercase().ifBlank { "bn" }
            val old = current?.language ?: "bn"
            if (current != null && old == clean) null else {
                Triple(
                    AuditSanitizer.computeDiffSnapshots(mapOf("language" to old), mapOf("language" to clean)),
                    "Changed language (${old.uppercase()} -> ${clean.uppercase()})",
                    "অ্যাপের ভাষা পরিবর্তন (${if (clean == "bn") "বাংলা" else "English"})"
                )
            }
        },
        applier = { current, actor, now ->
            val clean = lang.trim().lowercase().ifBlank { "bn" }
            if (current == null) {
                appSettingsDao.insertOrUpdate(
                    AppSettingsEntity(
                        id = 1,
                        language = clean,
                        createdAt = now,
                        updatedAt = now,
                        updatedByOperatorId = actor.userId,
                        updatedByOperatorName = actor.operatorName
                    )
                )
            } else {
                appSettingsDao.updateLanguageWithOperator(clean, actor.userId, actor.operatorName, now)
            }
        }
    )

    override suspend fun updateThemeMode(theme: String): PosResult<Unit> = withAuditedSettingsMutation(
        actionType = AuditActionType.APP_PREFERENCES_UPDATED,
        entityId = "APP-PREFERENCES",
        diffBuilder = { current ->
            val clean = theme.trim().uppercase().ifBlank { "LIGHT" }
            val old = current?.themeMode ?: "LIGHT"
            if (current != null && old == clean) null else {
                Triple(
                    AuditSanitizer.computeDiffSnapshots(mapOf("themeMode" to old), mapOf("themeMode" to clean)),
                    "Changed theme mode ($old -> $clean)",
                    "থিম পরিবর্তন ($old -> $clean)"
                )
            }
        },
        applier = { current, actor, now ->
            val clean = theme.trim().uppercase().ifBlank { "LIGHT" }
            if (current == null) {
                appSettingsDao.insertOrUpdate(
                    AppSettingsEntity(
                        id = 1,
                        themeMode = clean,
                        createdAt = now,
                        updatedAt = now,
                        updatedByOperatorId = actor.userId,
                        updatedByOperatorName = actor.operatorName
                    )
                )
            } else {
                appSettingsDao.updateThemeModeWithOperator(clean, actor.userId, actor.operatorName, now)
            }
        }
    )

    override suspend fun updatePaperSize(paperSize: String): PosResult<Unit> = withAuditedSettingsMutation(
        actionType = AuditActionType.PRINTER_SETTINGS_UPDATED,
        entityId = "PRINTER-SETTINGS",
        diffBuilder = { current ->
            val clean = paperSize.trim().ifBlank { "58mm" }
            val old = current?.paperSize ?: "58mm"
            if (current != null && old == clean) null else {
                Triple(
                    AuditSanitizer.computeDiffSnapshots(mapOf("paperSize" to old), mapOf("paperSize" to clean)),
                    "Updated thermal paper size ($old -> $clean)",
                    "থার্মাল পেপার সাইজ পরিবর্তন ($old -> $clean)"
                )
            }
        },
        applier = { current, actor, now ->
            val clean = paperSize.trim().ifBlank { "58mm" }
            if (current == null) {
                appSettingsDao.insertOrUpdate(
                    AppSettingsEntity(
                        id = 1,
                        paperSize = clean,
                        createdAt = now,
                        updatedAt = now,
                        updatedByOperatorId = actor.userId,
                        updatedByOperatorName = actor.operatorName
                    )
                )
            } else {
                appSettingsDao.updatePaperSizeWithOperator(clean, actor.userId, actor.operatorName, now)
            }
        }
    )

    override suspend fun updateAutoCutter(enabled: Boolean): PosResult<Unit> = withAuditedSettingsMutation(
        actionType = AuditActionType.PRINTER_SETTINGS_UPDATED,
        entityId = "PRINTER-SETTINGS",
        diffBuilder = { current ->
            val old = current?.autoCutterEnabled ?: true
            if (current != null && old == enabled) null else {
                Triple(
                    AuditSanitizer.computeDiffSnapshots(mapOf("autoCutter" to old), mapOf("autoCutter" to enabled)),
                    "Updated auto-cutter setting (${if (enabled) "Enabled" else "Disabled"})",
                    "অটো-কাটার সেটিং পরিবর্তন (${if (enabled) "সক্রিয়" else "নিষ্ক্রিয়"})"
                )
            }
        },
        applier = { current, actor, now ->
            if (current == null) {
                appSettingsDao.insertOrUpdate(
                    AppSettingsEntity(
                        id = 1,
                        autoCutterEnabled = enabled,
                        createdAt = now,
                        updatedAt = now,
                        updatedByOperatorId = actor.userId,
                        updatedByOperatorName = actor.operatorName
                    )
                )
            } else {
                appSettingsDao.updateAutoCutterWithOperator(enabled, actor.userId, actor.operatorName, now)
            }
        }
    )

    override suspend fun updateBluetoothPrinter(address: String?, name: String?): PosResult<Unit> = withAuditedSettingsMutation(
        actionType = AuditActionType.PRINTER_SETTINGS_UPDATED,
        entityId = "PRINTER-SETTINGS",
        diffBuilder = { current ->
            val cleanAddr = address?.trim()?.takeIf { it.isNotBlank() }
            val cleanName = name?.trim()?.takeIf { it.isNotBlank() }
            val oldAddr = current?.bluetoothPrinterAddress
            val oldName = current?.bluetoothPrinterName
            if (current != null && oldAddr == cleanAddr && oldName == cleanName) null else {
                val label = cleanName ?: cleanAddr ?: "None"
                Triple(
                    AuditSanitizer.computeDiffSnapshots(
                        before = mapOf("printerName" to (oldName ?: ""), "printerAddress" to (oldAddr ?: "")),
                        after = mapOf("printerName" to (cleanName ?: ""), "printerAddress" to (cleanAddr ?: ""))
                    ),
                    "Updated Bluetooth printer ($label)",
                    "ব্লুটুথ প্রিন্টার হালনাগাদ ($label)"
                )
            }
        },
        applier = { current, actor, now ->
            val cleanAddr = address?.trim()?.takeIf { it.isNotBlank() }
            val cleanName = name?.trim()?.takeIf { it.isNotBlank() }
            if (current == null) {
                appSettingsDao.insertOrUpdate(
                    AppSettingsEntity(
                        id = 1,
                        bluetoothPrinterAddress = cleanAddr,
                        bluetoothPrinterName = cleanName,
                        createdAt = now,
                        updatedAt = now,
                        updatedByOperatorId = actor.userId,
                        updatedByOperatorName = actor.operatorName
                    )
                )
            } else {
                appSettingsDao.updateBluetoothPrinterWithOperator(cleanAddr, cleanName, actor.userId, actor.operatorName, now)
            }
        }
    )

    override suspend fun updateFooterNotes(l1: String, l2: String): PosResult<Unit> = withAuditedSettingsMutation(
        actionType = AuditActionType.FOOTER_NOTES_UPDATED,
        entityId = "FOOTER-NOTES",
        diffBuilder = { current ->
            val clean1 = l1.trim()
            val clean2 = l2.trim()
            val old1 = current?.footerNoteLine1 ?: ""
            val old2 = current?.footerNoteLine2 ?: ""
            if (current != null && old1 == clean1 && old2 == clean2) null else {
                Triple(
                    AuditSanitizer.computeDiffSnapshots(
                        before = mapOf("footerLine1" to old1, "footerLine2" to old2),
                        after = mapOf("footerLine1" to clean1, "footerLine2" to clean2)
                    ),
                    "Updated receipt footer notes",
                    "রিসিপ্ট ফুটার নোট হালনাগাদ করা হয়েছে"
                )
            }
        },
        applier = { current, actor, now ->
            val clean1 = l1.trim()
            val clean2 = l2.trim()
            if (current == null) {
                appSettingsDao.insertOrUpdate(
                    AppSettingsEntity(
                        id = 1,
                        footerNoteLine1 = clean1,
                        footerNoteLine2 = clean2,
                        createdAt = now,
                        updatedAt = now,
                        updatedByOperatorId = actor.userId,
                        updatedByOperatorName = actor.operatorName
                    )
                )
            } else {
                appSettingsDao.updateFooterNotesWithOperator(clean1, clean2, actor.userId, actor.operatorName, now)
            }
        }
    )

    private suspend fun withAuditedSettingsMutation(
        actionType: String,
        entityId: String,
        diffBuilder: (AppSettingsEntity?) -> Triple<Pair<String?, String?>, String, String>?,
        applier: suspend (AppSettingsEntity?, com.example.pos.data.db.OperatorEntity, Long) -> Unit
    ): PosResult<Unit> {
        return try {
            db.withTransaction {
                val actor = operatorDao.getActiveOperator()
                    ?: return@withTransaction PosResult.Error(PosError.Unauthenticated())

                val current = appSettingsDao.getSettings()
                val diffResult = diffBuilder(current)
                    ?: return@withTransaction PosResult.Success(Unit)

                val (diffPair, summaryEn, summaryBn) = diffResult
                val now = timeProvider()
                applier(current, actor, now)

                auditLogDao.insertAuditLog(
                    AuditLogEntity(
                        eventId = "AUD-" + UUID.randomUUID().toString(),
                        timestamp = now,
                        operatorId = actor.userId,
                        operatorName = actor.operatorName,
                        actionType = actionType,
                        entityType = AuditEntityType.APP_SETTINGS,
                        entityId = entityId,
                        summaryEn = summaryEn,
                        summaryBn = summaryBn,
                        beforeData = diffPair.first,
                        afterData = diffPair.second,
                        isSuccess = true
                    )
                )
                PosResult.Success(Unit)
            }
        } catch (e: Exception) {
            PosResult.Error(PosError.DatabaseError("Failed to update settings: ${e.message}", e))
        }
    }
}
