# LSPosed entry points must survive shrinking/renaming: they are loaded by name
# from assets/xposed_init.
-keep class com.example.parallelview.Hook { *; }
-keep class com.example.parallelview.EmbeddingExtHook { *; }
-keep class com.example.parallelview.Targets { *; }
