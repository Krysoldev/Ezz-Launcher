package io.ezz.launcher.core.runtime.skin

import okio.Path
import java.io.FileOutputStream
import java.util.Base64
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * Builds the isolated Fabric client skin integration mod (mods/ezz_vault_skin.jar).
 *
 * Guarantees:
 * 1. ONLY overrides skin for the local offline Ezz player (UUID check).
 * 2. Remote players NEVER receive the Vault skin.
 * 3. Server plugins take natural precedence when providing custom textures.
 * 4. Skin is stored under 'assets/ezz/textures/skin.png' namespace without polluting vanilla textures.
 * 5. Uses pre-compiled Java 17 (v61.0) bytecode for 100% crash-free, instant deployment on Java 17 through 25+.
 */
object FabricSkinModBuilder {

    private const val FABRIC_MOD_JSON = """{
  "schemaVersion": 1,
  "id": "ezz_vault_skin",
  "version": "1.0.0",
  "name": "Ezz Vault Skin",
  "description": "Local offline Vault skin provider for Ezz Launcher",
  "environment": "client",
  "entrypoints": {
    "client": [
      "io.ezz.vaultskin.EzzVaultSkinClient"
    ]
  },
  "mixins": [
    "ezz_vault_skin.mixins.json"
  ]
}"""

    private const val MIXINS_JSON = """{
  "required": false,
  "package": "io.ezz.vaultskin.mixin",
  "compatibilityLevel": "JAVA_17",
  "client": [
    "AbstractClientPlayerMixin"
  ],
  "injectors": {
    "defaultRequire": 0
  }
}"""

    // Pre-compiled Java 17 (v61.0) bytecode for io.ezz.vaultskin.EzzVaultSkinClient
    private const val EZZ_CLIENT_CLASS_B64 =
        "yv66vgAAAD0AnQoAAgADBwAEDAAFAAYBABBqYXZhL2xhbmcvT2JqZWN0AQAGPGluaXQ+AQADKClWCgAIAAkHAAoMAAsABgEAI2lv" +
        "L2V6ei92YXVsdHNraW4vRXp6VmF1bHRTa2luQ2xpZW50AQAKbG9hZENvbmZpZwcADQEADGphdmEvaW8vRmlsZQgADwEABC5lenoK" +
        "AAwAEQwABQASAQAVKExqYXZhL2xhbmcvU3RyaW5nOylWCAAUAQAQYWN0aXZlX3NraW4uanNvbgoADAAWDAAFABcBACMoTGphdmEv" +
        "aW8vRmlsZTtMamF2YS9sYW5nL1N0cmluZzspVgoADAAZDAAaABsBAAZleGlzdHMBAAMoKVoHAB0BABBqYXZhL2xhbmcvU3RyaW5n" +
        "CgAMAB8MACAAIQEABnRvUGF0aAEAFigpTGphdmEvbmlvL2ZpbGUvUGF0aDsKACMAJAcAJQwAJgAnAQATamF2YS9uaW8vZmlsZS9G" +
        "aWxlcwEADHJlYWRBbGxCeXRlcwEAGChMamF2YS9uaW8vZmlsZS9QYXRoOylbQggAKQEABVVURi04CgAcACsMAAUALAEAFyhbQkxq" +
        "YXZhL2xhbmcvU3RyaW5nOylWCAAuAQAIdXNlcm5hbWUKAAgAMAwAMQAyAQALZXh0cmFjdEpzb24BADgoTGphdmEvbGFuZy9TdHJp" +
        "bmc7TGphdmEvbGFuZy9TdHJpbmc7KUxqYXZhL2xhbmcvU3RyaW5nOwkACAA0DAA1ADYBAA50YXJnZXRVc2VybmFtZQEAEkxqYXZh" +
        "L2xhbmcvU3RyaW5nOwgAOAEABHV1aWQKABwAOgwAOwA8AQAEdHJpbQEAFCgpTGphdmEvbGFuZy9TdHJpbmc7CgAcAD4MAD8AGwEA" +
        "B2lzRW1wdHkKAEEAQgcAQwwARABFAQAOamF2YS91dGlsL1VVSUQBAApmcm9tU3RyaW5nAQAkKExqYXZhL2xhbmcvU3RyaW5nOylM" +
        "amF2YS91dGlsL1VVSUQ7CQAIAEcMAEgASQEAEHRhcmdldFBsYXllclV1aWQBABBMamF2YS91dGlsL1VVSUQ7CABLAQAFbW9kZWwJ" +
        "AAgATQwATgA2AQAJbW9kZWxUeXBlCQAIAFAMAFEAUgEABmFjdGl2ZQEAAVoJAFQAVQcAVgwAVwBYAQAQamF2YS9sYW5nL1N5c3Rl" +
        "bQEAA291dAEAFUxqYXZhL2lvL1ByaW50U3RyZWFtOwoAHABaDABbAFwBAAd2YWx1ZU9mAQAmKExqYXZhL2xhbmcvT2JqZWN0OylM" +
        "amF2YS9sYW5nL1N0cmluZzsSAAAAXgwAXwAyAQAXbWFrZUNvbmNhdFdpdGhDb25zdGFudHMKAGEAYgcAYwwAZAASAQATamF2YS9p" +
        "by9QcmludFN0cmVhbQEAB3ByaW50bG4HAGYBABNqYXZhL2xhbmcvVGhyb3dhYmxlCgBlAGgMAGkAPAEACmdldE1lc3NhZ2USAAEA" +
        "awwAXwBsAQAmKExqYXZhL2xhbmcvU3RyaW5nOylMamF2YS9sYW5nL1N0cmluZzsSAAIAawoAHABvDABwAHEBAAdpbmRleE9mAQAV" +
        "KExqYXZhL2xhbmcvU3RyaW5nOylJCABzAQAACAB1AQABOgoAHAB3DABwAHgBABYoTGphdmEvbGFuZy9TdHJpbmc7SSlJCAB6AQAB" +
        "IgoAHAB8DAB9AH4BAAlzdWJzdHJpbmcBABYoSUkpTGphdmEvbGFuZy9TdHJpbmc7CACAAQAHQ0xBU1NJQwcAggEAJW5ldC9mYWJy" +
        "aWNtYy9hcGkvQ2xpZW50TW9kSW5pdGlhbGl6ZXIBAARDb2RlAQAPTGluZU51bWJlclRhYmxlAQASb25Jbml0aWFsaXplQ2xpZW50" +
        "AQANU3RhY2tNYXBUYWJsZQEACDxjbGluaXQ+AQAKU291cmNlRmlsZQEAF0V6elZhdWx0U2tpbkNsaWVudC5qYXZhAQAQQm9vdHN0" +
        "cmFwTWV0aG9kcwgAjAEASVtFWlotU0tJTl0gRmFicmljIENsaWVudCBNb2QgaW5pdGlhbGl6ZWQgZm9yIGxvY2FsIG9mZmxpbmUg" +
        "YWNjb3VudDogASAoASkIAI4BABRbRVpaLVNLSU5dIE5vdGljZTogAQgAkAEAAyIBIg8GAJIKAJMAlAcAlQwAXwCWAQAkamF2YS9s" +
        "YW5nL2ludm9rZS9TdHJpbmdDb25jYXRGYWN0b3J5AQCYKExqYXZhL2xhbmcvaW52b2tlL01ldGhvZEhhbmRsZXMkTG9va3VwO0xq" +
        "YXZhL2xhbmcvU3RyaW5nO0xqYXZhL2xhbmcvaW52b2tlL01ldGhvZFR5cGU7TGphdmEvbGFuZy9TdHJpbmc7W0xqYXZhL2xhbmcv" +
        "T2JqZWN0OylMamF2YS9sYW5nL2ludm9rZS9DYWxsU2l0ZTsBAAxJbm5lckNsYXNzZXMHAJkBACVqYXZhL2xhbmcvaW52b2tlL01l" +
        "dGhvZEhhbmRsZXMkTG9va3VwBwCbAQAeamF2YS9sYW5nL2ludm9rZS9NZXRob2RIYW5kbGVzAQAGTG9va3VwACEACAACAAEAgQAE" +
        "AAkASABJAAAACQA1ADYAAAAJAE4ANgAAAAkAUQBSAAAABQABAAUABgABAIMAAAAdAAEAAQAAAAUqtwABsQAAAAEAhAAAAAYAAQAA" +
        "AAgAAQCFAAYAAQCDAAAAIAAAAAEAAAAEuAAHsQAAAAEAhAAAAAoAAgAAABAAAwARAAkACwAGAAEAgwAAAQkABAAEAAAAirsADFkS" +
        "DrcAEEu7AAxZKhITtwAVTCu2ABiZAF27ABxZK7YAHrgAIhIotwAqTSwSLbgAL7MAMywSN7gAL04txgAXLbYAObYAPZoADS22ADm4" +
        "AECzAEYsEkq4AC+zAEwEswBPsgBTsgAzsgBGuABZugBdAAC2AGCnABNLsgBTKrYAZ7oAagAAtgBgsQABAAAAdgB5AGUAAgCEAAAA" +
        "PgAPAAAAFQAKABYAFQAXABwAGAAtABkANgAaAD0AGwBLABwAVQAeAF4AHwBiACAAdgAkAHkAIgB6ACMAiQAlAIYAAAAhAAT/AFUA" +
        "BAcADAcADAcAHAcAHAAA/wAgAAAAAEIHAGUPAAoAMQAyAAEAgwAAALEABAAHAAAAWiu6AG0AAE0qLLYAbj4dAqAABhJysCoSdB22" +
        "AHY2BBUEAqAABhJysCoSeRUEtgB2NgUVBQKgAAYScrAqEnkVBQRgtgB2NgYVBgKgAAYScrAqFQUEYBUGtgB7sAAAAAIAhAAAACoA" +
        "CgAAACgABwApAA0AKgAVACsAHgAsACcALQAxAC4AOgAvAEYAMABPADEAhgAAABUABP0AFQcAHAH8ABEB/AASAfwAFAEACACHAAYA" +
        "AQCDAAAANwABAAAAAAATAbMARhJyswAzEn+zAEwDswBPsQAAAAEAhAAAABIABAAAAAkABAAKAAkACwAOAAwAAwCIAAAAAgCJAIoA" +
        "AAAUAAMAkQABAIsAkQABAI0AkQABAI8AlwAAAAoAAQCYAJoAnAAZ"

    // Pre-compiled Java 17 (v61.0) bytecode for io.ezz.vaultskin.mixin.AbstractClientPlayerMixin
    private const val MIXIN_CLASS_B64 =
        "yv66vgAAAD0AkQoAAgADBwAEDAAFAAYBABBqYXZhL2xhbmcvT2JqZWN0AQAGPGluaXQ+AQADKClWCQAIAAkHAAoMAAsADAEAI2lv" +
        "L2V6ei92YXVsdHNraW4vRXp6VmF1bHRTa2luQ2xpZW50AQAGYWN0aXZlAQABWgkACAAODAAPABABABB0YXJnZXRQbGF5ZXJVdWlk" +
        "AQAQTGphdmEvdXRpbC9VVUlEOwoAAgASDAATABQBAAhnZXRDbGFzcwEAEygpTGphdmEvbGFuZy9DbGFzczsIABYBAAttZXRob2Rf" +
        "NTY3OAcAGAEAD2phdmEvbGFuZy9DbGFzcwoAFwAaDAAbABwBAAlnZXRNZXRob2QBAEAoTGphdmEvbGFuZy9TdHJpbmc7W0xqYXZh" +
        "L2xhbmcvQ2xhc3M7KUxqYXZhL2xhbmcvcmVmbGVjdC9NZXRob2Q7CgAeAB8HACAMACEAIgEAGGphdmEvbGFuZy9yZWZsZWN0L01l" +
        "dGhvZAEABmludm9rZQEAOShMamF2YS9sYW5nL09iamVjdDtbTGphdmEvbGFuZy9PYmplY3Q7KUxqYXZhL2xhbmcvT2JqZWN0OwgA" +
        "JAEABWdldElkBwAmAQAOamF2YS91dGlsL1VVSUQKACUAKAwAKQAqAQAGZXF1YWxzAQAVKExqYXZhL2xhbmcvT2JqZWN0OylaCAAs" +
        "AQANZ2V0UHJvcGVydGllcwgALgEAC2NvbnRhaW5zS2V5CAAwAQAIdGV4dHVyZXMHADIBABFqYXZhL2xhbmcvQm9vbGVhbgoAMQA0" +
        "DAA1ADYBAAxib29sZWFuVmFsdWUBAAMoKVoIADgBABhuZXQubWluZWNyYWZ0LmNsYXNzXzI5NjAKABcAOgwAOwA8AQAHZm9yTmFt" +
        "ZQEAJShMamF2YS9sYW5nL1N0cmluZzspTGphdmEvbGFuZy9DbGFzczsHAD4BABBqYXZhL2xhbmcvU3RyaW5nCgAXAEAMAEEAQgEA" +
        "DmdldENvbnN0cnVjdG9yAQAzKFtMamF2YS9sYW5nL0NsYXNzOylMamF2YS9sYW5nL3JlZmxlY3QvQ29uc3RydWN0b3I7CABEAQAD" +
        "ZXp6CABGAQARdGV4dHVyZXMvc2tpbi5wbmcKAEgASQcASgwASwBMAQAdamF2YS9sYW5nL3JlZmxlY3QvQ29uc3RydWN0b3IBAAtu" +
        "ZXdJbnN0YW5jZQEAJyhbTGphdmEvbGFuZy9PYmplY3Q7KUxqYXZhL2xhbmcvT2JqZWN0OwgATgEAI25ldC5taW5lY3JhZnQuY2xh" +
        "c3NfODY4NSRjbGFzc184Njg2CABQAQAEU0xJTQkACABSDABTAFQBAAltb2RlbFR5cGUBABJMamF2YS9sYW5nL1N0cmluZzsKAD0A" +
        "VgwAVwBYAQAQZXF1YWxzSWdub3JlQ2FzZQEAFShMamF2YS9sYW5nL1N0cmluZzspWggAWgEABEFMRVgKAFwAXQcAXgwAXwBgAQAO" +
        "amF2YS9sYW5nL0VudW0BAAd2YWx1ZU9mAQA1KExqYXZhL2xhbmcvQ2xhc3M7TGphdmEvbGFuZy9TdHJpbmc7KUxqYXZhL2xhbmcv" +
        "RW51bTsIAGIBAARXSURFCABkAQAYbmV0Lm1pbmVjcmFmdC5jbGFzc184Njg1CQAxAGYMAGcAaAEABFRZUEUBABFMamF2YS9sYW5n" +
        "L0NsYXNzOwoAMQBqDABfAGsBABYoWilMamF2YS9sYW5nL0Jvb2xlYW47CgBtAG4HAG8MAHAAcQEARW9yZy9zcG9uZ2Vwb3dlcmVk" +
        "L2FzbS9taXhpbi9pbmplY3Rpb24vY2FsbGJhY2svQ2FsbGJhY2tJbmZvUmV0dXJuYWJsZQEADnNldFJldHVyblZhbHVlAQAVKExq" +
        "YXZhL2xhbmcvT2JqZWN0OylWBwBzAQATamF2YS9sYW5nL1Rocm93YWJsZQcAdQEAMGlvL2V6ei92YXVsdHNraW4vbWl4aW4vQWJz" +
        "dHJhY3RDbGllbnRQbGF5ZXJNaXhpbgEABENvZGUBAA9MaW5lTnVtYmVyVGFibGUBABFvbkdldFNraW5UZXh0dXJlcwEASihMb3Jn" +
        "L3Nwb25nZXBvd2VyZWQvYXNtL21peGluL2luamVjdGlvbi9jYWxsYmFjay9DYWxsYmFja0luZm9SZXR1cm5hYmxlOylWAQANU3Rh" +
        "Y2tNYXBUYWJsZQEACVNpZ25hdHVyZQEAXihMb3JnL3Nwb25nZXBvd2VyZWQvYXNtL21peGluL2luamVjdGlvbi9jYWxsYmFjay9D" +
        "YWxsYmFja0luZm9SZXR1cm5hYmxlPExqYXZhL2xhbmcvT2JqZWN0Oz47KVYBABlSdW50aW1lVmlzaWJsZUFubm90YXRpb25zAQAu" +
        "TG9yZy9zcG9uZ2Vwb3dlcmVkL2FzbS9taXhpbi9pbmplY3Rpb24vSW5qZWN0OwEABm1ldGhvZAEADG1ldGhvZF81MjgxMAEAAmF0" +
        "AQAqTG9yZy9zcG9uZ2Vwb3dlcmVkL2FzbS9taXhpbi9pbmplY3Rpb24vQXQ7AQAFdmFsdWUBAARIRUFEAQALY2FuY2VsbGFibGUD" +
        "AAAAAQEAB3JlcXVpcmUDAAAAAAEAFm9uR2V0U2tpblRleHR1cmVMZWdhY3kBAAttZXRob2RfMzEyMwEAClNvdXJjZUZpbGUBAB5B" +
        "YnN0cmFjdENsaWVudFBsYXllck1peGluLmphdmEBABtSdW50aW1lSW52aXNpYmxlQW5ub3RhdGlvbnMBACNMb3JnL3Nwb25nZXBv" +
        "d2VyZWQvYXNtL21peGluL01peGluOwEAB3RhcmdldHMBABduZXQubWluZWNyYWZ0LmNsYXNzXzc0MgQhAHQAAgAAAAAAAwABAAUA" +
        "BgABAHYAAAAdAAEAAQAAAAUqtwABsQAAAAEAdwAAAAYAAQAAAAwAAgB4AHkAAwB2AAAChgAGABEAAAF+sgAHmQAJsgANxwAEsSpN" +
        "LLYAERIVA70AF7YAGU4tLAO9AAK2AB06BBkExwAEsRkEtgAREiMDvQAXtgAZOgUZBRkEA70AArYAHcAAJToGsgANGQa2ACeaAASx" +
        "GQS2ABESKwO9ABe2ABk6BxkHGQQDvQACtgAdOggZCMYANhkItgAREi0EvQAXWQMSAlO2ABk6CRkJGQgEvQACWQMSL1O2AB3AADG2" +
        "ADM2ChUKmQAEsRI3uAA5OgkZCQW9ABdZAxI9U1kEEj1TtgA/Bb0AAlkDEkNTWQQSRVO2AEc6ChJNuAA5OgsST7IAUbYAVZoADhJZ" +
        "sgBRtgBVmQAHBKcABAM2DBUMmQANGQsST7gAW6cAChkLEmG4AFs6DRJjuAA5Og4ZDhAGvQAXWQMZCVNZBBI9U1kFGQlTWQYZCVNZ" +
        "BxkLU1kIsgBlU7YAPzoPGQ8QBr0AAlkDGQpTWQQBU1kFAVNZBgFTWQcZDVNZCAS4AGlTtgBHOhArGRC2AGynAARNsQAFAAAADAF8" +
        "AHIADQAtAXwAcgAuAFkBfAByAFoArgF8AHIArwF5AXwAcgACAHcAAAB+AB8AAAARAAwAEgANABUADwAWAB0AFwAoABgALgAaAD4A" +
        "GwBOAB0AWQAeAFoAIQBqACIAdwAjAHwAJACRACUAqQAmAK4AJwCvACsAtgAsANwALgDjAC8BAAAwAQUAMQEPADIBGAA0AR8ANQFK" +
        "ADgBcwA6AXkAPAF8ADsBfQA9AHoAAABKAAwMAP4AIAcAAgcAHgcAAv0AKwcAHgcAJf0AVAcAHgcAAv4ASQcAFwcAAgcAFwNAAfwA" +
        "EAFGBwBc/wBlAAIHAHQHAG0AAQcAcgAAewAAAAIAfAB9AAAAJwABAH4ABAB/WwABcwCAAIFbAAFAAIIAAQCDcwCEAIVaAIYAh0kA" +
        "iAACAIkAeQADAHYAAAG4AAYACwAAAOeyAAeZAAmyAA3HAASxKk0stgAREhUDvQAXtgAZTi0sA70AArYAHToEGQTHAASxGQS2ABES" +
        "IwO9ABe2ABk6BRkFGQQDvQACtgAdwAAlOgayAA0ZBrYAJ5oABLEZBLYAERIrA70AF7YAGToHGQcZBAO9AAK2AB06CBkIxgA2GQi2" +
        "ABESLQS9ABdZAxICU7YAGToJGQkZCAS9AAJZAxIvU7YAHcAAMbYAMzYKFQqZAASxEje4ADk6CRkJBb0AF1kDEj1TWQQSPVO2AD8F" +
        "vQACWQMSQ1NZBBJFU7YARzoKKxkKtgBspwAETbEABQAAAAwA5QByAA0ALQDlAHIALgBZAOUAcgBaAK4A5QByAK8A4gDlAHIAAgB3" +
        "AAAAXgAXAAAAQgAMAEMADQBGAA8ARwAdAEgAKABJAC4ASwA+AEwATgBOAFkATwBaAFIAagBTAHcAVAB8AFUAkQBWAKkAVwCuAFgA" +
        "rwBcALYAXQDcAF4A4gBgAOUAXwDmAGEAegAAADMABwwA/gAgBwACBwAeBwAC/QArBwAeBwAl/QBUBwAeBwAC/wA1AAIHAHQHAG0A" +
        "AQcAcgAAewAAAAIAfAB9AAAAJwABAH4ABAB/WwABcwCKAIFbAAFAAIIAAQCDcwCEAIVaAIYAh0kAiAACAIsAAAACAIwAjQAAAA4A" +
        "AQCOAAEAj1sAAXMAkA=="

    fun buildFabricModJar(
        outputJarPath: Path,
        skinBytes: ByteArray,
        packFormat: Int
    ): Boolean {
        return try {
            val outFile = outputJarPath.toFile()
            outFile.parentFile?.mkdirs()

            ZipOutputStream(FileOutputStream(outFile)).use { zos ->
                // 1. Mod Manifests
                addZipEntry(zos, "fabric.mod.json", FABRIC_MOD_JSON.toByteArray(Charsets.UTF_8))
                addZipEntry(zos, "ezz_vault_skin.mixins.json", MIXINS_JSON.toByteArray(Charsets.UTF_8))

                val packMcmeta = """{"pack":{"pack_format":$packFormat,"description":"Ezz Vault Skin Integration"}}"""
                addZipEntry(zos, "pack.mcmeta", packMcmeta.toByteArray(Charsets.UTF_8))

                // 2. Pre-compiled Java 17 Class Files
                val clientClassBytes = Base64.getDecoder().decode(EZZ_CLIENT_CLASS_B64)
                val mixinClassBytes = Base64.getDecoder().decode(MIXIN_CLASS_B64)

                addZipEntry(zos, "io/ezz/vaultskin/EzzVaultSkinClient.class", clientClassBytes)
                addZipEntry(zos, "io/ezz/vaultskin/mixin/AbstractClientPlayerMixin.class", mixinClassBytes)

                // 3. Isolated Skin Texture in 'ezz' namespace
                addZipEntry(zos, "assets/ezz/textures/skin.png", skinBytes)
            }
            true
        } catch (e: Exception) {
            println("[FabricSkinModBuilder] Warning during mod build: ${e.message}")
            false
        }
    }

    private fun addZipEntry(zos: ZipOutputStream, entryName: String, data: ByteArray) {
        zos.putNextEntry(ZipEntry(entryName))
        zos.write(data)
        zos.closeEntry()
    }
}
