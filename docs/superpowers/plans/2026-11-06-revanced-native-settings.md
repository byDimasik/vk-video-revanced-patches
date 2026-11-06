Status: round-1 review findings applied (2026-11-06); ready for round-2 review.

# ReVanced Settings: Native Look & Feel (Themed Screen, Ads Submenu, Native Toggles, Menu Above Sign Out) — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace the injected dialog-style ReVanced settings screen (#41/#43) with a native full screen that matches the app's own settings screens: themed for dark/light automatically, an "Ads" submenu with the app's own switch-row component, a 4th toggle for the top shelf converted from a build-time resource patch to a runtime conditional, and the menu entry placed directly above "Sign out".

**Architecture:** Pure bytecode, zero new resources (the proven hybrid device-install recipe from #42 carries stock resources, so anything referencing a newly added resource would crash on the device-test build). The settings entry keeps landing in the profile menu via the existing `addMenuEntry` list-append, but the call moves into the app's Sign-out row builder so the entry renders directly above "Sign out". Clicking it opens an injected `FragmentImpl` subclass (the base class of every video-app screen) that replicates the app's own toolbar-fragment chrome (`VkTopBar` + back → `finish()`) and builds its content from the app's design-system switch row `VkSwitchItem` — the exact widget family behind the "Download via Wi-Fi only" row. The build-time `SuppressTopShelfPatch` resource patch is deleted; its two layout-collapse effects are reproduced at runtime by a new bytecode patch that collapses the two top-shelf views when `isTopShelfAdsEnabled()` is false. All four toggles read/write the same `revanced_settings` SharedPreferences through the existing `AdsSettings` helper.

**Tech Stack:** Kotlin, ReVanced Patcher v22.0.1 Kotlin DSL (`bytecodePatch`, `fingerprint`), smali/dexlib2, JUnit 5, Gradle via Makefile.

**Spec:** https://github.com/byDimasik/vk-video-revanced-patches/issues/44 `ReVanced settings: native look & feel (themed screen, Ads submenu, native toggles, menu placement above Sign out)`.

## Verified APK evidence (1.170, versionCode 54881 — all claims below were read from the 1.170 smali/resources, not assumed)

Everything in this section was verified by baksmali-ing the stock `vkvideo-1.170.apk` and reading `res/`. **Decompile provenance (round-1 review note):** the shared `vk170/full` tree is an apktool decode of the *patched hybrid base* (`a-patched-base.apk`, see `apktool.yml`), so several stock-APK obfuscated names cited here (`yj6/r`, `j57/e`(+`$a`), `ty1/i0`, `VkSwitchItem$a`) do not exist under those names in `vk170/full` (`MaterialPreferenceFragment` IS present there, `smali_classes11`). The cited stock classes were re-verified from fresh baksmali of the stock APK's dexes kept in `vk170/smali170-stock3` (`yj6/r`, `j57/e`, `j57/e$a`, `ProfileMenuFragment`), `vk170/smali170-stock11` (`VkSwitchItem`, `VkSwitchItem$a`), `vk170/smali170-stock{2,9,10}` (supporting references), and `vk170/smali170/b6` (`ty1/i0`, from a previous stock-classes6 baksmali). `vk170/full` itself was NOT modified. Obfuscated names are **1.170 fixture values only** — production code resolves them from fingerprints, per the repo's standing rule.

### The app's own settings menu (spec pic 2)

- `com/vk/video/screens/profile/ProfileMenuFragment` extends `com/vkontakte/android/fragments/CardRecyclerFragment` (AppKit RecyclerView screen). The menu list is built in `cm(II)V` (obfuscated; the existing `profileMenuItemsFingerprint` already anchors it via `PLAYER_SETTINGS` + the `BaseRecyclerFragment;->hm(Ljava/util/List;Z)V` call).
- Adapter `Lyj6/b;` extends `j57/e` (extends `me/grishka/appkit/views/UsableRecyclerView$c`). `j57/e.V(I)I` returns `j57/e$a.a` (the `ProfileMenuItemType` ordinal) as the RecyclerView view type; `j57/e.E(I)I` returns constant 1; `j57/e.h0` binds `e$a.b` via `j57/d.n3`. **Display order = list insertion order; nothing sorts by ordinal.**
- Row item data class: `com/vk/video/screens/profile/adapter/holder/j$a` (= `ProfileMenuItemWithIconHolder$ItemWithIcon`, name recovered from the `const-string "onMenuItemClick(Lcom/vk/video/screens/profile/adapter/holder/ProfileMenuItemWithIconHolder$ItemWithIcon;)V"` method-reference metadata in `yj6/b.j0`). Fields: `a:Ljava/lang/String;` (title), `b:I` (icon res), `c:Z`, `d:Lkotlin/jvm/functions/Function1;` (click). Constructors: `(I titleRes, I iconRes, Function1, Z)` — resolves the title resource via `u32/g1;->e(I)Ljava/lang/String;` and delegates to `(I, Ljava/lang/String;, Function1, Z)`.
- Row view holder: `com/vk/video/screens/profile/adapter/holder/j` builds a **`com/vk/core/view/components/cell/VkCell`** programmatically: `VkCell$Left$Main` with icon `p72/j`, `VkCell$Left$Main$Size.Medium`, icon tint resolved from theme attrs `?attr/vk_ui_icon_primary` (0x7f040d62) / `?attr/vk_ui_icon_accent` (0x7f040d58). No layout XML, no literal colors — the row look is entirely theme-attr driven.
- Click contract: `ProfileMenuFragment$b.a(j$a)` reads `j$a.d` and invokes it with `Fragment.requireContext()` — **the click `Function1` receives a `Context`** (this is what the #41 `StartReVancedSettings` already relies on, device-verified in #43).
- Menu build order in `cm(II)V` + builder `Lyj6/r;` (logged-in branch): PASSPORT → AD_FREE_SUBSCRIPTION (`yj6/r.i`) → hint (`c`) → child-profile (`d`) → PLAYER_SETTINGS ("player_settings_title" 0x7f141f4f, icon 0x7f082393) → General (`yj6/r.b`, "sett_general" 0x7f14249a) → AD_FREE_SUBSCRIPTION (`i`) → VK Video for TV (`j`, 0x7f142fee) → Help (`yj6/r.f`, "sett_help" 0x7f14249e, icon 0x7f081ba0) → build-info row (`e`, internal builds only) → debug-log rows (`h`, internal) → `MENU_SEPARATOR` → SWITCH_PROFILE → **Sign out (`yj6/r.g`)**.
- `yj6/r.g(Ljava/util/ArrayList;)V` is the method that appends the Sign out row: `j$a("logout" 0x7f141580, icon 0x7f0819a7, click qx/b, false)` wrapped as `ProfileMenuItemType;->LOGOUT`, guarded by `com/vk/bridges/f;->a()...a()Z` and field `yj6/r.e:Z`; the logged-out branch appends a Login row (`login` 0x7f14157f) instead. **`g` is referenced only from `ProfileMenuFragment.cm(II)V` (two call sites, both branches), and `g` is the only method of `yj6/r` that references `ProfileMenuItemType;->LOGOUT`** — a unique, stable anchor.
- Current #41/#43 placement: `revancedSettingsPatch` inserts `addMenuEntry` immediately before the `hm(Ljava/util/List;Z)V` call, i.e. **after `g` has already appended Sign out** → the entry renders at the very bottom, below "Sign out". Moving the call to the top of `g` places it directly above "Sign out" in both branches with a single patch site.

### The native switch row (spec pic 3 style)

- `com/vk/core/view/components/switch/VkSwitchItem` (public, extends `LinearLayout`): inflates **`ds_internal_switch_item`** (0x7f0e03dc) = `com/vk/core/view/components/switch/VkSwitch` (extends `androidx/appcompat/widget/VkSwitchImpl` extends **`SwitchCompat`**) + `com/vk/core/view/components/control/selection/VkSelectionControlsMiddle` (title/subtitle text block). Reads its styleable (`n42/a.F`) via `obtainStyledAttributes`; the switch is set non-clickable and the whole row gets `setOnClickListener`; the row background is **`?attr/selectableItemBackground`** (0x7f0408de) resolved through `vx1/c;->h(ILandroid/content/Context;)Landroid/graphics/drawable/Drawable;` — ripple/highlight behavior is intrinsic.
- Public API (all non-obfuscated names on `VkSwitchItem`): `setTitle(Lpx1/a;)V`, `setSubtitle(Lpx1/a;)V`, `setChecked(Z)V`, `isChecked()Z`, `setListener(Lcom/vk/core/view/components/switch/VkSwitchItem$a;)V`, `setAlignSwitchToEnd(Z)V`, `toggle()V`.
- `VkSwitchItem$a` is the listener interface with a single abstract method `b(Z)V` in 1.170 (obfuscated name — resolve at patch time as "the single abstract `(Z)V` method of `VkSwitchItem$a`").
- `px1/a` is the app's CharSequence resource wrapper. For plain strings: **`px1/a$h` is `public final` with a public constructor `(Ljava/lang/CharSequence;)V`** (verified in classes6.dex) — `new px1/a$h("Block video ads")` is enough; no resource IDs needed.
- The app itself wires this exact row into preference screens via `com/vk/prefui/views/VkSwitchItemPreference` (extends `androidx/preference/SwitchPreferenceCompat`): its layout `vk_switch_item_preference_layout` (0x7f0e0f4c) is a bare `VkSwitchItem` with `layout_marginVertical="6.0dip"`, and its bind method (`r(l)`) copies `Preference.mTitle`/summary into `VkSwitchItem.setTitle/setSubtitle`, calls `setChecked(mChecked)` and `setAlignSwitchToEnd(true)`. This proves the intended screen usage of the widget; this plan constructs `VkSwitchItem` views directly (same widget the preference binding drives) instead of going through `androidx.preference`.
- The "Download settings" screen of pic 3 is `com/vk/libvideo/offline/settings/impl/ui/VideoOfflineSettingsFragment` — it hosts a **`ComposeView`** (`onCreateView` creates `ComposeView` + `setContent`), and the "Download with Wi-Fi only" row ("video_download_settings_wifi_only" 0x7f142de3) is rendered by Compose `Cell` composables (`com/vk/core/compose/component/cell/content/Cell$Middle…`). The Compose side cannot be reused from injected View code; `VkSwitchItem` is the same design system's View-side equivalent (it is the widget inside `ds_internal_switch_item` and `vk_switch_item_preference_layout`). This substitution is deliberate and recorded here.

### Screen hosting, chrome and theming

- `com/vk/prefui/fragments/PreferenceFragmentCompat` extends **`com/vk/core/fragments/FragmentImpl`** — the base class of every video-app screen (`ProfileMenuFragment`, `VideoUserSettingsFragment`, `VideoOfflineSettingsFragment` all extend it directly or transitively). `FragmentImpl` is `public` with a `public ()V` constructor and a `public finish()V`.
- The app's own toolbar-carrying preference screen pattern is `com/vk/prefui/fragments/MaterialPreferenceToolbarFragment` (abstract, extends `MaterialPreferenceFragment`):
  - `onCreateView`: calls super, inflates the **existing** layout `appkit_toolbar_fragment_hidable_with_coordinator` (0x7f0e0062: `CoordinatorLayout` → `AppBarLayout`(style VKAppBar) + `include appkit_toolbar` + `FrameLayout @id/appkit_content` (0x7f0b01a0) + `AppBarShadowView @id/shadow`), adds the content view into `appkit_content`, then hides the legacy AppKit toolbar and shadow via `ty1/i0;->m(Landroid/view/View;)V` — verified body: `setVisibility(8)`.
  - `onViewCreated`: builds `com/vk/core/view/components/topbar/VkTopBar` with `(Landroid/content/Context;Landroid/util/AttributeSet;I I)V` called as `(context, null, 6, 0)`; sets `Middle` = `VkTopBar$Middle$Text$Title` ctor `(Ljava/lang/String; Li12/o; Lcom/vk/core/view/components/topbar/VkTopBar$Middle$Text$Title$a$a; Lcom/vk/core/view/components/topbar/VkTopBar$Middle$Text$Title$Size; I)` invoked as `(title, null, null, null, 30)` wrapped in `VkTopBar$Middle$Text` ctor `(Title; Text$c; Text$a$b; Lcom/vk/core/compose/component/semantics/SemanticsConfiguration; I)` invoked as `(title, null, null, null, 14)`; sets `Back` = `VkTopBar$c` ctor `(Lkotlin/jvm/functions/Function0; Lpx1/a; Lp72/e; Lcom/vk/core/compose/component/semantics/a; I)` invoked as `(backClick, px1/a$f("accessibility_back" 0x7f140052), null, null, 28)`; then `findViewById(@id/app_bar_layout` 0x7f0b0191 `).addView(topBar)`; finally `g57/a;->f(Lcom/vk/core/fragments/FragmentImpl; Lcom/vk/core/view/components/topbar/VkTopBar;)Z` and, when true, `setBefore(null)`.
  - **Dead-code caution (verified, corrected in round 1):** `MaterialPreferenceToolbarFragment`'s back handler is a reflective `aq6/e` lookup of `"onToolbarNavigationClick()V"` — a method that does **not exist** on the class (its only methods are `<init>`, `onCreateView`, `onViewCreated`). Its **only** subclass in 1.170 is `com/vk/notifications/settings/SettingsNotificationsFragment` (smali_classes4; adds only `<init>`, `Ol`, `onActivityResult`, `onCreate`) — **base and only subclass both lack `onToolbarNavigationClick`**, so every real subclass overrides back handling elsewhere; extending the base as-is would ship a broken back button. The plan therefore **transcribes** the two methods above into the injected fragment with (a) our own title string instead of the hardcoded `@string/notification_settings` (0x7f141c6e) and (b) a real `Function0` that calls `FragmentImpl.finish()` instead of the reflective lookup. `g57/a.f` is kept: it is the app's own "should this fragment draw its own top bar" check for `o54/q` nav hosts (reads the host activity type).
- Theme following (no hardcoded colors anywhere):
  - `VkVideoFragmentWrapperActivity.onCreate` calls `ThemableActivity;->setTheme(c32/l0.E()/*.VKTheme*/.id)` — the app resolves the **current** VK theme (dark or light) at runtime and applies it to the activity; every fragment inside inherits that themed attribute set.
  - `VkSwitchItem`, `VkCell`, `VkTopBar` all resolve `?attr/vk_ui_*` / `?attr/selectableItemBackground` from the ambient theme — dark/light follows automatically. Verified attr chain: `VkMilkDarkStyle` and `VkMilkLightStyle` both set `preferenceTheme=@style/PreferenceThemeMilkshake` whose `textColorPrimary/Secondary` are `?vk_ui_text_primary` / `?vk_ui_text_secondary`; the DS components used here are the same ones the app's own themed screens use.
- Navigation/open idiom (how the app opens "General"): the click lambda (`oi5/d.invoke`, R8-synthetic) does `new Lo54/r;(Lcom/vk/video/screens/settings/main/ui/VideoUserSettingsFragment;.class, null, null)` then `h(Landroid/content/Context;)V`. `Lo54/r;` is `public`, its `(Ljava/lang/Class;Ljava/lang/Class;Landroid/os/Bundle;)V` constructor and `h` are `public final`; `h` routes through the app's nav manager (`i2.t(ctx)` → `o54/d` → `j(o54/a)`). This is the app-wide standard fragment-open builder (7+ call sites across the app).

### Top shelf (4th toggle)

- Today: `SuppressTopShelfPatch` (resource patch) zeroes `res/layout/catalog_video_topshelf.xml` (0x7f0e029c) and `res/layout/catalog_video_music_topshelf.xml` (0x7f0e027e) at build time — always-on, no runtime check.
- Runtime renderers (verified — the only two inflate sites for these layouts in all 13 dexes):
  - `Lni0/k;-><init>(Landroidx/constraintlayout/widget/ConstraintLayout;Landroid/view/LayoutInflater;ZLcom/vk/catalog2/common/dto/ui/UIBlockTopshelf$TopshelfItem;Z)V` inflates 0x7f0e029c, stores the root in field `b:Landroid/view/View;`, and `getView()` returns `b`. Sole caller: `com/vk/catalog2/common/ui/holders/video/topshelf/l` (the catalog `UIBlockTopshelf` block holder). Registers: 15, the `iput-object v10, v9, ...->b` sits at the second-to-last instruction before `return-void`.
  - `Lcom/vk/catalog2/common/ui/holders/video/musicvideo/b;` (a `Fragment`): `onCreateView` builds its own root `ConstraintLayout`, inflates 0x7f0e027e into it (32 registers, inflate result in `move-result-object` right after the `const` of the layout id), and returns the container.
- Runtime collapse = the exact runtime equivalent of the build-time patch on the same view: `layoutParams.height = 0` + `setVisibility(GONE)`. Applied when `isTopShelfAdsEnabled()` is false. Register scratch comes from the **append-scratch growth** proven in the round-15 revision of the #40 plan (grow `registerCount`, scratch = the new top registers; parameters keep their registers; no operand remapping) — mid-method insertion is safe because the new instructions only touch registers ≥ the old count.
- **35c register-encoding limit (round-1 review, #42 failure class):** after the append-scratch growth the collapse block's receivers land on registers ≥ the old count — fixture A (`ni0/k.<init>`, 15 total registers) grows 15→18 (scratch `v15`–`v17`), fixture B (`musicvideo/b.onCreateView`, 32 total registers) grows 32→35 (scratch `v32`–`v34`). The plain `35c` invoke opcodes (`invoke-virtual`, `invoke-direct`, `invoke-static`, …) encode each register as a 4-bit nibble and are only legal for `v0`–`v15`; an `invoke-virtual {v16}, ...setLayoutParams` written as `35c` is an **illegal encoding that dexlib2 builds silently** and only surfaces as a device `VerifyError`. Rule for the collapse block (and every appended instruction): **any invoke with a register operand ≥ 16 must use the corresponding `/range` opcode** (`invoke-virtual/range`, `invoke-static/range`, …), or the scratch/registers must be ordered so the invoke's receiver stays ≤ 15.

### Persistence

- All reads/writes stay inside the existing `AdsSettings` helper (`com/vk/core/preference/Preference;->f("revanced_settings")` — verified body: name mapped through `bb/a;->w`, then internal `l()`). The screen never touches `androidx.preference` persistence, so there is exactly one store and one key set: `revanced_ads_video`, `revanced_ads_feed`, `revanced_ads_catalog`, new `revanced_ads_topshelf`, all default `true`.

## Global Constraints

- Target APK pinned by `version.mk`: 1.170, versionCode 54881. Target patcher 22.0.1.
- Every fingerprint anchors on stable class names (`com/vk/video/screens/profile/ProfileMenuFragment`, `com/vk/core/view/components/switch/VkSwitchItem$a`, `com/vk/prefui/fragments/MaterialPreferenceToolbarFragment`, `com/vk/video/screens/settings/main/ui/VideoUserSettingsFragment`, `Lni0/k;`, `com/vk/catalog2/common/ui/holders/video/musicvideo/b`) plus stable references (`ProfileMenuItemType;->LOGOUT`, layout resource ids from `res/values/public.xml`, framework signatures). Obfuscated names/registers are resolved from matches, never hard-coded in production code.
- **Zero new resources.** The hybrid device-install recipe (issue #42) keeps stock resources; any patch referencing a newly added resource would crash on the device-test build. All UI is built programmatically from existing app classes; the only resource references are `const` ids of resources that already exist in 1.170.
- Defaults: all four toggles ON. Fresh build matches today's always-blocked behavior.
- The `prependConditionalReturn` machinery from #40–#43 is not touched by this plan (no new conditional-return sites; the top-shelf conditional appends at a tail position, it does not prepend).
- One PR. Commands through `make test`, `make test-e2e`, `make check`.

## Review Focus

- The 4th toggle must be wired through the SAME store and helper as the other three (`AdsSettings.getBoolean/setEnabled`), not a parallel store. Pinned by `InjectedClassesTest` asserting the listener's `setEnabled` path and `isTopShelfAdsEnabled` delegating to `getBoolean("revanced_ads_topshelf")` with default literal 1.
- Menu placement must be "directly above Sign out" in BOTH `cm(II)` branches. Anchoring the injection inside `yj6/r.g` (Sign out's own builder, called only from `cm`) achieves both branches with one site; the test pins that `g` is the matched method (LOGOUT reference + `(Ljava/util/ArrayList;)V` signature) and that `addMenuEntry` lands at its index 0.
- Back navigation must not rely on `MaterialPreferenceToolbarFragment`'s reflective `onToolbarNavigationClick` (verified absent). Pinned by `InjectedClassesTest` asserting the back `Function0` invokes `FragmentImpl;->finish()V` directly.
- No literal colors, no literal dimensions in injected smali except the 6dip row margin copied from `vk_switch_item_preference_layout` (density-scaled at runtime). Pinned by a test asserting no `const` high literal in the 0xff...... color range inside the injected classes.
- Each planned test's feasibility is stated in its task (JVM structural / real-APK e2e structural / device-only) with the reason.
- Device gate (Task 6) is a merge blocker, exactly as in #41–#43.

## Test strategy summary

- **JVM (dexlib2 structural, existing harness):** all patch behavior on fixture classes — fingerprints, injected smali shape, ordering, register discipline. Feasible because every mutation is bytecode-structural; this caught #42's verifier bug class only partially, hence the device gate below.
- **Real-APK e2e (JVM, `make test-e2e`):** fingerprints + patches against the real 1.170 dexes — proves anchors and resolved references exist in the shipped app. Structural only: it cannot execute ART.
- **Device (mandatory gate):** the only place VerifyError, theme rendering, navigation, and persistence can be observed. Uses the issue #42 build/install recipe (hybrid base, libvkcore bypass, shared keystore).

---

### Task 0: Branch from freshly fetched main

**Files:** none.

- [ ] **Step 1: Fetch and branch**

```bash
git fetch --prune origin
git worktree add /tmp/wt-issue44 -b feat/issue44-native-settings origin/main
```

When the worktree already exists, `git -C /tmp/wt-issue44 fetch origin && git -C /tmp/wt-issue44 reset --hard origin/main` instead of re-creating it.

- [ ] **Step 2: Confirm the base**

`git -C /tmp/wt-issue44 log --oneline -1` starts at the freshly fetched main commit.

---

### Task 1: Top shelf — runtime conditional + 4th toggle plumbing

**Files:**
- Modify: `patches/src/main/kotlin/app/revanced/patches/vkvideo/settings/InjectedClasses.kt`
- Create: `patches/src/main/kotlin/app/revanced/patches/vkvideo/ad/shelf/ConditionalTopShelfPatch.kt`
- Create: `patches/src/main/kotlin/app/revanced/patches/vkvideo/ad/shelf/Fingerprints.kt` (new content; the old file name may not exist — the shelf fingerprints were inline in the patch)
- Delete: `patches/src/main/kotlin/app/revanced/patches/vkvideo/ad/shelf/SuppressTopShelfPatch.kt`
- Delete: `patches/src/test/kotlin/app/revanced/patches/vkvideo/ad/shelf/SuppressTopShelfPatchTest.kt`
- Test: `patches/src/test/kotlin/app/revanced/patches/vkvideo/ad/shelf/ConditionalTopShelfTest.kt`
- Modify: `patches/src/test/kotlin/app/revanced/patches/vkvideo/e2e/RealApkSmokeTest.kt`

**Interfaces:**
- Produces: `internal const val KEY_TOPSHELF_ADS = "revanced_ads_topshelf"`, static helper `isTopShelfAdsEnabled()Z` on `AdsSettings`, `val conditionalTopShelfPatch`, fingerprints `videoTopshelfBlockFingerprint`, `musicTopshelfFragmentFingerprint`.

**Anchor evidence:** see "Top shelf (4th toggle)" above — `Lni0/k;` <init> (stable class; inflate of layout 0x7f0e029c; root stored in the `Landroid/view/View;` field that `getView()Landroid/view/View;` reads) and `com/vk/catalog2/common/ui/holders/video/musicvideo/b;->onCreateView` (stable class; inflate of layout 0x7f0e027e). The layout ids are **resolved, not hardcoded**: each fingerprint's custom match requires an `inflate(ILandroid/view/ViewGroup;Z)Landroid/view/View;` call whose layout register is fed by a `const` literal equal to the id read from the APK's `res/values/public.xml` for `catalog_video_topshelf` / `catalog_video_music_topshelf` (the e2e fixture pins the 1.170 literals 0x7f0e029c / 0x7f0e027e; production code takes whatever the matched const holds).

- [ ] **Step 1: Write the failing `ConditionalTopShelfTest`**

JVM structural test (feasibility: JVM/dexlib2 — the mutation is pure bytecode; runtime collapse is device-gated in Task 6). Fixtures mirror the two verified shapes:

- Fixture A (`ni0/k` shape): class with a `Landroid/view/View;` field `b`, `<init>` that ends `... iput-object vRX, vTHIS, F:...View;` then `return-void`, with the inflate+`move-result-object` feeding that register earlier. Deliberately small register count (e.g. `.registers 15`, params in the window) so the test proves append-scratch growth, not free-register luck.
- Fixture B (`musicvideo/b` shape): `onCreateView(...)` returning a container, with the shelf inflate mid-method (`const <layoutId>` → `inflate(I, ViewGroup, Z)` → `move-result-object vX`) and more code after it.

Apply only `conditionalTopShelfPatch` through `PatchTestUtil.applyPatches` (dependency on `revancedSettingsPatch` must inject `AdsSettings` in the same context). Assert for each fixture, at the insertion point:

1. sequence `INVOKE_STATIC isTopShelfAdsEnabled` → `MOVE_RESULT` → `IF_NEZ` → (collapse block) → original next instruction unchanged;
2. collapse block reads the root view register (fixture A: the field written by the `iput`; fixture B: the inflate result register) and performs `getLayoutParams` → `iput 0 into ...ViewGroup$LayoutParams;->height:I` → `setLayoutParams` → `const/16 0x8` → `setVisibility`;
3. post-patch `registerCount == pre + scratchWidth` and every parameter operand is unchanged (append-scratch discipline, round-15 rule);
4. `isTopShelfAdsEnabled` delegates to `getBoolean("revanced_ads_topshelf")` and `AdsSettings.getBoolean`'s default literal is 1;
5. `conditionalTopShelfPatch.dependencies` contains `revancedSettingsPatch`;
6. `suppressTopShelfPatch` no longer exists as a symbol (the resource patch and its XML rewrite are gone);
7. **35c/range discipline:** every invoke instruction in the collapse block (and in the appended conditional prologue) whose register operand is > 15 uses the `/range` form — no `35c` opcode may carry a register operand ≥ 16 (fixture A's scratch reaches `v16`/`v17`, fixture B's reaches `v32`+). This is the exact encoding class that dexlib2 accepts silently and only fails as a device VerifyError (#42/#43 failure class).

- [ ] **Step 2: Run to verify it fails**

`make test TEST_FILTER="app.revanced.patches.vkvideo.ad.shelf.ConditionalTopShelfTest"` — expected FAIL (unresolved `conditionalTopShelfPatch`).

- [ ] **Step 3: Implement**

- `InjectedClasses.kt`: add `KEY_TOPSHELF_ADS` and the `isTopShelfAdsEnabled()Z` wrapper to the `AdsSettings` smali template (same shape as the other three wrappers).
- Fingerprints:
  - `videoTopshelfBlockFingerprint`: class `Lni0/k;`, method `<init>`, contains an `inflate(I, Landroid/view/ViewGroup;Z)V→View` whose layout const equals the `catalog_video_topshelf` id, plus a `getView()Landroid/view/View;` method on the same class reading an instance `Landroid/view/View;` field — that field is the root.
  - `musicTopshelfFragmentFingerprint`: class `com/vk/catalog2/common/ui/holders/video/musicvideo/b;`, method `onCreateView`, same inflate+layout-const shape with the `catalog_video_music_topshelf` id.
- `conditionalTopShelfPatch` (`bytecodePatch`, `compatibleWith("com.vk.vkvideo")`, `dependsOn(revancedSettingsPatch)`): for each matched method, locate the insertion point (fixture A: immediately after the `iput-object` that stores the root field; fixture B: immediately after the inflate's `move-result-object`), grow the frame by 3 via the append-scratch rebuild (scratch = new top registers; no operand remap), and emit the conditional collapse block. `isTopShelfAdsEnabled()` false ⇒ collapse; true ⇒ branch to the original next instruction. **Register discipline (mandatory):** every emitted invoke must be checked against the 35c `v0`–`v15` limit — any invoke with a register operand ≥ 16 must use the `/range` opcode (e.g. `invoke-virtual/range {vRX}, ...setLayoutParams`), or the scratch must be ordered so the receiver stays ≤ 15. Fixture A (15→18, scratch `v15`–`v17`) and fixture B (32→35, scratch `v32`–`v34`) both cross this limit; dexlib2 builds a wrong `35c` silently and it only fails on device.
- Delete `SuppressTopShelfPatch.kt`, `SuppressTopShelfPatchTest.kt`; update `RealApkSmokeTest`: remove the resource-patch execution and the two XML-content checks, add assertions that both fingerprints matched, both insertion sequences exist, and `AdsSettings` was injected.

- [ ] **Step 4: Run to verify it passes**

`make test TEST_FILTER="app.revanced.patches.vkvideo.ad.shelf.*"` — expected PASS.

- [ ] **Step 5: Commit**

```bash
git add -A patches/src/main/kotlin/app/revanced/patches/vkvideo/ad/shelf patches/src/test/kotlin/app/revanced/patches/vkvideo/ad/shelf patches/src/main/kotlin/app/revanced/patches/vkvideo/settings/InjectedClasses.kt patches/src/test/kotlin/app/revanced/patches/vkvideo/e2e/RealApkSmokeTest.kt
git commit -m "feat: runtime top-shelf toggle (4th Ads switch, replaces build-time layout collapse)"
```

---

### Task 2: Menu entry placement — directly above "Sign out"

**Files:**
- Modify: `patches/src/main/kotlin/app/revanced/patches/vkvideo/settings/Fingerprints.kt`
- Modify: `patches/src/main/kotlin/app/revanced/patches/vkvideo/settings/ReVancedSettingsPatch.kt`
- Modify: `patches/src/test/kotlin/app/revanced/patches/vkvideo/util/PatchTestUtil.kt`
- Modify: `patches/src/test/kotlin/app/revanced/patches/vkvideo/settings/ReVancedSettingsPatchTest.kt`

**Interfaces:**
- Produces: `signOutRowFingerprint`; `revancedSettingsPatch` inserts `addMenuEntry` at index 0 of the Sign-out builder method instead of before the recycler call.

**Anchor evidence:** `yj6/r.g(Ljava/util/ArrayList;)V` appends the Sign out row (LOGOUT field refs at its instructions; verified above); it is called only from `ProfileMenuFragment.cm(II)V`; it is the only `yj6/r` method referencing `LOGOUT`. The builder class itself is obfuscated → resolve it at match time as the defining class of the `(Ljava/util/ArrayList;)V` instance-invokes inside the matched `cm(II)V`, then pick the method that references `ProfileMenuItemType;->LOGOUT`.

- [ ] **Step 1: Write the failing test**

JVM structural (feasibility: JVM/dexlib2 — insertion index + register are structural; final ordering is device-gated). Extend `PatchTestUtil` menu fixtures with a builder class mirroring `yj6/r`: a method `(Ljava/util/ArrayList;)V` that sget-refs a fixture `LOGOUT` enum field and appends a row, called (twice, two branches) from the existing `cm`-shaped fixture method before the recycler call. Assert after patching:

1. `signOutRowFingerprint` matches the LOGOUT-referencing builder method, not any other `(Ljava/util/ArrayList;)V` method;
2. `addMenuEntry` is at index 0 of that method and its register operand is the method's list parameter;
3. the builder's original instructions follow unchanged (append-scratch rules if the frame had to grow — here `addMenuEntry` is static and takes only the existing list register, so no frame growth is expected; pin that no instruction was remapped);
4. the old behavior is gone: no `addMenuEntry` instruction remains immediately before the `hm(Ljava/util/List;Z)V` call.

- [ ] **Step 2: Run to verify it fails**

`make test TEST_FILTER="app.revanced.patches.vkvideo.settings.ReVancedSettingsPatchTest"` — expected FAIL.

- [ ] **Step 3: Implement**

Add `signOutRowFingerprint` (builder-class resolution as in the anchor evidence; require `FieldReference` named `LOGOUT` + `(Ljava/util/ArrayList;)V` + returns V). In `revancedSettingsPatch.apply`, replace the recycler-index insertion with: match `signOutRowFingerprint`, take its list parameter register (last parameter window slot: `registerCount - paramWidth + 1`), insert `invoke-static {v<listReg>}, $ADS_SETTINGS_CLASS->addMenuEntry(Ljava/util/List;)V` at index 0 (use the `/range` form when the register exceeds 15, as the current code already does).

- [ ] **Step 4: Run to verify it passes**

Same filter — expected PASS.

- [ ] **Step 5: Commit**

```bash
git add patches/src/main/kotlin/app/revanced/patches/vkvideo/settings patches/src/test/kotlin/app/revanced/patches/vkvideo
git commit -m "feat: place ReVanced menu entry directly above Sign out"
```

---

### Task 3: Native settings screen (themed full screen, Ads rows via VkSwitchItem)

**Files:**
- Modify: `patches/src/main/kotlin/app/revanced/patches/vkvideo/settings/InjectedClasses.kt`
- Modify: `patches/src/main/kotlin/app/revanced/patches/vkvideo/settings/Fingerprints.kt`
- Modify: `patches/src/main/kotlin/app/revanced/patches/vkvideo/settings/ReVancedSettingsPatch.kt`
- Test: `patches/src/test/kotlin/app/revanced/patches/vkvideo/settings/InjectedClassesTest.kt`

**Interfaces:**
- Injected class set (final): `AdsSettings` (as after Task 1), `StartReVancedSettings` (Function1, opens the screen), **`ReVancedSettingsFragment`** (extends `com/vk/core/fragments/FragmentImpl;`), **`ReVancedToggleListener`** (implements `VkSwitchItem$a`), **`ReVancedBackClick`** (implements `Lkotlin/jvm/functions/Function0;`, calls `finish()`).
- Deleted injected classes: `ReVancedSettingsDialog`, `ToggleListener`. **`AdsSettings.buildContentView` (InjectedClasses.kt:126) is also DELETED** — its only production caller is the removed `ReVancedSettingsDialog`'s smali, so keeping it would ship dead code; the dialog-era `buildContentView` tests are deleted with it.
- New fingerprint: `navOpenerFingerprint` (resolves the `o54/r`-type fragment opener), `switchListenerMethodFingerprint` (resolves the `VkSwitchItem$a` abstract method name), `toolbarChromeFingerprint` (resolves `ty1/i0.m`, `g57/a.f`, `VkTopBar*` constructor refs from `MaterialPreferenceToolbarFragment`'s own code).

**Anchor evidence:** see "Screen hosting, chrome and theming" and "The native switch row" sections — every injected call below is transcribed from `MaterialPreferenceToolbarFragment.onCreateView/onViewCreated` (title string and back handler replaced), `VkSwitchItem`'s own constructor (background/ripple), and `VkSwitchItemPreference.r` (bind order). Existing layout/resources only: `appkit_toolbar_fragment_hidable_with_coordinator` (0x7f0e0062), `@id/appkit_content` (0x7f0b01a0), `@id/app_bar_layout` (0x7f0b0191), `px1/a$f("accessibility_back")` (0x7f140052). Row margins: `6dip` vertical, copied from `vk_switch_item_preference_layout`.

- [ ] **Step 1: Write the failing `InjectedClassesTest`**

JVM structural (feasibility: JVM/dexlib2 — class shape and call ordering; visual/theme/nav correctness is device-gated in Task 6, persistence round-trip likewise). Assert:

1. Class set is exactly the five above; second injection is idempotent; the two dialog-era classes are gone **and `AdsSettings.buildContentView` no longer exists (deleted with its only caller, the removed dialog — dead code must not ship)**.
2. `ReVancedSettingsFragment` extends `Lcom/vk/core/fragments/FragmentImpl;` **and has a `public` no-argument constructor `<init>()V`** — the app opens fragments reflectively: `o54/r.d()` routes through `FragmentEntry;->u8()`, whose body is `Class.newInstance()` (verified in `FragmentEntry.smali`); a missing public `()V` passes every JVM test and fails only on device.
3. `onCreateView` sequence (structural, in order): `inflate(0x7f0e0062-equivalent const, null, true)` → super-view `addView` into the `appkit_content` findViewById result → `invoke-static {vX}, <hide-helper>` (the `ty1/i0;->m(Landroid/view/View;)V` method ref resolved from `toolbarChromeFingerprint`) on **each** of the legacy-toolbar and shadow findViewById results → return the coordinator root. The transcription calls the fingerprint-resolved hide helper (device-verified body: `const/16 0x8` + `setVisibility`) exactly as the app's own code does — it must **not** inline `setVisibility(8)`. The layout/appbar/content ids are interpolated from `toolbarChromeFingerprint` matches, not literals in the template.
4. `onViewCreated` sequence: `VkTopBar(context, null, 6, 0)` → `Title("ReVanced", null, null, null, 30)` wrapped in `Middle$Text(title, null, null, null, 14)` → `setMiddle` → `VkTopBar$c(new ReVancedBackClick, px1/a$f(accessibility_back-id), null, null, 28)` → `setBack` → `app_bar_layout.addView(topBar)` → `g57/a.f(this, topBar)` → `setBefore(null)` when true. The back path must show `ReVancedBackClick` invoking `FragmentImpl;->finish()V` — the reflective `aq6/e` shape from the app's dead-code pattern is forbidden.
5. Content build: a vertical `LinearLayout` inside `appkit_content` with four `VkSwitchItem(Context, null)` rows. For each row, in order: `setTitle(new px1/a$h(<label>))` → `setChecked(AdsSettings.is<X>Enabled())` → `setListener(ReVancedToggleListener(<key>))` → `setAlignSwitchToEnd(true)`; margins set from a density-scaled 6dip. Labels: `Block video ads`, `Block feed ads`, `Block catalog banners`, `Block top shelf promotions`. `px1/a$h`'s constructor must be the `(Ljava/lang/CharSequence;)V` one.
6. No `const` literal in the 0xff000000..0xffffffff color range and no `0x7f1...`-style style references in the injected smali (theme-attr components only; the 6dip margin is computed, not a color).
7. `ReVancedToggleListener.b(Z)V` (method name interpolated from `switchListenerMethodFingerprint`) calls `AdsSettings.setEnabled(key, checked)` — pinned per key including `revanced_ads_topshelf`.
8. `StartReVancedSettings.invoke` casts the argument to `Context` and calls `<resolvedOpener>;-><init>(Ljava/lang/Class;Ljava/lang/Class;Landroid/os/Bundle;)V` with `(ReVancedSettingsFragment.class, null, null)` followed by `h(Landroid/content/Context;)V`, then **returns null (`const/4 v0, 0x0` / `return-object v0`) — the pinned #43 return behavior, device-verified** (the `Function1<Context, Unit>` contract does not require a unit sentinel; do not gate this on the resolved opener's call shape). The opener type comes from `navOpenerFingerprint`, never a literal.

- [ ] **Step 2: Run to verify it fails**

`make test TEST_FILTER="app.revanced.patches.vkvideo.settings.InjectedClassesTest"` — expected FAIL.

- [ ] **Step 3: Implement**

- Fingerprints:
  - `navOpenerFingerprint`: any method containing `const-class Lcom/vk/video/screens/settings/main/ui/VideoUserSettingsFragment;` AND an `invoke-direct <init>(Ljava/lang/Class;Ljava/lang/Class;Landroid/os/Bundle;)V` AND an `invoke-virtual h(Landroid/content/Context;)V` on the same new-instance type (verified unique shape from the General-row click lambda; assert exactly one distinct opener type across matches).
  - `switchListenerMethodFingerprint`: on `Lcom/vk/core/view/components/switch/VkSwitchItem$a;`, the single abstract method with descriptor `(Z)V`.
  - `toolbarChromeFingerprint`: on `Lcom/vk/prefui/fragments/MaterialPreferenceToolbarFragment;` (stable), extract from `onCreateView`/`onViewCreated`: the coordinator layout id const, `appkit_content`/`app_bar_layout` id consts, the `ty1/i0.m` and `g57/a.f` method refs, and the `VkTopBar`/`VkTopBar$Middle$Text$Title`/`VkTopBar$Middle$Text`/`VkTopBar$c` constructor refs with their default-argument constants. All interpolated into the smali templates.
  - Accessibility-back string: resolve `px1/a;->Companion` + the `v.d(Companion, I)px1/a$f` helper ref from the same `onViewCreated` (the `0x7f140052` const stays an interpolated match value).
- Rewrite the injected smali per the asserted sequences. Keep `AdsSettings.getBoolean/setEnabled` unchanged; add nothing to the dialog path. **Delete the `buildContentView` method from the `AdsSettings` smali template** (only the dialog called it) and delete the dialog-era `buildContentView` tests. **`ReVancedSettingsFragment` must be declared `.class public` with a `public` no-arg `<init>()V`** — the nav host instantiates it reflectively via `FragmentEntry;->u8()` (`Class.newInstance()`).
- Keep the `revanced_ads_topshelf` row's default-ON semantics identical to the other rows (missing key reads as ON via `getBoolean`'s default literal).

- [ ] **Step 4: Run to verify it passes**

Same filter — expected PASS.

- [ ] **Step 5: Commit**

```bash
git add patches/src/main/kotlin/app/revanced/patches/vkvideo/settings patches/src/test/kotlin/app/revanced/patches/vkvideo/settings
git commit -m "feat: native themed ReVanced settings screen with VkSwitchItem Ads rows"
```

---

### Task 4: Real-APK e2e coverage and full check

**Files:**
- Modify: `patches/src/test/kotlin/app/revanced/patches/vkvideo/e2e/RealApkSettingsEntryTest.kt`
- Create: `patches/src/test/kotlin/app/revanced/patches/vkvideo/e2e/RealApkTopShelfConditionalTest.kt`
- Modify: `patches/src/test/kotlin/app/revanced/patches/vkvideo/e2e/RealApkSmokeTest.kt` (if not already updated in Task 1)

**Feasibility:** JVM against the real 1.170 dexes (structural only — proves the anchors live in the shipped app; ART behavior is the device gate). If `.cache` has no APK, `make fetch-apk` first; `assumeTrue` skip rules stay as-is.

- [ ] **Step 1: Update `RealApkSettingsEntryTest`**

Replace the "immediately before the recycler call" assertions with: `signOutRowFingerprint` matches, `addMenuEntry` at its index 0, register equals its list parameter, and the entry item references the resolved item/wrapper/icon types (existing assertions, re-anchored).

- [ ] **Step 2: Add `RealApkTopShelfConditionalTest`**

Both shelf fingerprints match the real APK; both patched methods contain the conditional collapse sequence with branch targets past the fallback; `AdsSettings` present; the resolved layout consts equal the `public.xml` ids for `catalog_video_topshelf` / `catalog_video_music_topshelf`.

- [ ] **Step 3: Run e2e and full check**

```bash
make test-e2e
make check
```

Expected: PASS (all e2e classes; lint+unit green).

- [ ] **Step 4: Commit**

```bash
git add patches/src/test/kotlin/app/revanced/patches/vkvideo/e2e
git commit -m "test: real-APK coverage for native settings screen and top-shelf conditional"
```

---

### Task 5: README

**Files:** Modify: `README.md`

- [ ] **Step 1: Update the feature list**

Replace the shelf bullet with: `Runtime-toggleable blocking of video ads, feed ads, catalog banners and top-shelf promotions via a native themed ReVanced settings screen (profile menu, above Sign out)`.

- [ ] **Step 2: Commit**

```bash
git add README.md
git commit -m "docs: describe native ReVanced settings screen"
```

---

### Task 6: PR, CI, and the device gate (merge blocker)

**Files:** none.

- [ ] **Step 1: Rebase on freshly fetched main; `make check` green.**
- [ ] **Step 2: Open the PR** via the pr-ready skill (body at `.tmp-issue44/pr-body.md`); reference issue #44 by URL; no `Closes` line; attribute line per repo practice. PR stays non-ready until the device gate below passes.
- [ ] **Step 3: Build for device — issue #42 recipe, verbatim**

Bundle: `ANDROID_HOME=/home/bydimasik/android ./gradlew --no-daemon :patches:rvp`; apply with revanced-cli; **hybrid base** (stock merged `vk170/vkvideo-1.170.apk`, swap ALL `classes*.dex` for the patched set, swap `AndroidManifest.xml` for the stripped-permission one), arm64 split with the libvkcore bypass (ret @0x7560 + NOP b.eq @0x7580 — re-verify offsets against THIS build's lib), zipalign + sign all pieces with `keystore/new.keystore` (`revanced`/`revanced`), `adb install-multiple --no-incremental -r` base+arm64+en+xxhdpi.

- [ ] **Step 4: Device checklist (device-test skill; both themes)**

1. Install + cold start; pid alive at t+10s; crash buffer empty.
2. **VerifyError sweep:** feed scroll + video play + catalog open + both settings screens; `logcat -d | grep VerifyError` must be empty.
3. Profile menu order (logged in): `... → Help → separator → Switch profile → ReVanced → Sign out` — `MENU_SEPARATOR` and `SWITCH_PROFILE` sit between Help and Sign out in `cm`, and the ReVanced entry is inserted at the top of `g`, so it renders directly above Sign out; the logged-out branch shows `... → Help → ReVanced → Login` (Login instead of Sign out; `MENU_SEPARATOR`/`SWITCH_PROFILE` are appended only in the logged-in branch of `cm`). The ReVanced row renders with the same VkCell row style as its neighbors (icon, typography, divider).
4. **Both themes:** screenshot the app's own "General"/profile menu and the ReVanced screen in light AND dark (system theme toggle + app relaunch); row backgrounds, text, switches, and the top bar must match the app's own screens — no white-on-white / black-on-black artifacts.
5. `ReVanced → Ads submenu`: opens as a full screen with the `VkTopBar` ("ReVanced") and four native switch rows in the `VkSwitchItem` style; system back AND the top-bar back both return to the profile menu.
6. **4-toggle persistence:** toggle each of the four switches, kill (`am force-stop`), relaunch — all four states persisted; with a toggle OFF the corresponding ads/shelf return, ON → blocked again (top shelf: the catalog top area must no longer be force-collapsed when OFF; note that a shelf only appears if promo content is served that session — record what is observed).
7. Feed loads without "Offline mode"; no new `FATAL`/`AndroidRuntime` lines.
8. **Mandatory close-out:** `am force-stop com.vk.vkvideo`, `input keyevent KEYCODE_SLEEP`, verify `Dozing` + empty pidof.

- [ ] **Step 5: Attach the device results to the PR body checklist; only then mark ready** and run the coderabbit-loop + pr-lifecycle review/merge flow. Post one comment on issue #44 with the PR link and status.

---

## Changelog (plan history)

- Round 1 (2026-11-06): initial version.
- Round 1 review pass (2026-11-06): corrected screen-hosting evidence (SettingsNotificationsFragment is the base's only subclass; both lack `onToolbarNavigationClick`); added the 35c `/range` register rule and its test assertion for the collapse block (fixtures 15→18 / 32→35); pinned the #43 null return in `StartReVancedSettings.invoke`; fixed the device-checklist menu order (Help → separator → Switch profile → ReVanced → Sign out; Login when logged out); `onCreateView` must call the fingerprint-resolved `ty1/i0;->m(View)` helper, not inline `setVisibility(8)`; required a public no-arg constructor on `ReVancedSettingsFragment` (reflective `FragmentEntry;->u8()` instantiation); marked `AdsSettings.buildContentView` for deletion (dead code once the dialog is removed); documented stock-APK decompile provenance (`vk170/smali170-stock{2,3,9,10,11}`, `vk170/smali170/b6`) for the classes missing from the patched-base `vk170/full` decode.
