import Flutter
import UIKit

public class HaloImPlugin: NSObject, FlutterPlugin {
  public static func register(with registrar: FlutterPluginRegistrar) {
      let binaryMessenger = registrar.messenger();
      let messageApi = IMFlutterApiImplementation(binaryMessenger: binaryMessenger)
      IMHostApiSetup.setUp(binaryMessenger: binaryMessenger, api: IMHostApiImplementation(messageApi: messageApi))
  }
}
