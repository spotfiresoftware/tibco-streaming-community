@echo on
rem Copyright 2012-2018 TIBCO Software Inc.
setlocal

java -Xmx512m -XX:+UseCompressedOops -cp "sb-java-tools.jar;tsclient_1.1.jar" %LIVEVIEW_CLIENT_JVM_ARGS% com.tibco.techsupport.tools.TSClient %*
