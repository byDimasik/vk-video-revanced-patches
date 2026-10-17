# Analogue Ad-Blocking Patterns (YouTube, TikTok, Instagram) & ReVanced v22 DSL

This document details the mechanics of ReVanced Patcher v22 Kotlin DSL and documents concrete bytecode/resource ad-interception patterns analyzed from upstream YouTube, TikTok, and Instagram patches.

---

## 1. ReVanced Patcher v22 DSL Mechanics

ReVanced Patcher v22 introduces a declarative Kotlin DSL for defining patches, fingerprints, and bytecode/resource mutations under the `app.revanced.patcher.*` package namespace.

### Core Package Imports

```kotlin
import app.revanced.patcher.annotation.Name
import app.revanced.patcher.annotation.Description
import app.revanced.patcher.annotation.Version
import app.revanced.patcher.annotation.CompatiblePackage
import app.revanced.patcher.patch.BytecodePatch
import app.revanced.patcher.patch.ResourcePatch
import app.revanced.patcher.patch.annotation.Patch
import app.revanced.patcher.fingerprint.method.impl.MethodFingerprint
import app.revanced.patcher.fingerprint.method.annotation.Fuzzy
import app.revanced.patcher.extensions.Mutators.replace
import app.revanced.patcher.extensions.Mutators.add
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.instruction.Instruction
```

### DSL Structures

#### 1. Bytecode Patch (`BytecodePatch`)

```kotlin
@Patch
@Name("sample-bytecode-patch")
@Description("Mutates Dalvik bytecode instructions at build time.")
@Version("0.0.1")
@CompatiblePackage("com.vk.vkvideo")
class SampleBytecodePatch : BytecodePatch(
    fingerprints = setOf(SampleFingerprint)
) {
    override fun execute(context: BytecodePatch.Context) {
        val match = SampleFingerprint.result ?: throw Exception("Fingerprint not found")
        val method = match.mutableMethod

        // Mutate bytecode instructions
        method.replaceInstruction(0, "return-void")
    }
}
```

#### 2. Resource Patch (`ResourcePatch`)

```kotlin
@Patch
@Name("sample-resource-patch")
@Description("Modifies XML layouts, values, and resource tables.")
@Version("0.0.1")
@CompatiblePackage("com.vk.vkvideo")
class SampleResourcePatch : ResourcePatch() {
    override fun execute(context: ResourcePatch.Context) {
        val resourceTable = context.resourceTable
        val id = resourceTable.getResourceId("id/ad_container", "com.vk.vkvideo")

        context.xmlFiles.forEach { xmlFile ->
            if (xmlFile.path.endsWith("res/layout/player_overlay.xml")) {
                xmlFile.document.getElementsByTagName("View").let { nodes ->
                    // Mutate XML nodes
                }
            }
        }
    }
}
```

#### 3. Fingerprint DSL (`MethodFingerprint`)

```kotlin
object SampleFingerprint : MethodFingerprint(
    returnType = "V",
    parameters = listOf("Ljava/lang/String;", "I"),
    opcodes = listOf(
        Opcode.INVOKE_VIRTUAL,
        Opcode.CONST_4,
        Opcode.RETURN_VOID
    ),
    strings = listOf("ad_trigger_url")
)
```

---

## 2. Concrete Ad-Interception Patterns

### Pattern 1: Video Player VAST / Mid-Roll Early-Return Short-Circuiting (YouTube Pattern)

In video player architectures (like YouTube VAST/VMAP or VK Video ad loaders), ad request methods trigger network fetches or instantiations for video ads. Short-circuiting these methods by injecting an early `return-void` or `return null` prevents ad loading entirely without breaking player execution.

#### ReVanced v22 Kotlin DSL Implementation

```kotlin
object AdLoaderFingerprint : MethodFingerprint(
    returnType = "V",
    parameters = listOf("Lcom/vk/video/ad/AdRequest;"),
    opcodes = listOf(
        Opcode.IGET_OBJECT,
        Opcode.INVOKE_VIRTUAL,
        Opcode.RETURN_VOID
    ),
    strings = listOf("request_vast_ad")
)

@Patch
@Name("video-ad-short-circuit")
@Description("Short-circuits player VAST ad loading methods.")
class VideoAdShortCircuitPatch : BytecodePatch(
    fingerprints = setOf(AdLoaderFingerprint)
) {
    override fun execute(context: BytecodePatch.Context) {
        val match = AdLoaderFingerprint.result ?: return
        val method = match.mutableMethod

        // Insert return-void at index 0 to exit method immediately
        method.implementation?.instructions?.let { instructions ->
            method.replaceInstruction(0, "return-void")
        }
    }
}
```

#### Smali Bytecode Comparison

**Original Smali:**
```smali
.method public loadVastAd(Lcom/vk/video/ad/AdRequest;)V
    .registers 3

    iget-object v0, p0, Lcom/vk/video/ad/AdRequestLoader;->adClient:Lcom/vk/video/ad/AdClient;
    invoke-virtual {v0, p1}, Lcom/vk/video/ad/AdClient;->fetchAd(Lcom/vk/video/ad/AdRequest;)V
    return-void
.end method
```

**Patched Smali (Short-Circuited):**
```smali
.method public loadVastAd(Lcom/vk/video/ad/AdRequest;)V
    .registers 3

    return-void
    iget-object v0, p0, Lcom/vk/video/ad/AdRequestLoader;->adClient:Lcom/vk/video/ad/AdClient;
    invoke-virtual {v0, p1}, Lcom/vk/video/ad/AdClient;->fetchAd(Lcom/vk/video/ad/AdRequest;)V
    return-void
.end method
```

---

### Pattern 2: Feed / JSON Parser List Filtering via Injected Extension (TikTok / Instagram Pattern)

Feed-based apps (TikTok, Instagram, VK Video) return sponsored posts mixed inside normal feed response arrays. Rather than attempting complex opcode rewrites inside obfuscated loop logic, bytecode is patched to route the parsed `List` through an injected standalone Kotlin/Java Extension helper method (`filterAdItems`).

#### ReVanced v22 Kotlin DSL Implementation

```kotlin
object FeedParserFingerprint : MethodFingerprint(
    returnType = "Ljava/util/List;",
    parameters = listOf("Lorg/json/JSONObject;"),
    opcodes = listOf(
        Opcode.INVOKE_VIRTUAL,
        Opcode.MOVE_RESULT_OBJECT,
        Opcode.RETURN_OBJECT
    ),
    strings = listOf("items", "ads")
)

@Patch
@Name("feed-ad-filter")
@Description("Filters out ad items from feed API responses via injected extension.")
class FeedAdFilterPatch : BytecodePatch(
    fingerprints = setOf(FeedParserFingerprint)
) {
    override fun execute(context: BytecodePatch.Context) {
        val match = FeedParserFingerprint.result ?: return
        val method = match.mutableMethod

        // Locate return-object index
        val returnIndex = method.implementation?.instructions?.indexOfLast {
            it.opcode == Opcode.RETURN_OBJECT
        } ?: return

        // Intercept return register v0 and pass through extension filter
        method.addInstruction(
            returnIndex,
            "invoke-static {v0}, Lcom/vk/video/patches/Extension;->filterAdItems(Ljava/util/List;)Ljava/util/List;"
        )
        method.addInstruction(
            returnIndex + 1,
            "move-result-object v0"
        )
    }
}
```

#### Injected Kotlin Extension Helper (`Extension.kt`)

```kotlin
package com.vk.video.patches

object Extension {
    @JvmStatic
    fun filterAdItems(items: List<Any>): List<Any> {
        return items.filterNot { item ->
            val isAd = item.javaClass.getDeclaredField("isSponsored").run {
                isAccessible = true
                getBoolean(item)
            }
            isAd
        }
    }
}
```

#### Smali Bytecode Comparison

**Original Smali:**
```smali
.method public parseFeed(Lorg/json/JSONObject;)Ljava/util/List;
    .registers 3

    invoke-virtual {p1, "items"}, Lorg/json/JSONObject;->getJSONArray(Ljava/lang/String;)Lorg/json/JSONArray;
    move-result-object v0
    invoke-static {v0}, Lcom/vk/video/model/Parser;->parseItems(Lorg/json/JSONArray;)Ljava/util/List;
    move-result-object v0
    return-object v0
.end method
```

**Patched Smali (Extension Injected):**
```smali
.method public parseFeed(Lorg/json/JSONObject;)Ljava/util/List;
    .registers 3

    invoke-virtual {p1, "items"}, Lorg/json/JSONObject;->getJSONArray(Ljava/lang/String;)Lorg/json/JSONArray;
    move-result-object v0
    invoke-static {v0}, Lcom/vk/video/model/Parser;->parseItems(Lorg/json/JSONArray;)Ljava/util/List;
    move-result-object v0
    invoke-static {v0}, Lcom/vk/video/patches/Extension;->filterAdItems(Ljava/util/List;)Ljava/util/List;
    move-result-object v0
    return-object v0
.end method
```

---

### Pattern 3: Resource ID Lookup and View / Litho Visibility Suppression

When UI components (such as ad banners or Litho/RecyclerView containers) cannot be cleanly removed at the data layer, their layout visibility is forced to `View.GONE` (`8`) immediately after binding, or layout resource dimensions are collapsed.

#### ReVanced v22 Kotlin DSL Implementation

```kotlin
object AdViewBinderFingerprint : MethodFingerprint(
    returnType = "V",
    parameters = listOf("Landroid/view/View;"),
    opcodes = listOf(
        Opcode.INVOKE_VIRTUAL,
        Opcode.RETURN_VOID
    ),
    strings = listOf("ad_banner_holder")
)

@Patch
@Name("suppress-ad-view-visibility")
@Description("Forces Ad View containers to View.GONE (0x8).")
class SuppressAdViewVisibilityPatch : BytecodePatch(
    fingerprints = setOf(AdViewBinderFingerprint)
) {
    override fun execute(context: BytecodePatch.Context) {
        val match = AdViewBinderFingerprint.result ?: return
        val method = match.mutableMethod

        // Inject setVisibility(View.GONE) -> 8
        // p1 = View parameter
        val instructions = listOf(
            "const/4 v0, 0x8",
            "invoke-virtual {p1, v0}, Landroid/view/View;->setVisibility(I)"
        )

        method.addInstructions(0, instructions)
    }
}
```

#### Resource ID Patch Implementation

```kotlin
@Patch
@Name("collapse-ad-layout-dimensions")
@Description("Sets ad container height and width to 0dp in resources.")
class CollapseAdLayoutPatch : ResourcePatch() {
    override fun execute(context: ResourcePatch.Context) {
        val resId = context.resourceTable.getResourceId("id/ad_banner_container", "com.vk.vkvideo")

        context.xmlFiles.filter { it.path.endsWith(".xml") }.forEach { file ->
            val nodes = file.document.getElementsByTagName("View")
            for (i in 0 until nodes.length) {
                val node = nodes.item(i)
                val idAttr = node.attributes?.getNamedItem("android:id")
                if (idAttr?.nodeValue?.contains("ad_banner_container") == true) {
                    node.attributes.getNamedItem("android:layout_height")?.nodeValue = "0dp"
                    node.attributes.getNamedItem("android:layout_width")?.nodeValue = "0dp"
                    node.attributes.getNamedItem("android:visibility")?.nodeValue = "gone"
                }
            }
        }
    }
}
```

#### Smali Bytecode Comparison

**Original Smali:**
```smali
.method public bindAdView(Landroid/view/View;)V
    .registers 2

    invoke-virtual {p1}, Landroid/view/View;->requestLayout()V
    return-void
.end method
```

**Patched Smali (Visibility Forced GONE):**
```smali
.method public bindAdView(Landroid/view/View;)V
    .registers 3

    const/4 v0, 0x8
    invoke-virtual {p1, v0}, Landroid/view/View;->setVisibility(I)V
    invoke-virtual {p1}, Landroid/view/View;->requestLayout()V
    return-void
.end method
```

---

## 3. ReVanced v22 JVM Unit-Testing Pattern

ReVanced v22 enables headless JVM unit testing for fingerprints and bytecode instruction mutations without requiring a physical Android device or emulator. Synthetic Smali code is compiled into an in-memory DEX file using `smali` tooling, passed to `BytecodeContext`, and verified with JUnit 5.

### Test Fixture Example (`VideoAdShortCircuitPatchTest.kt`)

```kotlin
package com.vk.video.patches

import app.revanced.patcher.patch.BytecodePatch
import app.revanced.patcher.util.smali.SmaliCompiler
import com.android.tools.smali.dexlib2.Opcode
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class VideoAdShortCircuitPatchTest {

    private val syntheticSmali = """
        .class public Lcom/vk/video/ad/AdRequestLoader;
        .super Ljava/lang/Object;

        .method public loadVastAd(Lcom/vk/video/ad/AdRequest;)V
            .registers 3
            .line 10
            const-string v0, "request_vast_ad"
            iget-object v0, p0, Lcom/vk/video/ad/AdRequestLoader;->adClient:Lcom/vk/video/ad/AdClient;
            invoke-virtual {v0, p1}, Lcom/vk/video/ad/AdClient;->fetchAd(Lcom/vk/video/ad/AdRequest;)V
            return-void
        .end method
    """.trimIndent()

    @Test
    fun `verify fingerprint matching and early return mutation`() {
        // Compile synthetic smali into memory DEX
        val dexFile = SmaliCompiler.compile(syntheticSmali)
        val context = BytecodePatch.Context(setOf(dexFile))

        // Execute patch against synthetic context
        val patch = VideoAdShortCircuitPatch()
        patch.execute(context)

        // Verify fingerprint matched
        assertTrue(AdLoaderFingerprint.result != null, "AdLoaderFingerprint must match synthetic smali")

        // Assert first instruction is Opcode.RETURN_VOID
        val mutatedMethod = AdLoaderFingerprint.result!!.mutableMethod
        val firstInstruction = mutatedMethod.implementation!!.instructions.first()

        assertEquals(Opcode.RETURN_VOID, firstInstruction.opcode, "First opcode must be RETURN_VOID")
    }
}
```

---

## 4. Required Gradle Scaffolding Files

To build ReVanced v22 patches with Gradle, three standard configuration files are required.

### 1. `settings.gradle.kts`

```kotlin
pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        maven { url = uri("https://repo.clojars.org") }
    }
}

rootProject.name = "vk-video-revanced-patches"
include(":patches")
```

### 2. `gradle/libs.versions.toml`

```toml
[versions]
kotlin = "1.9.22"
revanced-patcher = "22.0.1"
junit = "5.10.1"

[libraries]
revanced-patcher = { group = "app.revanced", name = "revanced-patcher", version.ref = "revanced-patcher" }
junit-jupiter-api = { group = "org.junit.jupiter", name = "junit-jupiter-api", version.ref = "junit" }
junit-jupiter-engine = { group = "org.junit.jupiter", name = "junit-jupiter-engine", version.ref = "junit" }

[plugins]
kotlin-jvm = { id = "org.jetbrains.kotlin.jvm", version.ref = "kotlin" }
```

### 3. `patches/build.gradle.kts`

```kotlin
plugins {
    alias(libs.plugins.kotlin.jvm)
}

group = "com.vk.video"
version = "0.0.1"

dependencies {
    implementation(libs.revanced.patcher)

    testImplementation(libs.junit.jupiter.api)
    testRuntimeOnly(libs.junit.jupiter.engine)
}

tasks.test {
    useJUnitPlatform()
}
```
