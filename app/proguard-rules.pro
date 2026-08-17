# jaudiotagger resolves tag classes reflectively and ships its own logging config.
-keep class org.jaudiotagger.** { *; }
-dontwarn org.jaudiotagger.**
-dontwarn java.awt.**
-dontwarn javax.imageio.**

# NewPipeExtractor. The release build minifies and shrinks, so a missing rule here produces a debug
# build that works and a release APK that dies at runtime — verify these against a release APK.
-keep class org.schabi.newpipe.extractor.timeago.patterns.** { *; }

# Rhino executes YouTube's signature-decipher JavaScript. Kept whole: it is reached reflectively
# through the JSR-223 engine, so R8 cannot see the call graph.
-keep class org.mozilla.javascript.** { *; }
-keep class org.mozilla.javascript.engine.** { *; }
-keep class org.mozilla.classfile.ClassFileWriter
-dontwarn org.mozilla.javascript.JavaToJSONConverters
-dontwarn org.mozilla.javascript.tools.**

# Neither package exists on Android. rhino-engine references both, and these are what stop R8
# failing the build over references that are never actually taken.
-keep class javax.script.** { *; }
-dontwarn javax.script.**
-keep class jdk.dynalink.** { *; }
-dontwarn jdk.dynalink.**

# Protobuf reads these fields reflectively; renaming them breaks parsing at runtime.
-keepclassmembers class * extends com.google.protobuf.GeneratedMessageLite { <fields>; }
