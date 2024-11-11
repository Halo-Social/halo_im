package halo.social.halo_im

import IMHostApi
import android.content.Context
import android.util.Log
import halo.social.halo_im.api.IMFlutterApiImpl
import halo.social.halo_im.api.IMHostApiImpl

import io.flutter.embedding.engine.plugins.FlutterPlugin
import io.flutter.plugin.common.MethodCall
import io.flutter.plugin.common.MethodChannel.MethodCallHandler
import io.flutter.plugin.common.MethodChannel.Result

/** HaloImPlugin */
class HaloImPlugin: FlutterPlugin, MethodCallHandler {

  private lateinit var context: Context
  override fun onAttachedToEngine(flutterPluginBinding: FlutterPlugin.FlutterPluginBinding) {
    context = flutterPluginBinding.applicationContext
    IMHostApi.setUp(
      flutterPluginBinding.binaryMessenger,
      IMHostApiImpl(flutterPluginBinding, context)
    )

    //FlutterApi
    IMFlutterApiImpl.setUp(flutterPluginBinding.binaryMessenger)
    Log.d("HaloImPlugin","instance -- $this")
  }

  override fun onDetachedFromEngine(binding: FlutterPlugin.FlutterPluginBinding) {

  }

  override fun onMethodCall(call: MethodCall, result: Result) {
  }

}