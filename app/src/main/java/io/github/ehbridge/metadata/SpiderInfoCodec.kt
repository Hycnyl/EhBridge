package io.github.ehbridge.metadata

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.cbor.Cbor
import kotlinx.serialization.decodeFromByteArray
import kotlinx.serialization.encodeToByteArray

@Serializable
data class SpiderInfo(
    val gid: Long,
    var token: String? = null,
    val pages: Int,
    val pTokenMap: MutableMap<Int, String> = hashMapOf(),
    var startPage: Int = 0,
    var previewPages: Int = -1,
    var previewPerPage: Int = -1,
    var upgradeFrom: Long? = null
)

@OptIn(ExperimentalSerializationApi::class)
object SpiderInfoCodec {
    private val cbor = Cbor { ignoreUnknownKeys = true; encodeDefaults = true }
    fun encode(info: SpiderInfo): ByteArray {
        require(info.gid > 0 && info.pages in 1..100_000)
        return cbor.encodeToByteArray(info).also { check(decode(it) == info) }
    }
    fun decode(bytes: ByteArray): SpiderInfo = cbor.decodeFromByteArray(bytes)
}
