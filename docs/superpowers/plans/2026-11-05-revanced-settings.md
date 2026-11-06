Status: plan revised through 8 review rounds; round-8 verdict resolved all Critical/Important findings; minors fixed below.

# ReVanced Settings Menu with Ads Toggles Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a runtime "ReVanced" settings entry whose dialog has an Ads section, and make all three bytecode ad blockers conditional on persisted toggles that default to ON.

**Architecture:** Pure bytecode. One settings bytecode patch injects four helper classes into the app dex and appends a menu entry to the profile settings list. Each ad patch stops short-circuiting unconditionally and branches on a static helper call. Helper classes persist booleans in a dedicated `revanced_settings` SharedPreferences file and build the ReVanced dialog programmatically with the Ads section inline. Every ad patch declares `dependsOn(revancedSettingsPatch)`. Top shelf stays always-on and is documented in the dialog as static text.

**Tech Stack:** Kotlin, ReVanced Patcher v22.0.1 Kotlin DSL, smali/dexlib2, JUnit 5, Gradle via Makefile.

**Spec:** https://github.com/byDimasik/vk-video-revanced-patches/issues/40 `feat: in-app ReVanced settings menu with Ads toggles`.

## Global Constraints

- Target APK is pinned by `version.mk`: version 1.170, versionCode 54881, SHA-256 `06d333f9d98382d65b38e08aceb1e29fb6112d4f41b28f6f8e88070ca37c6daf`.
- Target patcher is `app.revanced:patcher:22.0.1` with `bytecodePatch`, `resourcePatch`, and `fingerprint`.
- Every fingerprint is anchored on stable strings, field names, or non-obfuscated class/method references. No obfuscated class names as the sole anchor.
- Injected references to obfuscated classes and resource IDs are generated from fingerprint matches, never hard-coded.
- Each ad patch depends on `revancedSettingsPatch`, tested through the normal patcher dependency path.
- Default for all three toggles is ON. Fresh patched build matches today's always-blocked behavior.
- No extension APK, no separate source module, no preference XML. Menu is built programmatically.
- Out of scope: per-toggle sub-options, non-ad settings, preference-fragment screens, top-shelf runtime toggle.
- One PR. Commands run through `make test`, `make test-e2e`, `make check`.

## Review Focus

- Toggle OFF must fall through to the untouched original method body, not return a stale value. Pinned by fall-through assertions in every ad patch test.
- Signature is fixed above. Instructions use absolute v-registers; parameters conventionally occupy the window [registerCount - paramWidth, registerCount). The verified-safe growth: (1) grow the frame to oldCount+1 via the rebuild below; (2) allocate scratch at oldCount - paramWidth, the first slot of the OLD parameter window, which becomes free once the parameters are shifted +1 into the new window [oldCount+1-paramWidth, oldCount+1); (3) remap every original-instruction operand register >= oldCount - paramWidth by +1, processing operand values in descending register order so an already-shifted value is not shifted twice; (4) rebuild the implementation via ImmutableMethodImplementation(oldCount + 1, immutable instructions, tryBlocks, debugItems) and set it back with method.setImplementation. The last new index (oldCount) is the new last parameter, so it can never alias the scratch. When paramWidth == 0, scratch = oldCount and no remap is needed. The test also pins that remap at runtime with a .registers fixture whose OFF path reads its last parameter AFTER the branch. The test asserts: scratch == pre-patch registerCount - paramWidth, post rc == pre+1, parameter-window operands shift by +1, and non-parameter operands, instruction order, labels, and OFF-path behavior are unchanged.
- Missing preference must read as ON. Pinned by `InjectedClassesTest` asserting the helper's default literal and Boolean default path.
- Every switch is initialized from its persisted `is*AdsEnabled` value before its change listener is registered. Pinned structurally by `InjectedClassesTest`: for each row, the smali asserts getBoolean-call precedes setChecked which precedes setOnCheckedChangeListener referencing ToggleListener(key); true runtime behavior (init-from-prefs values, listener write-back) is verified in the device-test gate (device-test skill, pipeline step 8) and is a required PR checklist item.
- Entry insertion must land before the recycler call, using the list register read from that call rather than assuming `p1`. Pinned by a fixture that deliberately puts the list in a register other than `p1`, plus `ReVancedSettingsPatchTest` asserting the insertion index and the register read from the call.
- Injected menu references must come from the matched menu method (enum class, item wrapper, item class, icon literal), not hard-coded obfuscated names or resource IDs. The icon is anchored on the second register operand of the item constructor `invoke-direct` (the operand after the receiver, `p1` in both fixtures), never on a register number. Pinned by fixtures for both documented symbol variants (`im`/`nm`/`holder/i$a`/`z17/e$a` and `cm`/`hm`/`holder/j$a`/`j57/e$a`) with deliberately different icon literals.
- Applying each of the three ad patches alone must apply `revancedSettingsPatch` through the normal patcher dependency path. Pinned by `applyPatches`-based dependency-resolution assertions in all three ad patch tests.
- Applying the settings patch twice must not duplicate the injected classes. Pinned by a second-apply assertion in `ReVancedSettingsPatchTest`.
- Top shelf has no toggle; the ReVanced dialog must say so. Pinned by `InjectedClassesTest` asserting the static note string.

### Settings hierarchy ruling

**PM RULING (2026-11-05):** use a single ReVanced dialog with an inline Ads section. Do not build a ReVanced screen that navigates to a separate Ads sub-screen and do not build navigation or a second fragment. The profile entry opens the ReVanced dialog directly; the Ads section is a header plus toggle rows inside that same dialog.

---

### Task 0: Branch from freshly fetched main

**Files:** none.

- [ ] **Step 1: Fetch and branch**

Fetch the newest main and start the work branch from it, never from a stale local ref. Use `upstream` when configured, otherwise `origin`:

```bash
git fetch --prune origin
git switch -c feat/issue40-revanced-settings FETCH_HEAD
```

When another worktree already owns the branch, run the same steps through the worktree workflow instead of re-creating it.

- [ ] **Step 2: Confirm the base**

Run `git log --oneline -1` and confirm the branch starts at the freshly fetched main commit before any file changes.

---

### Task 1: Conditional-return helper and injected class bundle

**Files:**
- Modify: `Makefile`
- Create: `patches/src/main/kotlin/app/revanced/patches/vkvideo/settings/ConditionalReturn.kt`
- Create: `patches/src/main/kotlin/app/revanced/patches/vkvideo/settings/InjectedClasses.kt`
- Test: `patches/src/test/kotlin/app/revanced/patches/vkvideo/settings/ConditionalReturnTest.kt`
- Test: `patches/src/test/kotlin/app/revanced/patches/vkvideo/settings/InjectedClassesTest.kt`
- Test deps: list any newly added test-only dependencies here (existing smali-dexlib2 only; the behavioral harness runs on the JVM and adds no DEX runtime dependency).

**Interfaces:**
- Consumes: `app.revanced.com.android.tools.smali.dexlib2.mutable.MutableMethod`, `com.android.tools.smali.smali.SmaliTestUtils.compileSmali`.
- Produces:
  - `internal fun MutableMethod.prependConditionalReturn(enabledMethod: String, fallbackSmali: String)`
  - `internal data class SettingsMenuTypes(val menuItemType: String, val menuItemWrapper: String, val menuItemClass: String, val iconResourceId: Int)`
  - `internal fun injectSettingsClasses(context: BytecodePatchContext, types: SettingsMenuTypes)` in `InjectedClasses.kt`
  - `internal const val ADS_SETTINGS_CLASS = "Lapp/revanced/extension/vkvideo/AdsSettings;"`
  - Preference keys `revanced_ads_video`, `revanced_ads_feed`, `revanced_ads_catalog`.
  - Static helper methods `getBoolean(Ljava/lang/String;)Z`, `setEnabled(Ljava/lang/String;Z)V`, `isVideoAdsEnabled()Z`, `isFeedAdsEnabled()Z`, `isCatalogAdsEnabled()Z`, `addMenuEntry(Ljava/util/List;)V`, `buildContentView(Landroid/content/Context;)Landroid/view/View;`. `S` is the generated prefs file name, `T` the resolved `SettingsMenuTypes`.
  - Injected listener classes `StartReVancedSettings`, `ReVancedSettingsDialog`, `ToggleListener`.

- [ ] **Step 1: Add a test filter to the Makefile**

Replace the `test` target so it forwards an optional filter:

```make
test:
	./gradlew test $(if $(TEST_FILTER),--tests "$(TEST_FILTER)")
```

- [ ] **Step 2: Write the failing `ConditionalReturnTest`**

Create `ConditionalReturnTest.kt` against a parameterized fixture that has both parameters and a live local, so the test catches parameter/local remapping. Compile a method such as:

```smali
.method public static calc(II)I
    .registers 5
    .param p0, "a"
    .param p1, "b"
    .local v2, "last":I

    const/4 v2, 0x2
    add-int v0, p0, p1
    add-int v0, v0, v2
    return v0
.end method
```

Before patching capture `implementation!!.registerCount`, `parameterTypes`, and the complete original instruction list. Call `prependConditionalReturn` with a stub enabled method and `"const/4 v0, 0x0\nreturn v0"` (the fixture returns `I`; object-returning fixtures use `return-object`). Assert:

- index 0 is `INVOKE_STATIC`, index 1 `MOVE_RESULT`, index 2 `IF_EQZ`;
- the `MOVE_RESULT` register equals the captured pre-patch `registerCount - paramWidth` (for this fixture, 5 - 2 = 3), proving the scratch is the first slot freed by shifting the parameter window +1, not the new last parameter;
- the rebuilt implementation (via ImmutableMethodImplementation(oldCount+1, ...)) has registerCount == captured + 1 and is set back on the method;
- `parameterTypes` is unchanged and the tail still maps `p0`/`p1` and the named local `v2` after the +1 remap (p0 `v3`→`v4`, p1 `v4`→`v5`), including the OFF-path `add-int` that reads `v2` as its last local;
- a `.registers` fixture with params at high indices whose OFF path reads its last parameter AFTER the branch still observes the correct value;
- parameter operands in the original body are shifted +1 (remapped descending so no operand shifts twice); all other operands, the instruction sequence, labels, and OFF-path behaviour are unchanged from the captured original;
- the `IF_EQZ` branch target is the first original instruction, so the OFF path lands on the original body (unchanged apart from the +1 parameter-window operand shift);
- the fallback instructions precede that branch target and the original body follows it unchanged.

- [ ] **Step 3: Run `ConditionalReturnTest` to verify it fails**

Run: `make test TEST_FILTER="app.revanced.patches.vkvideo.settings.ConditionalReturnTest"`
Expected: FAIL, unresolved reference `prependConditionalReturn`.

- [ ] **Step 4: Implement `prependConditionalReturn` in `ConditionalReturn.kt`**

Signature is fixed above. Instructions use absolute v-registers; parameters conventionally occupy the window [registerCount - paramWidth, registerCount). The verified-safe growth: grow the frame to oldCount+1 via rebuild; allocate scratch = oldCount - paramWidth (the first slot of the OLD parameter window, freed once parameters shift +1); remap parameter operands (add +1 to every original-instruction operand register >= oldCount - paramWidth, descending so nothing shifts twice); then rebuild the implementation via ImmutableMethodImplementation(oldCount + 1, immutable instructions, tryBlocks, debugItems) and set it back with method.setImplementation. The method's parameter window shifts to [oldCount+1-paramWidth, oldCount+1), where the last new index (oldCount) is the new last parameter and cannot alias the scratch. When paramWidth == 0, scratch = oldCount and no remap is needed. The test also pins that remap at runtime with a .registers fixture whose OFF path reads its last parameter AFTER the branch. The test asserts: scratch == pre-patch registerCount - paramWidth, post rc == pre+1, and non-parameter operands are unchanged, so the only change to the original body is the +1 shift of parameter-window operands; everything else is untouched. Prepend at index 0:

```smali
invoke-static {}, <enabledMethod>
move-result v<register>
if-eqz v<register>, :revanced_continue
<fallbackSmali>
:revanced_continue
```

The original body after the label is unchanged apart from the +1 parameter-window operand shift. `fallbackSmali` is `"const/4 v0, 0x0\nreturn-object v0"` or `"return-void"`.

- [ ] **Step 5: Run `ConditionalReturnTest` to verify it passes**

Run: `make test TEST_FILTER="app.revanced.patches.vkvideo.settings.ConditionalReturnTest"`
Expected: PASS.

- [ ] **Step 6: Write the failing `InjectedClassesTest`**

Create `InjectedClassesTest.kt`. Build an empty bytecode context with one dummy class. Assert the four helper descriptors are absent before injection, then call injection with deliberately obfuscated fixture types:

```kotlin
val menuTypes = SettingsMenuTypes(
    menuItemType = "Lobf/e;",
    menuItemWrapper = "Lobf/w\$a;",
    menuItemClass = "Lobf/h\$a;",
    iconResourceId = 0x7f08230a
)
injectSettingsClasses(context, menuTypes)
```

Assert:

```kotlin
val expected = setOf(
    "Lapp/revanced/extension/vkvideo/AdsSettings;",
    "Lapp/revanced/extension/vkvideo/StartReVancedSettings;",
    "Lapp/revanced/extension/vkvideo/ReVancedSettingsDialog;",
    "Lapp/revanced/extension/vkvideo/ToggleListener;"
)
val types = context.classDefs.map { it.type }.toSet()
assertTrue(types.containsAll(expected))

val settings = context.classDefs.first { it.type == ADS_SETTINGS_CLASS }
val getBoolean = settings.methods.first { it.name == "getBoolean" }
val defaultInstr = getBoolean.implementation!!.instructions
    .first { it.opcode == Opcode.CONST_4 }
assertEquals(1, (defaultInstr as NarrowLiteralInstruction).narrowLiteral)

val addMenuEntry = settings.methods.first { it.name == "addMenuEntry" }
val references = addMenuEntry.implementation!!.instructions
    .filterIsInstance<ReferenceInstruction>().map { it.reference }
assertTrue(references.any { it is TypeReference && it.type == menuTypes.menuItemType })
assertTrue(references.any { it is TypeReference && it.type == menuTypes.menuItemWrapper })
assertTrue(references.any { it is TypeReference && it.type == menuTypes.menuItemClass })
assertTrue(addMenuEntry.implementation!!.instructions.any {
    it.opcode == Opcode.CONST && (it as NarrowLiteralInstruction).narrowLiteral == menuTypes.iconResourceId
})

val content = settings.methods.first { it.name == "buildContentView" }
assertTrue(content.implementation!!.instructions.any {
    it.opcode == Opcode.CONST_STRING &&
        ((it as ReferenceInstruction).reference as StringReference).string ==
        "Top shelf promotions are always blocked."
})
```

In `buildContentView`, for each key assert that an `invoke-static` to its matching `is*AdsEnabled` wrapper is followed by `setChecked` before a `setOnCheckedChangeListener` referencing a `ToggleListener` built with that key.

Cover both persisted states. Parameterize the row assertion over `true` and `false` and assert the switch's checked value is the result of the persisted read, never a constant: the `is*AdsEnabled` call must occur before `setChecked`, and no `CONST`/`CONST_4` may feed `setChecked` directly. For the write path, assert `setEnabled` loads its boolean parameter and passes it to `putBoolean` before `apply`, so both stored values round-trip; assert the `getBoolean(key, true)` default literal separately so a missing pref still reads as ON.

**Structural sub-step:** the injected smali is asserted structurally: for each of the three keys, the buildContentView template contains, in order, an invoke-static to its is*AdsEnabled wrapper, then a setChecked call on the fresh switch, then setOnCheckedChangeListener with a ToggleListener constructed with that key; FakeSharedPreferences-style default handling (missing key reads as ON) is asserted on the getBoolean default path in the smali constants. True runtime behavior (stored-true/stored-false values reaching setChecked, listener write-back through setEnabled) is covered by the device-test gate: toggle each of the three switches, kill + relaunch the app, assert state persisted and ads respond accordingly — listed as explicit device-test checklist items in Task 6 and required in the PR body checklist.

Also assert a second `injectSettingsClasses(context, menuTypes)` leaves exactly one class with each descriptor.

- [ ] **Step 7: Run `InjectedClassesTest` to verify it fails**

Run: `make test TEST_FILTER="app.revanced.patches.vkvideo.settings.InjectedClassesTest"`
Expected: FAIL, unresolved reference `injectSettingsClasses` / `ADS_SETTINGS_CLASS`.

- [ ] **Step 8: Implement injected smali classes in `InjectedClasses.kt`**

Hold one `private const val` smali template per class. Templates for `AdsSettings` interpolate `types`; `injectSettingsClasses` skips a descriptor already in `context.classDefs`, otherwise calls `SmaliTestUtils.compileSmali(source)` and `context.classDefs.add(classDef)`.

Required behavior of the smali:

- `AdsSettings.getBoolean(Ljava/lang/String;)Z` opens `com.vk.core.preference.Preference.f("revanced_settings")`, calls `getBoolean(key, true)`, returns it.
- `setEnabled` opens the same prefs, calls `edit().putBoolean(key, value).apply()`.
- The three `is*AdsEnabled` wrappers pass their key to `getBoolean`.
- `addMenuEntry(Ljava/util/List;)V` reads `types.menuItemType`'s `ITEM_WITH_ICON` field, builds a `types.menuItemClass` with `types.iconResourceId`, title `"ReVanced"`, a new `StartReVancedSettings`, enabled `true`, wraps it in `types.menuItemWrapper` with the enum ordinal, and calls `List.add`. Use the item constructor overload `(ILjava/lang/String;Lkotlin/jvm/functions/Function1;Z)V` so no title resource ID is needed.
- `StartReVancedSettings` implements `Lkotlin/jvm/functions/Function1;`, casts the argument to `Context`, constructs and shows `ReVancedSettingsDialog`, and returns `null` (the holder discards the return value, so no obfuscated result singleton is referenced).
- `ReVancedSettingsDialog` extends `android/app/Dialog`, sets title `"ReVanced"`, sets content to `AdsSettings.buildContentView(context)`.
- `buildContentView` builds a vertical `LinearLayout` with padding, an `Ads` header, one row per key, and the static top-shelf note. Each row creates a `Switch`, sets its label, calls `setChecked(AdsSettings.is<X>AdsEnabled())`, then registers `ToggleListener(key)` with `setOnCheckedChangeListener`.
- `ToggleListener` implements `Landroid/widget/CompoundButton$OnCheckedChangeListener;` and calls `AdsSettings.setEnabled(key, isChecked)`.

- [ ] **Step 6: Run test to verify it passes**

Run: `make test TEST_FILTER="app.revanced.patches.vkvideo.settings.InjectedClassesTest"`
Expected: PASS.

- [ ] **Step 7: Commit**

```bash
git add Makefile patches/src/main/kotlin/app/revanced/patches/vkvideo/settings patches/src/test/kotlin/app/revanced/patches/vkvideo/settings
git commit -m "feat: inject ReVanced settings helper classes"
```

---

### Task 2: Settings entry patch

**Files:**
- Create: `patches/src/main/kotlin/app/revanced/patches/vkvideo/settings/Fingerprints.kt`
- Create: `patches/src/main/kotlin/app/revanced/patches/vkvideo/settings/ReVancedSettingsPatch.kt`
- Modify: `patches/src/test/kotlin/app/revanced/patches/vkvideo/util/PatchTestUtil.kt`
- Test: `patches/src/test/kotlin/app/revanced/patches/vkvideo/settings/ReVancedSettingsPatchTest.kt`

**Interfaces:**
- Consumes: `injectSettingsClasses`, `SettingsMenuTypes`, `ADS_SETTINGS_CLASS`, `PatchTestUtil`.
- Produces: `profileMenuItemsFingerprint`, `val revancedSettingsPatch`, `PatchTestUtil.profileMenuFixture`, `PatchTestUtil.profileMenuFixture170`.

**Anchor evidence:** the profile menu builder lives at `com/vk/video/screens/profile/ProfileMenuFragment` (stable class name; the method itself is obfuscated, `im(II)V` in 1.168 and `cm(II)V` in 1.170). It pushes `ProfileMenuItemType;->PLAYER_SETTINGS` and finishes by calling `BaseRecyclerFragment` with `(Ljava/util/List;Z)V` (`nm` in 1.168, `hm` in 1.170). Anchor on those two references and the method shape, not on the method name or recycler method name. `VideoUserSettingsFragment` renders with Compose through a synthetic `androidx.compose.foundation.lazy.layout.h2` switch, so the RecyclerView menu is the stable non-Compose insertion point.

The item types and icon are obfuscated and drift between versions. The documented variants are:

| Symbol            | 1.168 (verified smali)                                                          | 1.170 (verified smali)                              |
| ----------------- | ------------------------------------------------------------------------------- | --------------------------------------------------- |
| Menu method       | `im(II)V`                                                                       | `cm(II)V`                                           |
| Recycler call     | `BaseRecyclerFragment;->nm(Ljava/util/List;Z)V`                                 | `BaseRecyclerFragment;->hm(Ljava/util/List;Z)V`     |
| Item class        | `Lcom/vk/video/screens/profile/adapter/holder/i$a;`                             | `Lcom/vk/video/screens/profile/adapter/holder/j$a;` |
| Item wrapper      | `Lz17/e$a;`                                                                     | `Lj57/e$a;`                                         |
| Item constructor  | `(IILkotlin/jvm/functions/Function1;Z)V` (also has `(ILjava/lang/String;...)V`) | same shape                                          |
| Real icon literal | `0x7f08230a`                                                                    | `0x7f082393`                                        |

Resolve all of these from the matched method instead of hard-coding:

- enum class from the `PLAYER_SETTINGS` field reference's defining class;
- item class from the `new-instance`/`invoke-direct <init>` immediately before `PLAYER_SETTINGS` whose constructor is `(I, I|String, Lkotlin/jvm/functions/Function1;, Z)V`;
- item wrapper from the `new-instance` after `PLAYER_SETTINGS` whose constructor is `(I, Ljava/lang/Object;)V`;
- icon from the `const` that loads the item constructor's second parameter register (the one after the receiver and first int parameter).

No production code may mention `j$a`, `j57/e$a`, `a87/u`, a list register, or an icon literal as constants; the obfuscated names above are only fixture/evidence values.

- [ ] **Step 1: Write the failing test**

Add `profileMenuFixture` and `profileMenuFixture170` to `PatchTestUtil`, one per documented variant. Each fixture declares an obfuscated enum `Lfixture/menu/Type;` exposing `PLAYER_SETTINGS` and `ITEM_WITH_ICON`, a fixture item class with the string-title constructor, and a fixture wrapper with the `(ILjava/lang/Object;)V` constructor. The fixture-local item and wrapper descriptors follow the documented obfuscated shape so the test proves resolution by descriptor rather than by a hard-coded name.

- `profileMenuFixture` uses the 1.168 shape: method `im(II)V`, recycler `BaseRecyclerFragment->nm(Ljava/util/List;Z)V`, fixture item `Lcom/vk/video/screens/profile/adapter/holder/i$a;`, fixture wrapper `Lz17/e$a;`. Use `.registers 8` and place the list in `v7`, not `p1`, so the test proves the register is read from the call.
- `profileMenuFixture170` uses the 1.170 shape: method `cm(II)V`, recycler `BaseRecyclerFragment->hm(Ljava/util/List;Z)V`, fixture item `Lcom/vk/video/screens/profile/adapter/holder/j$a;`, fixture wrapper `Lj57/e$a;`, with the list in a different non-`p1` register.

Both fixtures build one `PLAYER_SETTINGS` row, each with its own distinct deliberately-fake icon ID (1.168 fixture `0x7f082301`, 1.170 fixture `0x7f082302`) different from the real literals and from each other, and end with the matching recycler call passing the list register and the boolean parameter.

Create `ReVancedSettingsPatchTest.kt`, parameterized over both fixtures. Build a context from the fixture, assert the fingerprint matches, and execute `revancedSettingsPatch` via `PatchTestUtil.execute`. Assert:

- an `invoke-static` named `addMenuEntry` is immediately before the recycler call;
- its single register operand equals the recycler call's list register (read from the instruction, not assumed to be `p1`);
- `ADS_SETTINGS_CLASS` is present;
- the injected `addMenuEntry` references the fixture enum's `ITEM_WITH_ICON`, that fixture's item class and wrapper, and that fixture's own icon literal (`0x7f082301` or `0x7f082302`).

Also execute `revancedSettingsPatch` a second time on the same context and assert exactly one class exists for each of the four injected descriptors.

- [ ] **Step 2: Run test to verify it fails**

Run: `make test TEST_FILTER="app.revanced.patches.vkvideo.settings.ReVancedSettingsPatchTest"`
Expected: FAIL, fingerprint or patch unresolved.

- [ ] **Step 3: Implement the fingerprint**

```kotlin
val profileMenuItemsFingerprint = fingerprint {
    returns("V")
    parameters("I", "I")
    custom { method, classDef ->
        classDef.type == "Lcom/vk/video/screens/profile/ProfileMenuFragment;" &&
            method.implementation?.instructions?.any { instruction ->
                (instruction as? ReferenceInstruction)?.reference
                    .let { it is FieldReference && it.name == "PLAYER_SETTINGS" }
            } == true &&
            method.implementation?.instructions?.any { instruction ->
                (instruction as? ReferenceInstruction)?.reference.let {
                    it is MethodReference &&
                        it.definingClass == "Lme/grishka/appkit/fragments/BaseRecyclerFragment;" &&
                        it.parameterTypes == listOf("Ljava/util/List;", "Z") &&
                        it.returnType == "V"
                }
            } == true
    }
}
```

- [ ] **Step 4: Implement `revancedSettingsPatch`**

`bytecodePatch(name = "ReVanced settings", description = "...")`, `compatibleWith("com.vk.vkvideo")`. The patch has no dependencies; ad patches depend on it. In `apply`:

1. Resolve `profileMenuItemsFingerprint.method` and its instructions.
2. Find the recycler call index. The call is an instance invoke with signature (Ljava/util/List;Z)V — argument registers are {receiver, list, boolean}. For FiveRegisterInstruction read registerD (second argument = list); for invoke-xxx/range read startingRegister + 1. Validate: the register feeds addMenuEntry(Ljava/util/List;)V, so assert the resolved register's type by checking the fingerprint's method string is an invoke with List as first non-receiver arg.
3. Resolve `SettingsMenuTypes` from the same instruction list as described in the anchor evidence; fail with a clear `error` if any piece is missing.
4. Call `injectSettingsClasses(this, types)`.
5. Insert `invoke-static {v<listRegister>}, Lapp/revanced/extension/vkvideo/AdsSettings;->addMenuEntry(Ljava/util/List;)V` at the recycler call index.

- [ ] **Step 5: Run test to verify it passes**

Run: `make test TEST_FILTER="app.revanced.patches.vkvideo.settings.ReVancedSettingsPatchTest"`
Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add patches/src/main/kotlin/app/revanced/patches/vkvideo/settings patches/src/test/kotlin/app/revanced/patches/vkvideo/settings patches/src/test/kotlin/app/revanced/patches/vkvideo/util
git commit -m "feat: add ReVanced entry to profile settings menu"
```

---

### Task 3: Video ads conditional

**Files:**
- Modify: `patches/src/main/kotlin/app/revanced/patches/vkvideo/ad/video/DisableVideoAdsPatch.kt`
- Modify: `patches/src/test/kotlin/app/revanced/patches/vkvideo/ad/video/DisableVideoAdsPatchTest.kt`
- Modify: `patches/src/test/kotlin/app/revanced/patches/vkvideo/util/PatchTestUtil.kt`

**Interfaces:**
- Consumes: `prependConditionalReturn`, `revancedSettingsPatch`, `PatchTestUtil.profileMenuFixture`, `PatchTestUtil.profileMenuFixture170`.
- Produces: `PatchTestUtil.applyPatches(context, vararg patches)`.

- [ ] **Step 1: Rewrite the failing test**

Add `PatchTestUtil.applyPatches(context, vararg patches)` that calls `PatchingKt.apply(patches.toSet(), context, dummyResourceContext) {}` so dependency resolution runs through the same path as the patcher.

Build ONE integrated context per menu variant: that menu fixture (`profileMenuFixture` or `profileMenuFixture170`) PLUS the four video ad fixtures. Apply only `disableVideoAdsPatch` through `PatchTestUtil.applyPatches` and, in that same context, assert the dependency ran (`revancedSettingsPatch` in `dependencies`, `ADS_SETTINGS_CLASS` injected) and verify every conditional and fall-through sequence on the patched methods.

For each of the four methods assert this sequence instead of the old unconditional sequence:

- object parsers: index 0 `INVOKE_STATIC` to `isVideoAdsEnabled`, index 1 `MOVE_RESULT`, index 2 `IF_EQZ`, index 3 `CONST_4` with literal 0, index 4 `RETURN_OBJECT`, index 5 the original first opcode.
- loader and controller: same, but index 3 `RETURN_VOID`, index 4 the original first opcode.

Also assert the `IF_EQZ` branch target points at the original first instruction. Run the test before touching the patch.

- [ ] **Step 2: Run test to verify it fails**

Run: `make test TEST_FILTER="app.revanced.patches.vkvideo.ad.video.DisableVideoAdsPatchTest"`
Expected: FAIL, index 0 is `CONST_4` instead of `INVOKE_STATIC`.

- [ ] **Step 3: Implement**

Add `dependsOn(revancedSettingsPatch)` to the patch builder. Replace each `replaceInstruction(0, ...)` + `addInstruction(1, ...)` pair with one call:

```kotlin
instreamAdJsonParserMethod.prependConditionalReturn(
    "Lapp/revanced/extension/vkvideo/AdsSettings;->isVideoAdsEnabled()Z",
    "const/4 v0, 0x0\nreturn-object v0"
)
```

Return-object fallback for the parser and `videoFileInstreamAd`; `"return-void"` for loader and controller.

- [ ] **Step 4: Run test to verify it passes**

Run: `make test TEST_FILTER="app.revanced.patches.vkvideo.ad.video.DisableVideoAdsPatchTest"`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add patches/src/main/kotlin/app/revanced/patches/vkvideo/ad/video patches/src/test/kotlin/app/revanced/patches/vkvideo/ad/video patches/src/test/kotlin/app/revanced/patches/vkvideo/util
git commit -m "feat: make video ad blocking runtime-toggleable"
```

---

### Task 4: Feed and catalog conditionals

**Files:**
- Modify: `patches/src/main/kotlin/app/revanced/patches/vkvideo/ad/feed/HideFeedAdsPatch.kt`
- Modify: `patches/src/main/kotlin/app/revanced/patches/vkvideo/ad/catalog/HideCatalogBannersPatch.kt`
- Modify: `patches/src/test/kotlin/app/revanced/patches/vkvideo/ad/feed/HideFeedAdsPatchTest.kt`
- Modify: `patches/src/test/kotlin/app/revanced/patches/vkvideo/ad/catalog/HideCatalogBannersPatchTest.kt`

**Interfaces:**
- Consumes: `prependConditionalReturn`, `isFeedAdsEnabled`, `isCatalogAdsEnabled`, `revancedSettingsPatch`, `PatchTestUtil.applyPatches`, `PatchTestUtil.profileMenuFixture`, `PatchTestUtil.profileMenuFixture170`.
- Produces: no new public API.

- [ ] **Step 1: Rewrite both failing tests**

Apply the same assertion shape as Task 3 to each return-object method. Feed uses `isFeedAdsEnabled`; catalog uses `isCatalogAdsEnabled`. Every method asserts branch target equals the original first opcode.

For each patch, build ONE integrated context per menu variant: the menu fixture PLUS that patch's complete ad-fixture set (feed: 2 fixtures, catalog: 2 fixtures). Apply only the patch under test via `PatchTestUtil.applyPatches` and, in that same context, assert the dependency ran and verify every conditional and fall-through sequence.

- [ ] **Step 2: Run tests to verify they fail**

Run: `make test TEST_FILTER="app.revanced.patches.vkvideo.ad.feed.*"` then `make test TEST_FILTER="app.revanced.patches.vkvideo.ad.catalog.*"`
Expected: FAIL, index 0 `CONST_4`.

- [ ] **Step 3: Implement**

Add `dependsOn(revancedSettingsPatch)` to both patch builders. Call `prependConditionalReturn` with the matching enabled method and `"const/4 v0, 0x0\nreturn-object v0"`. Delete the `replaceInstruction`/`addInstruction` pairs. Leave all fingerprint files unchanged.

- [ ] **Step 4: Run tests to verify they pass**

Run: `make test TEST_FILTER="app.revanced.patches.vkvideo.ad.feed.*"` then `make test TEST_FILTER="app.revanced.patches.vkvideo.ad.catalog.*"`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add patches/src/main/kotlin/app/revanced/patches/vkvideo/ad patches/src/test/kotlin/app/revanced/patches/vkvideo/ad
git commit -m "feat: make feed and catalog ad blocking runtime-toggleable"
```

---

### Task 5: E2E coverage and real-APK settings entry test

**Files:**
- Modify: `Makefile`
- Modify: `patches/src/test/kotlin/app/revanced/patches/vkvideo/e2e/RealApkSmokeTest.kt`
- Modify: `patches/src/test/kotlin/app/revanced/patches/vkvideo/e2e/RealApkCatalogBannerTest.kt`
- Create: `patches/src/test/kotlin/app/revanced/patches/vkvideo/e2e/RealApkSettingsEntryTest.kt`

**Interfaces:**
- Consumes: `revancedSettingsPatch`, `profileMenuItemsFingerprint`, `ADS_SETTINGS_CLASS`, `PatchTestUtil.applyPatches`, real-APK context builder already used by e2e tests.
- Produces: no new public API.

- [ ] **Step 1: Update the Makefile e2e target**

Make `test-e2e` run the whole E2E package, not one class:

```make
test-e2e:
	./gradlew -Dvkvideo.e2e=true :patches:cleanTest :patches:test --tests "app.revanced.patches.vkvideo.e2e.*"
```

- [ ] **Step 2: Update `RealApkSmokeTest`**

Apply the three ad patches together with `applyPatches` instead of executing them one by one, so the normal dependency path injects the settings patch. Assert each of `disableVideoAdsPatch`, `hideFeedAdsPatch`, and `hideCatalogBannersPatch` declares `revancedSettingsPatch` in `dependencies`, proving each would resolve it alone. After applying, assert `ADS_SETTINGS_CLASS` was injected, and after each ad patch method assert `IF_EQZ` exists before the first `RETURN_OBJECT`/`RETURN_VOID` and that a branch target exists past the fallback.

- [ ] **Step 3: Update `RealApkCatalogBannerTest`**

Apply only `hideCatalogBannersPatch` with `applyPatches`, assert its `dependencies` contains `revancedSettingsPatch` (so `ADS_SETTINGS_CLASS` is injected without applying settings by hand), then assert the same conditional shape while retaining the defining-class assertions.

- [ ] **Step 4: Add `RealApkSettingsEntryTest`**

Copy the version-agnostic APK locator and strict/assume handling from `RealApkSmokeTest`. Resolve `profileMenuItemsFingerprint`, execute `revancedSettingsPatch` through `applyPatches`, assert `addMenuEntry` is immediately before the `BaseRecyclerFragment` call, its register equals the call's list register, and both `AdsSettings` and `ProfileMenuFragment` types are present.

- [ ] **Step 5: Run e2e**

Run: `make test-e2e`
Expected: PASS for `RealApkSmokeTest`, `RealApkCatalogBannerTest`, and `RealApkSettingsEntryTest`. If no cached APK exists, run `make fetch-apk` first.

- [ ] **Step 6: Run all checks**

Run: `make check`
Expected: PASS.

- [ ] **Step 7: Commit**

```bash
git add Makefile patches/src/test/kotlin/app/revanced/patches/vkvideo/e2e
git commit -m "test: cover conditional ad blocking and settings entry on real APK"
```

---

### Task 6: README and manual verification note

**Files:**
- Modify: `README.md`

- [ ] **Step 1: Update the feature list**

Add one bullet after the ad-blocking bullets: `Runtime in-app "ReVanced" settings menu with per-blocker Ads toggles, all defaulting to on`.

- [ ] **Step 2: Add manual device verification**

Add a short "Manual verification" subsection: install the patched build, open Profile tab settings, open ReVanced, toggle each blocker off, confirm ads return in video, feed, and catalog; toggle back on and confirm blocking resumes. Note top shelf stays blocked and says so in the dialog.
- Kill and relaunch the app: assert each toggle persisted its state (SharedPreferences round-trip).
- Toggle all OFF then all ON: ads blocked in both fresh and relaunched sessions per state.

- [ ] **Step 3: Commit**

```bash
git add README.md
git commit -m "docs: describe ReVanced settings menu"
```

---

### Task 7: Open PR and reach CI green

**Files:** none.

- [ ] **Step 1: Rebase on current trunk**

Run `git fetch --prune origin` immediately before the rebase check.

Confirm the branch is based on the newest `main`; rebase onto `origin/main` if the trunk moved.

- [ ] **Step 2: Run lint and tests on the final tree**

Run `make check`. Expected: PASS.

- [ ] **Step 3: Open a normal PR**

Invoke the pr-ready skill to prepare and open the PR (body at .tmp-issue40/pr-body.md). Reference the spec issue by URL only in the body; no Closes #40 line.

The body explains what and why, how it was tested (unit plus real-APK e2e), what a reviewer should look at, links the spec issue by URL, and records the top-shelf ruling from the section below. End with the attribution line `🤖 Generated with [opencode](https://opencode.ai)`.
- PR body includes the device-test checklist results (toggle state persistence, ads blocked/returned per state) as a completion gate — PR does not go ready until the device-test skill has run the full checklist.

- [ ] **Step 4: Watch CI to green**

Follow the CI phase of the pr-lifecycle skill: watch `gh pr checks <n> --watch`; fix any failure on this branch and push again until green.

- [ ] **Step 5: Comment on the spec issue and drive review/merge**

Post one comment on issue #40 with the PR link and the one-line status "PR open, CI green, awaiting review loop", then follow the pr-lifecycle skill for review and merge.

---

## Top-shelf ruling

Keep `suppressTopShelfPatch` always-on and unchanged. It is a resource patch that rewrites two layout XMLs at patch time; a runtime toggle would require swapping decoded layouts or adding a second layout set, which the spec allows deferring. The dialog carries the static note `Top shelf promotions are always blocked.` Record this ruling in the PR body.


## Round 15 — implementation gate (2026-11-06)

All plan tasks implemented and committed on `feat/issue40-revanced-settings`
(90bcd8b..d694716, 7 commits, 22 files, +2399/−159).

**Redesign during e2e (supersedes the round-14 shift scheme):**
`prependConditionalReturn` now uses **append-scratch** — grow `registerCount`
by 1, scratch register = old count. DEX parameter registers are always the
last `paramWidth` slots, so they keep their exact registers after growth:
no instruction remapping, no try-block/debug-item rewrite, and the 4-bit
nibble limit of 35c operands can never overflow (the old scheme produced
`Invalid register: v16` on the 1.168 loader with `.registers 15`).
Verified by dedicated checks: scratch does not alias the last parameter and
the injected block reads no parameter registers.

**Verification:** full unit suite green; e2e on the real 1.168 APK
(RealApkSmokeTest, RealApkCatalogBannerTest, RealApkSettingsEntryTest) green;
`make check` pass. Reviewer verdict: **Ready** (one Minor naming note).
