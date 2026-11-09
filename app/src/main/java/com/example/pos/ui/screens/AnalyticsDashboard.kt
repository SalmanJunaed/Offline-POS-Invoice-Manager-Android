package com.example.pos.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.Analytics
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.MoneyOff
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.PointOfSale
import androidx.compose.material.icons.filled.Receipt
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.pos.core.util.CurrencyFormatter
import com.example.pos.core.util.DateTimeUtils
import com.example.pos.data.db.ItemSalesSummary
import com.example.pos.data.db.PaymentMethodSummary
import com.example.pos.data.db.PosDatabase
import com.example.pos.data.db.SalesPeriodAggregate
import com.example.pos.domain.model.ChartDataPoint
import com.example.pos.modules.chart.PosAnalyticsBarChart
import com.example.pos.ui.viewmodel.AnalyticsGrouping
import com.example.pos.ui.viewmodel.AnalyticsMetric
import com.example.pos.ui.viewmodel.DateFilterMode
import com.example.ui.theme.PosBorderGreen
import com.example.ui.theme.PosDeepGreen
import com.example.ui.theme.PosDueRed
import com.example.ui.theme.PosEmerald
import com.example.ui.theme.PosFreshGreen
import com.example.ui.theme.PosLightGreen
import com.example.ui.theme.PosMainText
import com.example.ui.theme.PosMint
import com.example.ui.theme.PosMutedText
import com.example.ui.theme.PosPaleGreen

enum class AnalyticsDetailView {
    PRODUCTS,
    PAYMENTS,
    TRANSACTIONS
}

@Composable
fun AnalyticsDashboardView(
    language: String,
    dateFilter: DateFilterMode,
    analyticsGrouping: AnalyticsGrouping,
    analyticsMetric: AnalyticsMetric,
    analyticsDataPoints: List<SalesPeriodAggregate>,
    chartDataPoints: List<ChartDataPoint>,
    aggregatedOrders: Int,
    aggregatedRawPaisa: Long,
    aggregatedRoundedBdt: Long,
    topSellingItems: List<ItemSalesSummary>,
    onSelectDateFilter: (DateFilterMode) -> Unit,
    onOpenCustomDateDialog: () -> Unit,
    onSelectGrouping: (AnalyticsGrouping) -> Unit,
    onSelectMetric: (AnalyticsMetric) -> Unit,
    modifier: Modifier = Modifier,
    periodDueBdt: Long = 0L,
    overallDueBdt: Long = 0L,
    paymentBreakdown: List<PaymentMethodSummary> = emptyList(),
    totalCollectionBdt: Long = 0L
) {
    var selectedDetailView by remember { mutableStateOf(AnalyticsDetailView.PRODUCTS) }

    // Financial Calculations
    val totalSalesBdt = aggregatedRoundedBdt
    val totalCollectionPaisa = paymentBreakdown.sumOf { it.totalPaisa }
    val effectiveCollectionBdt = if (totalCollectionBdt > 0L) totalCollectionBdt else (totalCollectionPaisa / 100L)

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(Color.White)
            .testTag("analytics_dashboard_view"),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // --- 1. Dashboard Header ---
        item {
            DashboardHeaderCard()
        }

        // --- 2. Compact Selectors & Filters ---
        item {
            FilterControlsCard(
                selectedFilter = dateFilter,
                selectedGrouping = analyticsGrouping,
                selectedMetric = analyticsMetric,
                onSelectDateFilter = onSelectDateFilter,
                onOpenCustomDateDialog = onOpenCustomDateDialog,
                onSelectGrouping = onSelectGrouping,
                onSelectMetric = onSelectMetric
            )
        }

        // --- 3. Essential Business KPI Metrics Grid ---
        item {
            KpiMetricsGrid(
                totalSalesBdt = totalSalesBdt,
                totalCollectionBdt = effectiveCollectionBdt,
                periodDueBdt = periodDueBdt,
                overallDueBdt = overallDueBdt,
                transactionCount = aggregatedOrders
            )
        }

        // --- 4. Interactive Trend Chart ---
        item {
            AnalyticsChartCard(
                chartPoints = chartDataPoints,
                selectedMetric = analyticsMetric,
                selectedGrouping = analyticsGrouping
            )
        }

        // --- 5. Detail View Switcher (Segmented Control) ---
        item {
            DetailViewSegmentedBar(
                currentView = selectedDetailView,
                onSelectView = { selectedDetailView = it }
            )
        }

        // --- 6. Active Detail View Content ---
        item {
            when (selectedDetailView) {
                AnalyticsDetailView.PRODUCTS -> {
                    TopSellingProductsCard(topSellingItems = topSellingItems)
                }
                AnalyticsDetailView.PAYMENTS -> {
                    PaymentMethodBreakdownCard(
                        paymentBreakdown = paymentBreakdown,
                        totalCollectionBdt = effectiveCollectionBdt
                    )
                }
                AnalyticsDetailView.TRANSACTIONS -> {
                    PeriodBreakdownTableCard(dataPoints = analyticsDataPoints)
                }
            }
        }

        item {
            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

// -------------------------------------------------------------
// Component: Header Card
// -------------------------------------------------------------
@Composable
private fun DashboardHeaderCard() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = PosMint),
        border = androidx.compose.foundation.BorderStroke(1.dp, PosBorderGreen)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .background(PosDeepGreen, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Analytics,
                    contentDescription = "Analytics",
                    tint = Color.White,
                    modifier = Modifier.size(26.dp)
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column {
                Text(
                    text = "অ্যানালিটিক্স ড্যাশবোর্ড",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = PosDeepGreen
                )
                Text(
                    text = "ব্যবসায়িক বিক্রি, আদায় ও ট্রেন্ড বিশ্লেষণ",
                    style = MaterialTheme.typography.bodySmall,
                    color = PosMutedText
                )
            }
        }
    }
}

// -------------------------------------------------------------
// Component: Compact Filter & Grouping Controls
// -------------------------------------------------------------
@Composable
private fun FilterControlsCard(
    selectedFilter: DateFilterMode,
    selectedGrouping: AnalyticsGrouping,
    selectedMetric: AnalyticsMetric,
    onSelectDateFilter: (DateFilterMode) -> Unit,
    onOpenCustomDateDialog: () -> Unit,
    onSelectGrouping: (AnalyticsGrouping) -> Unit,
    onSelectMetric: (AnalyticsMetric) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        border = androidx.compose.foundation.BorderStroke(1.dp, PosBorderGreen)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Section 1: Date Range Filter
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.DateRange,
                    contentDescription = null,
                    tint = PosEmerald,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "সময়কাল:",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = PosMainText
                )
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                CompactFilterChip(
                    label = "সব",
                    selected = selectedFilter == DateFilterMode.ALL,
                    tag = "analytics_filter_all",
                    onClick = { onSelectDateFilter(DateFilterMode.ALL) }
                )
                CompactFilterChip(
                    label = "আজ",
                    selected = selectedFilter == DateFilterMode.TODAY,
                    tag = "analytics_filter_today",
                    onClick = { onSelectDateFilter(DateFilterMode.TODAY) }
                )
                CompactFilterChip(
                    label = "গত ৭ দিন",
                    selected = selectedFilter == DateFilterMode.WEEK,
                    tag = "analytics_filter_week",
                    onClick = { onSelectDateFilter(DateFilterMode.WEEK) }
                )
                CompactFilterChip(
                    label = "এই মাস",
                    selected = selectedFilter == DateFilterMode.MONTH,
                    tag = "analytics_filter_month",
                    onClick = { onSelectDateFilter(DateFilterMode.MONTH) }
                )
                CompactFilterChip(
                    label = "এই বছর",
                    selected = selectedFilter == DateFilterMode.YEAR,
                    tag = "analytics_filter_year",
                    onClick = { onSelectDateFilter(DateFilterMode.YEAR) }
                )
                CompactFilterChip(
                    label = "কাস্টম",
                    selected = selectedFilter == DateFilterMode.CUSTOM,
                    tag = "analytics_filter_custom",
                    onClick = {
                        onSelectDateFilter(DateFilterMode.CUSTOM)
                        onOpenCustomDateDialog()
                    }
                )
            }

            HorizontalDivider(color = PosBorderGreen.copy(alpha = 0.5f), thickness = 0.8.dp)

            // Section 2: Grouping & Metric Selectors side-by-side or stacked
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Grouping
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "গ্রুপিং:",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = PosMutedText
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        CompactSegmentChip(
                            label = "দিন",
                            selected = selectedGrouping == AnalyticsGrouping.DAY,
                            tag = "grouping_day",
                            onClick = { onSelectGrouping(AnalyticsGrouping.DAY) }
                        )
                        CompactSegmentChip(
                            label = "মাস",
                            selected = selectedGrouping == AnalyticsGrouping.MONTH,
                            tag = "grouping_month",
                            onClick = { onSelectGrouping(AnalyticsGrouping.MONTH) }
                        )
                        CompactSegmentChip(
                            label = "বছর",
                            selected = selectedGrouping == AnalyticsGrouping.YEAR,
                            tag = "grouping_year",
                            onClick = { onSelectGrouping(AnalyticsGrouping.YEAR) }
                        )
                    }
                }

                Spacer(modifier = Modifier.width(8.dp))

                // Metric
                Column(modifier = Modifier.weight(1.2f)) {
                    Text(
                        text = "গ্রাফ পরিমাপক:",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = PosMutedText
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        CompactSegmentChip(
                            label = "বিক্রি",
                            selected = selectedMetric == AnalyticsMetric.ROUNDED_SALES,
                            tag = "metric_rounded",
                            onClick = { onSelectMetric(AnalyticsMetric.ROUNDED_SALES) }
                        )
                        CompactSegmentChip(
                            label = "প্রকৃত",
                            selected = selectedMetric == AnalyticsMetric.RAW_SALES,
                            tag = "metric_raw",
                            onClick = { onSelectMetric(AnalyticsMetric.RAW_SALES) }
                        )
                        CompactSegmentChip(
                            label = "সংখ্যা",
                            selected = selectedMetric == AnalyticsMetric.TRANSACTION_COUNT,
                            tag = "metric_count",
                            onClick = { onSelectMetric(AnalyticsMetric.TRANSACTION_COUNT) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CompactFilterChip(
    label: String,
    selected: Boolean,
    tag: String,
    onClick: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = if (selected) PosDeepGreen else PosPaleGreen,
        contentColor = if (selected) Color.White else PosMainText,
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            if (selected) PosDeepGreen else PosBorderGreen
        ),
        modifier = Modifier
            .testTag(tag)
            .clickable(onClick = onClick)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
        )
    }
}

@Composable
private fun CompactSegmentChip(
    label: String,
    selected: Boolean,
    tag: String,
    onClick: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(6.dp),
        color = if (selected) PosEmerald else PosMint,
        contentColor = if (selected) Color.White else PosDeepGreen,
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            if (selected) PosEmerald else PosBorderGreen
        ),
        modifier = Modifier
            .testTag(tag)
            .clickable(onClick = onClick)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
        )
    }
}

// -------------------------------------------------------------
// Component: Essential Business Metrics Grid
// -------------------------------------------------------------
@Composable
private fun KpiMetricsGrid(
    totalSalesBdt: Long,
    totalCollectionBdt: Long,
    periodDueBdt: Long,
    overallDueBdt: Long,
    transactionCount: Int
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = "ব্যবসায়িক সারসংক্ষেপ",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = PosDeepGreen
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // মোট বিক্রি
            KpiMetricCard(
                modifier = Modifier.weight(1f),
                title = "মোট বিক্রি",
                value = CurrencyFormatter.formatToBengaliBdt(totalSalesBdt),
                icon = Icons.Default.PointOfSale,
                containerColor = PosPaleGreen,
                accentColor = PosDeepGreen
            )

            // মোট আদায়
            KpiMetricCard(
                modifier = Modifier.weight(1f),
                title = "মোট আদায়",
                value = CurrencyFormatter.formatToBengaliBdt(totalCollectionBdt),
                icon = Icons.Default.Payments,
                containerColor = PosMint,
                accentColor = PosFreshGreen
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // নির্বাচিত সময়ের বকেয়া
            KpiMetricCard(
                modifier = Modifier.weight(1f),
                title = "নির্বাচিত সময়ের বকেয়া",
                value = CurrencyFormatter.formatToBengaliBdt(periodDueBdt),
                icon = Icons.Default.AccountBalanceWallet,
                containerColor = if (periodDueBdt > 0) Color(0xFFFEF2F2) else PosPaleGreen,
                accentColor = if (periodDueBdt > 0) PosDueRed else PosDeepGreen
            )

            // সর্বমোট বর্তমান বকেয়া
            KpiMetricCard(
                modifier = Modifier.weight(1f),
                title = "সর্বমোট বর্তমান বকেয়া",
                value = CurrencyFormatter.formatToBengaliBdt(overallDueBdt),
                icon = Icons.Default.AccountBalanceWallet,
                containerColor = if (overallDueBdt > 0) Color(0xFFFFFBEB) else PosPaleGreen,
                accentColor = if (overallDueBdt > 0) Color(0xFFB45309) else PosDeepGreen
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // মোট লেনদেন
            KpiMetricCard(
                modifier = Modifier.weight(1f),
                title = "মোট লেনদেন",
                value = "${DateTimeUtils.toBengaliNumerals(transactionCount.toString())} টি",
                icon = Icons.Default.Receipt,
                containerColor = PosPaleGreen,
                accentColor = PosEmerald
            )

            // মোট খরচ (তথ্য প্রযোজ্য নয়)
            KpiUnavailableCard(
                modifier = Modifier.weight(1f),
                title = "মোট খরচ",
                reason = "খরচ ট্র্যাকিং ডাটা অনুপস্থিত",
                icon = Icons.Default.MoneyOff
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // নিট লাভ (তথ্য প্রযোজ্য নয়)
            KpiUnavailableCard(
                modifier = Modifier.weight(1f),
                title = "নিট লাভ",
                reason = "ক্রয়মূল্য ডাটা অনুপস্থিত",
                icon = Icons.Default.TrendingUp
            )

            Spacer(modifier = Modifier.weight(1f))
        }
    }
}

@Composable
private fun KpiMetricCard(
    modifier: Modifier = Modifier,
    title: String,
    value: String,
    icon: ImageVector,
    containerColor: Color,
    accentColor: Color
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(containerColor = containerColor),
        border = androidx.compose.foundation.BorderStroke(1.dp, PosBorderGreen)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.labelSmall,
                    color = PosMutedText,
                    fontWeight = FontWeight.Medium
                )
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = accentColor,
                    modifier = Modifier.size(16.dp)
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = value,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = accentColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun KpiUnavailableCard(
    modifier: Modifier = Modifier,
    title: String,
    reason: String,
    icon: ImageVector
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFF8FAFC)),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFE2E8F0))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.labelSmall,
                    color = Color(0xFF94A3B8),
                    fontWeight = FontWeight.Medium
                )
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = Color(0xFF94A3B8),
                    modifier = Modifier.size(16.dp)
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "তথ্য প্রযোজ্য নয়",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = Color(0xFF64748B)
            )
            Text(
                text = reason,
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                color = Color(0xFF94A3B8),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

// -------------------------------------------------------------
// Component: Analytics Visual Chart Card
// -------------------------------------------------------------
@Composable
private fun AnalyticsChartCard(
    chartPoints: List<ChartDataPoint>,
    selectedMetric: AnalyticsMetric,
    selectedGrouping: AnalyticsGrouping
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("analytics_chart_card"),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        border = androidx.compose.foundation.BorderStroke(1.dp, PosBorderGreen)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.TrendingUp,
                        contentDescription = null,
                        tint = PosDeepGreen,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "পারফরম্যান্স ট্রেন্ড চার্ট",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = PosDeepGreen
                    )
                }

                val groupingLabel = when (selectedGrouping) {
                    AnalyticsGrouping.DAY -> "দৈনিক"
                    AnalyticsGrouping.MONTH -> "মাসিক"
                    AnalyticsGrouping.YEAR -> "বাৎসরিক"
                }

                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = PosMint,
                    border = androidx.compose.foundation.BorderStroke(1.dp, PosBorderGreen)
                ) {
                    Text(
                        text = groupingLabel,
                        style = MaterialTheme.typography.labelSmall,
                        color = PosDeepGreen,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            val metricUnit = when (selectedMetric) {
                AnalyticsMetric.ROUNDED_SALES -> "টাকা"
                AnalyticsMetric.RAW_SALES -> "পয়সা"
                AnalyticsMetric.TRANSACTION_COUNT -> "টি"
            }

            val isCurrency = selectedMetric != AnalyticsMetric.TRANSACTION_COUNT

            // Canvas Bar Chart
            PosAnalyticsBarChart(
                dataPoints = chartPoints,
                modifier = Modifier.fillMaxWidth(),
                metricTitle = metricUnit,
                isCurrency = isCurrency,
                primaryColor = PosDeepGreen,
                secondaryColor = PosFreshGreen,
                emptyMessage = "গ্রাফ প্রদর্শনের জন্য কোন তথ্য নেই"
            )
        }
    }
}

// -------------------------------------------------------------
// Component: Detail View Segmented Control
// -------------------------------------------------------------
@Composable
private fun DetailViewSegmentedBar(
    currentView: AnalyticsDetailView,
    onSelectView: (AnalyticsDetailView) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(containerColor = PosMint),
        border = androidx.compose.foundation.BorderStroke(1.dp, PosBorderGreen)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(4.dp),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            DetailSegmentButton(
                title = "পণ্যভিত্তিক",
                icon = Icons.Default.Category,
                selected = currentView == AnalyticsDetailView.PRODUCTS,
                onClick = { onSelectView(AnalyticsDetailView.PRODUCTS) },
                modifier = Modifier.weight(1f).testTag("detail_segment_products")
            )
            DetailSegmentButton(
                title = "পেমেন্ট মাধ্যম",
                icon = Icons.Default.CreditCard,
                selected = currentView == AnalyticsDetailView.PAYMENTS,
                onClick = { onSelectView(AnalyticsDetailView.PAYMENTS) },
                modifier = Modifier.weight(1f).testTag("detail_segment_payments")
            )
            DetailSegmentButton(
                title = "লেনদেন বিবরণ",
                icon = Icons.Default.Receipt,
                selected = currentView == AnalyticsDetailView.TRANSACTIONS,
                onClick = { onSelectView(AnalyticsDetailView.TRANSACTIONS) },
                modifier = Modifier.weight(1f).testTag("detail_segment_transactions")
            )
        }
    }
}

@Composable
private fun DetailSegmentButton(
    title: String,
    icon: ImageVector,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(8.dp),
        color = if (selected) Color.White else Color.Transparent,
        border = if (selected) androidx.compose.foundation.BorderStroke(1.dp, PosBorderGreen) else null
    ) {
        Row(
            modifier = Modifier.padding(vertical = 8.dp, horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (selected) PosDeepGreen else PosMutedText,
                modifier = Modifier.size(15.dp)
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                color = if (selected) PosDeepGreen else PosMutedText,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

// -------------------------------------------------------------
// Component: Top Selling Products Card
// -------------------------------------------------------------
@Composable
private fun TopSellingProductsCard(topSellingItems: List<ItemSalesSummary>) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("top_selling_items_card"),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        border = androidx.compose.foundation.BorderStroke(1.dp, PosBorderGreen)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "সর্বাধিক বিক্রিত পণ্যসমূহ",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = PosDeepGreen
                )
                Text(
                    text = "${DateTimeUtils.toBengaliNumerals(topSellingItems.size.toString())} টি পণ্য",
                    style = MaterialTheme.typography.labelSmall,
                    color = PosMutedText
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            if (topSellingItems.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "কোন পণ্য বিক্রির তথ্য পাওয়া যায়নি",
                        style = MaterialTheme.typography.bodySmall,
                        color = PosMutedText
                    )
                }
            } else {
                val maxRevenue = topSellingItems.maxOfOrNull { it.totalRevenuePaisa }?.coerceAtLeast(1L) ?: 1L

                topSellingItems.forEachIndexed { index, item ->
                    val revenueBdt = item.totalRevenuePaisa / 100
                    val progress = (item.totalRevenuePaisa.toFloat() / maxRevenue).coerceIn(0f, 1f)

                    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(22.dp)
                                        .background(if (index < 3) PosLightGreen else PosMint, CircleShape),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = DateTimeUtils.toBengaliNumerals((index + 1).toString()),
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = PosDeepGreen
                                    )
                                }
                                Spacer(modifier = Modifier.width(8.dp))
                                Column {
                                    Text(
                                        text = item.itemName,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.SemiBold,
                                        color = PosMainText
                                    )
                                    Text(
                                        text = "${DateTimeUtils.toBengaliNumerals(item.orderCount.toString())} বার বিক্রিত",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = PosMutedText
                                    )
                                }
                            }

                            Text(
                                text = CurrencyFormatter.formatToBengaliBdt(revenueBdt),
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold,
                                color = PosDeepGreen
                            )
                        }

                        Spacer(modifier = Modifier.height(4.dp))
                        LinearProgressIndicator(
                            progress = { progress },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(4.dp)
                                .clip(RoundedCornerShape(2.dp)),
                            color = PosFreshGreen,
                            trackColor = PosMint
                        )
                    }

                    if (index < topSellingItems.size - 1) {
                        HorizontalDivider(
                            color = PosBorderGreen.copy(alpha = 0.4f),
                            thickness = 0.5.dp,
                            modifier = Modifier.padding(vertical = 4.dp)
                        )
                    }
                }
            }
        }
    }
}

// -------------------------------------------------------------
// Component: Payment Method Breakdown Card
// -------------------------------------------------------------
@Composable
private fun PaymentMethodBreakdownCard(
    paymentBreakdown: List<PaymentMethodSummary>,
    totalCollectionBdt: Long
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        border = androidx.compose.foundation.BorderStroke(1.dp, PosBorderGreen)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "পেমেন্ট পদ্ধতিভিত্তিক আদায়",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = PosDeepGreen
                )
                Text(
                    text = CurrencyFormatter.formatToBengaliBdt(totalCollectionBdt),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = PosFreshGreen
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            if (paymentBreakdown.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "কোন পেমেন্ট লেনদেনের তথ্য পাওয়া যায়নি",
                        style = MaterialTheme.typography.bodySmall,
                        color = PosMutedText
                    )
                }
            } else {
                val totalPaisa = paymentBreakdown.sumOf { it.totalPaisa }.coerceAtLeast(1L)

                paymentBreakdown.forEachIndexed { index, method ->
                    val bdt = method.totalPaisa / 100
                    val sharePercentage = (method.totalPaisa * 100f / totalPaisa).coerceIn(0f, 100f)

                    val methodLabel = when (method.payment_method.uppercase()) {
                        "CASH" -> "নগদ (Cash)"
                        "CARD" -> "কার্ড (Card)"
                        "MOBILE_BANKING", "BKASH", "NAGAD", "ROCKET" -> "মোবাইল ব্যাংকিং"
                        else -> method.payment_method
                    }

                    val methodIcon = when (method.payment_method.uppercase()) {
                        "CASH" -> Icons.Default.Payments
                        "CARD" -> Icons.Default.CreditCard
                        else -> Icons.Default.AccountBalanceWallet
                    }

                    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = methodIcon,
                                    contentDescription = null,
                                    tint = PosEmerald,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Column {
                                    Text(
                                        text = methodLabel,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.SemiBold,
                                        color = PosMainText
                                    )
                                    Text(
                                        text = "${DateTimeUtils.toBengaliNumerals(method.count.toString())} টি লেনদেন",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = PosMutedText
                                    )
                                }
                            }

                            Column(horizontalAlignment = Alignment.End) {
                                Text(
                                    text = CurrencyFormatter.formatToBengaliBdt(bdt),
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = PosDeepGreen
                                )
                                Text(
                                    text = "${DateTimeUtils.toBengaliNumerals(String.format(java.util.Locale.US, "%.1f", sharePercentage))}%",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = PosMutedText
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(4.dp))
                        LinearProgressIndicator(
                            progress = { sharePercentage / 100f },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(6.dp)
                                .clip(RoundedCornerShape(3.dp)),
                            color = PosEmerald,
                            trackColor = PosMint
                        )
                    }

                    if (index < paymentBreakdown.size - 1) {
                        HorizontalDivider(
                            color = PosBorderGreen.copy(alpha = 0.4f),
                            thickness = 0.5.dp,
                            modifier = Modifier.padding(vertical = 4.dp)
                        )
                    }
                }
            }
        }
    }
}

// -------------------------------------------------------------
// Component: Period Breakdown Table Card
// -------------------------------------------------------------
@Composable
private fun PeriodBreakdownTableCard(dataPoints: List<SalesPeriodAggregate>) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("period_breakdown_table"),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        border = androidx.compose.foundation.BorderStroke(1.dp, PosBorderGreen)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "পর্যায়ভিত্তিক লেনদেন বিবরণ",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = PosDeepGreen
                )
                Text(
                    text = "${DateTimeUtils.toBengaliNumerals(dataPoints.size.toString())} টি রেকর্ড",
                    style = MaterialTheme.typography.labelSmall,
                    color = PosMutedText
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Table Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(PosPaleGreen, RoundedCornerShape(6.dp))
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "সময়কাল",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = PosDeepGreen,
                    modifier = Modifier.weight(1.2f)
                )
                Text(
                    text = "লেনদেন",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = PosDeepGreen,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(0.8f)
                )
                Text(
                    text = "মোট বিক্রি",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = PosDeepGreen,
                    textAlign = TextAlign.End,
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(modifier = Modifier.height(4.dp))

            if (dataPoints.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "কোন লেনদেনের তথ্য পাওয়া যায়নি",
                        style = MaterialTheme.typography.bodySmall,
                        color = PosMutedText
                    )
                }
            } else {
                dataPoints.forEachIndexed { index, point ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = DateTimeUtils.toBengaliNumerals(point.periodKey),
                            style = MaterialTheme.typography.bodySmall,
                            color = PosMainText,
                            modifier = Modifier.weight(1.2f)
                        )
                        Text(
                            text = "${DateTimeUtils.toBengaliNumerals(point.transactionCount.toString())} টি",
                            style = MaterialTheme.typography.bodySmall,
                            color = PosMutedText,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.weight(0.8f)
                        )
                        Text(
                            text = CurrencyFormatter.formatToBengaliBdt(point.totalRoundedBdt),
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Bold,
                            color = PosDeepGreen,
                            textAlign = TextAlign.End,
                            modifier = Modifier.weight(1f)
                        )
                    }

                    if (index < dataPoints.size - 1) {
                        HorizontalDivider(
                            color = PosBorderGreen.copy(alpha = 0.3f),
                            thickness = 0.5.dp
                        )
                    }
                }
            }
        }
    }
}
