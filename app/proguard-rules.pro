# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# If your project uses WebView with JS, uncomment the following
# and specify the fully qualified class name to the JavaScript interface
# class:
#-keepclassmembers class fqcn.of.javascript.interface.for.webview {
#   public *;
#}

# A minified stack trace with no line numbers says only that something threw
# somewhere. Keeping these costs a little size and is the difference between a
# report that can be acted on and one that cannot.
-keepattributes SourceFile,LineNumberTable
# The line numbers are worth keeping; the original file names are not, and leaving
# them in hands out the source layout.
-renamesourcefileattribute SourceFile