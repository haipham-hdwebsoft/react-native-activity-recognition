
Pod::Spec.new do |s|
  s.name         = "RNActivityRecognition"
  s.version      = "1.0.0"
  s.summary      = "RNActivityRecognition"
  s.description  = <<-DESC
                  RNActivityRecognition
                   DESC
  s.homepage     = "https://github.com/author/RNActivityRecognition.git"
  s.license      = "MIT"
  # s.license      = { :type => "MIT", :file => "FILE_LICENSE" }
  s.author             = { "author" => "author@domain.cn" }
  s.platform     = :ios, "7.0"
  s.source       = { :git => "https://github.com/author/RNActivityRecognition.git", :tag => "master" }
  s.source_files  = "ios/RNActivityRecognition.h", "ios/RNActivityRecognition.m"
  s.requires_arc = true


  s.dependency "React"
  #s.dependency "others"

end

  