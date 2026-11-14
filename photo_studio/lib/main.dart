import 'package:material_ui/material_ui.dart';
import 'package:provider/provider.dart';

import 'screens/home_screen.dart';
import 'state/studio_state.dart';

void main() {
  WidgetsFlutterBinding.ensureInitialized();
  final state = StudioState()..load();
  runApp(ChangeNotifierProvider.value(value: state, child: const PhotoStudioApp()));
}

class PhotoStudioApp extends StatelessWidget {
  const PhotoStudioApp({super.key});

  @override
  Widget build(BuildContext context) {
    const seed = Color(0xFF1E5AA8);
    return MaterialApp(
      title: 'Photo Studio',
      debugShowCheckedModeBanner: false,
      theme: ThemeData(
        colorScheme: ColorScheme.fromSeed(seedColor: seed),
        useMaterial3: true,
      ),
      darkTheme: ThemeData(
        colorScheme:
            ColorScheme.fromSeed(seedColor: seed, brightness: Brightness.dark),
        useMaterial3: true,
      ),
      home: const HomeScreen(),
    );
  }
}
