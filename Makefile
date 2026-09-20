FLUTTER := mise exec -- flutter
DART := mise exec -- dart

.PHONY: format lint analyze test test-story check doctor dev probe

# `test` runs only checked-in tests and injected HTTP doubles. Live yande.re
# access is limited to the opt-in `probe` target.

format:
	$(DART) format --output=none --set-exit-if-changed .

lint:
	$(FLUTTER) analyze

analyze: lint

test:
	$(FLUTTER) test test
	$(FLUTTER) test tool/feasibility/yandere_probe_test.dart

test-story:
	@if [ "$(STORY)" = "discover" ] || [ "$(STORY)" = "popular" ] || [ "$(STORY)" = "search" ]; then \
		$(FLUTTER) test test/domain/popular_query_test.dart test/sites/yandere test/features/explore; \
	else \
		echo "usage: make test-story STORY=popular|discover|search" >&2; exit 2; \
	fi

check: format lint test

doctor:
	$(FLUTTER) doctor -v

dev:
	$(FLUTTER) run

probe:
	$(DART) run tool/feasibility/yandere_probe.dart
