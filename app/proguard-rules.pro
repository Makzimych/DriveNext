# ЛР1–4. Общие правила обработки кода при сборке выпуска.
# Здесь задаются собственные правила ProGuard для приложения.
# Набор подключаемых файлов правил определяется параметром
# proguardFiles в файле build.gradle.
#
# Подробнее см. документацию:
#   http://developer.android.com/guide/developing/tools/proguard.html

# Если используется WebView с JavaScript, раскомментируйте правило ниже
# и укажите полное имя класса интерфейса JavaScript
# вместо примера:
#-keepclassmembers class fqcn.of.javascript.interface.for.webview {
#   public *;
#}

# Раскомментируйте строку ниже для сохранения номеров строк
# при анализе стека ошибок.
#-keepattributes SourceFile,LineNumberTable

# При сохранении номеров строк эта настройка позволяет
# скрыть исходное имя файла.
#-renamesourcefileattribute SourceFile