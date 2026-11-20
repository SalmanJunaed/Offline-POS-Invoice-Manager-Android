package com.example.pos.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.pos.core.util.DateTimeUtils
import com.example.pos.data.Localization
import com.example.pos.data.audit.AuditCategoryFilter
import com.example.pos.data.audit.AuditDateFilter
import com.example.pos.data.audit.AuditEntityType
import com.example.pos.data.audit.AuditSanitizer
import com.example.pos.data.db.AuditLogEntity
import com.example.pos.ui.viewmodel.PosViewModel
import com.example.ui.theme.PosBorderGreen
import com.example.ui.theme.PosBorderNeutral
import com.example.ui.theme.PosCharcoalText
import com.example.ui.theme.PosCleanWhite
import com.example.ui.theme.PosDeepGreen
import com.example.ui.theme.PosDueRed
import com.example.ui.theme.PosLightGreen
import com.example.ui.theme.PosMintGreen
import com.example.ui.theme.PosMutedText
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun EmployeeActivityScreen(
    viewModel: PosViewModel,
    modifier: Modifier = Modifier
) {
    val language by viewModel.language.collectAsState()
    val auditLogs by viewModel.auditLogs.collectAsState()
    val auditTotalCount by viewModel.auditTotalCount.collectAsState()
    val currentPage by viewModel.auditCurrentPage.collectAsState()
    val operatorFilter by viewModel.auditOperatorFilter.collectAsState()
    val categoryFilter by viewModel.auditCategoryFilter.collectAsState()
    val dateFilter by viewModel.auditDateFilter.collectAsState()
    val query by viewModel.auditQuery.collectAsState()
    val operators by viewModel.auditOperators.collectAsState()
    val pageSize = viewModel.auditPageSize

    var selectedEntry by remember { mutableStateOf<AuditLogEntity?>(null) }
    var showEmployeeMenu by remember { mutableStateOf(false) }
    var showCustomDateDialog by remember { mutableStateOf(false) }

    val totalPages = if (auditTotalCount == 0) 1 else ((auditTotalCount - 1) / pageSize) + 1
    val selectedOperatorLabel = when (operatorFilter) {
        "" -> Localization.get(Localization.Key.ACTIVITY_FILTER_ALL_EMPLOYEES, language)
        "__SYSTEM__" -> Localization.get(Localization.Key.ACTIVITY_FILTER_SYSTEM, language)
        else -> operators.firstOrNull { it.operatorId == operatorFilter }?.operatorName ?: operatorFilter
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(PosMintGreen.copy(alpha = 0.35f))
            .padding(horizontal = 14.dp, vertical = 10.dp)
            .testTag("employee_activity_screen")
    ) {
        // Header Summary Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp),
            colors = CardDefaults.cardColors(containerColor = PosCleanWhite),
            border = BorderStroke(1.dp, PosBorderGreen)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(14.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = PosLightGreen,
                        modifier = Modifier.size(40.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(Icons.Default.History, contentDescription = null, tint = PosDeepGreen)
                        }
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = Localization.get(Localization.Key.ACTIVITY_TITLE, language),
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                            color = PosCharcoalText
                        )
                        Text(
                            text = Localization.get(Localization.Key.ACTIVITY_SUBTITLE, language),
                            style = MaterialTheme.typography.bodySmall,
                            color = PosMutedText,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                Spacer(modifier = Modifier.width(8.dp))
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = PosDeepGreen,
                    modifier = Modifier.testTag("activity_total_count_badge")
                ) {
                    Text(
                        text = "${Localization.get(Localization.Key.ACTIVITY_RECORDS_COUNT, language)}: ${AuditSanitizer.localizeCount(auditTotalCount, language)}",
                        color = Color.White,
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Search + Employee Filter Row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = query,
                onValueChange = { viewModel.setAuditQuery(it) },
                placeholder = {
                    Text(
                        text = Localization.get(Localization.Key.ACTIVITY_SEARCH_HINT, language),
                        fontSize = 12.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = PosDeepGreen) },
                trailingIcon = {
                    if (query.isNotEmpty()) {
                        IconButton(
                            onClick = { viewModel.setAuditQuery("") },
                            modifier = Modifier.defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
                        ) {
                            Icon(Icons.Default.Clear, contentDescription = Localization.get(Localization.Key.CANCEL, language))
                        }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier
                    .weight(1.3f)
                    .testTag("activity_search_input")
            )

            Box(modifier = Modifier.weight(0.9f)) {
                OutlinedButton(
                    onClick = { showEmployeeMenu = true },
                    shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(1.dp, PosBorderGreen),
                    colors = ButtonDefaults.outlinedButtonColors(containerColor = PosCleanWhite),
                    modifier = Modifier
                        .fillMaxWidth()
                        .defaultMinSize(minHeight = 48.dp)
                        .testTag("activity_employee_filter_btn")
                ) {
                    Icon(Icons.Default.Person, contentDescription = null, tint = PosDeepGreen, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = selectedOperatorLabel,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        color = PosCharcoalText,
                        style = MaterialTheme.typography.labelMedium,
                        modifier = Modifier.weight(1f)
                    )
                    Icon(Icons.Default.ArrowDropDown, contentDescription = null, tint = PosMutedText)
                }

                DropdownMenu(
                    expanded = showEmployeeMenu,
                    onDismissRequest = { showEmployeeMenu = false }
                ) {
                    DropdownMenuItem(
                        text = { Text(Localization.get(Localization.Key.ACTIVITY_FILTER_ALL_EMPLOYEES, language)) },
                        modifier = Modifier.testTag("activity_op_all"),
                        onClick = {
                            viewModel.setAuditOperatorFilter("")
                            showEmployeeMenu = false
                        }
                    )
                    DropdownMenuItem(
                        text = { Text(Localization.get(Localization.Key.ACTIVITY_FILTER_SYSTEM, language)) },
                        modifier = Modifier.testTag("activity_op_system"),
                        onClick = {
                            viewModel.setAuditOperatorFilter("__SYSTEM__")
                            showEmployeeMenu = false
                        }
                    )
                    operators.forEach { op ->
                        DropdownMenuItem(
                            text = { Text("${op.operatorName} (${op.operatorId})") },
                            modifier = Modifier.testTag("activity_op_${op.operatorId}"),
                            onClick = {
                                viewModel.setAuditOperatorFilter(op.operatorId)
                                showEmployeeMenu = false
                            }
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        // Category Filter Chips
        LazyRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            items(AuditCategoryFilter.entries) { cat ->
                val selected = categoryFilter == cat
                FilterChip(
                    selected = selected,
                    onClick = { viewModel.setAuditCategoryFilter(cat) },
                    label = { Text(localizedCategoryLabel(cat, language), fontSize = 11.sp) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = PosDeepGreen,
                        selectedLabelColor = Color.White,
                        containerColor = PosCleanWhite
                    ),
                    modifier = Modifier.testTag("activity_cat_${cat.name.lowercase()}")
                )
            }
        }

        // Date Filter Chips
        LazyRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            items(AuditDateFilter.entries) { df ->
                val selected = dateFilter == df
                FilterChip(
                    selected = selected,
                    onClick = {
                        if (df == AuditDateFilter.CUSTOM) {
                            showCustomDateDialog = true
                        } else {
                            viewModel.setAuditDateFilter(df)
                        }
                    },
                    label = { Text(localizedDateFilterLabel(df, language), fontSize = 11.sp) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = PosLightGreen,
                        selectedLabelColor = PosDeepGreen,
                        containerColor = PosCleanWhite
                    ),
                    modifier = Modifier.testTag("activity_date_${df.name.lowercase()}")
                )
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        // Activity Feed List
        if (auditLogs.isEmpty()) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = PosCleanWhite),
                border = BorderStroke(1.dp, PosBorderNeutral)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        imageVector = Icons.Default.History,
                        contentDescription = null,
                        tint = PosMutedText,
                        modifier = Modifier.size(40.dp)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = Localization.get(Localization.Key.ACTIVITY_EMPTY_STATE, language),
                        style = MaterialTheme.typography.bodyMedium,
                        color = PosMutedText
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedButton(
                        onClick = { viewModel.resetAuditFilters() },
                        modifier = Modifier
                            .defaultMinSize(minHeight = 48.dp)
                            .testTag("activity_reset_filters_btn")
                    ) {
                        Text(Localization.get(Localization.Key.ACTIVITY_RESET_FILTERS, language))
                    }
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .testTag("activity_log_list"),
                contentPadding = PaddingValues(vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(auditLogs, key = { it.eventId }) { entry ->
                    AuditLogRowCard(
                        entry = entry,
                        language = language,
                        onClick = { selectedEntry = entry }
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        // Pagination Bar (30 per page)
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(10.dp),
            color = PosCleanWhite,
            border = BorderStroke(1.dp, PosBorderGreen)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedButton(
                    onClick = { viewModel.prevAuditPage() },
                    enabled = currentPage > 0,
                    modifier = Modifier
                        .defaultMinSize(minHeight = 48.dp)
                        .testTag("activity_prev_btn")
                ) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(Localization.get(Localization.Key.BTN_PREV_PAGE, language), fontSize = 12.sp)
                }

                Text(
                    text = "${Localization.get(Localization.Key.PAGE_LABEL, language)} ${AuditSanitizer.localizeCount(currentPage + 1, language)} / ${AuditSanitizer.localizeCount(totalPages, language)}",
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                    color = PosCharcoalText,
                    modifier = Modifier.testTag("activity_page_indicator")
                )

                OutlinedButton(
                    onClick = { viewModel.nextAuditPage() },
                    enabled = (currentPage + 1) * pageSize < auditTotalCount,
                    modifier = Modifier
                        .defaultMinSize(minHeight = 48.dp)
                        .testTag("activity_next_btn")
                ) {
                    Text(Localization.get(Localization.Key.BTN_NEXT_PAGE, language), fontSize = 12.sp)
                    Spacer(modifier = Modifier.width(4.dp))
                    Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null, modifier = Modifier.size(16.dp))
                }
            }
        }
    }

    selectedEntry?.let { entry ->
        AuditLogDetailDialog(
            entry = entry,
            language = language,
            onDismiss = { selectedEntry = null }
        )
    }

    if (showCustomDateDialog) {
        CustomDateRangeDialog(
            language = language,
            onDismiss = { showCustomDateDialog = false },
            onApply = { startMillis, endMillis ->
                showCustomDateDialog = false
                viewModel.setAuditCustomDateRange(startMillis, endMillis)
            }
        )
    }
}

@Composable
private fun AuditLogRowCard(
    entry: AuditLogEntity,
    language: String,
    onClick: () -> Unit
) {
    val isBn = language.equals("bn", ignoreCase = true)
    val summary = if (isBn) entry.summaryBn else entry.summaryEn
    val operatorDisplay = if (entry.operatorId == AuditEntityType.SYSTEM_OPERATOR_ID) {
        Localization.get(Localization.Key.ACTIVITY_FILTER_SYSTEM, language)
    } else {
        entry.operatorName
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .testTag("audit_log_card_${entry.eventId}"),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = PosCleanWhite),
        border = BorderStroke(1.dp, if (entry.isSuccess) PosBorderGreen else PosDueRed.copy(alpha = 0.45f))
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = if (entry.isSuccess) PosLightGreen else PosDueRed.copy(alpha = 0.12f)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = if (entry.isSuccess) Icons.Default.CheckCircle else Icons.Default.ErrorOutline,
                            contentDescription = null,
                            tint = if (entry.isSuccess) PosDeepGreen else PosDueRed,
                            modifier = Modifier.size(13.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = localizedActionLabel(entry.actionType, language),
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                            color = if (entry.isSuccess) PosDeepGreen else PosDueRed
                        )
                    }
                }

                Text(
                    text = formatAuditTimestamp(entry.timestamp, language),
                    style = MaterialTheme.typography.labelSmall,
                    color = PosMutedText
                )
            }

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = summary,
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                color = PosCharcoalText
            )

            Spacer(modifier = Modifier.height(6.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "$operatorDisplay (${entry.operatorId})",
                    style = MaterialTheme.typography.labelSmall,
                    color = PosDeepGreen,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = entry.entityId,
                    style = MaterialTheme.typography.labelSmall,
                    color = PosMutedText,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
fun AuditLogDetailDialog(
    entry: AuditLogEntity,
    language: String,
    onDismiss: () -> Unit
) {
    val isBn = language.equals("bn", ignoreCase = true)
    val beforePairs = remember(entry.beforeData, language) {
        AuditSanitizer.parseSnapshotPairs(entry.beforeData, language)
    }
    val afterPairs = remember(entry.afterData, language) {
        AuditSanitizer.parseSnapshotPairs(entry.afterData, language)
    }
    val metadataPairs = remember(entry.metadataJson, language) {
        AuditSanitizer.parseSnapshotPairs(entry.metadataJson, language)
    }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .testTag("audit_detail_dialog"),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = PosCleanWhite)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = Localization.get(Localization.Key.ACTIVITY_DETAIL_TITLE, language),
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = PosCharcoalText
                    )
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier
                            .defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
                            .testTag("audit_detail_close_btn")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = Localization.get(Localization.Key.BTN_CLOSE, language)
                        )
                    }
                }

                HorizontalDivider(color = PosBorderNeutral)
                Spacer(modifier = Modifier.height(10.dp))

                DetailInfoRow(
                    label = Localization.get(Localization.Key.ACTIVITY_DETAIL_SUMMARY, language),
                    value = if (isBn) entry.summaryBn else entry.summaryEn
                )
                DetailInfoRow(
                    label = Localization.get(Localization.Key.ACTIVITY_DETAIL_OPERATOR, language),
                    value = "${entry.operatorName} (${entry.operatorId})"
                )
                DetailInfoRow(
                    label = Localization.get(Localization.Key.ACTIVITY_DETAIL_TIMESTAMP, language),
                    value = formatAuditTimestamp(entry.timestamp, language)
                )
                DetailInfoRow(
                    label = Localization.get(Localization.Key.ACTIVITY_DETAIL_ACTION, language),
                    value = "${localizedActionLabel(entry.actionType, language)} (${
                        if (entry.isSuccess) Localization.get(Localization.Key.ACTIVITY_STATUS_SUCCESS, language)
                        else Localization.get(Localization.Key.ACTIVITY_STATUS_FAILED, language)
                    })"
                )
                DetailInfoRow(
                    label = Localization.get(Localization.Key.ACTIVITY_DETAIL_ENTITY, language),
                    value = "${entry.entityType} • ${entry.entityId}"
                )

                if (beforePairs.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(10.dp))
                    SnapshotPairsCard(
                        title = Localization.get(Localization.Key.ACTIVITY_DETAIL_BEFORE, language),
                        pairs = beforePairs,
                        containerColor = PosDueRed.copy(alpha = 0.06f),
                        modifier = Modifier.testTag("audit_detail_before_section")
                    )
                }

                if (afterPairs.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(10.dp))
                    SnapshotPairsCard(
                        title = Localization.get(Localization.Key.ACTIVITY_DETAIL_AFTER, language),
                        pairs = afterPairs,
                        containerColor = PosMintGreen,
                        modifier = Modifier.testTag("audit_detail_after_section")
                    )
                }

                if (metadataPairs.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(10.dp))
                    SnapshotPairsCard(
                        title = Localization.get(Localization.Key.ACTIVITY_DETAIL_METADATA, language),
                        pairs = metadataPairs,
                        containerColor = PosBorderNeutral.copy(alpha = 0.35f),
                        modifier = Modifier.testTag("audit_detail_metadata_section")
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))
                Button(
                    onClick = onDismiss,
                    modifier = Modifier
                        .fillMaxWidth()
                        .defaultMinSize(minHeight = 48.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = PosDeepGreen)
                ) {
                    Text(Localization.get(Localization.Key.BTN_CLOSE, language))
                }
            }
        }
    }
}

@Composable
private fun DetailInfoRow(label: String, value: String) {
    Column(modifier = Modifier.padding(vertical = 3.dp)) {
        Text(text = label, style = MaterialTheme.typography.labelSmall, color = PosMutedText)
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
            color = PosCharcoalText
        )
    }
}

@Composable
private fun SnapshotPairsCard(
    title: String,
    pairs: List<Pair<String, String>>,
    containerColor: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(10.dp),
        color = containerColor,
        border = BorderStroke(1.dp, PosBorderNeutral)
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                color = PosCharcoalText
            )
            Spacer(modifier = Modifier.height(4.dp))
            pairs.forEach { (k, v) ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 2.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(text = "$k:", style = MaterialTheme.typography.labelSmall, color = PosMutedText, modifier = Modifier.weight(0.45f))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = v,
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                        color = PosCharcoalText,
                        modifier = Modifier.weight(0.55f)
                    )
                }
            }
        }
    }
}

private fun localizedCategoryLabel(category: AuditCategoryFilter, language: String): String = when (category) {
    AuditCategoryFilter.ALL -> Localization.get(Localization.Key.ACTIVITY_FILTER_ALL_CATEGORIES, language)
    AuditCategoryFilter.INVOICE -> Localization.get(Localization.Key.ACTIVITY_CAT_INVOICE, language)
    AuditCategoryFilter.PAYMENT -> Localization.get(Localization.Key.ACTIVITY_CAT_PAYMENT, language)
    AuditCategoryFilter.PRODUCT -> Localization.get(Localization.Key.ACTIVITY_CAT_PRODUCT, language)
    AuditCategoryFilter.CUSTOMER -> Localization.get(Localization.Key.ACTIVITY_CAT_CUSTOMER, language)
    AuditCategoryFilter.COMPANY -> Localization.get(Localization.Key.ACTIVITY_CAT_COMPANY, language)
    AuditCategoryFilter.SETTINGS -> Localization.get(Localization.Key.ACTIVITY_CAT_SETTINGS, language)
    AuditCategoryFilter.AUTH -> Localization.get(Localization.Key.ACTIVITY_CAT_AUTH, language)
    AuditCategoryFilter.EXCHANGE -> Localization.get(Localization.Key.ACTIVITY_CAT_EXCHANGE, language)
}

private fun localizedDateFilterLabel(filter: AuditDateFilter, language: String): String = when (filter) {
    AuditDateFilter.TODAY -> Localization.get(Localization.Key.ACTIVITY_DATE_TODAY, language)
    AuditDateFilter.YESTERDAY -> Localization.get(Localization.Key.ACTIVITY_DATE_YESTERDAY, language)
    AuditDateFilter.LAST_7_DAYS -> Localization.get(Localization.Key.ACTIVITY_DATE_7_DAYS, language)
    AuditDateFilter.LAST_30_DAYS -> Localization.get(Localization.Key.ACTIVITY_DATE_30_DAYS, language)
    AuditDateFilter.ALL -> Localization.get(Localization.Key.ACTIVITY_DATE_ALL, language)
    AuditDateFilter.CUSTOM -> Localization.get(Localization.Key.ACTIVITY_DATE_CUSTOM, language)
}

private fun localizedActionLabel(actionType: String, language: String): String {
    val isBn = language.equals("bn", ignoreCase = true)
    return when (actionType) {
        "INVOICE_CREATED" -> if (isBn) "ইনভয়েস তৈরি" else "Invoice Created"
        "INVOICE_METADATA_UPDATED" -> if (isBn) "ইনভয়েস সম্পাদনা" else "Invoice Edited"
        "INVOICE_DELETED" -> if (isBn) "ইনভয়েস বাতিল" else "Invoice Deleted"
        "PAYMENT_COLLECTED_CHECKOUT" -> if (isBn) "চেকআউট পেমেন্ট" else "Checkout Payment"
        "PAYMENT_DUE_SETTLED" -> if (isBn) "বকেয়া আদায়" else "Due Settled"
        "PRODUCT_CREATED" -> if (isBn) "নতুন পণ্য" else "Product Added"
        "PRODUCT_UPDATED" -> if (isBn) "পণ্য হালনাগাদ" else "Product Updated"
        "PRODUCT_DELETED" -> if (isBn) "পণ্য মুছে ফেলা" else "Product Deleted"
        "PRODUCT_REORDERED" -> if (isBn) "পণ্য ক্রম পরিবর্তন" else "Catalog Reordered"
        "CUSTOMER_CREATED" -> if (isBn) "নতুন কাস্টমার" else "Customer Added"
        "CUSTOMER_UPDATED" -> if (isBn) "কাস্টমার হালনাগাদ" else "Customer Updated"
        "COMPANY_HEADER_UPDATED" -> if (isBn) "কোম্পানি হেডার" else "Company Header"
        "COMPANY_LOGO_UPDATED" -> if (isBn) "লোগো হালনাগাদ" else "Logo Updated"
        "COMPANY_LOGO_REMOVED" -> if (isBn) "লোগো মুছে ফেলা" else "Logo Removed"
        "FOOTER_NOTES_UPDATED" -> if (isBn) "ফুটার নোট" else "Footer Notes"
        "PRINTER_SETTINGS_UPDATED" -> if (isBn) "প্রিন্টার সেটিংস" else "Printer Settings"
        "APP_PREFERENCES_UPDATED" -> if (isBn) "ভাষা/থিম পরিবর্তন" else "Preferences"
        "XLSX_EXPORTED" -> if (isBn) "এক্সেল এক্সপোর্ট" else "XLSX Exported"
        "XLSX_IMPORTED" -> if (isBn) "এক্সেল ইমপোর্ট" else "XLSX Imported"
        "AUTH_REGISTER" -> if (isBn) "নিবন্ধন" else "Registered"
        "AUTH_LOGIN_SUCCESS" -> if (isBn) "লগইন সফল" else "Sign In"
        "AUTH_LOGIN_FAILED" -> if (isBn) "ব্যর্থ লগইন" else "Failed Sign In"
        "AUTH_LOGIN_LOCKED" -> if (isBn) "লগইন লকআউট" else "Account Locked"
        "AUTH_LOGOUT" -> if (isBn) "লগআউট" else "Sign Out"
        "AUTH_PROFILE_UPDATED" -> if (isBn) "প্রোফাইল আপডেট" else "Profile Updated"
        "AUTH_PASSWORD_RESET" -> if (isBn) "পাসওয়ার্ড রিসেট" else "Password Reset"
        "AUTH_RECOVERY_CODE_REGENERATED" -> if (isBn) "রিকভারি কোড" else "Recovery Code"
        else -> actionType
    }
}

private fun formatAuditTimestamp(timeMillis: Long, language: String): String {
    return if (language.equals("bn", ignoreCase = true)) {
        formatHistoryDateTimeBn(timeMillis)
    } else {
        SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.US).format(Date(timeMillis))
    }
}
