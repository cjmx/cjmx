
Repeatedly gets attribute values of MBeans that match the specified object name pattern and query expression.

    sample [* | projection] from 'object-name-pattern' [where query-expression] [every m seconds] [for n seconds]
    mbeans 'object-name-pattern' [where query-expression] sample [* | projection] [every m seconds] [for n seconds]

 - object-name-pattern - object name pattern conformant to pattern described in https://docs.oracle.com/javase/8/docs/api/javax/management/ObjectName.html
 - query-expression - expression that limits the MBeans sampled.  See "help query" for more information.
 - projection - specification of the attribute values to sample.  Projections use the same format as those used in select.  See "help select" for more information.
 - m - number of seconds between samplings (defaults to 1).
 - n - number of seconds to perform samplings (defaults to infinite).

Sampling can be cancelled by hitting the enter key during sampling.

Examples:

    > sample HeapMemoryUsage.used / HeapMemoryUsage.max * 100 as 'Heap Used Percentage' from 'java.lang:type=Memory' every 1 second for 10 seconds
    > mbeans 'java.lang:type=Memory' sample HeapMemoryUsage.used / HeapMemoryUsage.max * 100 as 'Heap Used Percentage'
    > mbeans 'java.lang:type=Memory' sample HeapMemoryUsage.used / HeapMemoryUsage.max * 100 as 'Heap Used Percentage' every 1 second for 10 seconds

