.PHONY: all build test check fetch-apk rvp clean

all: build

build:
	./gradlew build

test:
	./gradlew test

check:
	./gradlew check

fetch-apk:
	./scripts/fetch-apk.sh

rvp:
	./gradlew rvp

clean:
	./gradlew clean
