GRADLE := mise exec -- gradle

.PHONY: check validate test lint md-check md-format assemble install dev clean

test:
	$(GRADLE) testDebugUnitTest

lint:
	$(GRADLE) lintDebug

md-check:
	mise exec -- rumdl check .

md-format:
	mise exec -- rumdl fmt .

check: md-check test

validate: check assemble

assemble:
	$(GRADLE) assembleDebug

install:
	$(GRADLE) installDebug

dev: install
	adb shell am start -n com.azusachino.latte/.MainActivity

clean:
	$(GRADLE) clean
