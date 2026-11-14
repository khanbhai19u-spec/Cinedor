import 'package:material_ui/material_ui.dart';

void toast(BuildContext context, String msg, {bool error = false}) {
  final m = ScaffoldMessenger.of(context);
  m.hideCurrentSnackBar();
  m.showSnackBar(SnackBar(
    content: Text(msg),
    backgroundColor: error ? Colors.red.shade700 : null,
    behavior: SnackBarBehavior.floating,
  ));
}

/// Shows a blocking progress dialog while [task] runs. [task] receives a
/// callback to update the message. Returns the task's result, or null on error
/// (the error is shown as a toast).
Future<T?> withProgress<T>(
  BuildContext context,
  String title,
  Future<T> Function(void Function(String msg) update) task,
) async {
  final msg = ValueNotifier<String>(title);
  showDialog<void>(
    context: context,
    barrierDismissible: false,
    builder: (_) => PopScope(
      canPop: false,
      child: AlertDialog(
        content: Row(children: [
          const SizedBox(
              width: 28, height: 28, child: CircularProgressIndicator()),
          const SizedBox(width: 20),
          Expanded(
            child: ValueListenableBuilder<String>(
              valueListenable: msg,
              builder: (_, v, _) => Text(v),
            ),
          ),
        ]),
      ),
    ),
  );
  try {
    final r = await task((m) => msg.value = m);
    if (context.mounted) Navigator.of(context, rootNavigator: true).pop();
    return r;
  } catch (e) {
    if (context.mounted) {
      Navigator.of(context, rootNavigator: true).pop();
      toast(context, e.toString().replaceFirst('Exception: ', ''), error: true);
    }
    return null;
  } finally {
    msg.dispose();
  }
}

class SectionTitle extends StatelessWidget {
  final String text;
  const SectionTitle(this.text, {super.key});
  @override
  Widget build(BuildContext context) => Padding(
        padding: const EdgeInsets.fromLTRB(16, 18, 16, 8),
        child: Text(text,
            style: Theme.of(context)
                .textTheme
                .titleSmall
                ?.copyWith(color: Theme.of(context).colorScheme.primary)),
      );
}
