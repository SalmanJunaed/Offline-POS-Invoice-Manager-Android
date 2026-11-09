package com.example.pos.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.R
import com.example.pos.core.util.CompanyLogoManager
import com.example.pos.core.util.DateTimeUtils
import com.example.pos.data.db.CompanyHeaderEntity
import com.example.pos.data.db.OperatorEntity
import com.example.ui.theme.PosBorderGreen
import com.example.ui.theme.PosCharcoalText
import com.example.ui.theme.PosCleanWhite
import com.example.ui.theme.PosDeepGreen
import com.example.ui.theme.PosEmerald
import com.example.ui.theme.PosFreshGreen
import com.example.ui.theme.PosLightGreen
import com.example.ui.theme.PosMintGreen
import com.example.ui.theme.PosMutedText
import com.example.ui.theme.PosPaleGreen
import kotlinx.coroutines.delay
import java.io.File
import java.util.Locale

/**
 * Centered Company Logo View with official bundled logo fallback.
 */
@Composable
fun CompanyLogoView(
    logoUri: String?,
    modifier: Modifier = Modifier,
    contentDescription: String? = "Company Logo",
    fallback: @Composable (() -> Unit)? = null
) {
    val context = LocalContext.current
    val effectiveFile: File? = remember(logoUri) {
        CompanyLogoManager.getEffectiveLogoFile(context, logoUri)
    }

    if (effectiveFile != null) {
        AsyncImage(
            model = ImageRequest.Builder(context)
                .data(effectiveFile)
                .crossfade(true)
                .build(),
            contentDescription = contentDescription,
            modifier = modifier.clip(RoundedCornerShape(8.dp)),
            contentScale = ContentScale.Fit
        )
    } else if (CompanyLogoManager.hasDefaultOfficialLogo(context)) {
        AsyncImage(
            model = ImageRequest.Builder(context)
                .data(R.drawable.logo_original)
                .crossfade(true)
                .build(),
            contentDescription = contentDescription,
            modifier = modifier.clip(RoundedCornerShape(8.dp)),
            contentScale = ContentScale.Fit
        )
    } else if (fallback != null) {
        fallback()
    } else {
        Box(
            modifier = modifier
                .clip(RoundedCornerShape(8.dp))
                .background(PosMintGreen)
                .border(1.dp, PosBorderGreen, RoundedCornerShape(8.dp)),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "লোগো",
                style = MaterialTheme.typography.labelSmall,
                color = PosDeepGreen,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

/**
 * Top Station Header (White-Green Theme, Bengali-Only, Centered Company Name & Logo).
 * Language and Theme toggle controls removed as per Stage 05.
 */
@Composable
fun TopStationHeader(
    company: CompanyHeaderEntity?,
    operator: OperatorEntity?,
    currentLanguage: String = "bn",
    currentTheme: String = "LIGHT",
    onToggleLanguage: () -> Unit = {},
    onToggleTheme: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    var currentTimeString by remember {
        mutableStateOf(DateTimeUtils.formatLiveTime())
    }

    LaunchedEffect(Unit) {
        while (true) {
            delay(1000L)
            currentTimeString = DateTimeUtils.formatLiveTime()
        }
    }

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(bottomStart = 16.dp, bottomEnd = 16.dp),
        colors = CardDefaults.cardColors(
            containerColor = PosCleanWhite,
            contentColor = PosCharcoalText
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Centered Company Header with Logo & 3-Line Name
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                CompanyLogoView(
                    logoUri = company?.logoUri,
                    modifier = Modifier.size(48.dp)
                )

                Spacer(modifier = Modifier.width(12.dp))

                Column(
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    val defaultLine1 = "মেসার্স রাকিব ফিলিং স্টেশন"
                    val defaultLine2 = "এন্ড"
                    val defaultLine3 = "এল পি জি অটোগ্যাস ফিলিং স্টেশন"

                    val companyName = company?.companyName
                    val (line1, line2, line3) = if (!companyName.isNullOrBlank()) {
                        val lines = companyName.lines().map { it.trim() }.filter { it.isNotEmpty() }
                        when {
                            lines.size >= 3 -> Triple(lines[0], lines[1], lines.drop(2).joinToString(" "))
                            lines.size == 2 -> Triple(lines[0], "", lines[1])
                            else -> Triple(lines[0], "", "")
                        }
                    } else {
                        Triple(defaultLine1, defaultLine2, defaultLine3)
                    }

                    Text(
                        text = line1,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = PosDeepGreen,
                        textAlign = TextAlign.Center
                    )

                    if (line2.isNotEmpty()) {
                        Text(
                            text = line2,
                            style = MaterialTheme.typography.labelSmall,
                            color = PosMutedText,
                            textAlign = TextAlign.Center
                        )
                    }

                    if (line3.isNotEmpty()) {
                        Text(
                            text = line3,
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Medium,
                            color = PosCharcoalText,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Sub-header details row: Operator, Branch, Real-Time Bengali Clock
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(PosMintGreen, RoundedCornerShape(8.dp))
                    .border(1.dp, PosBorderGreen, RoundedCornerShape(8.dp))
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Operator Info
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .background(PosFreshGreen, CircleShape)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (operator != null) "অপারেটর: ${operator.operatorName}" else "অফলাইন মোড",
                        style = MaterialTheme.typography.labelMedium,
                        color = PosDeepGreen,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                // Branch Info
                val branchDisplay = if (!company?.branchName.isNullOrBlank()) {
                    "শাখা: ${company?.branchName}"
                } else {
                    "শাখা: প্রধান শাখা"
                }
                Text(
                    text = branchDisplay,
                    style = MaterialTheme.typography.labelSmall,
                    color = PosMutedText
                )

                // Bengali Live Time
                Text(
                    text = currentTimeString,
                    style = MaterialTheme.typography.labelSmall,
                    color = PosDeepGreen,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}

/**
 * Item Category Badge with Polish White-Green theme colors.
 */
@Composable
fun ItemTypeBadge(
    type: String,
    modifier: Modifier = Modifier
) {
    val (bgColor, textColor, labelBn) = when (type.lowercase(Locale.ROOT)) {
        "liquid" -> Triple(PosLightGreen, PosDeepGreen, "তরল জ্বালানি")
        "product" -> Triple(PosMintGreen, PosEmerald, "পণ্য")
        "service" -> Triple(PosPaleGreen, PosFreshGreen, "সেবা")
        else -> Triple(PosMintGreen, PosCharcoalText, type)
    }

    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(4.dp),
        color = bgColor,
        border = androidx.compose.foundation.BorderStroke(1.dp, PosBorderGreen)
    ) {
        Text(
            text = labelBn,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Medium,
            color = textColor
        )
    }
}
