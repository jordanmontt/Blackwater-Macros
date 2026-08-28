import 'package:flutter/material.dart';

/// Paleta del proyecto: verde agua, usado como color semilla del tema M3.
const seedColor = Color(0xFF2E8B8B);

/// Verde bosque (inspirado en Opeth) para los botones de acción.
const opethGreen = Color(0xFF1E6B4A);

ThemeData buildTheme(Brightness brightness) {
  final colorScheme = ColorScheme.fromSeed(
    seedColor: seedColor,
    brightness: brightness,
  );
  final isDark = brightness == Brightness.dark;
  final buttonStyle = FilledButton.styleFrom(
    backgroundColor: isDark ? opethGreen : colorScheme.primary,
    foregroundColor: isDark ? Colors.white : colorScheme.onPrimary,
  );
  return ThemeData(
    colorScheme: colorScheme,
    useMaterial3: true,
    cardTheme: CardThemeData(
      elevation: 0,
      color: isDark
          ? colorScheme.surfaceContainerHighest.withValues(alpha: 0.35)
          : colorScheme.surface,
      shape: RoundedRectangleBorder(
        borderRadius: BorderRadius.circular(16),
        side: BorderSide(color: colorScheme.outlineVariant),
      ),
    ),
    inputDecorationTheme: InputDecorationTheme(
      border: OutlineInputBorder(borderRadius: BorderRadius.circular(12)),
      filled: true,
    ),
    floatingActionButtonTheme: FloatingActionButtonThemeData(
      backgroundColor: opethGreen,
      foregroundColor: Colors.white,
    ),
    filledButtonTheme: FilledButtonThemeData(style: buttonStyle),
  );
}