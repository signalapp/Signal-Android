# ez-vcard
# https://github.com/nickel-chrome/CucumberSync/blob/master/proguard-project.txt
-dontwarn com.fasterxml.jackson.**		# Jackson JSON Processor (for jCards) not used
-dontwarn freemarker.**				# freemarker templating library (for creating hCards) not used
-dontwarn org.jsoup.**				# jsoup library (for hCard parsing) not used
-dontwarn sun.misc.Perf
-keep class ezvcard.property.** { *; }		# keep all VCard properties (created at runtime)
-keepattributes EnclosingMethod
-keepattributes InnerClasses