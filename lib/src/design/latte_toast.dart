import 'package:flutter/material.dart';
import 'package:toastification/toastification.dart';

enum ToastType { info, success, warning, error }

class LatteToast {
  static const config = ToastificationConfig(
    alignment: Alignment.topCenter,
    itemWidth: double.infinity,
    maxToastLimit: 5,
  );

  static void show(
    BuildContext context, {
    required String message,
    ToastType type = ToastType.info,
    Widget? action,
    Duration duration = const Duration(milliseconds: 2500),
  }) {
    toastification.showCustom(
      context: context,
      alignment: Alignment.topCenter,
      autoCloseDuration: duration,
      animationDuration: const Duration(milliseconds: 220),
      builder: (context, item) {
        final scheme = Theme.of(context).colorScheme;
        final isDark = Theme.of(context).brightness == Brightness.dark;
        final (icon, iconColor) = switch (type) {
          ToastType.info => (Icons.info_outline, scheme.primary),
          ToastType.success => (Icons.check_circle_outline, scheme.primary),
          ToastType.warning => (
            Icons.warning_amber_outlined,
            isDark ? Colors.amber.shade300 : Colors.amber.shade800,
          ),
          ToastType.error => (Icons.error_outline, scheme.error),
        };

        return Dismissible(
          key: ValueKey('latte-toast-dismiss-${item.id}'),
          direction: DismissDirection.up,
          onDismissed: (_) {
            toastification.dismiss(item, showRemoveAnimation: false);
          },
          child: Container(
            width: double.infinity,
            margin: const EdgeInsets.symmetric(horizontal: 16, vertical: 4),
            child: Material(
              key: const ValueKey('latte-toast-pill'),
              elevation: 4,
              shape: const StadiumBorder(),
              color: scheme.surfaceContainerHigh,
              child: Padding(
                padding: const EdgeInsets.symmetric(
                  horizontal: 16,
                  vertical: 10,
                ),
                child: Semantics(
                  liveRegion: true,
                  label: message,
                  child: Row(
                    children: [
                      Icon(icon, size: 20, color: iconColor),
                      const SizedBox(width: 12),
                      Expanded(
                        child: Text(
                          message,
                          style: Theme.of(context).textTheme.bodyMedium
                              ?.copyWith(
                                color: scheme.onSurface,
                                fontWeight: FontWeight.w500,
                              ),
                          maxLines: 2,
                          overflow: TextOverflow.ellipsis,
                        ),
                      ),
                      if (action != null) ...[const SizedBox(width: 8), action],
                    ],
                  ),
                ),
              ),
            ),
          ),
        );
      },
    );
  }

  static void hide() {
    toastification.dismissAll();
  }
}
