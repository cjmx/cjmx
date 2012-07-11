
Gets attribute values of MBeans that match the specified object name pattern and query expression.

    select [* | projection] from 'object-name-pattern' [where query-expression]
    mbeans from 'object-name-pattern' [where query-expression] select [* | projection]

 - object-name-pattern - object name pattern conformant to pattern described in http://docs.oracle.com/javase/7/docs/api/javax/management/ObjectName.html
 - query-expression - expression that limits the MBeans selected.  See "help query" for more information.
 - projection - specification of the attribute values to select.  Projections take the following form:

    projection := expression [as 'name'][, expression [as 'name']]*
    expression :=
        expression * expression |
        expression / expression |
        expression + expression |
        expression - expression |
        (expression) |
        value
    value := value-ref | literal-value
    value-ref := name[.name]*
    name := string | 'string'
    literal-value := number | true | false | 'string'

Examples:

    > select * from 'java.lang:*'
    > select HeapMemoryUsage from 'java.lang:type=Memory'
    > mbeans from 'java.lang:type=Memory' select HeapMemoryUsage as Heap
    > mbeans from 'java.lang:type=Memory' select 'HeapMemoryUsage' as 'Heap'
    > mbeans from 'java.lang:type=Memory' select HeapMemoryUsage.used / HeapMemoryUsage.max * 100 as 'Heap Used Percentage'
    > mbeans from '*:*' where ErrorCount > 0 select *

