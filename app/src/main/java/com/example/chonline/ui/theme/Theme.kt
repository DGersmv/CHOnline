package com.example.chonline.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val CorpChatScheme = darkColorScheme(
    primary = CorpChatColors.accent,
    onPrimary = CorpChatColors.bgDeep,
    primaryContainer = CorpChatColors.bgSurface,
    onPrimaryContainer = CorpChatColors.textPrimary,
    secondary = CorpChatColors.textSecondary,
    onSecondary = CorpChatColors.bgDeep,
    tertiary = CorpChatColors.bgBubbleOut,
    onTertiary = CorpChatColors.textPrimary,
    background = CorpChatColors.bgDeep,
    onBackground = CorpChatColors.textPrimary,
    surface = CorpChatColors.bgPanel,
    onSurface = CorpChatColors.textPrimary,
    surfaceVariant = CorpChatColors.bgSurface,
    onSurfaceVariant = CorpChatColors.textMuted,
    outline = CorpChatColors.border,
    outlineVariant = CorpChatColors.border,
    error = CorpChatColors.error,
    onError = Color.White,
)

@Composable
fun CHOnlineTheme(
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = CorpChatScheme,
        typography = Typography,
        content = content,
    )
}
