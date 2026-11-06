package app.revanced.patches.vkvideo.ad.shelf

import app.revanced.patches.vkvideo.settings.ADS_SETTINGS_CLASS
import app.revanced.patches.vkvideo.settings.revancedSettingsPatch
import app.revanced.patches.vkvideo.util.PatchTestUtil
import app.revanced.patches.vkvideo.util.PatchTestUtil.applyPatches
import app.revanced.patches.vkvideo.util.PatchTestUtil.getInstructions
import app.revanced.patches.vkvideo.util.PatchTestUtil.getMethod
import app.revanced.patches.vkvideo.util.PatchTestUtil.matchOrNull
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.builder.BuilderInstruction
import com.android.tools.smali.dexlib2.iface.instruction.FiveRegisterInstruction
import com.android.tools.smali.dexlib2.iface.instruction.Instruction
import com.android.tools.smali.dexlib2.iface.instruction.NarrowLiteralInstruction
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction
import com.android.tools.smali.dexlib2.iface.instruction.OffsetInstruction
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction
import com.android.tools.smali.dexlib2.iface.instruction.RegisterRangeInstruction
import com.android.tools.smali.dexlib2.iface.reference.FieldReference
import com.android.tools.smali.dexlib2.iface.reference.MethodReference
import com.android.tools.smali.dexlib2.iface.reference.StringReference
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestFactory

/**
 * Structural tests for the runtime top-shelf conditional (replaces the deleted
 * build-time SuppressTopShelfPatch resource rewrite).
 *
 * Register-discipline deviation from the plan's inline collapse block, deliberate:
 * the plan put `getLayoutParams`/`iput height`/`setLayoutParams` into the appended
 * scratch registers, but DEX field opcodes are format 22c (4-bit registers, no
 * /range form) — they cannot address scratch at v16+. The collapse mechanics
 * therefore live in the injected `AdsSettings.collapseTopShelfView(View)` helper
 * (compiled by the smali assembler, which validates every encoding), and the
 * appended block only emits ops legal at any register width: `invoke-static/range`,
 * `move-result`, `if-nez`. The plan's /range rule is still pinned: the appended
 * collapse invoke is always the range form, so a matched method whose view
 * register exceeds v15 stays legal.
 */
class ConditionalTopShelfTest {

    private val topLevelshelfLayoutId = 0x7f0e029c
    private val musicTopshelfLayoutId = 0x7f0e027e

    /** `Lni0/k;` shape: <init> inflates the shelf, stores it in the root View field, returns. */
    private val videoBlockFixture = """
        .class public final Lni0/k;
        .super Ljava/lang/Object;

        .field public final a:Lcom/vk/catalog2/common/dto/ui/UIBlockTopshelf${'$'}TopshelfItem;
        .field public final b:Landroid/view/View;

        .method public constructor <init>(Landroidx/constraintlayout/widget/ConstraintLayout;Landroid/view/LayoutInflater;ZLcom/vk/catalog2/common/dto/ui/UIBlockTopshelf${'$'}TopshelfItem;Z)V
            .registers 15

            invoke-direct {p0}, Ljava/lang/Object;-><init>()V

            iput-object p3, p0, Lni0/k;->a:Lcom/vk/catalog2/common/dto/ui/UIBlockTopshelf${'$'}TopshelfItem;

            const v1, 0x7f0e029c
            const/4 v2, 0x0
            invoke-virtual {p2, v1, p1, v2}, Landroid/view/LayoutInflater;->inflate(ILandroid/view/ViewGroup;Z)Landroid/view/View;
            move-result-object p1

            invoke-virtual {p1, v1}, Landroid/view/View;->findViewById(I)Landroid/view/View;
            move-result-object v2
            invoke-virtual {p1, v2}, Landroid/view/View;->setLayoutParams(Landroid/view/ViewGroup${'$'}LayoutParams;)V

            iput-object p1, p0, Lni0/k;->b:Landroid/view/View;

            return-void
        .end method

        .method public final getView()Landroid/view/View;
            .registers 2

            iget-object v0, p0, Lni0/k;->b:Landroid/view/View;
            return-object v0
        .end method
    """.trimIndent()

    /** `musicvideo/b` shape: onCreateView inflates the shelf mid-method, keeps building. */
    private val musicBlockFixture = """
        .class public Lcom/vk/catalog2/common/ui/holders/video/musicvideo/b;
        .super Landroidx/fragment/app/Fragment;

        .method public onCreateView(Landroid/view/LayoutInflater;Landroid/view/ViewGroup;Landroid/os/Bundle;)Landroid/view/View;
            .registers 32

            move-object/from16 v0, p0

            new-instance v1, Landroidx/constraintlayout/widget/ConstraintLayout;
            invoke-virtual/range {p1 .. p1}, Landroid/view/LayoutInflater;->getContext()Landroid/content/Context;
            move-result-object v2
            invoke-direct {v1, v2}, Landroidx/constraintlayout/widget/ConstraintLayout;-><init>(Landroid/content/Context;)V

            const/4 v8, 0x1
            const v5, 0x7f0e027e
            move-object/from16 v9, p1
            invoke-virtual {v9, v5, v1, v8}, Landroid/view/LayoutInflater;->inflate(ILandroid/view/ViewGroup;Z)Landroid/view/View;
            move-result-object v5

            new-instance v10, Landroidx/constraintlayout/widget/ConstraintLayout${'$'}b;
            const/4 v14, 0x3
            const/4 v15, -0x2
            invoke-direct {v10, v14, v15}, Landroidx/constraintlayout/widget/ConstraintLayout${'$'}b;-><init>(II)V
            invoke-virtual {v5, v10}, Landroid/view/View;->setLayoutParams(Landroid/view/ViewGroup${'$'}LayoutParams;)V
            invoke-virtual {v1, v5}, Landroid/view/ViewGroup;->addView(Landroid/view/View;)V

            return-object v1
        .end method
    """.trimIndent()

    private data class Fixture(
        val name: String,
        val sources: List<String>,
        val fingerprint: app.revanced.patcher.Fingerprint,
        val expectedLayoutId: Int,
        val expectedMethodName: String,
    )

    private val fixtures = listOf(
        Fixture(
            name = "video topshelf block (ni0/k, 15 registers)",
            sources = PatchTestUtil.profileMenuFixture170 + listOf(videoBlockFixture),
            fingerprint = videoTopshelfBlockFingerprint,
            expectedLayoutId = topLevelshelfLayoutId,
            expectedMethodName = "<init>",
        ),
        Fixture(
            name = "music topshelf fragment (32 registers)",
            sources = PatchTestUtil.profileMenuFixture170 + listOf(musicBlockFixture),
            fingerprint = musicTopshelfFragmentFingerprint,
            expectedLayoutId = musicTopshelfLayoutId,
            expectedMethodName = "onCreateView",
        ),
    )

    private fun createContext(sources: List<String>) =
        PatchTestUtil.createBytecodeContext(*sources.toTypedArray())

    private fun shelfInflateIndex(instructions: List<BuilderInstruction>): Int =
        instructions.indexOfFirst { instruction ->
            val reference = (instruction as? ReferenceInstruction)?.reference
            instruction.opcode == Opcode.INVOKE_VIRTUAL &&
                reference is MethodReference &&
                reference.name == "inflate" &&
                reference.parameterTypes == listOf("I", "Landroid/view/ViewGroup;", "Z") &&
                reference.returnType == "Landroid/view/View;"
        }

    /** Register holding the shelf root view right after the inflate's move-result-object. */
    private fun shelfViewRegister(instructions: List<BuilderInstruction>): Int =
        (instructions[shelfInflateIndex(instructions) + 1] as OneRegisterInstruction).registerA

    @TestFactory
    fun `appends the runtime collapse conditional after the shelf view is acquired`(): List<DynamicTest> =
        fixtures.map { fixture ->
            DynamicTest.dynamicTest(fixture.name) {
                val context = createContext(fixture.sources)
                assertNotNull(fixture.fingerprint.matchOrNull(context))
                val method = fixture.fingerprint.getMethod(context)
                val before = method.getInstructions()
                val insertIndex = shelfInflateIndex(before) + 2
                val oldRegisterCount = method.implementation!!.registerCount

                applyPatches(context, conditionalTopShelfPatch)

                val after = method.getInstructions()
                assertEquals(
                    oldRegisterCount + 1,
                    method.implementation!!.registerCount,
                    "frame must grow by one scratch register without shifting existing registers",
                )

                // The appended block: guard getter -> move-result -> if-nez -> /range collapse invoke.
                val blockStart = after.indexOfFirst { instruction ->
                    val reference = (instruction as? ReferenceInstruction)?.reference
                    instruction.opcode == Opcode.INVOKE_STATIC &&
                        reference is MethodReference &&
                        reference.definingClass == ADS_SETTINGS_CLASS &&
                        reference.name == "isTopShelfAdsEnabled"
                }
                assertTrue(blockStart > 0, "conditional block not appended")
                val block = after.subList(blockStart, blockStart + 4)
                assertEquals(Opcode.MOVE_RESULT, block[1].opcode)
                assertEquals(
                    oldRegisterCount,
                    (block[1] as OneRegisterInstruction).registerA,
                    "guard result must live in the new scratch register",
                )
                assertEquals(Opcode.IF_NEZ, block[2].opcode)
                assertEquals(oldRegisterCount, (block[2] as OneRegisterInstruction).registerA)

                assertEquals(Opcode.INVOKE_STATIC_RANGE, block[3].opcode, "collapse invoke must be the range form")
                val collapseRef = (block[3] as ReferenceInstruction).reference as MethodReference
                assertEquals(ADS_SETTINGS_CLASS, collapseRef.definingClass)
                assertEquals("collapseTopShelfView", collapseRef.name)
                assertEquals("Landroid/view/View;", collapseRef.parameterTypes.single().toString())
                assertEquals(
                    shelfViewRegister(before),
                    (block[3] as RegisterRangeInstruction).startRegister,
                    "collapse invoke must receive the shelf root view register",
                )

                // 35c/range discipline: no non-range invoke anywhere may carry a register >= 16.
                for (insn in after) {
                    if (insn is FiveRegisterInstruction) {
                        val regs = listOf(insn.registerC, insn.registerD, insn.registerE, insn.registerF, insn.registerG)
                            .take(insn.registerCount)
                        assertFalse(
                            regs.any { it > 15 },
                            "35c opcode ${insn.opcode} carries register operand >= 16: $regs",
                        )
                    }
                }

                // The branch skips the collapse and lands on the original next instruction
                // (branch offsets are relative to the branching instruction).
                val branch = block[2] as OffsetInstruction
                val targetAddress = block[2].location.codeAddress + branch.codeOffset
                val targetIndex = after.indexOfFirst { it.location.codeAddress == targetAddress }
                assertEquals(blockStart + 4, targetIndex, "if-nez must skip past the collapse invoke")

                // Every original instruction survives unchanged, in order (append-scratch
                // discipline: no operand remap).
                val originals = before
                val rebuilt = after.subList(0, blockStart) + after.subList(blockStart + 4, after.size)
                assertEquals(originals.size, rebuilt.size, "only the 4-instruction block may be added")
                originals.forEachIndexed { i, original ->
                    assertEquals(original.opcode, rebuilt[i].opcode, "original instruction $i opcode")
                    assertEquals(
                        (original as? OneRegisterInstruction)?.registerA,
                        (rebuilt[i] as? OneRegisterInstruction)?.registerA,
                        "original instruction $i register",
                    )
                    assertEquals(
                        (original as? ReferenceInstruction)?.reference?.toString(),
                        (rebuilt[i] as? ReferenceInstruction)?.reference?.toString(),
                        "original instruction $i reference",
                    )
                }
                // ...and the block sits exactly where the shelf view was acquired.
                assertEquals(insertIndex, blockStart, "block must be appended right after the view acquisition")
            }
        }

    @Test
    fun `collapse helper performs the runtime equivalent of the deleted resource patch`() {
        val context = createContext(PatchTestUtil.profileMenuFixture170)
        applyPatches(context, conditionalTopShelfPatch)

        val helper = context.classDefs
            .first { it.type == ADS_SETTINGS_CLASS }
            .methods
            .first { it.name == "collapseTopShelfView" }
        val instructions = helper.implementation!!.instructions.toList()

        fun indexOfInvoke(name: String, start: Int = 0): Int {
            val index = (start until instructions.size).firstOrNull { i ->
                val reference = (instructions[i] as? ReferenceInstruction)?.reference
                reference is MethodReference && reference.name == name
            }
            assertNotNull(index, "expected invoke of $name")
            return index!!
        }

        val getLayoutParams = indexOfInvoke("getLayoutParams")
        assertEquals(
            "Landroid/view/ViewGroup\$LayoutParams;",
            ((instructions[getLayoutParams] as ReferenceInstruction).reference as MethodReference).returnType,
        )
        val iput = instructions.indexOfFirst {
            it.opcode == Opcode.IPUT &&
                ((it as ReferenceInstruction).reference as? FieldReference).let { r ->
                    r != null && r.name == "height" && r.definingClass == "Landroid/view/ViewGroup\$LayoutParams;"
                }
        }
        assertTrue(iput > getLayoutParams, "height write must follow getLayoutParams")
        val zero = instructions[iput - 1]
        assertEquals(Opcode.CONST_4, zero.opcode)
        assertEquals(0, (zero as NarrowLiteralInstruction).narrowLiteral)

        val setLayoutParams = indexOfInvoke("setLayoutParams", iput)
        assertTrue(setLayoutParams > iput)
        val visibilityConst = instructions.indexOfFirst {
            it.opcode == Opcode.CONST_16 && (it as NarrowLiteralInstruction).narrowLiteral == 0x8
        }
        assertTrue(visibilityConst > setLayoutParams)
        val setVisibility = indexOfInvoke("setVisibility", visibilityConst)
        assertTrue(setVisibility > visibilityConst)
    }

    @Test
    fun `isTopShelfAdsEnabled reads the shared AdsSettings store`() {
        val context = createContext(PatchTestUtil.profileMenuFixture170)
        applyPatches(context, conditionalTopShelfPatch)

        val adsSettings = context.classDefs.first { it.type == ADS_SETTINGS_CLASS }
        val getter = adsSettings.methods.first { it.name == "isTopShelfAdsEnabled" }
        val instructions = getter.implementation!!.instructions.toList()

        val strings = instructions.filterIsInstance<ReferenceInstruction>()
            .mapNotNull { (it.reference as? StringReference)?.string }
        assertTrue(strings.contains("revanced_ads_topshelf"), "must read revanced_ads_topshelf")

        val getBooleanIndex = instructions.indexOfFirst {
            val reference = (it as? ReferenceInstruction)?.reference
            reference is MethodReference && reference.name == "getBoolean"
        }
        assertTrue(getBooleanIndex >= 0, "must delegate to AdsSettings.getBoolean")

        val defaults = adsSettings.methods.first { it.name == "getBoolean" }
        val defaultInstr = defaults.implementation!!.instructions.first { it.opcode == Opcode.CONST_4 }
        assertEquals(1, (defaultInstr as NarrowLiteralInstruction).narrowLiteral, "missing key defaults to ON")
    }

    @Test
    fun `depends on the settings patch that injects AdsSettings`() {
        assertTrue(
            conditionalTopShelfPatch.dependencies.contains(revancedSettingsPatch),
        )
    }

    @Test
    fun `the build-time resource patch no longer exists`() {
        val thrown = runCatching {
            Class.forName("app.revanced.patches.vkvideo.ad.shelf.SuppressTopShelfPatchKt")
        }
        assertTrue(thrown.isFailure, "SuppressTopShelfPatch must be deleted")
    }

    @Test
    fun `fingerprints anchor the shelf inflate sites by class and layout id`() {
        for (fixture in fixtures.take(2)) {
            val context = createContext(fixture.sources)
            assertNotNull(fixture.fingerprint.matchOrNull(context))
            val method = fixture.fingerprint.getMethod(context)
            assertEquals(fixture.expectedMethodName, method.name)

            val instructions = method.getInstructions()
            val inflateIndex = shelfInflateIndex(instructions)
            assertTrue(inflateIndex >= 0, "no inflate call in matched method")

            val layoutReg = (instructions[inflateIndex] as FiveRegisterInstruction).registerD
            val layoutConst = (inflateIndex downTo 0).firstNotNullOfOrNull { i ->
                val insn = instructions[i]
                val reg = (insn as? OneRegisterInstruction)?.registerA
                if (insn is NarrowLiteralInstruction &&
                    insn.opcode in setOf(Opcode.CONST, Opcode.CONST_4, Opcode.CONST_16) &&
                    reg == layoutReg
                ) {
                    insn.narrowLiteral
                } else {
                    null
                }
            }
            assertEquals(fixture.expectedLayoutId, layoutConst, "inflate must target the shelf layout")
        }
    }
}
