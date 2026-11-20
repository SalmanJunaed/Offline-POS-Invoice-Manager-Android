package com.example.pos.data.repository

import androidx.room.withTransaction
import com.example.pos.core.error.PosError
import com.example.pos.core.error.PosResult
import com.example.pos.data.audit.AuditActionType
import com.example.pos.data.audit.AuditEntityType
import com.example.pos.data.audit.AuditSanitizer
import com.example.pos.data.db.AuditLogDao
import com.example.pos.data.db.AuditLogEntity
import com.example.pos.data.db.CompanyHeaderDao
import com.example.pos.data.db.CompanyHeaderEntity
import com.example.pos.data.db.OperatorDao
import com.example.pos.data.db.PosDatabase
import kotlinx.coroutines.flow.Flow
import java.util.UUID

interface CompanyRepository {
    val companyHeader: Flow<CompanyHeaderEntity?>
    suspend fun getCompanyHeader(): CompanyHeaderEntity?
    suspend fun updateCompanyHeader(
        companyName: String,
        branchName: String,
        branchNumber: String,
        phoneNumber: String,
        address: String
    ): PosResult<Unit>

    suspend fun updateCompanyHeaderEntity(
        updatedHeader: CompanyHeaderEntity,
        logoChangeAction: String? = null
    ): PosResult<Unit>
}

class CompanyRepositoryImpl(
    private val companyHeaderDao: CompanyHeaderDao,
    private val operatorDao: OperatorDao,
    private val auditLogDao: AuditLogDao,
    private val db: PosDatabase,
    private val timeProvider: () -> Long = { System.currentTimeMillis() }
) : CompanyRepository {

    override val companyHeader: Flow<CompanyHeaderEntity?> = companyHeaderDao.getCompanyHeaderFlow()

    override suspend fun getCompanyHeader(): CompanyHeaderEntity? = companyHeaderDao.getCompanyHeader()

    override suspend fun updateCompanyHeader(
        companyName: String,
        branchName: String,
        branchNumber: String,
        phoneNumber: String,
        address: String
    ): PosResult<Unit> {
        return try {
            db.withTransaction {
                val actor = operatorDao.getActiveOperator()
                    ?: return@withTransaction PosResult.Error(PosError.Unauthenticated())

                val current = companyHeaderDao.getCompanyHeader()
                val newName = companyName.trim()
                val newBranch = branchName.trim()
                val newNum = branchNumber.trim()
                val newPhone = phoneNumber.trim()
                val newAddress = address.trim()

                val (beforeJson, afterJson) = if (current != null) {
                    AuditSanitizer.computeDiffSnapshots(
                        before = mapOf(
                            "companyName" to current.companyName,
                            "branchName" to current.branchName,
                            "branchNumber" to current.branchNumber,
                            "phoneNumber" to current.phoneNumber,
                            "address" to current.address
                        ),
                        after = mapOf(
                            "companyName" to newName,
                            "branchName" to newBranch,
                            "branchNumber" to newNum,
                            "phoneNumber" to newPhone,
                            "address" to newAddress
                        )
                    )
                } else {
                    null to AuditSanitizer.buildSnapshotJson(
                        mapOf(
                            "companyName" to newName,
                            "branchName" to newBranch,
                            "branchNumber" to newNum,
                            "phoneNumber" to newPhone,
                            "address" to newAddress
                        )
                    )
                }

                // Skip no-op edits
                if (current != null && beforeJson == null && afterJson == null) {
                    return@withTransaction PosResult.Success(Unit)
                }

                val now = timeProvider()
                val updated = current?.copy(
                    companyName = newName,
                    branchName = newBranch,
                    branchNumber = newNum,
                    phoneNumber = newPhone,
                    address = newAddress,
                    updatedAt = now,
                    updatedByOperatorId = actor.userId,
                    updatedByOperatorName = actor.operatorName
                ) ?: CompanyHeaderEntity(
                    id = 1,
                    companyName = newName,
                    branchName = newBranch,
                    branchNumber = newNum,
                    phoneNumber = newPhone,
                    address = newAddress,
                    createdAt = now,
                    updatedAt = now,
                    updatedByOperatorId = actor.userId,
                    updatedByOperatorName = actor.operatorName
                )
                companyHeaderDao.insertOrUpdate(updated)

                auditLogDao.insertAuditLog(
                    AuditLogEntity(
                        eventId = "AUD-" + UUID.randomUUID().toString(),
                        timestamp = now,
                        operatorId = actor.userId,
                        operatorName = actor.operatorName,
                        actionType = AuditActionType.COMPANY_HEADER_UPDATED,
                        entityType = AuditEntityType.COMPANY_HEADER,
                        entityId = "COMPANY-1",
                        summaryEn = "Updated company header & branch information",
                        summaryBn = "কোম্পানি হেডার ও শাখার তথ্য হালনাগাদ করা হয়েছে",
                        beforeData = beforeJson,
                        afterData = afterJson,
                        isSuccess = true
                    )
                )
                PosResult.Success(Unit)
            }
        } catch (e: Exception) {
            PosResult.Error(PosError.DatabaseError("Failed to update company header: ${e.message}", e))
        }
    }

    override suspend fun updateCompanyHeaderEntity(
        updatedHeader: CompanyHeaderEntity,
        logoChangeAction: String?
    ): PosResult<Unit> {
        return try {
            db.withTransaction {
                val actor = operatorDao.getActiveOperator()
                    ?: return@withTransaction PosResult.Error(PosError.Unauthenticated())

                val current = companyHeaderDao.getCompanyHeader()
                val isLogoEvent = logoChangeAction != null ||
                    (current != null && current.logoUri != updatedHeader.logoUri)

                if (isLogoEvent) {
                    val isRemoved = updatedHeader.logoUri.isNullOrBlank()
                    val actionType = if (isRemoved) {
                        AuditActionType.COMPANY_LOGO_REMOVED
                    } else {
                        AuditActionType.COMPANY_LOGO_UPDATED
                    }
                    val statusCode = when {
                        isRemoved -> "REMOVED"
                        logoChangeAction == "OFFICIAL_DEFAULT" -> "OFFICIAL_DEFAULT"
                        else -> "CUSTOM_CONFIGURED"
                    }
                    if (current != null && current.logoUri == updatedHeader.logoUri && logoChangeAction == null) {
                        return@withTransaction PosResult.Success(Unit)
                    }
                    val now = timeProvider()
                    val toSave = updatedHeader.copy(
                        id = 1,
                        createdAt = current?.createdAt ?: updatedHeader.createdAt,
                        updatedAt = now,
                        updatedByOperatorId = actor.userId,
                        updatedByOperatorName = actor.operatorName
                    )
                    companyHeaderDao.insertOrUpdate(toSave)

                    val prevStatus = if (current?.logoUri.isNullOrBlank()) "NONE" else "CONFIGURED"
                    auditLogDao.insertAuditLog(
                        AuditLogEntity(
                            eventId = "AUD-" + UUID.randomUUID().toString(),
                            timestamp = now,
                            operatorId = actor.userId,
                            operatorName = actor.operatorName,
                            actionType = actionType,
                            entityType = AuditEntityType.COMPANY_HEADER,
                            entityId = "COMPANY-LOGO",
                            summaryEn = if (isRemoved) "Removed company logo" else "Updated company logo",
                            summaryBn = if (isRemoved) "কোম্পানি লোগো মুছে ফেলা হয়েছে" else "কোম্পানি লোগো হালনাগাদ করা হয়েছে",
                            beforeData = AuditSanitizer.buildSnapshotJson(mapOf("logoStatus" to prevStatus)),
                            afterData = AuditSanitizer.buildSnapshotJson(mapOf("logoStatus" to statusCode)),
                            isSuccess = true
                        )
                    )
                    return@withTransaction PosResult.Success(Unit)
                }

                // Otherwise delegate header text update inside the same transaction logic
                val newName = updatedHeader.companyName.trim()
                val newBranch = updatedHeader.branchName.trim()
                val newNum = updatedHeader.branchNumber.trim()
                val newPhone = updatedHeader.phoneNumber.trim()
                val newAddress = updatedHeader.address.trim()

                val (beforeJson, afterJson) = if (current != null) {
                    AuditSanitizer.computeDiffSnapshots(
                        before = mapOf(
                            "companyName" to current.companyName,
                            "branchName" to current.branchName,
                            "branchNumber" to current.branchNumber,
                            "phoneNumber" to current.phoneNumber,
                            "address" to current.address
                        ),
                        after = mapOf(
                            "companyName" to newName,
                            "branchName" to newBranch,
                            "branchNumber" to newNum,
                            "phoneNumber" to newPhone,
                            "address" to newAddress
                        )
                    )
                } else {
                    null to AuditSanitizer.buildSnapshotJson(
                        mapOf(
                            "companyName" to newName,
                            "branchName" to newBranch,
                            "branchNumber" to newNum,
                            "phoneNumber" to newPhone,
                            "address" to newAddress
                        )
                    )
                }

                if (current != null && beforeJson == null && afterJson == null) {
                    return@withTransaction PosResult.Success(Unit)
                }

                val now = timeProvider()
                val toSave = updatedHeader.copy(
                    id = 1,
                    companyName = newName,
                    branchName = newBranch,
                    branchNumber = newNum,
                    phoneNumber = newPhone,
                    address = newAddress,
                    createdAt = current?.createdAt ?: updatedHeader.createdAt,
                    updatedAt = now,
                    updatedByOperatorId = actor.userId,
                    updatedByOperatorName = actor.operatorName
                )
                companyHeaderDao.insertOrUpdate(toSave)

                auditLogDao.insertAuditLog(
                    AuditLogEntity(
                        eventId = "AUD-" + UUID.randomUUID().toString(),
                        timestamp = now,
                        operatorId = actor.userId,
                        operatorName = actor.operatorName,
                        actionType = AuditActionType.COMPANY_HEADER_UPDATED,
                        entityType = AuditEntityType.COMPANY_HEADER,
                        entityId = "COMPANY-1",
                        summaryEn = "Updated company header & branch information",
                        summaryBn = "কোম্পানি হেডার ও শাখার তথ্য হালনাগাদ করা হয়েছে",
                        beforeData = beforeJson,
                        afterData = afterJson,
                        isSuccess = true
                    )
                )
                PosResult.Success(Unit)
            }
        } catch (e: Exception) {
            PosResult.Error(PosError.DatabaseError("Failed to update company profile: ${e.message}", e))
        }
    }
}
