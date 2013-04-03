
Invokes the operation on the MBeans that match the specified object name pattern and query expression.

    invoke operationName(arg[,arg]*) on 'object-name-pattern' [where query-expression]
    mbeans 'object-name-pattern' [where query-expression] invoke operationName(arg[,arg]*)

 - operationName - name of operation to invoke
 - arg - argument to pass

Examples:

    > invoke gc() on 'java.lang:type=Memory'
    > mbeans 'java.lang:type=Memory' invoke gc()
    > mbeans 'java.lang:type=Threading' invoke findDeadlockedThreads()
    > mbeans 'java.lang:type=Threading' invoke dumpAllThreads(false, false)
    > mbeans 'java.lang:type=Threading' invoke getThreadInfo(1234L)

