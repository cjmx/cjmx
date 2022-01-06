
Displays the names of all MBeans whose object names match the specified object name pattern.

    names ['object-name-pattern' [where query-expression]]
    mbeans 'object-name-pattern' [where query-expression] names

 - object-name-pattern - object name pattern conformant to pattern described in https://docs.oracle.com/javase/7/docs/api/javax/management/ObjectName.html
 - query-expression - expression that limits the MBeans selected.  See "help query" for more information.

Examples:

    > names 'java.lang:*'
    > names 'java.lang:type=Memory,*'
    > names 'java.*:*'
    > names '*:*'
    > names '*:*' where ErrorCount > 0
    > mbeans '*:*' where ErrorCount > 0 names

