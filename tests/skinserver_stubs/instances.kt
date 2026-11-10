package net.kdt.pojavlaunch.instances
import java.io.File
/**
 * `gameDirectory` mirrors the real accessor (`Instance.getGameDirectory()`, nullable) because the
 * per-instance cosmetics opt-out is read out of that folder, and `selected` is mutable so the test
 * can point the bridge at a directory it controls instead of always seeing "no instance".
 */
class Instance(val isAerixClient: Boolean, val gameDirectory: File? = null)
object Instances {
 var selected: Instance? = null
 fun loadSelectedInstance(): Instance? = selected
}
