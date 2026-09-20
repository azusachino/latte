enum SiteFailureKind {
  invalidRequest,
  transportUnavailable,
  throttled,
  remoteUnavailable,
  notFound,
  malformedResponse,
  rejectedMedia,
  remoteFailure,
}

class SiteFailure {
  const SiteFailure({
    required this.kind,
    required this.retryable,
    required this.message,
    this.retryAfter,
  });

  final SiteFailureKind kind;
  final bool retryable;
  final String message;
  final Duration? retryAfter;

  @override
  bool operator ==(Object other) =>
      other is SiteFailure &&
      other.kind == kind &&
      other.retryable == retryable &&
      other.message == message &&
      other.retryAfter == retryAfter;

  @override
  int get hashCode => Object.hash(kind, retryable, message, retryAfter);
}

class SiteFailureException implements Exception {
  const SiteFailureException(this.failure);

  final SiteFailure failure;

  @override
  String toString() => 'SiteFailureException(${failure.kind})';
}
