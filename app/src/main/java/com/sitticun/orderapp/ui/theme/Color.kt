package com.sitticun.orderapp.ui.theme

import androidx.compose.ui.graphics.Color

// ============================================================================
// iOS Human Interface Guidelines (Apple HIG) System Color Palette
// ============================================================================

// System Backgrounds & Grouped Surfaces
val IosGroupedBackground = Color(0xFFF2F2F7)      // systemGroupedBackground (พื้นหลังหลักสไตล์ iOS)
val IosSecondaryGroupedBg = Color(0xFFFFFFFF)     // secondarySystemGroupedBackground (การ์ดสีขาว)
val IosTertiaryGroupedBg = Color(0xFFE5E5EA)      // tertiarySystemGroupedBackground / Search field fill
val IosSearchFieldBg = Color(0xFFE3E3E8)          // พื้นหลังช่องค้นหาสไตล์ iOS SearchBar
val IosTranslucentBar = Color(0xEBFFFFFF)         // แถบ Navigation/Tab Bar โปร่งแสงสไตล์ iOS Blur

// System Tints
val IosBlue = Color(0xFF007AFF)                   // systemBlue
val IosBlueLight = Color(0xFFE5F1FF)              // Tinted Blue Container
val IosBlueDark = Color(0xFF0040DD)

val IosIndigo = Color(0xFF5856D6)                 // systemIndigo
val IosIndigoLight = Color(0xFFEEEEFF)
val IosIndigoDark = Color(0xFF3634A3)

val IosTeal = Color(0xFF00C7BE)                   // systemMint / Teal

val IosGreen = Color(0xFF34C759)                  // systemGreen
val IosGreenLight = Color(0xFFE8F8EC)
val IosGreenDark = Color(0xFF1E7E34)

val IosOrange = Color(0xFFFF9500)                 // systemOrange
val IosOrangeLight = Color(0xFFFFF3E0)
val IosOrangeDark = Color(0xFFC93400)

val IosRed = Color(0xFFFF3B30)                    // systemRed
val IosRedLight = Color(0xFFFFEBEA)
val IosRedDark = Color(0xFFD70015)

// Labels & Separators
val IosLabelPrimary = Color(0xFF000000)           // label (ดำคมชัดสไตล์ iOS)
val IosLabelSecondary = Color(0xFF3C3C43)         // secondaryLabel
val IosSystemGray = Color(0xFF8E8E93)             // systemGray
val IosSystemGray2 = Color(0xFFAEAEB2)            // systemGray2
val IosSystemGray4 = Color(0xFFD1D1D6)            // systemGray4
val IosSystemGray5 = Color(0xFFE5E5EA)            // systemGray5
val IosSystemGray6 = Color(0xFFF2F2F7)            // systemGray6
val IosSeparator = Color(0xFFC6C6C8)              // separator (เส้นคั่นบางสไตล์ iOS)
val IosSeparatorLight = Color(0xFFE5E5EA)         // เส้นคั่นในการ์ด

// ============================================================================
// Mapped Semantic Tokens (Preserving compatibility across screens with iOS look)
// ============================================================================
val PastelBackground = IosGroupedBackground       // พื้นหลัง Grouped iOS (#F2F2F7)
val CardWhite = IosSecondaryGroupedBg             // การ์ดสีขาวบริสุทธิ์ (#FFFFFF)

val PastelMint = IosBlue                          // สีหลัก iOS System Blue (#007AFF)
val PastelMintLight = IosBlueLight                // พื้นหลังสีฟ้าอ่อน iOS (#E5F1FF)
val PastelMintDark = IosBlueDark                  // สีฟ้าเข้ม iOS (#0040DD)

val PastelPeach = IosOrange                       // สีรอง iOS System Orange (#FF9500)
val PastelPeachLight = IosOrangeLight             // พื้นหลังส้มอ่อน (#FFF3E0)
val PastelPeachDark = IosOrangeDark               // สีส้มเข้ม (#C93400)

val PastelLavender = IosIndigo                    // สีที่สาม iOS System Indigo (#5856D6)
val PastelLavenderLight = IosIndigoLight          // พื้นหลังม่วงครามอ่อน (#EEEEFF)
val PastelLavenderDark = IosIndigoDark            // สีม่วงครามเข้ม (#3634A3)

val TextPrimary = Color(0xFF1C1C1E)               // สีตัวอักษรหลัก iOS Dark Charcoal
val TextSecondary = Color(0xFF6C6C70)             // สีตัวอักษรรอง iOS Secondary Label
val TextMuted = IosSystemGray                     // สีตัวอักษรจาง / Placeholder (#8E8E93)
val BorderLight = IosSeparatorLight               // เส้นขอบการ์ดบางสไตล์ iOS (#E5E5EA)
val SurfaceVariantColor = IosSystemGray6          // พื้นผิวการ์ดรอง (#F2F2F7)

// Semantic Status Colors (iOS HIG)
val StatusSuccess = IosGreen                      // เขียว iOS (#34C759)
val StatusWarning = IosOrange                     // ส้ม iOS (#FF9500)
val StatusDanger = IosRed                         // แดง iOS (#FF3B30)
val StatusInfo = IosBlue                          // ฟ้า iOS (#007AFF)
val StatusNeutral = IosSystemGray                 // เทา iOS (#8E8E93)
