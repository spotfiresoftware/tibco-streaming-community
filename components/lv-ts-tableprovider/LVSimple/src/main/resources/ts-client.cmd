@echo off
rem Copyright 2012-2018 TIBCO Software Inc.
setlocal

rem - sb-java-tools.jar and tsclient_1.1.jar are assumed to be in the same directory with this command shell
set this_bin=%~dp0

java -Xmx512m -XX:+UseCompressedOops -cp "%this_bin%sb-java-tools.jar;%this_bin%tsclient_1.1.jar" %LIVEVIEW_CLIENT_JVM_ARGS% com.tibco.techsupport.tools.TSClient %*
