package halo.social.halo_im.api

import IMConversation
import IMDecryptedMessage
import IMFlutterApi
import android.util.Log
import androidx.annotation.UiThread
import io.flutter.plugin.common.BinaryMessenger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class IMFlutterApiImpl {
    companion object {
        var api: IMFlutterApi? = null
        fun setUp(binaryMessenger: BinaryMessenger) {
            Log.i("IMFlutterApiImpl", "setUp")
            if (api == null) {
                api = IMFlutterApi(binaryMessenger)
            }
        }
    }

    fun onSubscribeToConversations(inboxIdArg: String, mArg: IMConversation) {
        CoroutineScope(Dispatchers.Main).launch {
            try {
                api?.onSubscribeToConversations(inboxIdArg, mArg) {}
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun onSubscribeToMessages(inboxIdArg: String, message: IMDecryptedMessage) {
        Log.i("IMFlutterApiImpl", "onSubscribeToMessages")
        Log.i("IMFlutterApiImpl", "${message.content}")
        CoroutineScope(Dispatchers.Main).launch {
            api?.onSubscribeToMessages(inboxIdArg, message) {}
        }
    }

    fun flutterTest(str: String) {
        Log.d("IMFlutterApiImpl", "flutterTest A $str  Thread - ${Thread.currentThread().name}")
        try {
            api?.flutterApiTest(str) {}
        } catch (e: Exception) {
            e.printStackTrace()
        }
        Log.d("IMFlutterApiImpl", "flutterTest B $str  Thread - ${Thread.currentThread().name}")
    }

    fun onSubscribeToAllMessages(
        inboxIdArg: String,
        messageArg: IMDecryptedMessage,
        callback: (Result<Unit>) -> Unit
    ) {
        CoroutineScope(Dispatchers.Main).launch {
            api?.onSubscribeToAllMessages(inboxIdArg, messageArg) {}
        }
    }

    fun onSignMessage(
        id: String,
        message: String,
    ) {
        CoroutineScope(Dispatchers.Main).launch {
            api?.onSignMessage(id, message){}
        }
    }


}