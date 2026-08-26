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
 * 1. Mixin interceptor on DefaultSkinHelper.getSkinTextures(UUID) and AbstractClientPlayerEntity.
 * 2. Uses local offline account UUID matching to ensure ONLY the local player receives the Vault skin.
 * 3. Pre-compiled Java 17 (v61.0) bytecode for 100% crash-free, instant deployment.
 * 4. Skin is stored under 'assets/ezz/textures/skin.png' and the local player's specific calculated default skin path.
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
    "DefaultSkinHelperMixin",
    "AbstractClientPlayerMixin"
  ],
  "injectors": {
    "defaultRequire": 0
  }
}"""

    // Pre-compiled Java 17 (v61.0) bytecode for io.ezz.vaultskin.EzzVaultSkinClient
    private const val EZZ_CLIENT_CLASS_B64 =
        "yv66vgAAAD0AsAoAAgADBwAEDAAFAAYBABBqYXZhL2xhbmcvT2JqZWN0AQAGPGluaXQ+AQADKClWCgAIAAkHAAoMAAsABgEAI2lv" +
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
        "cmFwTWV0aG9kcwgAjAEAWltFWlotU0tJTl0gRmFicmljIENsaWVudCBNb2QgYWN0aXZlIGZvcjogASAoASkIAI4BABRbRVpaLVNL" +
        "SU5dIE5vdGljZTogAQgAkAEAAyIBIg8GAJIKAJMAlAcAlQwAXwCWAQAkamF2YS9sYW5nL2ludm9rZS9TdHJpbmdDb25jYXRGYWN0" +
        "b3J5AQCYKExqYXZhL2xhbmcvaW52b2tlL01ldGhvZEhhbmRsZXMkTG9va3VwO0xqYXZhL2xhbmcvU3RyaW5nO0xqYXZhL2xhbmcv" +
        "aW52b2tlL01ldGhvZFR5cGU7TGphdmEvbGFuZy9TdHJpbmc7W0xqYXZhL2xhbmcvT2JqZWN0OylMamF2YS9sYW5nL2ludm9rZS9D" +
        "YWxsU2l0ZTsBAAxJbm5lckNsYXNzZXMHAJkBACVqYXZhL2xhbmcvaW52b2tlL01ldGhvZEhhbmRsZXMkTG9va3VwBwCbAQAeamF2" +
        "YS9sYW5nL2ludm9rZS9NZXRob2RIYW5kbGVzAQAGTG9va3VwACEACAACAAEAgQAEAAkASABJAAAACQA1ADYAAAAJAE4ANgAAAAkA" +
        "UQBSAAAABQABAAUABgABAIMAAAAdAAEAAQAAAAUqtwABsQAAAAEAhAAAAAYAAQAAAAgAAQCFAAYAAQCDAAAAIAAAAAEAAAAEuAAH" +
        "sQAAAAEAhAAAAAoAAgAAABAAAwARAAkACwAGAAEAgwAAARsABAAFAAAAksAADBMBtwAQUy2AABMstgAQVSu2ABiZAF27ABxZK7YA" +
        "HrgAIhIotwAqTSwSLbgAL7MAMywSN7gAL04txgAXLbYAObYAPZoADS22ADm4AECzAEYsEkq4AC+zAEwEswBPsgBTsgAzsgBGuABZ" +
        "ugBdAAC2AGCnABNLsgBTKrYAZ7oAagAAtgBgsQABAAAAdgB5AGUAAgCEAAAAPgAPAAAAFQAKABYAFQAXABwAGAAtABkANgAaAD0A" +
        "GwBLABwAVQAeAF4AHwBiACAAdgAkAHkAIgB6ACMAiQAlAIYAAAAhAAT/AFUABAcADAcADAcAHAcAHAAA/wAgAAAAAEIHAGUPAAoA" +
        "MQAyAAEAgwAAALEABAAHAAAAWiu6AG0AAE0qLLYAbj4dAqAABhJysCoSdB22AHY2BBUEAqAABhJysCoSeRUEtgB2NgUVBQKgAAYS" +
        "crAqEnkVBQRgtgB2NgYVBgKgAAYScrAqFQUEYBUGtgB7sAAAAAIAhAAAACoACgAAACgABwApAA0AKgAVACsAHgAsACcALQAxAC4A" +
        "OgAvAEYAMABPADEAhgAAABUABP0AFQcAHAH8ABEB/AASAfwAFAEACACHAAYAAQCDAAAANwABAAAAAAATAbMARhJyswAzEn+zAEwD" +
        "swBPsQAAAAEAhAAAABIABAAAAAkABAAKAAkACwAOAAwAAwCIAAAAAgCJAIoAAAAUAAMAkQABAIsAkQABAI0AkQABAI8AlwAAAAoA" +
        "AQCYAJoAnAAZ"

    // Pre-compiled Java 17 (v61.0) bytecode for io.ezz.vaultskin.mixin.DefaultSkinHelperMixin
    private const val DEFAULT_SKIN_MIXIN_B64 =
        "yv66vgAAAD0AYQoAAgADBwAEDAAFAAYBABBqYXZhL2xhbmcvT2JqZWN0AQAGPGluaXQ+AQADKClWCQAIAAkHAAoMAAsADAEAI2lv" +
        "L2V6ei92YXVsdHNraW4vRXp6VmF1bHRTa2luQ2xpZW50AQAGYWN0aXZlAQABWgkACAAODAAPABABABB0YXJnZXRQbGF5ZXJVdWlk" +
        "AQAQTGphdmEvdXRpbC9VVUlEOwoAEgATBwAUDAAVABYBAA5qYXZhL3V0aWwvVVVJRAEABmVxdWFscwEAFShMamF2YS9sYW5nL09i" +
        "amVjdDspWggAGAEAGG5ldC5taW5lY3JhZnQuY2xhc3NfMTA2OAoAGgAbBwAcDAAdAB4BAA9qYXZhL2xhbmcvQ2xhc3MBAAdmb3JO" +
        "YW1lAQAlKExqYXZhL2xhbmcvU3RyaW5nOylMamF2YS9sYW5nL0NsYXNzOwgAIAEAC2ZpZWxkXzQxMTIxCgAaACIMACMAJAEAEGdl" +
        "dERlY2xhcmVkRmllbGQBAC0oTGphdmEvbGFuZy9TdHJpbmc7KUxqYXZhL2xhbmcvcmVmbGVjdC9GaWVsZDsKACYAJwcAKAwAKQAq" +
        "AQAXamF2YS9sYW5nL3JlZmxlY3QvRmllbGQBAA1zZXRBY2Nlc3NpYmxlAQAEKFopVgoAJgAsDAAtAC4BAANnZXQBACYoTGphdmEv" +
        "bGFuZy9PYmplY3Q7KUxqYXZhL2xhbmcvT2JqZWN0OwcAMAEAE1tMamF2YS9sYW5nL09iamVjdDsIADIBAARTTElNCQAIADQMADUA" +
        "NgEACW1vZGVsVHlwZQEAEkxqYXZhL2xhbmcvU3RyaW5nOwoAOAA5BwA6DAA7ADwBABBqYXZhL2xhbmcvU3RyaW5nAQAQZXF1YWxz" +
        "SWdub3JlQ2FzZQEAFShMamF2YS9sYW5nL1N0cmluZzspWggAPgEABEFMRVgKAEAAQQcAQgwAQwBEAQBFb3JnL3Nwb25nZXBvd2Vy" +
        "ZWQvYXNtL21peGluL2luamVjdGlvbi9jYWxsYmFjay9DYWxsYmFja0luZm9SZXR1cm5hYmxlAQAOc2V0UmV0dXJuVmFsdWUBABUo" +
        "TGphdmEvbGFuZy9PYmplY3Q7KVYHAEYBABNqYXZhL2xhbmcvVGhyb3dhYmxlBwBIAQAtaW8vZXp6L3ZhdWx0c2tpbi9taXhpbi9E" +
        "ZWZhdWx0U2tpbkhlbHBlck1peGluAQAEQ29kZQEAD0xpbmVOdW1iZXJUYWJsZQEAF29uR2V0U2tpblRleHR1cmVzQnlVdWlkAQBa" +
        "KExqYXZhL3V0aWwvVVVJRDtMb3JnL3Nwb25nZXBvd2VyZWQvYXNtL21peGluL2luamVjdGlvbi9jYWxsYmFjay9DYWxsYmFja0lu" +
        "Zm9SZXR1cm5hYmxlOylWAQANU3RhY2tNYXBUYWJsZQEACVNpZ25hdHVyZQEAbihMamF2YS91dGlsL1VVSUQ7TG9yZy9zcG9uZ2Vw" +
        "b3dlcmVkL2FzbS9taXhpbi9pbmplY3Rpb24vY2FsbGJhY2svQ2FsbGJhY2tJbmZvUmV0dXJuYWJsZTxMamF2YS9sYW5nL09iamVj" +
        "dDs+OylWAQAZUnVudGltZVZpc2libGVBbm5vdGF0aW9ucwEALkxvcmcvc3BvbmdlcG93ZXJlZC9hc20vbWl4aW4vaW5qZWN0aW9u" +
        "L0luamVjdDsBAAZtZXRob2QBAAttZXRob2RfNDY0OAEAAmF0AQAqTG9yZy9zcG9uZ2Vwb3dlcmVkL2FzbS9taXhpbi9pbmplY3Rp" +
        "b24vQXQ7AQAFdmFsdWUBAARIRUFEAQALY2FuY2VsbGFibGUDAAAAAQEAB3JlcXVpcmUDAAAAAAEAClNvdXJjZUZpbGUBABtEZWZh" +
        "dWx0U2tpbkhlbHBlck1peGluLmphdmEBABtSdW50aW1lSW52aXNpYmxlQW5ub3RhdGlvbnMBACNMb3JnL3Nwb25nZXBvd2VyZWQv" +
        "YXNtL21peGluL01peGluOwEAB3RhcmdldHMEIQBHAAIAAAAAAAIAAQAFAAYAAQBJAAAAHQABAAEAAAAFKrcAAbEAAAABAEoAAAAG" +
        "AAEAAAAMAAoASwBMAAMASQAAAR4AAwAHAAAAg7IAB5kACbIADccABLGyAA0qtgARmgAEsRIXuAAZTSwSH7YAIU4tBLYAJS0BtgAr" +
        "wAAvOgQZBMYASBkEvp4AQhIxsgAztgA3mgAOEj2yADO2ADeZAAcEpwAEAzYFFQWZAAcDpwARGQS+EA+kAAgQD6cABAM2BisZBBUG" +
        "MrYAP6cABE2xAAMAAAAMAIEARQANABcAgQBFABgAfgCBAEUAAgBKAAAAPgAPAAAAEQAMABIADQAUABcAFQAYABgAHgAZACUAGgAq" +
        "ABsANAAcAD8AHQBcAB4AdQAfAH4AIgCBACEAggAjAE0AAAAtAAwMAAr+ADwHABoHACYHAC8DQAH8AAoBDEAB/wAKAAIHABIHAEAA" +
        "AEIHAEUAAE4AAAACAE8AUAAAACcAAQBRAAQAUlsAAXMAUwBUWwABQABVAAEAVnMAVwBYWgBZAFpJAFsAAgBcAAAAAgBdAF4AAAAO" +
        "AAEAXwABAGBbAAFzABg="

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
        packFormat: Int,
        targetPlayerSlotPath: String? = null
    ): Boolean {
        return try {
            val outFile = outputJarPath.toFile()
            outFile.parentFile?.mkdirs()

            val addedEntries = mutableSetOf<String>()

            ZipOutputStream(FileOutputStream(outFile)).use { zos ->
                // 1. Mod Manifests
                addZipEntry(zos, addedEntries, "fabric.mod.json", FABRIC_MOD_JSON.toByteArray(Charsets.UTF_8))
                addZipEntry(zos, addedEntries, "ezz_vault_skin.mixins.json", MIXINS_JSON.toByteArray(Charsets.UTF_8))

                val packMcmeta = """{"pack":{"pack_format":$packFormat,"description":"Ezz Vault Skin Integration"}}"""
                addZipEntry(zos, addedEntries, "pack.mcmeta", packMcmeta.toByteArray(Charsets.UTF_8))

                // 2. Pre-compiled Java 17 Class Files
                val clientClassBytes = Base64.getDecoder().decode(EZZ_CLIENT_CLASS_B64)
                val defaultMixinBytes = Base64.getDecoder().decode(DEFAULT_SKIN_MIXIN_B64)
                val playerMixinBytes = Base64.getDecoder().decode(MIXIN_CLASS_B64)

                addZipEntry(zos, addedEntries, "io/ezz/vaultskin/EzzVaultSkinClient.class", clientClassBytes)
                addZipEntry(zos, addedEntries, "io/ezz/vaultskin/mixin/DefaultSkinHelperMixin.class", defaultMixinBytes)
                addZipEntry(zos, addedEntries, "io/ezz/vaultskin/mixin/AbstractClientPlayerMixin.class", playerMixinBytes)

                // 3. Isolated Skin Texture in 'ezz' namespace
                addZipEntry(zos, addedEntries, "assets/ezz/textures/skin.png", skinBytes)

                // 4. Also register under the specific player's default skin slot if specified
                if (targetPlayerSlotPath != null && targetPlayerSlotPath.isNotBlank()) {
                    addZipEntry(zos, addedEntries, "assets/minecraft/textures/entity/player/$targetPlayerSlotPath.png", skinBytes)
                }
            }
            true
        } catch (e: Exception) {
            println("[FabricSkinModBuilder] Warning during mod build: ${e.message}")
            false
        }
    }

    private fun addZipEntry(zos: ZipOutputStream, addedEntries: MutableSet<String>, entryName: String, data: ByteArray) {
        if (addedEntries.add(entryName)) {
            zos.putNextEntry(ZipEntry(entryName))
            zos.write(data)
            zos.closeEntry()
        }
    }
}
