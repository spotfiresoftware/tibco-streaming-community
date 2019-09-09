# uxadmin

`uxadmin` is a simple wrapper around `epadmin` to provide a nicer ux for common development workflows. 

## Simple Build & Install

Run `mvn` in the root directory to build `dist/uxadmin.jar`. Copy the jar to a reasonable location, and then run as `java -jar uxadmin.jar` or set an alias in your shell profile for `alias uxadmin='java -jar uxadmin.jar'`. See `uxadmin help` for complete help.

## Common tasks

### Running a LiveView Project, Fragment, or Application Archive

```
$ uxadmin lv-run my-application.zip /node/install/dir A.nodename
$ uxadmin lv-run my-fragment.zip /node/install/dir A.nodename
$ uxadmin lv-run my-project/ /node/install/dir A.nodename
```
Note on the last option, if the project is unmodified, the application won't be rebuilt. Ensure you have `MVN_SETTINGS` or the `-s` flag if necessary

To stop an `lv-run`, press `Ctrl-C`, and the application will be stopped, and removed (unless `-n` is specified. See `uxadmin help` for details)

### Start, Monitor, and Stop LiveView

`lv-start`, `tail` and `lv-stop` are the three underlying components that make up `lv-run`, and can be run individually  for more control. Note that `tail` (by itself, and not as part of `lv-run`) can monitor multiple nodes by supplying just the cluster name. 
```
$ uxadmin tail A.nodename
$ uxadmin tail clustername
```

### Wrapping a Fragment

If you are rapidly iterating on a fragment, and don't want to build an application archive that is empty, the `wrap` command makes a simple application from a single fragment:
```
$ uxadmin wrap my-fragment.zip # outputs to my-application.zip
$ uxadmin wrap my-fragment.zip my-custom-named-application.zip
```

## Tab Completions

The command line parser is picocli, and as such supports tab completion. Generate the completion file, for bash:
```
java -jar dist/uxadmin.jar completion bash > uxadmin.bash && chmod +x uxadmin.bash && source uxadmin.bash
```

## Native image

This is mostly pointless, but if you want a solid binary, it's easy enough to generate with GraalVM CE: https://github.com/oracle/graal/releases.
```
$ wget https://github.com/oracle/graal/releases/download/vm-19.2.0/graalvm-ce-linux-amd64-19.2.0.tar.gz
$ tar xf graalvm-ce-linux-amd64-19.2.0.tar.gz
$ cd graalvm-ce-19.2.0
$ export JAVA_HOME=$PWD
$ export PATH=$PWD/bin:$PATH
$ wget https://repo1.maven.org/maven2/info/picocli/picocli-codegen/4.0.4/picocli-codegen-4.0.4.jar
```
And then actually generate the binary:
```
$ java -cp ~/uxadmin.jar:picocli-codegen-4.0.4.jar picocli.codegen.aot.graalvm.ReflectionConfigGenerator uxadmin.UxAdmin > uxadmin.json
$ native-image -jar ~/uxadmin.jar -H:ReflectionConfigurationFiles=uxadmin.json -H:+ReportUnsupportedElementsAtRuntime --no-server
$ ./uxadmin --version
uxadmin 0.2
Picocli 4.0.4
JVM: 1.8.0_222 (Oracle Corporation Substrate VM GraalVM 19.2.0 CE)
OS: Linux 3.10.0-862.3.2.el7.x86_64 amd64
```
(Note the Substrate VM in the JVM version)

