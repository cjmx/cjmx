cjmx
====

cjmx is a command line JMX client intended to be used when graphical tools (e.g., JConsole, VisualVM) are unavailable.  Additionally, cjmx intends to be useful in scripting environments.

Usage
=====

Launching cjmx is done via:

    java -jar cjmx.jar [PID]

If a PID is specified on the command line, cjmx will attempt to connect to the local JVM with that PID; otherwise, cjmx starts in a disconnected state.

Once cjmx starts, a prompt will appear.  Cjmx makes heavy use of tab completion, enabling exploration of the MBean tree.  For example:

    java -jar cjmx.jar 1234
    > <TAB>
    disconnect   exit         inspect      names        select
    > names 'java.<TAB>
    java.lang:           java.util.logging:
    > inspect -d 'java.lang:type=
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
    > inspect -d 'java.lang:type=Memory'
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

    > mbeans from 'java.lang:type=Memory' select *
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

    java -jar cjmx.jar 1234 "inspect 'java.lang:type=Memory'" "mbeans from 'java.lang:type=Memory' select *"
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

Commands
========

Cjmx has two main states -- connected to a JVM and disconnected.  The commands available are sensitive to the current connectivity state.

## Disconnected Commands

### jps

jps takes no aruments and when invoked, prints the VM identifier and description of all locally running JVMs.

### connect

    connect [-q] VMID

Connects cjmx to the local Java Virtual Machine with the specified VM ID (as reported by jps).

 - -q - Disables informational output upon successful connection (useful when scripting)
 - VMID - virtual machine identifier

## Connected Commands

### names

    names 'object-name-pattern' [where query-expression]

Displays the names of all MBeans whose object names match the specified object name pattern.

 - object-name-pattern - object name pattern conformant to pattern described in http://docs.oracle.com/javase/7/docs/api/javax/management/ObjectName.html
 - query-expression - expression that limits the MBeans selected.  See [query expression documentation](#Query Expressions)

Examples:

    > names 'java.lang:*'
    > names 'java.lang:type=Memory,*'
    > names 'java.*:*'
    > names '*:*'
    > names '*:*' where ErrorCount > 0

### Query Expressions


