import 'package:flutter/material.dart';

import '../../i18n/es.dart';

/// Estado genérico de carga/error para las pantallas que piden datos.
class AsyncView<T> extends StatelessWidget {
  const AsyncView({
    super.key,
    required this.loading,
    this.error,
    this.errorMessage,
    this.onRetry,
    required this.builder,
  });

  /// true mientras la pantalla carga.
  final bool loading;

  /// Mensaje de error si la última carga falló (null cuando todo va bien).
  final Object? error;

  /// Texto alternativo si el error no tiene mensaje legible.
  final String? errorMessage;

  final VoidCallback? onRetry;
  final Widget Function() builder;

  @override
  Widget build(BuildContext context) {
    if (loading) {
      return const Center(child: CircularProgressIndicator());
    }
    if (error != null) {
      return ErrorView(
        message: errorMessage ?? S.common.errorGeneric,
        onRetry: onRetry,
      );
    }
    return builder();
  }
}

class ErrorView extends StatelessWidget {
  const ErrorView({super.key, required this.message, this.onRetry});

  final String message;
  final VoidCallback? onRetry;

  @override
  Widget build(BuildContext context) {
    return Center(
      child: Padding(
        padding: const EdgeInsets.all(24),
        child: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            Icon(Icons.cloud_off, size: 48, color: Theme.of(context).colorScheme.outline),
            const SizedBox(height: 12),
            Text(message, textAlign: TextAlign.center),
            if (onRetry != null) ...[
              const SizedBox(height: 16),
              FilledButton.tonalIcon(
                onPressed: onRetry,
                icon: const Icon(Icons.refresh),
                label: Text(S.common.retry),
              ),
            ],
          ],
        ),
      ),
    );
  }
}

class EmptyState extends StatelessWidget {
  const EmptyState({super.key, required this.message, this.icon = Icons.inbox_outlined});

  final String message;
  final IconData icon;

  @override
  Widget build(BuildContext context) {
    return Center(
      child: Padding(
        padding: const EdgeInsets.all(32),
        child: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            Icon(icon, size: 48, color: Theme.of(context).colorScheme.outline),
            const SizedBox(height: 12),
            Text(message, textAlign: TextAlign.center),
          ],
        ),
      ),
    );
  }
}