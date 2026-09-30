GRADLE := mise exec -- gradle

.PHONY: check validate test lint md-check md-format assemble assemble-release install dev clean experiment-konachan

test:
	$(GRADLE) testDebugUnitTest

lint:
	$(GRADLE) lintDebug

md-check:
	mise exec -- rumdl check .

md-format:
	mise exec -- rumdl fmt .

check: md-check test

experiment-konachan:
	./scripts/experiments/konachan-com-probe.sh

validate: check assemble assemble-release

assemble:
	$(GRADLE) assembleDebug

assemble-release:
	$(GRADLE) assembleRelease

install:
	$(GRADLE) installDebug

dev: install
	adb shell am start -n com.azusachino.latte/.MainActivity

clean:
	$(GRADLE) clean
