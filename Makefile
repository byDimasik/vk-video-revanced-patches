.PHONY: all build test test-e2e check fetch-apk clean

all: build

build:
	./gradlew build

test:
	./gradlew test

test-e2e:
	./gradlew -Dvkvideo.e2e=true :patches:cleanTest :patches:test --tests "app.revanced.patches.vkvideo.e2e.RealApkSmokeTest"

check:
	./gradlew check

fetch-apk:
	./scripts/fetch-apk.sh

clean:
	./gradlew clean
