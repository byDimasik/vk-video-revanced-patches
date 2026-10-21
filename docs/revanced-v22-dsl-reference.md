# ReVanced Patcher v22.0.1 Kotlin DSL Reference

Reference guide for `app.revanced:patcher:22.0.1` Kotlin DSL components used in this project.

## Artifact Information

- **Group**: `app.revanced`
- **Artifact**: `patcher` (JVM module `patcher-jvm`)
- **Version**: `22.0.1`

---

## 1. Bytecode Patch (`bytecodePatch`)

### Import

```kotlin
import app.revanced.patcher.patch.bytecodePatch
```

### Signature

```kotlin
fun bytecodePatch(
    name: String,
    description: String,
    use: Boolean = true,
    block: BytecodePatchBuilder.() -> Unit
): Patch
```

### Usage Example

```kotlin
val myPatch = bytecodePatch(
    name = "vk-video-ad-block",
    description = "Removes ad views from VK Video"
) {
    compatibleWith("com.vk.vkvideo")

    apply {
        val method = myFingerprint.getMethod(this)
        method.replaceInstruction(0, "return-void")
    }
}
```

---

## 2. Resource Patch (`resourcePatch`)

### Import

```kotlin
import app.revanced.patcher.patch.resourcePatch
```

### Signature

```kotlin
fun resourcePatch(
    name: String,
    description: String,
    use: Boolean = true,
    block: ResourcePatchBuilder.() -> Unit
): Patch
```

### Usage Example

```kotlin
val myResourcePatch = resourcePatch(
    name = "vk-video-theme",
    description = "Modifies VK Video resource XMLs"
) {
    compatibleWith("com.vk.vkvideo")

    apply {
        document("res/layout/ad_view.xml").use { doc ->
            // XML document manipulation
        }
    }
}
```

---

## 3. Fingerprint (`fingerprint`)

### Import

```kotlin
import app.revanced.patcher.fingerprint
```

### Signature

```kotlin
fun fingerprint(
    fuzzyPatternScanThreshold: Int = 0,
    block: FingerprintBuilder.() -> Unit
): Fingerprint
```

### Builder API

- `accessFlags(vararg flags: AccessFlags)`
- `returns(returnType: String)`
- `parameters(vararg params: String)`
- `opcodes(vararg opcodes: Opcode)` / `opcodes(opcodeNames: String)`
- `strings(vararg strings: String)`
- `custom { method, classDef -> Boolean }`

### Usage Example

```kotlin
val myFingerprint = fingerprint {
    accessFlags(AccessFlags.PUBLIC)
    returns("V")
    parameters("Ljava/lang/String;")
    opcodes(Opcode.CONST_STRING, Opcode.RETURN_VOID)
    strings("ad_banner_id")
}
```

---

## 4. Instruction Mutators

### Import

```kotlin
import app.revanced.patcher.extensions.addInstruction
import app.revanced.patcher.extensions.addInstructions
import app.revanced.patcher.extensions.replaceInstruction
import app.revanced.patcher.extensions.replaceInstructions
import app.revanced.patcher.extensions.removeInstruction
import app.revanced.patcher.extensions.removeInstructions
import app.revanced.patcher.extensions.getInstructions
```

### Target Type

`app.revanced.com.android.tools.smali.dexlib2.mutable.MutableMethod`

### Key Extension Methods

- `MutableMethod.addInstruction(index: Int, instructionSmali: String)`
- `MutableMethod.addInstructions(index: Int, instructionSmali: String)`
- `MutableMethod.replaceInstruction(index: Int, instructionSmali: String)`
- `MutableMethod.replaceInstructions(index: Int, instructionSmali: String)`
- `MutableMethod.removeInstruction(index: Int)`
- `MutableMethod.removeInstructions(index: Int, count: Int)`
- `MutableMethod.getInstructions(): List<BuilderInstruction>`

---

## 5. Smali Test Helper (`PatchTestUtil`)

Use `PatchTestUtil.createBytecodeContext(smaliCode)` in JUnit 5 unit tests to compile synthetic Smali code into a test `BytecodePatchContext`.

```kotlin
val context = PatchTestUtil.createBytecodeContext(smaliString)
val match = myFingerprint.matchOrNull(context)
myPatch.execute(context)
```
