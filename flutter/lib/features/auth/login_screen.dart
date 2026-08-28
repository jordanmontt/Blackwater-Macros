import 'package:flutter/material.dart';

import '../../core/api/api.dart';
import '../../core/auth/auth_controller.dart';
import '../../i18n/es.dart';
import '../../shared/widgets/app_logo.dart';

/// Pantalla de inicio de sesión. Al autenticarse correctamente el router
/// redirige automáticamente a /hoy (AuthController notifica el cambio).
class LoginScreen extends StatefulWidget {
  const LoginScreen({super.key});

  @override
  State<LoginScreen> createState() => _LoginScreenState();
}

class _LoginScreenState extends State<LoginScreen> {
  final _formKey = GlobalKey<FormState>();
  final _username = TextEditingController();
  final _password = TextEditingController();
  bool _submitting = false;
  String? _error;

  @override
  void dispose() {
    _username.dispose();
    _password.dispose();
    super.dispose();
  }

  Future<void> _submit() async {
    if (!_formKey.currentState!.validate()) return;
    setState(() {
      _submitting = true;
      _error = null;
    });
    try {
      await appAuthController!.login(_username.text.trim(), _password.text);
    } on ApiException catch (e) {
      if (!mounted) return;
      setState(() {
        if (e.isUnauthorized) {
          _error = switch (e.message) {
            'USER_NOT_FOUND' => S.auth.userNotFound,
            'INVALID_PASSWORD' => S.auth.invalidPassword,
            _ => S.auth.invalidCredentials,
          };
        } else {
          _error = S.auth.genericError;
        }
      });
    } catch (_) {
      if (!mounted) return;
      setState(() => _error = S.auth.genericError);
    } finally {
      if (mounted) {
        setState(() => _submitting = false);
      }
    }
  }

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    return Scaffold(
      body: SafeArea(
        child: Center(
          child: SingleChildScrollView(
            padding: const EdgeInsets.all(24),
            child: ConstrainedBox(
              constraints: const BoxConstraints(maxWidth: 420),
              child: Form(
                key: _formKey,
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.stretch,
                  children: [
                    const Center(child: AppLogo(size: 120)),
                    const SizedBox(height: 16),
                    Text(
                      S.appName,
                      textAlign: TextAlign.center,
                      style: theme.textTheme.headlineMedium,
                    ),
                    const SizedBox(height: 4),
                    Text(
                      S.auth.loginDescription,
                      textAlign: TextAlign.center,
                      style: theme.textTheme.bodyMedium,
                    ),
                    const SizedBox(height: 32),
                    TextFormField(
                      controller: _username,
                      enabled: !_submitting,
                      decoration: InputDecoration(
                        labelText: S.auth.username,
                        prefixIcon: Icon(Icons.person_outline),
                      ),
                      textInputAction: TextInputAction.next,
                      autofillHints: const [AutofillHints.username],
                      validator: (value) =>
                          value == null || value.trim().isEmpty ? S.auth.username : null,
                    ),
                    const SizedBox(height: 16),
                    TextFormField(
                      controller: _password,
                      enabled: !_submitting,
                      obscureText: true,
                      decoration: InputDecoration(
                        labelText: S.auth.password,
                        prefixIcon: Icon(Icons.lock_outline),
                      ),
                      textInputAction: TextInputAction.done,
                      autofillHints: const [AutofillHints.password],
                      onFieldSubmitted: (_) => _submit(),
                      validator: (value) =>
                          value == null || value.isEmpty ? S.auth.password : null,
                    ),
                    if (_error != null) ...[
                      const SizedBox(height: 16),
                      Text(
                        _error!,
                        textAlign: TextAlign.center,
                        style: theme.textTheme.bodyMedium!
                            .copyWith(color: theme.colorScheme.error),
                      ),
                    ],
                    const SizedBox(height: 24),
                    FilledButton(
                      onPressed: _submitting ? null : _submit,
                      child: _submitting
                          ? const SizedBox(
                              width: 20,
                              height: 20,
                              child: CircularProgressIndicator(strokeWidth: 2),
                            )
                          : Text(S.auth.submit),
                    ),
                  ],
                ),
              ),
            ),
          ),
        ),
      ),
    );
  }
}