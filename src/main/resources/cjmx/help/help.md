
Cjmx has two main states -- connected to a JVM and disconnected.  The commands available are sensitive to the current connectivity state.

## Global Commands

 - help [topic] - Gets help for the specified topic
 - exit - Exits cjmx

## Disconnected Commands

When cjmx is not connected to a JVM, the following commands are available:

 - jps - Lists all local Java Virtual Machines
 - connect - Connects to a local Java Virtual Machine

## Connected Commands

Once cjmx is connected to a JVM, the following commands are available:

 - names - List MBean names
 - inspect - Inspect MBean information
 - select - Get MBean attributes
 - invoke - Invoke MBean operations

Each action has a prefix command and a postfix command.  The postfix form is preferred as it offers better tab completion.  Both forms are documented in the detailed help for the command.

