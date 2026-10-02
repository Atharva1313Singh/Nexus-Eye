package com.thirdeye.app.ui.theme

import androidx.compose.ui.graphics.Color

/*
 * Nexus-Eye Futuristic Color System
 *
 * Design direction:
 * - OLED / near-black foundation
 * - Electric cyan primary accent
 * - Deep blue secondary accent
 * - Violet AI/intelligence accent
 * - High-contrast futuristic text
 *
 * These are UI colors only.
 * They do not affect any application logic.
 */

// ================================================================
// CORE NEXUS COLORS
// ================================================================

val NexusCyan = Color(0xFF00E5FF)
val NexusCyanBright = Color(0xFF18F5FF)
val NexusCyanDark = Color(0xFF00AFC2)

val NexusBlue = Color(0xFF2979FF)
val NexusBlueBright = Color(0xFF448AFF)
val NexusBlueDark = Color(0xFF1555C0)

val NexusViolet = Color(0xFF7C4DFF)
val NexusVioletBright = Color(0xFF9A7BFF)
val NexusVioletDark = Color(0xFF512DA8)

// ================================================================
// DARK / OLED BACKGROUND
// ================================================================

val NexusBackground = Color(0xFF05070C)
val NexusBackgroundSecondary = Color(0xFF070B12)

val NexusSurface = Color(0xFF0B1019)
val NexusSurfaceVariant = Color(0xFF101824)
val NexusSurfaceElevated = Color(0xFF131D29)

// ================================================================
// GLASS / PANEL COLORS
// ================================================================

val NexusPanel = Color(0xFF0B1019)
val NexusPanelLight = Color(0xFF101824)
val NexusPanelElevated = Color(0xFF16212E)

val NexusGlass = Color(0xFF0E1722)
val NexusGlassLight = Color(0xFF152231)

// ================================================================
// TEXT
// ================================================================

val NexusTextPrimary = Color(0xFFEAFBFF)
val NexusTextSecondary = Color(0xFF8295A7)
val NexusTextMuted = Color(0xFF526576)
val NexusTextDisabled = Color(0xFF374654)

// ================================================================
// STATUS COLORS
// ================================================================

val NexusOnline = Color(0xFF00E5A0)
val NexusReady = Color(0xFF00E5FF)
val NexusWarning = Color(0xFFFFB74D)
val NexusError = Color(0xFFFF5252)
val NexusOffline = Color(0xFF66717D)

// ================================================================
// OUTLINE / BORDER COLORS
// ================================================================

val NexusOutline = Color(0xFF233342)
val NexusOutlineBright = Color(0xFF365064)

val NexusCyanOutline = Color(0xFF00E5FF)
val NexusBlueOutline = Color(0xFF2979FF)
val NexusVioletOutline = Color(0xFF7C4DFF)

// ================================================================
// OVERLAY / EFFECT COLORS
// ================================================================

val NexusCyanGlow = Color(0x3300E5FF)
val NexusBlueGlow = Color(0x332979FF)
val NexusVioletGlow = Color(0x337C4DFF)

val NexusScrim = Color(0x99000000)

// ================================================================
// LIGHT THEME SUPPORT
//
// Kept intentionally separate so the application can still work
// when the Android system is using light mode.
// ================================================================

val NexusLightBackground = Color(0xFFF4F8FB)
val NexusLightSurface = Color(0xFFFFFFFF)
val NexusLightSurfaceVariant = Color(0xFFEAF2F7)

val NexusLightTextPrimary = Color(0xFF0A1620)
val NexusLightTextSecondary = Color(0xFF455A64)

val NexusLightPrimary = Color(0xFF007C91)
val NexusLightSecondary = Color(0xFF2459B8)
val NexusLightTertiary = Color(0xFF6540C4)