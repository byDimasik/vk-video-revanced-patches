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
}
