# ez-vcard
-keep,includedescriptorclasses class ezvcard.property.** { *; }
-keep enum ezvcard.VCardVersion { *; }
-dontwarn ezvcard.io.json.**
-dontwarn freemarker.**
-keep class ezvcard.parameter.** {
    <init>(...);
}

# helpers/PrivateExtras is persisted as JSON through Gson, which reads field names by reflection: keep
# the classes and their field names stable across builds, or an update could not read the stored file.
-keep class org.fossify.contacts.helpers.PrivateExtras$Extra { *; }
-keep class org.fossify.contacts.helpers.PrivateExtras$StoredRow { *; }
-keep class org.fossify.contacts.helpers.PrivateExtras$** { *; }
-keepattributes Signature
-keep class * extends com.google.gson.reflect.TypeToken
