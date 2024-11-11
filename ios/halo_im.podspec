#
# To learn more about a Podspec see http://guides.cocoapods.org/syntax/podspec.html.
# Run `pod lib lint halo_im.podspec` to validate before publishing.
#
Pod::Spec.new do |s|
  s.name             = 'halo_im'
  s.version          = '0.0.1'
  s.summary          = 'A new Flutter project.'
  s.description      = <<-DESC
A new Flutter project.
                       DESC
  s.homepage         = 'http://example.com'
  s.license          = { :file => '../LICENSE' }
  s.author           = { 'Your Company' => 'email@example.com' }
  s.source           = { :path => '.' }
  s.source_files = 'Classes/**/*'
  s.dependency 'Flutter'
  s.platform = :ios, '14.0'
  s.static_framework = true
  s.pod_target_xcconfig = {
        'DEFINES_MODULE' => 'YES',
        'SWIFT_COMPILATION_MODE' => 'wholemodule'
  }

  # Flutter.framework does not contain a i386 slice.
  s.pod_target_xcconfig = { 'DEFINES_MODULE' => 'YES', 'EXCLUDED_ARCHS[sdk=iphonesimulator*]' => 'i386' }
  s.swift_version = '5.4'
  s.dependency 'secp256k1.swift'
  s.dependency "MessagePacker"
  s.dependency "XMTP"
end
