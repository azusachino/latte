GRADLE := ./gradlew

.PHONY: check validate test lint assemble install dev clean

test:
	$(GRADLE) testDebugUnitTest

lint:
	$(GRADLE) lintDebug

check: test

validate: check assemble

assemble:
	$(GRADLE) assembleDebug

install:
	$(GRADLE) installDebug

dev: install
	adb shell am start -n com.azusachino.latte/.MainActivity

clean:
	$(GRADLE) clean
