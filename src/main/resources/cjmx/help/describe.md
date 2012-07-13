
Describes the MBeans that match the specified object name pattern and query expression.

    describe [-d] 'object-name-pattern' [where query-expression]
    mbeans 'object-name-pattern' [where query-expression] describe -d

 - -d - enables detailed output

Examples:

    > describe 'java.lang:type=Memory'
    > describe -d 'java.lang:type=Memory'
    > describe -d '*:*' where ErrorCount > 0
    > mbeans 'java.lang:type=Memory' describe -d
    > mbeans '*:*' where ErrorCount > 0 describe -d

