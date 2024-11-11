package halo.social.halo_im.xmtp

import android.util.Base64
import android.util.Base64.NO_WRAP
import com.google.protobuf.kotlin.toByteString
import halo.social.halo_im.api.IMFlutterApiImpl
import kotlinx.coroutines.suspendCancellableCoroutine
import org.xmtp.android.library.SigningKey
import org.xmtp.android.library.XMTPException
import org.xmtp.android.library.messages.Signature
import org.xmtp.proto.message.contents.SignatureOuterClass
import java.util.UUID
import kotlin.coroutines.Continuation
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class HaloSingerKey(private val flutterApi: IMFlutterApiImpl, override val address: String) : SigningKey {

    private val continuations: MutableMap<String, Continuation<Signature>> = mutableMapOf()

    fun handle(id: String, signature: String) {
        val continuation = continuations[id] ?: return
        val signatureData = Base64.decode(signature.toByteArray(), NO_WRAP)
        if (signatureData == null || signatureData.size != 65) {
            continuation.resumeWithException(XMTPException("Invalid Signature"))
            continuations.remove(id)
            return
        }
        val sig = Signature.newBuilder().also {
            it.ecdsaCompact = it.ecdsaCompact.toBuilder().also { builder ->
                builder.bytes = signatureData.take(64).toByteArray().toByteString()
                builder.recovery = signatureData[64].toInt()
            }.build()
        }.build()
        continuation.resume(sig)
        continuations.remove(id)
    }

    override suspend fun sign(data: ByteArray): SignatureOuterClass.Signature? {
        val request = SignatureRequest(message = String(data, Charsets.UTF_8))
        flutterApi.onSignMessage(request.id, request.message)
        return suspendCancellableCoroutine { continuation ->
            continuations[request.id] = continuation
        }
    }

    override suspend fun sign(message: String): SignatureOuterClass.Signature? {
        return sign(message.toByteArray())
    }
}

data class SignatureRequest(
    var id: String = UUID.randomUUID().toString(),
    var message: String,
)
