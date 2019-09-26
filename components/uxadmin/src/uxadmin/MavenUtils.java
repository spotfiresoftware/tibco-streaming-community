/*
* Copyright © 2019. TIBCO Software Inc.
* This file is subject to the license terms contained
* in the license file that is distributed with this file.
*/

package uxadmin;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Scanner;


public class MavenUtils {

	public static final String PREFIX = "ZJZJZJZJZZ"; // arbitrary prefix to grep for
	public static final String TIMESTAMP_FILE=".uxadmin-timestamp";
	
	public static File buildProject(File targetdir, File settings) throws InterruptedException, IOException
	{
		// mvn -DskipTests=true -Dexec.executable='echo' -Dexec.args='GREP FOR ME: ${project.artifactId}-${project.version}-${project.packaging}.zip' package exec:exec -s ~/code/branches/dtm/projtools/scripts/sw_mvn_settings.xml
		List<String> lst = new ArrayList<>(Arrays.asList("mvn", "-DskipTests=true","-Dexec.executable=echo", "-Dexec.args="+PREFIX+"${project.artifactId}-${project.version}-${project.packaging}.zip", "package", "exec:exec"));
		if (settings != null)
		{
			lst.add("-s");
			lst.add(settings.getAbsolutePath());
		}
		boolean forceColor = !System.getProperty("os.name").toLowerCase().contains("win");
		if (forceColor)
		{
			lst.add("-Dstyle.color=always");
			lst.add("-Djansi.force=true");
		}
		ProcessBuilder pb = new ProcessBuilder(lst);
		if (forceColor)
			pb.environment().put("MAVEN_OPTS", "-Djansi.force=true");
		pb.directory(targetdir);
		Process proc = pb.start();
		BufferedReader stdout = new BufferedReader(new InputStreamReader(proc.getInputStream()));
		BufferedReader stderr = new BufferedReader(new InputStreamReader(proc.getErrorStream()));
		
		Thread t = new Thread(() -> {
			try {
				while (!Thread.interrupted())
				{
					String line = stderr.readLine();
					if (line == null)
						return;
					System.err.println(line);
				}
			}
			catch(IOException e)
			{
				e.printStackTrace();
			}
		});
		t.start();
		String line;
		String output = null;
		while ((line = stdout.readLine()) != null) {
			if (line.startsWith(PREFIX))
			{
				output = line.trim().substring(PREFIX.length());
			}
			else
			{
				System.out.println(line);
				System.out.flush();
			}
        }
		proc.waitFor();
		if (output == null)
			throw new RuntimeException("Maven build failed");
		File outputpath = Paths.get(targetdir.getAbsolutePath(), "target", output).toFile();
		if (!outputpath.exists()) {
			throw new RuntimeException("File output didn't exist: " + output);
		}
		try (PrintWriter out = new PrintWriter(new File(new File(targetdir, "target"), TIMESTAMP_FILE))) {
		    out.println(output);
		}
		return outputpath;
	}

	
	public static File findCachedTarget(File base) throws IOException
	{
		File target = new File(base, "target");
		if (!target.exists())
			return null;
		File timestamp = new File(target, MavenUtils.TIMESTAMP_FILE);
		if (!timestamp.exists())
			return null;
		long lastBuild = timestamp.lastModified();
		File finalTarget = new File(target, readFile(timestamp));
		if (!finalTarget.exists())
			return null;
		if (new File(base, "pom.xml").lastModified() > lastBuild)
			return null;
		if (Files.walk(new File(base, "src").toPath())
				.mapToLong(f -> f.toFile().lastModified())
				.anyMatch(x-> x > lastBuild))
			return null;
		return finalTarget;
	}

	
	private static String readFile(File timestamp) throws FileNotFoundException
	{
		try (Scanner s = new Scanner(timestamp))
		{
			return s.useDelimiter("\\Z").next();
		}
	}
}
