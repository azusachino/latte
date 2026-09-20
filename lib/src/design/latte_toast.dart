import 'package:flutter/material.dart';
import 'package:toastification/toastification.dart';

enum ToastType { info, success, warning, error }

class LatteToast {
  static ToastificationItem? _activeItem;

  static void show(
    BuildContext context, {
    required String message,
    ToastType type = ToastType.info,
    Widget? action,
    Duration duration = const Duration(milliseconds: 2500),
  }) {
    if (_activeItem != null) {
      toastification.dismiss(_activeItem!);
      _activeItem = null;
    }

    _activeItem = toastification.showCustom(
      context: context,
      alignment: Alignment.topCenter,
      autoCloseDuration: duration,
      animationDuration: const Duration(milliseconds: 220),
      builder: (context, item) {
        final scheme = Theme.of(context).colorScheme;
        final (icon, iconColor) = switch (type) {
          ToastType.info => (Icons.info_outline, scheme.primary),
          ToastType.success => (Icons.check_circle_outline, scheme.primary),
          ToastType.warning => (Icons.warning_amber_outlined, scheme.error),
          ToastType.error => (Icons.error_outline, scheme.error),
        };

        return Dismissible(
          key: ValueKey('latte-toast-dismiss-${item.id}'),
          direction: DismissDirection.up,
          onDismissed: (_) {
            if (identical(_activeItem, item)) _activeItem = null;
            toastification.dismiss(item, showRemoveAnimation: false);
          },
          child: Material(
            key: const ValueKey('latte-toast-pill'),
            elevation: 4,
            shape: const StadiumBorder(),
            color: scheme.surfaceContainerHigh,
            child: Container(
              padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 10),
              constraints: const BoxConstraints(maxWidth: 480),
              child: Semantics(
                liveRegion: true,
                label: message,
                child: Row(
                  mainAxisSize: MainAxisSize.min,
                  children: [
                    Icon(icon, size: 20, color: iconColor),
                    const SizedBox(width: 10),
                    Flexible(
                      child: Text(
                        message,
                        style: Theme.of(context).textTheme.bodyMedium?.copyWith(
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
        );
      },
    );
  }

  static void hide() {
    if (_activeItem != null) {
      toastification.dismiss(_activeItem!);
      _activeItem = null;
    }
  }
}
