FLUTTER := mise exec -- flutter
DART := mise exec -- dart

.PHONY: format analyze test test-story check doctor dev probe

# `test` runs only checked-in tests and injected HTTP doubles. Live yande.re
# access is limited to the opt-in `probe` target.

format:
	$(DART) format --output=none --set-exit-if-changed .

analyze:
	$(FLUTTER) analyze

test:
	$(FLUTTER) test test
	$(FLUTTER) test tool/feasibility/yandere_probe_test.dart

test-story:
	@test "$(STORY)" = "discover" || (echo "usage: make test-story STORY=discover" >&2; exit 2)
	$(FLUTTER) test test/sites/yandere test/features/explore

check: format analyze test

doctor:
	$(FLUTTER) doctor -v

dev:
	$(FLUTTER) run

probe:
	$(DART) run tool/feasibility/yandere_probe.dart
