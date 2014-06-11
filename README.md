cjmx
====

cjmx is a command line JMX client intended to be used when graphical tools (e.g., JConsole, VisualVM) are unavailable.  Additionally, cjmx intends to be useful in scripting environments.

Getting cjmx
============

cjmx is available on Maven Central using groupId com.gihub.cjmx and artifactId cjmx_2.9.2 or cjmx_2.10.  An executable JAR is published using the `app` classifier.

 - cjmx artifacts on Maven Central: http://search.maven.org/#search%7Cga%7C1%7Cg%3A%22com.github.cjmx%22
 - Executable JAR using Scala 2.9.2: http://search.maven.org/remotecontent?filepath=com/github/cjmx/cjmx_2.9.2/1.0.0-RELEASE/cjmx_2.9.2-1.0.0-RELEASE-app.jar
 - Executable JAR using Scala 2.10: http://search.maven.org/remotecontent?filepath=com/github/cjmx/cjmx_2.10/1.0.0-RELEASE/cjmx_2.10-1.0.0-RELEASE-app.jar

Building
========

To build, run `./sbt publish-local`.  This will build target/scala-2.10/proguard/cjmx_2.10-2.0.0-SNAPSHOT.jar and install a copy to your local ivy cache.

Note: cjmx is currently cross compiled for both Scala 2.9.2 and 2.10.  The 2.10 version depends on SBT 0.13 JARs, which are not available on public Maven or Ivy repositories.
To build the 2.10 version, you must first run `sbt publish-local` from a local checkout of SBT on branch 0.13.  To only build the 2.9.2 version, run `++ 2.9.2` from the sbt
prompt before running publish-local.

Usage
=====

Launching cjmx is done via:

    java -cp $JAVA_HOME/lib/tools.jar:target/scala-2.10/proguard/cjmx_2.10-2.0.0-SNAPSHOT.jar cjmx.Main [PID]

Or, if tools.jar is on the classpath already (e.g., Apple JVM):

    java -jar target/scala-2.10/proguard/cjmx_2.10-2.0.0-SNAPSHOT.jar [PID]

If a PID is specified on the command line, cjmx will attempt to connect to the local JVM with that PID; otherwise, cjmx starts in a disconnected state.

Once cjmx starts, a prompt will appear.  Cjmx makes heavy use of tab completion, enabling exploration of the MBean tree.  For example:

    java -jar cjmx.jar 1234
    > <TAB>
    disconnect   exit         describe      names        select
    > names 'java.<TAB>
    java.lang:           java.util.logging:
    > describe -d 'java.lang:type=
    *                  <value>            ClassLoading       Compilation
    GarbageCollector   Memory             MemoryManager      MemoryPool
    OperatingSystem    Runtime            Threading
    > names 'java.lang:type=*'
    java.lang:type=ClassLoading
    java.lang:type=Compilation
    java.lang:type=Memory
    java.lang:type=OperatingSystem
    java.lang:type=Runtime
    java.lang:type=Threading
    > describe -d 'java.lang:type=Memory'
    Object name: java.lang:type=Memory
    ----------------------------------
    Description: Information on the management interface of the MBean

    Attributes:
      Verbose: boolean
        Description: Verbose
      ObjectPendingFinalizationCount: int
        Description: ObjectPendingFinalizationCount
      HeapMemoryUsage: CompositeData
        Description: HeapMemoryUsage
      NonHeapMemoryUsage: CompositeData
        Description: NonHeapMemoryUsage

    Operations:
      gc(): void
        Description: gc

    Notifications:
      javax.management.Notification
        Description: MEMORY NOTIFICATION
        Notification types:
          java.management.memory.threshold.exceeded
          java.management.memory.collection.threshold.exceeded

    > mbeans 'java.lang:type=Memory' select *
    java.lang:type=Memory
    ---------------------
      Verbose: false
      ObjectPendingFinalizationCount: 0
      HeapMemoryUsage:
        committed: 110432256
        init: 0
        max: 2130051072
        used: 63307880
      NonHeapMemoryUsage:
        committed: 140849152
        init: 24317952
        max: 318767104
        used: 121815008

Alternatively, cjmx can run a series of commands and then terminate.  This is done by specifying each command as a program argument.  For example:

    java -jar cjmx.jar 1234 "describe 'java.lang:type=Memory'" "mbeans 'java.lang:type=Memory' select *"
    Object name: java.lang:type=Memory
    ----------------------------------
    Description: Information on the management interface of the MBean

    Attributes:
      Verbose: boolean
      ObjectPendingFinalizationCount: int
      HeapMemoryUsage: CompositeData
      NonHeapMemoryUsage: CompositeData

    Operations:
      gc(): void

    Notifications:
      javax.management.Notification

    java.lang:type=Memory
    ---------------------
      Verbose: false
      ObjectPendingFinalizationCount: 0
      HeapMemoryUsage:
        committed: 110432256
        init: 0
        max: 2130051072
        used: 76943832
      NonHeapMemoryUsage:
        committed: 140980224
        init: 24317952
        max: 318767104
        used: 122049448

All commands have help information available by typing "help" in the console.

