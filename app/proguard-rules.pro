# Сохраняем модели данных для корректной работы Firebase и GSON
-keep class com.example.basehome.Address { *; }
-keep class com.example.basehome.BusinessAddress { *; }
-keep class com.example.basehome.GoogleAddress { *; }
-keep class com.example.basehome.Comment { *; }
-keep class com.example.basehome.Entrance { *; }
-keep class com.example.basehome.BusinessInfoEntry { *; }

# Правила для GSON
-keepattributes Signature
-keepattributes *Annotation*
-dontwarn sun.misc.**
-keep class com.google.gson.** { *; }

# Правила для Firebase
-keepattributes *Annotation*
-keepclassmembers class * {
  @com.google.firebase.database.PropertyName <fields>;
}
-keep class com.google.firebase.** { *; }

# Сохраняем системные атрибуты для отладки (если нужно будет читать логи ошибок)
-keepattributes SourceFile,LineNumberTable
