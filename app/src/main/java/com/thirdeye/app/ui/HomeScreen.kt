package com.thirdeye.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.thirdeye.app.language.AppTextKey
import com.thirdeye.app.language.nexusText

private data class NexusFeature(
    val title: String,
    val icon: ImageVector,
    val onClick: () -> Unit
)

@Composable
fun HomeScreen(
    onCommunicationClick: () -> Unit,
    onVoiceClick: () -> Unit,
    onIntelligenceClick: () -> Unit,
    onVisionClick: () -> Unit,
    onNavigationClick: () -> Unit,
    onWeatherClick: () -> Unit,
    onSettingsClick: () -> Unit
) {
    val background = Color(0xFF05070C)
    val panel = Color(0xFF0B1019)
    val panelLight = Color(0xFF101824)
    val cyan = Color(0xFF00E5FF)
    val blue = Color(0xFF2979FF)
    val violet = Color(0xFF7C4DFF)
    val textPrimary = Color(0xFFEAFBFF)
    val textSecondary = Color(0xFF8295A7)

    Scaffold(
        containerColor = background
    ) { innerPadding ->

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 18.dp, vertical = 16.dp)
        ) {

            // ---------------------------------------------------------
            // HEADER
            // ---------------------------------------------------------

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {

                Column(
                    modifier = Modifier.weight(1f)
                ) {

                    Text(
                        text = "NEXUS-EYE",
                        color = textPrimary,
                        fontSize = 25.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 3.sp
                    )

                    Spacer(
                        modifier = Modifier.height(3.dp)
                    )

                    Text(
                        text = "PERSONAL AI COMMAND SYSTEM",
                        color = cyan,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Medium,
                        letterSpacing = 1.6.sp
                    )
                }

                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .clip(CircleShape)
                        .background(
                            Brush.linearGradient(
                                colors = listOf(
                                    cyan.copy(alpha = 0.18f),
                                    blue.copy(alpha = 0.12f)
                                )
                            )
                        )
                        .border(
                            width = 1.dp,
                            color = cyan.copy(alpha = 0.45f),
                            shape = CircleShape
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Settings,
                        contentDescription = "Settings",
                        tint = cyan,
                        modifier = Modifier
                            .size(20.dp)
                            .clickable {
                                onSettingsClick()
                            }
                    )
                }
            }

            Spacer(
                modifier = Modifier.height(20.dp)
            )

            // ---------------------------------------------------------
            // AI CORE
            // ---------------------------------------------------------

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(28.dp))
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color(0xFF0C1722),
                                panel
                            )
                        )
                    )
                    .border(
                        width = 1.dp,
                        color = cyan.copy(alpha = 0.20f),
                        shape = RoundedCornerShape(28.dp)
                    )
                    .padding(vertical = 25.dp),
                contentAlignment = Alignment.Center
            ) {

                Column(
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {

                    // Outer core
                    Box(
                        modifier = Modifier
                            .size(132.dp)
                            .clip(CircleShape)
                            .background(
                                Brush.radialGradient(
                                    colors = listOf(
                                        cyan.copy(alpha = 0.20f),
                                        blue.copy(alpha = 0.08f),
                                        Color.Transparent
                                    )
                                )
                            )
                            .border(
                                width = 1.dp,
                                color = cyan.copy(alpha = 0.38f),
                                shape = CircleShape
                            ),
                        contentAlignment = Alignment.Center
                    ) {

                        // Inner core
                        Box(
                            modifier = Modifier
                                .size(76.dp)
                                .clip(CircleShape)
                                .background(
                                    Brush.radialGradient(
                                        colors = listOf(
                                            cyan.copy(alpha = 0.35f),
                                            blue.copy(alpha = 0.15f),
                                            Color(0xFF071019)
                                        )
                                    )
                                )
                                .border(
                                    width = 2.dp,
                                    color = cyan.copy(alpha = 0.75f),
                                    shape = CircleShape
                                ),
                            contentAlignment = Alignment.Center
                        ) {

                            Icon(
                                imageVector = Icons.Default.Mic,
                                contentDescription = "Voice system",
                                tint = cyan,
                                modifier = Modifier.size(32.dp)
                            )
                        }
                    }

                    Spacer(
                        modifier = Modifier.height(16.dp)
                    )

                    Text(
                        text = "SYSTEM ONLINE",
                        color = cyan,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 2.sp
                    )

                    Spacer(
                        modifier = Modifier.height(5.dp)
                    )

                    Text(
                        text = "HEY NEXUS READY",
                        color = textSecondary,
                        fontSize = 11.sp,
                        letterSpacing = 1.4.sp
                    )
                }
            }

            Spacer(
                modifier = Modifier.height(16.dp)
            )

            // ---------------------------------------------------------
            // SYSTEM STATUS
            // ---------------------------------------------------------

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(
                    containerColor = panel
                ),
                border = androidx.compose.foundation.BorderStroke(
                    width = 1.dp,
                    color = Color.White.copy(alpha = 0.06f)
                )
            ) {

                Column(
                    modifier = Modifier.padding(16.dp)
                ) {

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {

                        Text(
                            text = "SYSTEM STATUS",
                            color = textPrimary,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.5.sp,
                            modifier = Modifier.weight(1f)
                        )

                        Text(
                            text = "ONLINE",
                            color = cyan,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp
                        )
                    }

                    Spacer(
                        modifier = Modifier.height(13.dp)
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {

                        StatusItem(
                            label = "WAKE",
                            value = "READY",
                            color = cyan
                        )

                        StatusItem(
                            label = "SPEECH",
                            value = "READY",
                            color = cyan
                        )

                        StatusItem(
                            label = "ESP32",
                            value = "OFFLINE",
                            color = Color(0xFFFFB74D)
                        )
                    }
                }
            }

            Spacer(
                modifier = Modifier.height(18.dp)
            )

            // ---------------------------------------------------------
            // FEATURE HEADER
            // ---------------------------------------------------------

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {

                Text(
                    text = "COMMAND MODULES",
                    color = textPrimary,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.5.sp
                )

                Spacer(
                    modifier = Modifier.width(8.dp)
                )

                Box(
                    modifier = Modifier
                        .height(1.dp)
                        .weight(1f)
                        .background(
                            Brush.horizontalGradient(
                                colors = listOf(
                                    cyan.copy(alpha = 0.4f),
                                    Color.Transparent
                                )
                            )
                        )
                )
            }

            Spacer(
                modifier = Modifier.height(10.dp)
            )

            // ---------------------------------------------------------
            // FEATURE GRID
            // ---------------------------------------------------------

            val features = listOf(
                NexusFeature(
                    title = nexusText(AppTextKey.VOICE_SYSTEM),
                    icon = Icons.Default.Mic,
                    onClick = onVoiceClick
                ),
                NexusFeature(
                    title = nexusText(AppTextKey.INTELLIGENCE),
                    icon = Icons.Default.Psychology,
                    onClick = onIntelligenceClick
                ),
                NexusFeature(
                    title = "Vision",
                    icon = Icons.Default.Visibility,
                    onClick = onVisionClick
                ),
                NexusFeature(
                    title = "Navigation",
                    icon = Icons.Default.Explore,
                    onClick = onNavigationClick
                ),
                NexusFeature(
                    title = "Weather",
                    icon = Icons.Default.Cloud,
                    onClick = onWeatherClick
                ),
                NexusFeature(
                    title = nexusText(AppTextKey.COMMUNICATION),
                    icon = Icons.Default.Bluetooth,
                    onClick = onCommunicationClick
                )
            )

            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {

                items(
                    items = features
                ) { feature ->

                    NexusFeatureCard(
                        feature = feature,
                        panel = panelLight,
                        cyan = cyan,
                        violet = violet,
                        textPrimary = textPrimary,
                        textSecondary = textSecondary
                    )
                }
            }

            Spacer(
                modifier = Modifier.height(10.dp)
            )

            // ---------------------------------------------------------
            // FOOTER
            // ---------------------------------------------------------

            Text(
                text = "NEXUS-EYE • LOCAL AI CONTROL",
                modifier = Modifier.fillMaxWidth(),
                color = textSecondary.copy(alpha = 0.65f),
                fontSize = 8.sp,
                letterSpacing = 1.2.sp,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun StatusItem(
    label: String,
    value: String,
    color: Color
) {

    Column(
        horizontalAlignment = Alignment.CenterHorizontally
    ) {

        Row(
            verticalAlignment = Alignment.CenterVertically
        ) {

            Box(
                modifier = Modifier
                    .size(6.dp)
                    .clip(CircleShape)
                    .background(color)
            )

            Spacer(
                modifier = Modifier.width(5.dp)
            )

            Text(
                text = label,
                color = Color(0xFF8295A7),
                fontSize = 8.sp,
                letterSpacing = 0.8.sp
            )
        }

        Spacer(
            modifier = Modifier.height(4.dp)
        )

        Text(
            text = value,
            color = color,
            fontSize = 9.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.6.sp
        )
    }
}

@Composable
private fun NexusFeatureCard(
    feature: NexusFeature,
    panel: Color,
    cyan: Color,
    violet: Color,
    textPrimary: Color,
    textSecondary: Color
) {

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(1.55f)
            .clickable {
                feature.onClick()
            },
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = panel
        ),
        border = androidx.compose.foundation.BorderStroke(
            width = 1.dp,
            color = Color.White.copy(alpha = 0.065f)
        )
    ) {

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(15.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {

            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(RoundedCornerShape(13.dp))
                    .background(
                        Brush.linearGradient(
                            colors = listOf(
                                cyan.copy(alpha = 0.16f),
                                violet.copy(alpha = 0.10f)
                            )
                        )
                    )
                    .border(
                        width = 1.dp,
                        color = cyan.copy(alpha = 0.18f),
                        shape = RoundedCornerShape(13.dp)
                    ),
                contentAlignment = Alignment.Center
            ) {

                Icon(
                    imageVector = feature.icon,
                    contentDescription = feature.title,
                    tint = cyan,
                    modifier = Modifier.size(21.dp)
                )
            }

            Column {

                Text(
                    text = feature.title,
                    color = textPrimary,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold
                )

                Spacer(
                    modifier = Modifier.height(3.dp)
                )

                Text(
                    text = "OPEN MODULE",
                    color = textSecondary,
                    fontSize = 7.sp,
                    letterSpacing = 1.sp
                )
            }
        }
    }
}