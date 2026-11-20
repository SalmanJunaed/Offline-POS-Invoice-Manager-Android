package com.example.pos.data.repository

import androidx.room.withTransaction
import com.example.pos.core.error.PosError
import com.example.pos.core.error.PosResult
import com.example.pos.data.audit.AuditActionType
import com.example.pos.data.audit.AuditEntityType
import com.example.pos.data.audit.AuditSanitizer
import com.example.pos.data.db.AuditLogDao
import com.example.pos.data.db.AuditLogEntity
import com.example.pos.data.db.OperatorDao
import com.example.pos.data.db.PosDatabase
import com.example.pos.data.db.ProductDao
import com.example.pos.data.db.ProductEntity
import com.example.pos.domain.FinancialCalculationEngine
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import java.util.UUID

interface ProductRepository {
    val activeProducts: Flow<List<ProductEntity>>
    val allProducts: Flow<List<ProductEntity>>
    suspend fun getProductById(itemId: String): ProductEntity?
    suspend fun saveProduct(
        itemId: String?,
        itemName: String,
        itemType: String,
        unit: String,
        unitPricePaisa: Long,
        sortOrder: Int
    ): PosResult<ProductEntity>

    suspend fun saveProductEntity(product: ProductEntity): PosResult<ProductEntity>
    suspend fun deleteProduct(itemId: String): PosResult<Unit>
    suspend fun updateSortOrder(itemId: String, newOrder: Int): PosResult<Unit>
    suspend fun updateProductReorder(orderedItemIds: List<String>): PosResult<Unit>
}

class ProductRepositoryImpl(
    private val productDao: ProductDao,
    private val operatorDao: OperatorDao,
    private val auditLogDao: AuditLogDao,
    private val db: PosDatabase,
    private val timeProvider: () -> Long = { System.currentTimeMillis() }
) : ProductRepository {

    override val activeProducts: Flow<List<ProductEntity>> = productDao.getAllActiveProductsFlow()
    override val allProducts: Flow<List<ProductEntity>> = productDao.getAllProductsFlow()

    override suspend fun getProductById(itemId: String): ProductEntity? = productDao.getProductById(itemId)

    override suspend fun saveProduct(
        itemId: String?,
        itemName: String,
        itemType: String,
        unit: String,
        unitPricePaisa: Long,
        sortOrder: Int
    ): PosResult<ProductEntity> {
        val id = itemId?.takeIf { it.isNotBlank() } ?: UUID.randomUUID().toString()
        val trimmedName = itemName.trim()
        val hasBengali = trimmedName.any { it in '\u0980'..'\u09FF' }
        val entity = ProductEntity(
            itemId = id,
            itemName = trimmedName,
            nameBn = if (hasBengali) trimmedName else "",
            nameEn = if (!hasBengali) trimmedName else "",
            itemType = itemType,
            unit = unit.trim(),
            defaultUnitPricePaisa = unitPricePaisa,
            sortOrder = sortOrder,
            isActive = true
        )
        return saveProductEntity(entity)
    }

    override suspend fun saveProductEntity(product: ProductEntity): PosResult<ProductEntity> {
        return try {
            db.withTransaction {
                val actor = operatorDao.getActiveOperator()
                    ?: return@withTransaction PosResult.Error(PosError.Unauthenticated())

                val existing = productDao.getProductById(product.itemId)
                val now = timeProvider()
                val trimmedName = product.itemName.trim()
                val trimmedBn = product.nameBn.trim()
                val trimmedEn = product.nameEn.trim()
                val trimmedUnit = product.unit.trim()

                if (existing == null) {
                    val toInsert = product.copy(
                        itemName = trimmedName,
                        nameBn = trimmedBn,
                        nameEn = trimmedEn,
                        unit = trimmedUnit,
                        createdAt = now,
                        updatedAt = now,
                        createdByOperatorId = actor.userId,
                        createdByOperatorName = actor.operatorName,
                        updatedByOperatorId = actor.userId,
                        updatedByOperatorName = actor.operatorName
                    )
                    productDao.insertProduct(toInsert)

                    val displayEn = toInsert.getDisplayName("en")
                    val displayBn = toInsert.getDisplayName("bn")
                    val priceBdt = FinancialCalculationEngine.formatPaisaToBdt(toInsert.defaultUnitPricePaisa)

                    auditLogDao.insertAuditLog(
                        AuditLogEntity(
                            eventId = "AUD-" + UUID.randomUUID().toString(),
                            timestamp = now,
                            operatorId = actor.userId,
                            operatorName = actor.operatorName,
                            actionType = AuditActionType.PRODUCT_CREATED,
                            entityType = AuditEntityType.PRODUCT,
                            entityId = toInsert.itemId,
                            summaryEn = "Created product \"$displayEn\" ($priceBdt BDT/${toInsert.unit})",
                            summaryBn = "নতুন পণ্য \"$displayBn\" (${AuditSanitizer.formatPaisaAsBdt(toInsert.defaultUnitPricePaisa, "bn")}/${toInsert.unit}) যোগ করা হয়েছে",
                            beforeData = null,
                            afterData = AuditSanitizer.buildSnapshotJson(
                                mapOf(
                                    "itemName" to toInsert.itemName,
                                    "nameBn" to toInsert.nameBn,
                                    "nameEn" to toInsert.nameEn,
                                    "itemType" to toInsert.itemType,
                                    "unit" to toInsert.unit,
                                    "unitPriceBdt" to priceBdt,
                                    "sortOrder" to toInsert.sortOrder
                                )
                            ),
                            isSuccess = true
                        )
                    )
                    return@withTransaction PosResult.Success(toInsert)
                }

                // Existing product edit: preserve original creator & createdAt
                val oldPriceBdt = FinancialCalculationEngine.formatPaisaToBdt(existing.defaultUnitPricePaisa)
                val newPriceBdt = FinancialCalculationEngine.formatPaisaToBdt(product.defaultUnitPricePaisa)
                val (beforeJson, afterJson) = AuditSanitizer.computeDiffSnapshots(
                    before = mapOf(
                        "itemName" to existing.itemName,
                        "nameBn" to existing.nameBn,
                        "nameEn" to existing.nameEn,
                        "itemType" to existing.itemType,
                        "unit" to existing.unit,
                        "unitPriceBdt" to oldPriceBdt,
                        "sortOrder" to existing.sortOrder,
                        "isActive" to existing.isActive
                    ),
                    after = mapOf(
                        "itemName" to trimmedName,
                        "nameBn" to trimmedBn,
                        "nameEn" to trimmedEn,
                        "itemType" to product.itemType,
                        "unit" to trimmedUnit,
                        "unitPriceBdt" to newPriceBdt,
                        "sortOrder" to product.sortOrder,
                        "isActive" to product.isActive
                    )
                )

                // Skip no-op edits cleanly
                if (beforeJson == null && afterJson == null) {
                    return@withTransaction PosResult.Success(existing)
                }

                val toUpdate = product.copy(
                    itemName = trimmedName,
                    nameBn = trimmedBn,
                    nameEn = trimmedEn,
                    unit = trimmedUnit,
                    createdAt = existing.createdAt,
                    updatedAt = now,
                    createdByOperatorId = existing.createdByOperatorId,
                    createdByOperatorName = existing.createdByOperatorName,
                    updatedByOperatorId = actor.userId,
                    updatedByOperatorName = actor.operatorName
                )
                productDao.insertProduct(toUpdate)

                val displayEn = toUpdate.getDisplayName("en")
                val displayBn = toUpdate.getDisplayName("bn")
                auditLogDao.insertAuditLog(
                    AuditLogEntity(
                        eventId = "AUD-" + UUID.randomUUID().toString(),
                        timestamp = now,
                        operatorId = actor.userId,
                        operatorName = actor.operatorName,
                        actionType = AuditActionType.PRODUCT_UPDATED,
                        entityType = AuditEntityType.PRODUCT,
                        entityId = toUpdate.itemId,
                        summaryEn = "Updated product \"$displayEn\"",
                        summaryBn = "পণ্য \"$displayBn\" হালনাগাদ করা হয়েছে",
                        beforeData = beforeJson,
                        afterData = afterJson,
                        isSuccess = true
                    )
                )
                PosResult.Success(toUpdate)
            }
        } catch (e: Exception) {
            PosResult.Error(PosError.DatabaseError("Failed to save product: ${e.message}", e))
        }
    }

    override suspend fun deleteProduct(itemId: String): PosResult<Unit> {
        return try {
            db.withTransaction {
                val actor = operatorDao.getActiveOperator()
                    ?: return@withTransaction PosResult.Error(PosError.Unauthenticated())

                val existing = productDao.getProductById(itemId)
                    ?: return@withTransaction PosResult.Error(PosError.RecordNotFound("Product", itemId))

                productDao.deleteProductById(itemId)

                val now = timeProvider()
                val displayEn = existing.getDisplayName("en")
                val displayBn = existing.getDisplayName("bn")
                val priceBdt = FinancialCalculationEngine.formatPaisaToBdt(existing.defaultUnitPricePaisa)

                auditLogDao.insertAuditLog(
                    AuditLogEntity(
                        eventId = "AUD-" + UUID.randomUUID().toString(),
                        timestamp = now,
                        operatorId = actor.userId,
                        operatorName = actor.operatorName,
                        actionType = AuditActionType.PRODUCT_DELETED,
                        entityType = AuditEntityType.PRODUCT,
                        entityId = existing.itemId,
                        summaryEn = "Deleted product \"$displayEn\"",
                        summaryBn = "পণ্য \"$displayBn\" মুছে ফেলা হয়েছে",
                        beforeData = AuditSanitizer.buildSnapshotJson(
                            mapOf(
                                "itemName" to existing.itemName,
                                "nameBn" to existing.nameBn,
                                "nameEn" to existing.nameEn,
                                "itemType" to existing.itemType,
                                "unit" to existing.unit,
                                "unitPriceBdt" to priceBdt
                            )
                        ),
                        afterData = null,
                        isSuccess = true
                    )
                )
                PosResult.Success(Unit)
            }
        } catch (e: Exception) {
            PosResult.Error(PosError.DatabaseError("Failed to delete product: ${e.message}", e))
        }
    }

    override suspend fun updateSortOrder(itemId: String, newOrder: Int): PosResult<Unit> {
        return try {
            db.withTransaction {
                val actor = operatorDao.getActiveOperator()
                    ?: return@withTransaction PosResult.Error(PosError.Unauthenticated())

                val existing = productDao.getProductById(itemId)
                    ?: return@withTransaction PosResult.Error(PosError.RecordNotFound("Product", itemId))

                if (existing.sortOrder == newOrder) {
                    return@withTransaction PosResult.Success(Unit)
                }

                val now = timeProvider()
                productDao.insertProduct(
                    existing.copy(
                        sortOrder = newOrder,
                        updatedAt = now,
                        updatedByOperatorId = actor.userId,
                        updatedByOperatorName = actor.operatorName
                    )
                )

                val displayEn = existing.getDisplayName("en")
                val displayBn = existing.getDisplayName("bn")
                auditLogDao.insertAuditLog(
                    AuditLogEntity(
                        eventId = "AUD-" + UUID.randomUUID().toString(),
                        timestamp = now,
                        operatorId = actor.userId,
                        operatorName = actor.operatorName,
                        actionType = AuditActionType.PRODUCT_REORDERED,
                        entityType = AuditEntityType.PRODUCT,
                        entityId = existing.itemId,
                        summaryEn = "Updated sort order for \"$displayEn\" (#${existing.sortOrder} -> #$newOrder)",
                        summaryBn = "পণ্য \"$displayBn\"-এর ক্রম পরিবর্তন (#${AuditSanitizer.localizeCount(existing.sortOrder, "bn")} -> #${AuditSanitizer.localizeCount(newOrder, "bn")})",
                        beforeData = AuditSanitizer.buildSnapshotJson(mapOf("sortOrder" to existing.sortOrder)),
                        afterData = AuditSanitizer.buildSnapshotJson(mapOf("sortOrder" to newOrder)),
                        isSuccess = true
                    )
                )
                PosResult.Success(Unit)
            }
        } catch (e: Exception) {
            PosResult.Error(PosError.DatabaseError("Failed to reorder product: ${e.message}", e))
        }
    }

    override suspend fun updateProductReorder(orderedItemIds: List<String>): PosResult<Unit> {
        return try {
            db.withTransaction {
                val actor = operatorDao.getActiveOperator()
                    ?: return@withTransaction PosResult.Error(PosError.Unauthenticated())

                if (orderedItemIds.isEmpty()) {
                    return@withTransaction PosResult.Success(Unit)
                }

                val now = timeProvider()
                val currentProducts = productDao.getAllProductsFlow().first().associateBy { it.itemId }
                var changedCount = 0
                orderedItemIds.forEachIndexed { index, id ->
                    val existing = currentProducts[id] ?: return@forEachIndexed
                    val targetOrder = index + 1
                    if (existing.sortOrder != targetOrder) {
                        changedCount++
                        productDao.insertProduct(
                            existing.copy(
                                sortOrder = targetOrder,
                                updatedAt = now,
                                updatedByOperatorId = actor.userId,
                                updatedByOperatorName = actor.operatorName
                            )
                        )
                    }
                }

                if (changedCount == 0) {
                    return@withTransaction PosResult.Success(Unit)
                }

                auditLogDao.insertAuditLog(
                    AuditLogEntity(
                        eventId = "AUD-" + UUID.randomUUID().toString(),
                        timestamp = now,
                        operatorId = actor.userId,
                        operatorName = actor.operatorName,
                        actionType = AuditActionType.PRODUCT_REORDERED,
                        entityType = AuditEntityType.PRODUCT,
                        entityId = "CATALOG-REORDER",
                        summaryEn = "Reordered catalog display sequence ($changedCount products updated)",
                        summaryBn = "পণ্যের ডিসপ্লে ক্রম পুনর্বিন্যাস (${AuditSanitizer.localizeCount(changedCount, "bn")}টি পণ্য)",
                        metadataJson = AuditSanitizer.buildSnapshotJson(mapOf("reorderedCount" to changedCount)),
                        isSuccess = true
                    )
                )
                PosResult.Success(Unit)
            }
        } catch (e: Exception) {
            PosResult.Error(PosError.DatabaseError("Failed to reorder catalog: ${e.message}", e))
        }
    }
}
