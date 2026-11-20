package com.example.pos.data.repository

import com.example.pos.core.error.PosError
import com.example.pos.core.error.PosResult
import com.example.pos.data.audit.AuditCategoryFilter
import com.example.pos.data.audit.AuditEntityType
import com.example.pos.data.db.AuditLogDao
import com.example.pos.data.db.AuditLogEntity
import com.example.pos.data.db.AuditOperatorOption
import com.example.pos.data.db.OperatorDao
import com.example.pos.data.db.OperatorEntity
import kotlinx.coroutines.flow.Flow
import java.util.UUID

interface AuditRepository {
    suspend fun requireActiveOperator(): OperatorEntity?

    suspend fun appendBusinessEvent(
        actor: OperatorEntity,
        actionType: String,
        entityType: String,
        entityId: String,
        summaryEn: String,
        summaryBn: String,
        beforeData: String? = null,
        afterData: String? = null,
        isSuccess: Boolean = true,
        metadataJson: String? = null,
        timestamp: Long = System.currentTimeMillis()
    ): Long

    suspend fun appendSystemOrAuthEvent(
        operatorId: String = AuditEntityType.SYSTEM_OPERATOR_ID,
        operatorName: String = AuditEntityType.SYSTEM_OPERATOR_NAME_EN,
        actionType: String,
        entityType: String = AuditEntityType.OPERATOR,
        entityId: String,
        summaryEn: String,
        summaryBn: String,
        beforeData: String? = null,
        afterData: String? = null,
        isSuccess: Boolean = true,
        metadataJson: String? = null,
        timestamp: Long = System.currentTimeMillis()
    ): Long

    fun getAuditLogsPaged(
        operatorFilter: String,
        categoryFilter: AuditCategoryFilter,
        query: String,
        startDate: Long,
        endDate: Long,
        limit: Int,
        offset: Int
    ): Flow<List<AuditLogEntity>>

    fun getAuditLogsCount(
        operatorFilter: String,
        categoryFilter: AuditCategoryFilter,
        query: String,
        startDate: Long,
        endDate: Long
    ): Flow<Int>

    fun getDistinctAuditOperators(): Flow<List<AuditOperatorOption>>

    fun getAuditLogsForEntity(entityType: String, entityId: String): Flow<List<AuditLogEntity>>

    fun getTotalAuditLogCount(): Flow<Int>
}

class AuditRepositoryImpl(
    private val auditLogDao: AuditLogDao,
    private val operatorDao: OperatorDao,
    private val timeProvider: () -> Long = { System.currentTimeMillis() }
) : AuditRepository {

    override suspend fun requireActiveOperator(): OperatorEntity? = operatorDao.getActiveOperator()

    override suspend fun appendBusinessEvent(
        actor: OperatorEntity,
        actionType: String,
        entityType: String,
        entityId: String,
        summaryEn: String,
        summaryBn: String,
        beforeData: String?,
        afterData: String?,
        isSuccess: Boolean,
        metadataJson: String?,
        timestamp: Long
    ): Long {
        val entry = AuditLogEntity(
            eventId = "AUD-" + UUID.randomUUID().toString(),
            timestamp = timestamp,
            operatorId = actor.userId,
            operatorName = actor.operatorName,
            actionType = actionType,
            entityType = entityType,
            entityId = entityId,
            summaryEn = summaryEn,
            summaryBn = summaryBn,
            beforeData = beforeData,
            afterData = afterData,
            isSuccess = isSuccess,
            metadataJson = metadataJson
        )
        return auditLogDao.insertAuditLog(entry)
    }

    override suspend fun appendSystemOrAuthEvent(
        operatorId: String,
        operatorName: String,
        actionType: String,
        entityType: String,
        entityId: String,
        summaryEn: String,
        summaryBn: String,
        beforeData: String?,
        afterData: String?,
        isSuccess: Boolean,
        metadataJson: String?,
        timestamp: Long
    ): Long {
        val entry = AuditLogEntity(
            eventId = "AUD-" + UUID.randomUUID().toString(),
            timestamp = timestamp,
            operatorId = operatorId,
            operatorName = operatorName,
            actionType = actionType,
            entityType = entityType,
            entityId = entityId,
            summaryEn = summaryEn,
            summaryBn = summaryBn,
            beforeData = beforeData,
            afterData = afterData,
            isSuccess = isSuccess,
            metadataJson = metadataJson
        )
        return auditLogDao.insertAuditLog(entry)
    }

    override fun getAuditLogsPaged(
        operatorFilter: String,
        categoryFilter: AuditCategoryFilter,
        query: String,
        startDate: Long,
        endDate: Long,
        limit: Int,
        offset: Int
    ): Flow<List<AuditLogEntity>> = auditLogDao.getAuditLogsPagedFlow(
        operatorFilter = operatorFilter.trim(),
        categoryFilter = categoryFilter.sqlCode,
        query = query.trim(),
        startDate = startDate,
        endDate = endDate,
        limit = limit,
        offset = offset
    )

    override fun getAuditLogsCount(
        operatorFilter: String,
        categoryFilter: AuditCategoryFilter,
        query: String,
        startDate: Long,
        endDate: Long
    ): Flow<Int> = auditLogDao.getAuditLogsCountFlow(
        operatorFilter = operatorFilter.trim(),
        categoryFilter = categoryFilter.sqlCode,
        query = query.trim(),
        startDate = startDate,
        endDate = endDate
    )

    override fun getDistinctAuditOperators(): Flow<List<AuditOperatorOption>> =
        auditLogDao.getDistinctAuditOperatorsFlow()

    override fun getAuditLogsForEntity(entityType: String, entityId: String): Flow<List<AuditLogEntity>> =
        auditLogDao.getAuditLogsForEntityFlow(entityType, entityId)

    override fun getTotalAuditLogCount(): Flow<Int> = auditLogDao.getTotalAuditLogCountFlow()

    companion object {
        fun unauthenticatedError(): PosResult.Error =
            PosResult.Error(PosError.Unauthenticated())
    }
}
