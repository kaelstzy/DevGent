What went wrong:
Execution failed for task ':app:compileDebugKotlin' (registered by plugin 'com.android.internal.application').
> A failure occurred while executing org.jetbrains.kotlin.compilerRunner.btapi.BuildToolsApiCompilationWork
   > Kotlin compiler: UNRESOLVED_REFERENCE
       Kotlin compiler error
         Unresolved reference 'tool' on receiver of type 'ToolCall'.
Deprecated Gradle features were used in this build, making it incompatible with Gradle 10.
         Location: /home/runner/work/DevGent/DevGent/app/src/main/java/com/kaelzyy/devgent/agent/AgentSession.kt:429
You can use '--warning-mode all' to show the individual deprecation warnings and determine if they come from your own scripts or plugins.
* Try:
For more on this, please refer to https://docs.gradle.org/9.8.2/userguide/command_line_interface.html#sec:command_line_warnings in the Gradle documentation.
30 actionable tasks: 20 executed, 10 from cache
> Run with --info or --debug option to get more log output.
> Run with --scan to get full insights from a Build Scan (powered by Develocity).
> Get more help at https://help.gradle.org.
* Exception is:
org.gradle.api.tasks.TaskExecutionException: Execution failed for task ':app:compileDebugKotlin' (registered by plugin 'com.android.internal.application').
