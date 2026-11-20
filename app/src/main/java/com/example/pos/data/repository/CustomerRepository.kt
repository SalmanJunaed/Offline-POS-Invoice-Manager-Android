package com.example.pos.data.repository

import androidx.room.withTransaction
import com.example.pos.core.error.PosError
import com.example.pos.core.error.PosResult
import com.example.pos.data.audit.AuditActionType
import com.example.pos.data.audit.AuditEntityType
import com.example.pos.data.audit.AuditSanitizer
import com.example.pos.data.db.AuditLogDao
import com.example.pos.data.db.AuditLogEntity
import com.example.pos.data.db.CustomerDao
import com.example.pos.data.db.CustomerEntity
import com.example.pos.data.db.OperatorDao
import com.example.pos.data.db.OperatorEntity
import com.example.pos.data.db.PosDatabase
import kotlinx.coroutines.flow.Flow
import java.util.UUID

interface CustomerRepository {
    val allCustomers: Flow<List<CustomerEntity>>
    fun searchCustomersFlow(query: String, limit: Int = 20): Flow<List<CustomerEntity>>
    suspend fun searchCustomers(query: String, limit: Int = 20): List<CustomerEntity>
    suspend fun getCustomerByInfo(customerInfo: String): CustomerEntity?
    suspend fun saveCustomer(customer: CustomerEntity): PosResult<Unit>
    suspend fun recordCustomerOrder(customerInfo: String, totalPaisa: Long): PosResult<Unit>
    suspend fun deleteCustomer(customerInfo: String): PosResult<Unit>
}

class CustomerRepositoryImpl(
    private val customerDao: CustomerDao,
    private val operatorDao: OperatorDao,
    private val auditLogDao: AuditLogDao,
    private val db: PosDatabase,
    private val timeProvider: () -> Long = { System.currentTimeMillis() }
) : CustomerRepository {

    override val allCustomers: Flow<List<CustomerEntity>> = customerDao.getAllCustomersFlow()

    override fun searchCustomersFlow(query: String, limit: Int): Flow<List<CustomerEntity>> {
        return customerDao.searchCustomersFlow(query.trim(), limit)
    }

    override suspend fun searchCustomers(query: String, limit: Int): List<CustomerEntity> {
        return customerDao.searchCustomers(query.trim(), limit)
    }

    override suspend fun getCustomerByInfo(customerInfo: String): CustomerEntity? {
        return customerDao.getCustomerByInfo(customerInfo.trim())
    }

    override suspend fun saveCustomer(customer: CustomerEntity): PosResult<Unit> {
        return try {
            db.withTransaction {
                val actor = operatorDao.getActiveOperator()
                    ?: return@withTransaction PosResult.Error(PosError.Unauthenticated())

                val key = customer.customerInfo.trim()
                if (key.isBlank()) {
                    return@withTransaction PosResult.Error(PosError.ValidationError("Customer identifier cannot be empty."))
                }

                val existing = customerDao.getCustomerByInfo(key)
                val now = timeProvider()
                val newName = customer.customerName.trim()
                val newPhone = customer.phoneNumber.trim()
                val newAddress = customer.address.trim()
                val newNotes = customer.notes.trim()

                if (existing == null) {
                    val toInsert = customer.copy(
                        customerInfo = key,
                        customerName = newName,
                        phoneNumber = newPhone,
                        address = newAddress,
                        notes = newNotes,
                        createdAt = now,
                        updatedAt = now,
                        createdByOperatorId = actor.userId,
                        createdByOperatorName = actor.operatorName,
                        updatedByOperatorId = actor.userId,
                        updatedByOperatorName = actor.operatorName
                    )
                    customerDao.insertOrUpdateCustomer(toInsert)
                    auditLogDao.insertAuditLog(
                        AuditLogEntity(
                            eventId = "AUD-" + UUID.randomUUID().toString(),
                            timestamp = now,
                            operatorId = actor.userId,
                            operatorName = actor.operatorName,
                            actionType = AuditActionType.CUSTOMER_CREATED,
                            entityType = AuditEntityType.CUSTOMER,
                            entityId = key,
                            summaryEn = "Created customer \"$key\"",
                            summaryBn = "নতুন কাস্টমার \"$key\" যোগ করা হয়েছে",
                            beforeData = null,
                            afterData = AuditSanitizer.buildSnapshotJson(
                                mapOf(
                                    "customerInfo" to key,
                                    "customerName" to newName,
                                    "phoneNumber" to newPhone,
                                    "address" to newAddress,
                                    "notes" to newNotes
                                )
                            ),
                            isSuccess = true
                        )
                    )
                    return@withTransaction PosResult.Success(Unit)
                }

                val (beforeJson, afterJson) = AuditSanitizer.computeDiffSnapshots(
                    before = mapOf(
                        "customerName" to existing.customerName,
                        "phoneNumber" to existing.phoneNumber,
                        "address" to existing.address,
                        "notes" to existing.notes
                    ),
                    after = mapOf(
                        "customerName" to newName,
                        "phoneNumber" to newPhone,
                        "address" to newAddress,
                        "notes" to newNotes
                    )
                )

                if (beforeJson == null && afterJson == null) {
                    return@withTransaction PosResult.Success(Unit)
                }

                val toSave = existing.copy(
                    customerName = newName,
                    phoneNumber = newPhone,
                    address = newAddress,
                    notes = newNotes,
                    updatedAt = now,
                    updatedByOperatorId = actor.userId,
                    updatedByOperatorName = actor.operatorName
                )
                customerDao.insertOrUpdateCustomer(toSave)

                auditLogDao.insertAuditLog(
                    AuditLogEntity(
                        eventId = "AUD-" + UUID.randomUUID().toString(),
                        timestamp = now,
                        operatorId = actor.userId,
                        operatorName = actor.operatorName,
                        actionType = AuditActionType.CUSTOMER_UPDATED,
                        entityType = AuditEntityType.CUSTOMER,
                        entityId = key,
                        summaryEn = "Updated customer \"$key\"",
                        summaryBn = "কাস্টমার \"$key\"-এর তথ্য হালনাগাদ করা হয়েছে",
                        beforeData = beforeJson,
                        afterData = afterJson,
                        isSuccess = true
                    )
                )
                PosResult.Success(Unit)
            }
        } catch (e: Exception) {
            PosResult.Error(PosError.DatabaseError(e.message ?: "Failed to save customer", e))
        }
    }

    override suspend fun recordCustomerOrder(customerInfo: String, totalPaisa: Long): PosResult<Unit> {
        return try {
            db.withTransaction {
                val actor = operatorDao.getActiveOperator()
                    ?: return@withTransaction PosResult.Error(PosError.Unauthenticated())
                val trimmed = customerInfo.trim()
                if (trimmed.isNotBlank()) {
                    recordCustomerOrderWithinTransaction(
                        customerDao = customerDao,
                        auditLogDao = auditLogDao,
                        actor = actor,
                        customerInfo = trimmed,
                        totalPaisa = totalPaisa,
                        now = timeProvider()
                    )
                }
                PosResult.Success(Unit)
            }
        } catch (e: Exception) {
            PosResult.Error(PosError.DatabaseError(e.message ?: "Failed to record customer order", e))
        }
    }

    override suspend fun deleteCustomer(customerInfo: String): PosResult<Unit> {
        return try {
            db.withTransaction {
                val actor = operatorDao.getActiveOperator()
                    ?: return@withTransaction PosResult.Error(PosError.Unauthenticated())

                val key = customerInfo.trim()
                val existing = customerDao.getCustomerByInfo(key)
                    ?: return@withTransaction PosResult.Error(PosError.RecordNotFound("Customer", key))

                customerDao.deleteCustomer(key)
                val now = timeProvider()
                auditLogDao.insertAuditLog(
                    AuditLogEntity(
                        eventId = "AUD-" + UUID.randomUUID().toString(),
                        timestamp = now,
                        operatorId = actor.userId,
                        operatorName = actor.operatorName,
                        actionType = AuditActionType.CUSTOMER_DELETED,
                        entityType = AuditEntityType.CUSTOMER,
                        entityId = key,
                        summaryEn = "Deleted customer \"$key\"",
                        summaryBn = "কাস্টমার \"$key\" মুছে ফেলা হয়েছে",
                        beforeData = AuditSanitizer.buildSnapshotJson(
                            mapOf(
                                "customerInfo" to existing.customerInfo,
                                "customerName" to existing.customerName,
                                "phoneNumber" to existing.phoneNumber
                            )
                        ),
                        afterData = null,
                        isSuccess = true
                    )
                )
                PosResult.Success(Unit)
            }
        } catch (e: Exception) {
            PosResult.Error(PosError.DatabaseError(e.message ?: "Failed to delete customer", e))
        }
    }

    companion object {
        internal suspend fun recordCustomerOrderWithinTransaction(
            customerDao: CustomerDao,
            auditLogDao: AuditLogDao?,
            actor: OperatorEntity?,
            customerInfo: String,
            totalPaisa: Long,
            now: Long
        ) {
            val trimmed = customerInfo.trim()
            if (trimmed.isBlank() || trimmed == "External Customer") return

            val existing = customerDao.getCustomerByInfo(trimmed)
            if (existing != null) {
                customerDao.insertOrUpdateCustomer(
                    existing.copy(
                        totalOrders = existing.totalOrders + 1,
                        totalSpentPaisa = existing.totalSpentPaisa + totalPaisa,
                        updatedAt = now,
                        updatedByOperatorId = actor?.userId ?: existing.updatedByOperatorId,
                        updatedByOperatorName = actor?.operatorName ?: existing.updatedByOperatorName
                    )
                )
            } else {
                val phone = if (trimmed.all { it.isDigit() || it in "+- " }) trimmed else ""
                customerDao.insertOrUpdateCustomer(
                    CustomerEntity(
                        customerInfo = trimmed,
                        phoneNumber = phone,
                        totalOrders = 1,
                        totalSpentPaisa = totalPaisa,
                        createdAt = now,
                        updatedAt = now,
                        createdByOperatorId = actor?.userId,
                        createdByOperatorName = actor?.operatorName,
                        updatedByOperatorId = actor?.userId,
                        updatedByOperatorName = actor?.operatorName
                    )
                )
                if (actor != null && auditLogDao != null) {
                    auditLogDao.insertAuditLog(
                        AuditLogEntity(
                            eventId = "AUD-" + UUID.randomUUID().toString(),
                            timestamp = now,
                            operatorId = actor.userId,
                            operatorName = actor.operatorName,
                            actionType = AuditActionType.CUSTOMER_CREATED,
                            entityType = AuditEntityType.CUSTOMER,
                            entityId = trimmed,
                            summaryEn = "Created customer \"$trimmed\"",
                            summaryBn = "নতুন কাস্টমার \"$trimmed\" যোগ করা হয়েছে",
                            afterData = AuditSanitizer.buildSnapshotJson(
                                mapOf(
                                    "customerInfo" to trimmed,
                                    "phoneNumber" to phone
                                )
                            ),
                            isSuccess = true
                        )
                    )
                }
            }
        }
    }
}
