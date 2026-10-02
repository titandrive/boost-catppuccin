// Run as DEX with Morphe Manager APK on app_process classpath.
// JVM execution alone does not reproduce this Android assembler failure.
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.extensions.InstructionExtensions.addInstructionsWithLabels
import com.android.tools.smali.dexlib2.AccessFlags
import com.android.tools.smali.dexlib2.builder.MutableMethodImplementation
import com.android.tools.smali.dexlib2.immutable.ImmutableMethod
import com.android.tools.smali.dexlib2.immutable.ImmutableMethodParameter
import app.morphe.patcher.util.proxy.mutableTypes.MutableMethod.Companion.toMutable

fun main() {
 try {
 for (fixed in listOf(false, true)) {
 val method = ImmutableMethod("Lhe/f0;", "viewer", listOf(ImmutableMethodParameter("Landroid/app/Activity;", emptySet(), null)), "V", AccessFlags.PUBLIC.value or AccessFlags.STATIC.value, null, null, MutableMethodImplementation(7)).toMutable()
 val code = """
                invoke-static {}, Lid/b;->v0()Lid/b;
                move-result-object v0
                invoke-virtual {v0}, Lid/b;->D3()I
                move-result v0
                const/16 v1, 17
                if-eq v0, v1, :latte
                const/16 v1, 18
                if-ne v0, v1, :done
                const v1, 0xff24273a
                const v2, 0xff24273a
                const v3, 0xffcad3f5
                goto :apply
                :latte
                const v1, 0xffeff1f5
                const v2, 0xffeff1f5
                const v3, 0xff4c4f69
                :apply
                invoke-virtual {p0}, Landroid/app/Activity;->getWindow()Landroid/view/Window;
                move-result-object v0
                invoke-virtual {v0}, Landroid/view/Window;->getDecorView()Landroid/view/View;
                move-result-object v4
                invoke-virtual {v4, v1}, Landroid/view/View;->setBackgroundColor(I)V
                invoke-virtual {v0, v2}, Landroid/view/Window;->setStatusBarColor(I)V
                invoke-virtual {v0, v2}, Landroid/view/Window;->setNavigationBarColor(I)V
                const v0, 0x7f0a01ac
                invoke-virtual {p0, v0}, Landroid/app/Activity;->findViewById(I)Landroid/view/View;
                move-result-object v0
                if-eqz v0, :main
                invoke-virtual {v0, v1}, Landroid/view/View;->setBackgroundColor(I)V
                :main
                const v0, 0x7f0a0374
                invoke-virtual {p0, v0}, Landroid/app/Activity;->findViewById(I)Landroid/view/View;
                move-result-object v0
                if-eqz v0, :bottom
                invoke-virtual {v0, v1}, Landroid/view/View;->setBackgroundColor(I)V
                :bottom
                const v0, 0x7f0a015c
                invoke-virtual {p0, v0}, Landroid/app/Activity;->findViewById(I)Landroid/view/View;
                move-result-object v0
                if-eqz v0, :toolbar
                const/4 v4, 0
                invoke-virtual {v0, v4}, Landroid/view/View;->setBackgroundColor(I)V
                invoke-static {v0, v3}, Lhe/f0;->catppuccinViewerControls(Landroid/view/View;I)V
                :toolbar
                const v0, 0x7f0a0668
                invoke-virtual {p0, v0}, Landroid/app/Activity;->findViewById(I)Landroid/view/View;
                move-result-object v0
                if-eqz v0, :done
                const/4 v4, 0
                invoke-virtual {v0, v4}, Landroid/view/View;->setBackgroundColor(I)V
                invoke-static {v0, v3}, Lhe/f0;->catppuccinViewerControls(Landroid/view/View;I)V
                :done
                return-void
            """
 if (fixed) method.addInstructionsWithLabels(0, code) else method.addInstructions(0, code)
 if (!fixed) method.addInstructionsWithLabels(0, "invoke-static/range {p0 .. p0}, Lhe/f0;->catppuccinMediaToolbar(Landroid/app/Activity;)V")
 val instructions=method.implementation!!.instructions.toList()
 val starts=mutableSetOf<Int>(); var pc=0
 for (i in instructions) { starts.add(pc); pc+=i.codeUnits }
 pc=0; var bad=0
 for(i in instructions) {
 if(i is com.android.tools.smali.dexlib2.iface.instruction.OffsetInstruction && pc+i.codeOffset !in starts) { println("INVALID fixed=$fixed pc=$pc target=${pc+i.codeOffset}"); bad++ }
 pc+=i.codeUnits
 }
 println("CHECK fixed=$fixed invalid=$bad")
 if(fixed) check(bad==0) else check(bad>0)
 }
 } catch(e:Throwable){e.printStackTrace(System.out)}
}
