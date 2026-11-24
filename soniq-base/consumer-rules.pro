# Consumer rules shipped with :soniq-base.
#
# The library uses only framework classes and keeps no data through reflection,
# so nothing needs to be preserved beyond readable stack traces. Apps that
# minify can add their own provider-specific rules.
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile
