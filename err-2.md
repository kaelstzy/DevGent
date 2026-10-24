#err.6
FAILURE: Build failed with an exception.
> Task :app:compileDebugKotlin FAILED
* What went wrong:
gradle/actions: Writing build results to /home/runner/work/_temp/.gradle-actions/build-results/__run-1792842376162.json
Execution failed for task ':app:compileDebugKotlin' (registered by plugin 'com.android.internal.application').
> A failure occurred while executing org.jetbrains.kotlin.compilerRunner.btapi.BuildToolsApiCompilationWork
   > Kotlin compiler: UNRESOLVED_IMPORT
[Incubating] Problems report is available at: file:///home/runner/work/DevGent/DevGent/build/reports/problems/problems-report.html
       Kotlin compiler error
         Unresolved reference 'ModalDrawer'.
         Location: /home/runner/work/DevGent/DevGent/app/src/main/java/com/kaelzyy/devgent/ui/ChatScreen.kt:49
* Try:
> Run with --info or --debug option to get more log output.
> Run with --scan to get full insights from a Build Scan (powered by Develocity).
> Get more help at https://help.gradle.org.
* Exception is:
org.gradle.api.tasks.TaskExecutionException: Execution failed for task ':app:compileDebugKotlin' (registered by plugin 'com.android.internal.application').
	at 
#err.7

FAILURE: Build failed with an exception.

* What went wrong:
Execution failed for task ':app:compileDebugKotlin' (registered by plugin 'com.android.internal.application').

> A failure occurred while executing org.jetbrains.kotlin.compilerRunner.btapi.BuildToolsApiCompilationWork
Deprecated Gradle features were used in this build, making it incompatible with Gradle 10.

   > Kotlin compiler: UNRESOLVED_REFERENCE
You can use '--warning-mode all' to show the individual deprecation warnings and determine if they come from your own scripts or plugins.
       Kotlin compiler error

         Unresolved reference 'removeRange' on receiver of type 'MutableList<ChatMessage>'.
For more on this, please refer to https://docs.gradle.org/9.8.2/userguide/command_line_interface.html#sec:command_line_warnings in the Gradle documentation.
         Location: /home/runner/work/DevGent/DevGent/app/src/main/java/com/kaelzyy/devgent/agent/AgentSession.kt:261
30 actionable tasks: 19 executed, 11 from cache

* Try:
> Run with --info or --debug option to get more log output.
> Run with --scan to get full insights from a Build Scan (powered by Develocity).
> Get more help at https://help.gradle.org.

* Exception is:
org.gradle.api.tasks.TaskExecutionException: Execution failed for task ':app:compileDebugKotlin' (registered by plugin 'com.android.internal.application').
	at 
