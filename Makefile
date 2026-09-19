FLUTTER := mise exec -- flutter
DART := mise exec -- dart

.PHONY: format analyze test check doctor dev

format:
	$(DART) format --output=none --set-exit-if-changed .

analyze:
	$(FLUTTER) analyze

test:
	$(FLUTTER) test

check: format analyze test

doctor:
	$(FLUTTER) doctor -v

dev:
	$(FLUTTER) run
