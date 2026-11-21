package vn.guildmaster.game

import android.content.Context
import vn.guildmaster.core.GameState
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

/** Ghi atomic: tmp → fsync → bản cũ thành .bak → đổi tên tmp. */
class SaveStore(ctx: Context) {
    private val main = File(ctx.filesDir, "save.json")
    private val tmp = File(ctx.filesDir, "save.json.tmp")
    private val bak = File(ctx.filesDir, "save.json.bak")

    fun load(): GameState? {
        for (f in listOf(main, bak)) {
            if (!f.exists()) continue
            try {
                return SaveCodec.decode(f.readText())
            } catch (_: Exception) {
            }
        }
        return null
    }

    fun save(s: GameState) {
        val bytes = SaveCodec.encode(s).toByteArray()
        FileOutputStream(tmp).use {
            it.write(bytes)
            it.fd.sync()
        }
        if (main.exists()) {
            bak.delete()
            main.renameTo(bak)
        }
        if (!tmp.renameTo(main)) throw IOException("Không ghi được save.json")
    }
}
