/*
* Copyright © 2019. Cloud Software Group, Inc.
* This file is subject to the license terms contained
* in the license file that is distributed with this file.
*/

package uxadmin;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.URI;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.Stack;
import java.util.concurrent.Callable;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import picocli.AutoComplete;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.HelpCommand;
import picocli.CommandLine.IParameterConsumer;
import picocli.CommandLine.Mixin;
import picocli.CommandLine.Model.ArgSpec;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;
import picocli.CommandLine.TypeConversionException;

@Command(name="uxadmin", mixinStandardHelpOptions = true, description = "Enables easier running of epadmin", subcommands = {
		UxAdmin.Tail.class,
		UxAdmin.LvRun.class,
		UxAdmin.LvStart.class,
		UxAdmin.LvStop.class,
		UxAdmin.Wrap.class,
		HelpCommand.class, 
},version = {
	    "uxadmin 0.2",
	    "Picocli " + picocli.CommandLine.VERSION,
	    "JVM: ${java.version} (${java.vendor} ${java.vm.name} ${java.vm.version})",
	    "OS: ${os.name} ${os.version} ${os.arch}"})
public class UxAdmin implements Runnable {
	
	@Command(name = "tail", description="Tails the given node or service name, updating when nodes join or leave")
	public static class Tail implements Callable<Void>
	{
		
		@Parameters(index="0", arity="1", description="Service name or cluster name")
		private String servicename;
		
		@Option(names = {"-c", "--color"}, description = "Colorize liveview", arity="0..1", paramLabel="bool")
		private boolean colorize = false;

		@Option(names = {"-d", "--discovery"}, description = "Use non-default discovery port", arity="0..1", paramLabel = "port" )
		private Integer epport = null;

		@Option(names = {"-t", "--title"}, description = "Title String. Replaces %%d with port number", arity="0..1", paramLabel="string" )
		private String title="";
		
		public boolean exitOnStart = false;

		private static String loaded ="*** All tables have been loaded";
		private static String listening = "LiveView Web service listening";
		private static Pattern portpattern = Pattern.compile("^.*port (\\d+).*$");

		private Thread CopyStreamFiltered(Process p, InputStream is, String suffix, boolean coloring) {
			Thread t = new Thread(() -> {
				boolean done = false;
				try (BufferedReader r = new BufferedReader(new InputStreamReader(is)))
				{
					Pattern pat = Pattern.compile("^\\[([^\\]]+)\\."+suffix+"] ([^ :\t]+):");
					
					while (p.isAlive() || r.ready())
					{
						String line = r.readLine();
						if (line == null)
							continue;
						line = line.trim(); // TODO: trim?
						Matcher match = pat.matcher(line);
						line = match.replaceFirst("$1: ");
						if (coloring && !done)
						{
							if (line.contains(listening))
							{
								line = "\u001b[1m\u001b[4m\u001b[38;5;15m" + line + "\u001b[0m";
								if (!title.isEmpty())
								{
									Matcher m = portpattern.matcher(line);
									if (m.matches())
									{
										int port = Integer.parseInt(m.group(1));
										setTitle(String.format(title, port));
									}
								}
							}
							else if (line.contains(loaded))
							{
								line = "\u001b[0;30;102m" + line + "\u001b[0m"; 
								done = true;
								if (exitOnStart)
								{
									System.out.println(line);
									p.destroy();
									return;
								}
							}
							else 
							{
								line = "\u001b[38;5;11m" + line + "\u001b[0m"; 
							}
						}
						System.out.println(line); // TODO: more coloring?
					}
				} catch (IOException e) {
					if (retry == false && !done && !exitOnStart)
						e.printStackTrace();
				}
			});
			t.start();
			return t;
		}
		volatile boolean retry = true;
		
		@Override
		public Void call() throws Exception {
			verifyEnv();
			Process[] p = new Process[] {null};
			String suffix = servicename.contains(".") ? servicename.substring(servicename.lastIndexOf('.')+1) : servicename;
			if (!servicename.contains(".")) // watch mode
			{
				Thread t = new Thread(()-> {
					watchr(suffix, () -> {
						retry = true;
						System.out.println("+++++++++++++++++++++++++ A node was added +++++++++++++++++++++++++");
						if (p[0] != null)
							p[0].destroyForcibly();
					});
				});
				t.setDaemon(true);
				t.start();
				Thread.sleep(3000); // wait for monitor to catch
			}
			while (retry)
			{
				retry = false;
				ProcessBuilder pb = new ProcessBuilder("epadmin", 
						"--discoveryport", epport == null ? "54321" : Integer.toString(epport),
								"--servicename", servicename, "tail", "logging");
				p[0] = pb.start();
				Thread t1 = CopyStreamFiltered(p[0], p[0].getInputStream(), suffix, colorize);
				Thread t2 = CopyStreamFiltered(p[0], p[0].getErrorStream(), suffix, false);
				p[0].waitFor();
				t1.join();
				t2.join();

			}
			return null;
		}
		
		public void watchr(String cluster, Runnable onadd)
		{
			try {
				ProcessBuilder pb = new ProcessBuilder("epadmin", 
						"--discoveryport", epport == null ? "54321" : Integer.toString(epport),
								"browse", "services", "--servicename", cluster, "--servicetype", "node", "--showproperty", "NodeState");
				Process p = pb.start();
				InputStream is = p.getInputStream();
				try (BufferedReader r = new BufferedReader(new InputStreamReader(is)))
				{
					
					boolean addStateSearch = true;
					
					while (p.isAlive() || r.ready())
					{
						String line = r.readLine();
						if (line == null)
							continue;
						line = line.trim(); // TODO: trim?
						if (line.contains("Browsed State"))
						{
							addStateSearch = (line.contains("Added") || line.contains("Changed"));
						}
						else if (addStateSearch &&line.contains(" = running"))
						{
							onadd.run();
							addStateSearch = false;
						}
					
					}
				} 
			} catch (IOException e1) {
				// TODO Auto-generated catch block
				e1.printStackTrace();
			}
		}
	}
	
	// Implements the logic to support having port and substitutions both positional
	public static class PortDetector implements IParameterConsumer
	{
		public PortDetector() {
			// graal aot doesn't like default ctors 
		}

		@Override
		public void consumeParameters(Stack<String> args, ArgSpec argSpec, CommandSpec commandSpec) {
			if (args.size() == 0 || args.peek().contains("=") || args.peek().startsWith("-")) // if not a int, try subst variables
				return;
			String value = args.pop();
			Integer v;
			try
			{
				v = Integer.parseInt(value);
			}
			catch (NumberFormatException n)
			{
				throw new TypeConversionException(String.format("'%s' is not a %s", value, "int"));
			}
			argSpec.setValue(v);
		}
	}
	
	public static class LvRunOptions {
		@Parameters(index="0", arity="1", description="Application Archive to install")
		private File appArchive;
		
		@Parameters(index="1", arity="1", description="Node install directory")
		private File nodeDir;
		
		@Parameters(index="2", arity="1", description="Full node name")
		private String nodeName;
		
		@Parameters(index="3", arity="0..1", parameterConsumer = PortDetector.class,  description="LVPORT substition value, with SBPORT 80 below")
		private Integer lvport = null;
		
		@Parameters(index="3..*", arity="0..*",  description="Any other substitution variables, in KEY=value format.", paramLabel = "VARIABLE=value")
		private Map<String, String> substitutions = new HashMap<>();
		
		@Option(names = {"-d", "--discovery"}, description = "Use non-default discovery port", paramLabel="port")
		private int epport = 54321;

		@Option(names = {"-f", "--fragment"}, description = "Archive is a fragment, not an Application (overrides autodetection)", arity="0..1", paramLabel="bool")
		private boolean isFragment = false;

		@Option(names = {"-s", "--mvn-settings"}, description = "Maven settings file, oterwise retreived from the MVN_SETTINGS envvar, for building flat projects", paramLabel="file")
		private File mvnSettings;
		// TODO: batch mode, maven verbosity
		
		@Option(names = {"-g", "--debug-port"}, description = "Enables java debugger on the given port with the DEBUG_ARG substitution variable", paramLabel="port")
		private Integer debugPort = null;
	}

	public static class LvRemoveOptions {
		@Option(names = {"-n", "--no-rm"}, description = "Don't remove, leave on disk", arity="0..1", paramLabel="bool")
		private boolean noRemove = false;
	}
	
	public static class LvStopOptions {
		@Mixin
		private LvRemoveOptions rmOpts = new LvRemoveOptions();
		
		@Parameters(index="0", arity="1", description="Node install directory")
		private File nodeDir;
		
		@Parameters(index="1", arity="1", description="Full node name")
		private String nodeName;

		@Option(names = {"-d", "--discovery"}, description = "Use non-default discovery port", paramLabel="port")
		private int epport = 54321;
		
		public LvRunOptions makeRunOpts()
		{
			LvRunOptions ops = new LvRunOptions();
			ops.nodeDir = nodeDir;
			ops.nodeName = nodeName;
			ops.epport = epport;
			ops.isFragment = false;
			return ops;
		}
	}

	@Command(name = "lv-run", description="Run an lv app archive, stopping on ctrl-c", footer = {
			"Note that setting the lvport requires the project to have the subsitution "+
			"variables LVPORT and SBPORT in the appropriate client api listener configuration, "+
			"and the debug port requires the project to have a substitution variable "+
			"DEBUG_ARG in the jvmArgs configuration"
	}, footerHeading = "%n")
	public static class LvRun extends LvRunUtils implements Callable<Void>
	{
		@Mixin
		LvRunOptions mixedOptions;
		@Mixin
		LvRemoveOptions removeMixedOptions;

		@Override
		public Void call() throws Exception {
			options = mixedOptions; // set the parent options
			stopOpts = removeMixedOptions;
			
			tryPackaging();
			
			cleanup();
			
			setTitle(options.nodeName);
			
			if (!install())
				return null;

			Runtime.getRuntime().addShutdownHook(new Thread(this::remove));
			
			if (!start())
				return null;
			
			done = true;
			tailLogs(false);
			return null;
		}
	}


	@Command(name = "lv-start", description="Starts an lv app, then returns when it has started", footer = {
			"Note that setting the lvport requires the project to have the subsitution "+
			"variables LVPORT and SBPORT in the appropriate client api listener configuration, "+
			"and the debug port requires the project to have a substitution variable "+
			"DEBUG_ARG in the jvmArgs configuration"
	}, footerHeading = "%n")
	public static class LvStart extends LvRunUtils implements Callable<Void>
	{
		@Mixin
		LvRunOptions mixedOptions;

		@Override
		public Void call() throws Exception {
			options = mixedOptions; // set the parent options
			
			tryPackaging();
			
			cleanup();
			
			setTitle(options.nodeName);
			
			if (!install())
				return null;
			
			if (!start())
				return null;
			
			done = true;
			tailLogs(true); // TODO: stop when partialkly done
			return null;
		}
	}


	@Command(name = "lv-stop", description="Stops and removes a running liveview")
	public static class LvStop extends LvRunUtils implements Callable<Void>
	{
		@Mixin
		LvStopOptions mixedOptions;

		@Override
		public Void call() throws Exception {
			options = mixedOptions.makeRunOpts(); // set the parent options
			stopOpts = mixedOptions.rmOpts;
			
			done = true;
			remove();
			
			if (!stopOpts.noRemove)
				cleanup();
			
			return null;
		}
	}
	
	/**
	 * Common utils for all live view launches
	 */
	public static class LvRunUtils
	{
		LvRunOptions options;
		LvRemoveOptions stopOpts;

		volatile boolean done = false;

		void tailLogs(boolean exitOnStart) throws Exception {
			Tail t = new Tail();
			t.servicename = options.nodeName;
			t.colorize = true;
			t.title = options.nodeName.substring(0, options.nodeName.indexOf(".")) + ":%d";
			t.epport = options.epport;
			t.exitOnStart = exitOnStart;
			t.call();
		}

		boolean start() throws IOException, InterruptedException {
			return 0 == RunToCompletion("epadmin", 
					"--discoveryport", Integer.toString(options.epport),
							"--servicename", options.nodeName, "start", "node");
		}

		void remove() {
			if (options.isFragment)
				options.appArchive.delete(); // we built this temp, discard it
			
			if (done)
			{
				try {
				RunToCompletion("epadmin",
						"--discoveryport", Integer.toString(options.epport),
								"--servicename", options.nodeName, "stop", "node");
				} catch (IOException | InterruptedException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
			}
			if (!stopOpts.noRemove)
			{
				try {
					RunToCompletion("epadmin",
							"--discoveryport", Integer.toString(options.epport),
									"--servicename", options.nodeName, "remove", "node");
				} catch (IOException | InterruptedException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
			}
		}

		boolean install() throws IOException, InterruptedException {
			int r = RunToCompletion("epadmin", 
					"install", "node",
					"--discoveryport", Integer.toString(options.epport),
					"--nodedirectory", options.nodeDir.getCanonicalPath(),
					"--nodename", options.nodeName,
					"--application", options.appArchive.getCanonicalPath(),
					"--substitutions", getSubs());
			return r == 0;
		}

		void cleanup() {
			File target = new File(options.nodeDir, options.nodeName);
			if (target.exists())
			{
				System.out.println("Directory exists, removing...");
				try {
					RunToCompletion("epadmin", "remove", "node", "--installpath", target.getCanonicalPath());
				} catch (IOException | InterruptedException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
			}
		}

		void tryPackaging() throws IOException, InterruptedException {
			if (options.appArchive.isDirectory() && new File(options.appArchive, "pom.xml").exists())
			{
				File output = MavenUtils.findCachedTarget(options.appArchive);
				if (output != null)
				{
					System.out.println("Detected flat project with cached fragment " + output.getName());
					options.isFragment = true;
					options.appArchive = output;
				}
				else
				{
					System.out.println("Detected flat project. Building with mvn...");
					File settings = options.mvnSettings;
					if (settings == null)
					{
						String sf = System.getenv("MVN_SETTINGS");
						if (sf!= null && !sf.isEmpty())
						{
							settings = new File(sf);
						}
					}
					options.appArchive = MavenUtils.buildProject(options.appArchive, settings);
				}
			}
			if (options.isFragment || options.appArchive.getAbsolutePath().endsWith("-fragment.zip"))
			{
				System.out.println("Fragment detected, building default application to wrap it");
				options.appArchive = packageFragment(options.nodeDir, options.appArchive);
				options.appArchive.deleteOnExit();
				options.isFragment = true;
			}
		}

		String getSubs()
		{
			StringBuilder sb = new StringBuilder();
			sb.append("ZZLAZYCODEZZ=999");
			this.options.substitutions.forEach( (k, v) -> {
				sb.append(',');
				sb.append(k);
				sb.append('=');
				sb.append(v); // TODO: check for commas?
			});
			if (options.lvport != null)
			{
				sb.append(',');
				sb.append("LVPORT=");
				sb.append(options.lvport);
				sb.append(',');
				sb.append("SBPORT=");
				sb.append(options.lvport-80);
			}
			if (options.debugPort != null)
			{
				sb.append(",DEBUG_ARG=-agentlib:jdwp=transport=dt_socket,server=y,suspend=y,address=");
				sb.append((int)options.debugPort);
			}
			return sb.toString();
		}
		
	}
	

	@Command(name = "wrap", description="Package with defaults a fragment into an app archive")
	public static class Wrap implements Callable<Void>
	{
		@Parameters(index="0", arity="1", description="Fragment to wrap")
		private File fragment;
		
		@Parameters(index="1", arity="0..1", description="Application Archive to save to")
		private File appArchive = null;

		@Override
		public Void call() throws Exception {
			if (appArchive == null)
			{
				appArchive = new File(fragment.getAbsolutePath().replaceFirst("\\-ep\\-[a-z]+\\-fragment.zip$", "") + "-ep-application.zip");
			}
			File tmp = packageFragment(appArchive.getParentFile(), fragment);
			tmp.renameTo(appArchive);
			return null;
		}
		
	}
	
	private static File packageFragment(File targetFolder, File fragment) throws IOException
	{
		Map<String, String> env = new HashMap<>(); 
		env.put("create", "true");

		targetFolder.mkdirs();
		Path out = Paths.get(targetFolder.getAbsolutePath(), "tmp-app-" + new Random().nextInt() + ".zip");
		URI ui = URI.create("jar:" + out.toUri().toString());

		try (FileSystem zfs = FileSystems.newFileSystem(ui, env))
		{
		    Files.copy(Paths.get(fragment.getAbsolutePath()),
		    		zfs.getPath("/" + fragment.getAbsoluteFile().getName()),
		    		StandardCopyOption.REPLACE_EXISTING); 
		    // write the manifest
		    String mfPath = out.toAbsolutePath().toString() + ".mf";
		    try (PrintWriter mf = new PrintWriter(mfPath))
		    {
		    	mf.print(packageFragmentManifest(fragment));
		    }
		    Path mfNio = Paths.get(mfPath);

		    Files.createDirectories(zfs.getPath("/META-INF"));
		    Files.copy(mfNio,
		    		zfs.getPath("/META-INF/MANIFEST.MF"),
		    		StandardCopyOption.REPLACE_EXISTING);
		    
		    Files.delete(mfNio);
		    
		}
		return out.toFile();
	}
	
	
	private static String packageFragmentManifest(File fragment)
	{
		StringBuilder sb = new StringBuilder(
				"Manifest-Version: 1.0\n" + 
				"Package-Version: 0.0.1-SNAPSHOT\n" + 
				"TIBCO-EP-Fragment-Identifier: uxadmin.AutoPackagedApp\n" + 
				"Package-Title: UxAdminAutoApp\n");
		String begin = "TIBCO-EP-Fragment-List:";
		sb.append(begin);
		int beginlen = begin.length();
		int index = 0;
		String name = fragment.getAbsoluteFile().getName();
		while (true)
		{
			if (name.length() - index + beginlen <= 70)
			{
				sb.append(" ");
				sb.append(name.substring(index));
				sb.append("\n");
				break;
			}
			else
			{
				String app = name.substring(index, index + 69-beginlen);
				index += app.length();
				beginlen = 0;
				sb.append(" ");
				sb.append(app);
				sb.append("\n");
			}
		}
		
		sb.append("Archiver-Version: Plexus Archiver\n" + 
				"Built-By: uxadmin\n" + 
				"TIBCO-EP-Fragment-Type: ep-application\n" + 
				"TIBCO-EP-Fragment-Format-Version: 2\n" + 
				"Build-Jdk: 1.8.0_201\n");
		return sb.toString();
	}
	
	private static int RunToCompletion(String... args) throws IOException, InterruptedException
	{
		ProcessBuilder pb = new ProcessBuilder(args);
		Process p = pb.start();
		Thread t1 = CopyStream(p, p.getInputStream());
		Thread t2 = CopyStream(p, p.getErrorStream());
		p.waitFor();
		t1.join();
		t2.join();
		return p.exitValue();
	}
	
	private static Thread CopyStream(Process p, InputStream is) {
		Thread t = new Thread(() -> {
			try (BufferedReader r = new BufferedReader(new InputStreamReader(is)))
			{
				while (p.isAlive() || r.ready())
				{
					String line = r.readLine();
					if (line == null)
						continue;
					System.out.println(line.trim());
				}
			} catch (IOException e) {
				e.printStackTrace();
			}
		});
		t.start();
		return t;
	}
	
	
	CommandLine cl;

	public static void main(String[] args) throws Exception {
		UxAdmin ux = new UxAdmin();
		if (args[0].equals("completion"))
		{
			
			new CommandLine(new Generator()).execute(args);
		}
		else
		{
			CommandLine cl = new CommandLine(ux);
			ux.cl = cl;
			cl.execute(args);
		}
	}
	
	private static Boolean isTmux = null;
	
	private static void setTitle(String title)
	{
		System.out.print("\u001b]0;"+title+"\u001b\\");// normal + screen/tmux pane
		if (isTmux == null)
		{
			isTmux = (null != System.getenv("TMUX_PANE"));
		}
		if (isTmux)
			System.out.print("\u001bk"+title+"\u001b\\"); // screen/tmux window
		System.out.flush();
	}


	private static void verifyEnv() {
		String ep = System.getenv("TIBCO_EP_HOME");
		String jj = System.getenv("JAVA_HOME");
		if ((ep == null || ep.isEmpty() || !new File(ep).exists()) || (jj == null || jj.isEmpty() || !new File(jj).exists()))
		{
			System.out.println("ENV set incorrectly, use:");
			System.out.println("export TIBCO_EP_HOME=???????????????????/tibco/sb-cep/10.5/\n" + 
					"export JAVA_HOME=$TIBCO_EP_HOME/jdk\n" + 
					"export PATH=\"$TIBCO_EP_HOME/distrib/tibco/bin:$TIBCO_EP_HOME/bin:$JAVA_HOME/bin:$PATH\"\n");
			System.exit(-2);
		}
	}
	
	@Override
	public void run() {
		cl.usage(System.out);
	}
	
	@Command(name="completion")
	public static class Generator implements Runnable
	{
		@Parameters(index="0", arity="1", description="capture")
		private String compl;
		
		@Parameters(index="1", arity="0..1", description="Type of completion")
		private CompletionType type = CompletionType.bash;
		
		public static enum CompletionType
		{
			bash, zsh
		}

	    @Override
	    public void run() {
	    	if (type == CompletionType.bash)
	    		System.out.println(AutoComplete.bash("uxadmin", new CommandLine(new UxAdmin())));
	    	//else
	    	//	System.out.println(AutoComplete.zsh("uxadmin", new CommandLine(new UxAdmin())));
	    }
	}

}
