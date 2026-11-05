plugins {
    alias(libs.plugins.kotlin.jvm)
}

group = "com.vk.video"
version = "0.0.1"

dependencies {
    implementation(libs.revanced.patcher)
    implementation(libs.smali.dexlib2)
    implementation(libs.smali)

    testImplementation(libs.junit.jupiter.api)
    testRuntimeOnly(libs.junit.jupiter.engine)
}

kotlin {
    compilerOptions {
        freeCompilerArgs.addAll("-Xcontext-parameters", "-Xskip-prerelease-check")
    }
}

tasks.test {
    useJUnitPlatform()
    systemProperty("vkvideo.e2e", System.getProperty("vkvideo.e2e") ?: System.getenv("VKVIDEO_E2E") ?: "false")
}

val generatePatchBundleMetadata = tasks.register("generatePatchBundleMetadata") {
    val outputFile = layout.buildDirectory.file("generated/resources/patch-bundle.json")
    outputs.file(outputFile)

    doLast {
        val file = outputFile.get().asFile
        file.parentFile.mkdirs()
        file.writeText(
            """
            {
              "name": "vk-video-patches",
              "version": "${project.version}",
              "description": "ReVanced patches for VK Video",
              "targetPackage": "com.vk.vkvideo"
            }
            """.trimIndent() + "\n"
        )
    }
}

tasks.register<Jar>("rvp") {
    dependsOn(generatePatchBundleMetadata)
    archiveFileName.set("vk-video-patches.rvp")
    destinationDirectory.set(layout.buildDirectory.dir("libs"))

    manifest {
        attributes["Name"] = "vk-video-patches"
        attributes["Description"] = "ReVanced patches for VK Video"
        attributes["Version"] = project.version.toString()
        attributes["Timestamp"] = System.currentTimeMillis().toString()
        attributes["Source"] = "https://github.com/byDimasik/vk-video-revanced-patches"
        attributes["Author"] = "byDimasik"
        attributes["Contact"] = "https://github.com/byDimasik/vk-video-revanced-patches"
        attributes["Website"] = "https://github.com/byDimasik/vk-video-revanced-patches"
        attributes["License"] = "GPL-3.0"
    }

    from(sourceSets.main.get().output)
    from(generatePatchBundleMetadata.map { it.outputs.files })

    // Compile patch classes to DEX so ReVanced Manager can load the bundle.
    // dexlib2 is already a runtime dependency; use its DEX writer via the
    // dexlib2-backed approach: compile classes then convert with d8 if present.
    val d8 = providers.systemProperty("android.sdk.dir").orElse(providers.environmentVariable("ANDROID_HOME")).map { "$it/build-tools" }
    doLast {
        // Best-effort DEX: if Android build-tools (d8) are available, add classes.dex;
        // otherwise document the limitation in the bundle metadata.
        val buildTools = d8.get()
        val btDir = File(buildTools).listFiles()?.maxByOrNull { it.name }
        val d8Bin = btDir?.resolve("d8")
        if (d8Bin?.isFile == true) {
            val tmpDex = File(temporaryDir, "classes.dex")
            tmpDex.parentFile.mkdirs()
            exec {
                commandLine(d8Bin.absolutePath, "--release", "--min-api", "21", "--output", tmpDex.parentFile.absolutePath)
                args(sourceSets.main.get().output.classesDirs.flatMap { dir ->
                    dir.walkTopDown().filter { it.extension == "class" }.map { it.absolutePath }
                })
            }
            val jarFile = archiveFile.get().asFile
            val unpacked = temporaryDir.resolve("unpacked")
            delete(unpacked)
            copy {
                from(zipTree(jarFile))
                into(unpacked)
            }
            copy {
                from(tmpDex)
                into(unpacked)
            }
            delete(jarFile)
            project.exec {
                commandLine("zip", "-r", jarFile.absolutePath, ".")
                workingDir(unpacked)
            }
        } else {
            logger.lifecycle("rvp: Android build-tools not found (ANDROID_HOME unset); bundle will contain JVM classes only")
        }
    }
}
