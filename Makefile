.PHONY: all build test test-e2e check fetch-apk rvp clean

all: build

build:
	./gradlew build

test:
	./gradlew test

test-e2e:
	VKVIDEO_E2E=true ./gradlew :patches:cleanTest :patches:test --tests "app.revanced.patches.vkvideo.e2e.RealApkSmokeTest"

check:
	./gradlew check

fetch-apk:
	./scripts/fetch-apk.sh

rvp:
	./gradlew rvp

clean:
	./gradlew clean
