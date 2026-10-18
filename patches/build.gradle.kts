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

tasks.test {
    useJUnitPlatform()
}
