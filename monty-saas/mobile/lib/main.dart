import 'package:flutter/material.dart';

void main() {
  runApp(const MonAgentApp());
}

class MonAgentApp extends StatelessWidget {
  const MonAgentApp({super.key});

  @override
  Widget build(BuildContext context) {
    const brand = Color(0xFF174B3A);
    return MaterialApp(
      title: 'MON-AGENT',
      debugShowCheckedModeBanner: false,
      theme: ThemeData(
        useMaterial3: true,
        colorScheme: ColorScheme.fromSeed(
          seedColor: brand,
          brightness: Brightness.light,
        ),
        scaffoldBackgroundColor: const Color(0xFFF7F8F6),
      ),
      home: const MigrationFoundationScreen(),
    );
  }
}

class MigrationFoundationScreen extends StatelessWidget {
  const MigrationFoundationScreen({super.key});

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(
        title: const Text('MON-AGENT'),
        backgroundColor: Theme.of(context).colorScheme.surface,
      ),
      body: Center(
        child: ConstrainedBox(
          constraints: const BoxConstraints(maxWidth: 560),
          child: Padding(
            padding: const EdgeInsets.all(24),
            child: Column(
              mainAxisSize: MainAxisSize.min,
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text(
                  'L’humain avant tout.',
                  style: Theme.of(context).textTheme.headlineMedium,
                ),
                const SizedBox(height: 12),
                Text(
                  'La fondation Flutter multiplateforme est initialisée. '
                  'Les modules métier seront portés progressivement après '
                  'inventaire et caractérisation de l’application existante.',
                  style: Theme.of(context).textTheme.bodyLarge,
                ),
                const SizedBox(height: 20),
                const _PlatformStatus(label: 'Android', detail: 'Cible Flutter'),
                const _PlatformStatus(label: 'iOS', detail: 'Cible Flutter'),
                const _PlatformStatus(label: 'Web', detail: 'Cible Flutter'),
              ],
            ),
          ),
        ),
      ),
    );
  }
}

class _PlatformStatus extends StatelessWidget {
  const _PlatformStatus({required this.label, required this.detail});

  final String label;
  final String detail;

  @override
  Widget build(BuildContext context) {
    return ListTile(
      contentPadding: EdgeInsets.zero,
      leading: const Icon(Icons.check_circle_outline),
      title: Text(label),
      subtitle: Text(detail),
    );
  }
}
