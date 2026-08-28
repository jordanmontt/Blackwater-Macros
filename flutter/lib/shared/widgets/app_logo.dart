import 'package:flutter/material.dart';

/// Logotipo de la marca (cortado en círculo, igual que el frontend Next.js
/// que lo mostraba con `rounded-full`).
class AppLogo extends StatelessWidget {
  const AppLogo({super.key, this.size = 96});

  final double size;

  @override
  Widget build(BuildContext context) {
    return ClipOval(
      child: SizedBox(
        width: size,
        height: size,
        child: Image.asset(
          'assets/logo.png',
          fit: BoxFit.cover,
          errorBuilder: (context, error, stackTrace) => Icon(
            Icons.local_fire_department,
            size: size,
            color: Theme.of(context).colorScheme.primary,
          ),
        ),
      ),
    );
  }
}

