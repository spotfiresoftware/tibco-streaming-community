/*
* Copyright © 2019. TIBCO Software Inc.
* This file is subject to the license terms contained
* in the license file that is distributed with this file.
*/
package com.tibco.techsupport.tools;

import java.io.BufferedWriter;
import java.io.File;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;
import java.util.HashSet;
import java.util.Set;

import com.streambase.liveview.client.LiveViewConnection;
import com.streambase.liveview.client.LiveViewConnectionFactory;
import com.streambase.liveview.client.LiveViewException;
import com.streambase.liveview.client.LiveViewExceptionType;
import com.streambase.liveview.client.LiveViewQueryType;
import com.streambase.liveview.client.Query;
import com.streambase.liveview.client.QueryConfig;
import com.streambase.liveview.client.SnapshotQueryListener;
import com.streambase.sb.ByteArrayView;
import com.streambase.sb.Tuple;
import com.streambase.sb.util.Util;
import com.tibco.techsupport.tableprovider.TechSupportTableProvider;

public class TSClient {

 	public static final String NAME_INDEX="Index";
	public static final String NAME_TEXT="Text";
	public static final String NAME_BLOB="BlobValue";
	
	public final static String COMMAND_GETSYSTEMTABLES="getsystemtables";
	public static final String COMMAND_GETSNAPSHOT="getsnapshot";
	public static final String COMMAND_GETSTACKTRACE="getstacktrace";
	public static final String COMMAND_GETHEAPDUMP="getheapdump";
	public static final String COMMAND_DOFULLGC="dofullgc";
	public static final String COMMAND_GETLOGS="getlogs";
	public static final String COMMAND_GETPROFILES="getprofiles";
	public static final String COMMAND_READBIN="readbin";
	public static final String COMMAND_GETVERSION="getversion";
	
	private static String proxyLvuri=null;
	private static String serviceName=null;
	private static String distPath=null;
	
	private static OutputStream outStream=null;
	private static BufferedWriter bufferedWriter=null;
	private static String remotePath=null;
	
	private static final String systemTableList=Util.getSystemProperty("liveview.tstableprovider.client.tablelist",
			"LiveViewStatistics,LVAlertRulesStatus,LVNodeInfo,LVSessionPublishers,LVSessionQueries,LVSessions,LVTables");
	private static final int queryLimit=Util.getIntSystemProperty("liveview.tstableprovider.client.query.limit", 1000);
	
	private static Set<String> systemTableSet=null;
	
	private static String TS_CLIENT_VERSION="V1.5 31-Oct-2019";
	
	// Note that a proxyed getsystemtables command requires the ProxyQuery table be configured and entitled.
	private static void help() {
		System.out.println("Usage: ts-client [-u <LVURI>] [getsnapshot | getstacktrace | getheapdump | dofullgc | getlogs | getsystemtables | getprofiles | readbin | getversion ] [-d <path/to/directory] [-p <path/to/remote/file> ] [-r <Proxied LVURI> | -s <my.service.cluster[;username:password]> ]");
		System.out.println("");
		System.out.println("-u    The LiveView URI for server to connect to");
		System.out.println("-d    A path to a directory the returned results will be written to. Required for getsnapshot, getprofiles, and readbin.");
		System.out.println("-p    The path to the remote binary file to read.");
		System.out.println("-r    The LiveView URI for the end target of the command request.");
		System.out.println("-s    The service name of the node for the end target of the command request.");
		System.out.println("-v    Version of this tool");
	}
	
	public static void main(String[] args) {

		String LVURL="lv://localhost:10080";
		String command=null;
		if (args.length == 0) {
			help();
			System.exit(-1);
		}
		
		for (int i=0; i < args.length; i++) {
			if (args[i].equals("-u")) {
				LVURL=args[++i];
				continue;
			}
			if (args[i].equals("-d")) {
				distPath=args[++i];
				continue;
			}
			if (args[i].equals("-r")) {
				proxyLvuri=args[++i];
				continue;
			}
			if (args[i].equals("-s")) {
				serviceName=args[++i];
				continue;
			}
			if (args[i].equals("-v")) {
				System.out.println(String.format("Version: %s", TS_CLIENT_VERSION));
				continue;
			}
			if (args[i].equals("-p")) {
				remotePath=args[++i];
				continue;
			}
			if (args[i].startsWith("-")) {
				System.out.println(String.format("Unknown arg: %s", args[i]));
				help();
				System.exit(-1);
			}
			// command (projection) is always before any predicate
			if (command == null) {
				command=args[i];
				continue;
			}
			System.out.println(String.format("Unknown arg: %s", args[i]));
			help();
			System.exit(-1);
		}
		if (command == null) {
			System.out.println("Missing command");
			help();
			System.exit(-1);
		}
		
		LiveViewConnection lvconn=null;
		try {
			lvconn = LiveViewConnectionFactory.getConnection(LVURL);

			switch (command.toLowerCase()) {
			
				case COMMAND_GETSNAPSHOT: {
					doGetSnapShot(lvconn);
					break;
				}
				
				case COMMAND_GETSTACKTRACE: {
					doGetStackTrace(lvconn);
					break;
				}
				
				case COMMAND_GETHEAPDUMP : {
					doGetHeapDump(lvconn);
					break;
				}
				
				case COMMAND_DOFULLGC : {
					doFullGC(lvconn);
					break;
				}
				
				case COMMAND_GETVERSION: {
					doGetVersion(lvconn);
					break;
				}
				
				case COMMAND_GETSYSTEMTABLES: {
					doGetSystemTables(lvconn);
					break;
				}
				case COMMAND_GETLOGS: {
					doGetLogs(lvconn);
					break;
				}
				case COMMAND_GETPROFILES: {
					doGetProfiles(lvconn);
					break;
				}
				case COMMAND_READBIN: {
					doReadbin(lvconn);
					break;
				}
				
				default: {
					System.err.println(String.format("Command unknow: %s", command));
					help();
					System.exit (-1);
				}
			}

		} catch (Exception e) {
			System.err.println(String.format("Error doing %s: %s", command, e.getMessage()));
			System.exit (-1);
		} finally {
			if (lvconn !=null) {
				lvconn.close();
			}
		}
	}
	
	private static void checkDist(String command) {
		if (distPath==null) {
			System.out.println(String.format("You must supply a -d <path> with %s", command));
			help();
			System.exit(-1);
		}
	}
	
	private static void checkTablePresent(LiveViewConnection lvconn, String table) {
		try {
			lvconn.getTable(table);
		} catch (Exception e) {
			System.err.println(String.format("Problem getting table %s - %s", table, e.getMessage()));
			System.exit(-1);
		}
	}
	
	private static void doGetVersion(LiveViewConnection lvconn) throws Exception {
		String ident=getIdent();
		if (!Util.isEmpty(ident)) {
			checkTablePresent(lvconn, TechSupportTableProvider.tsTableName);
		}
		
		final QueryConfig qb = new QueryConfig();
		qb.setQueryType(LiveViewQueryType.SNAPSHOT);
		
		qb.setQueryString(String.format("select %s from %s%s", COMMAND_GETVERSION, TechSupportTableProvider.tsTableName, (Util.isEmpty(ident)) ? "" : " where " + ident));
		final SnapshotQueryListener iter = new SnapshotQueryListener();
		try {
			final Query q = lvconn.registerQuery(qb, iter);
			System.out.println(String.format("Getting version from: %s", (Util.isEmpty(ident)) ? lvconn.getConnectionURI() : ident));
			
			if (!iter.hasNext()) {
				System.err.println("Nothing returned for version");
				System.exit(-1);
			}
			
			Tuple t= iter.next();
			System.out.println(t.getString(NAME_TEXT));
		} catch (Exception e) {
			throw e;
		}
	}
	
	
	private static void doReadbin(LiveViewConnection lvconn) throws Exception {
		checkDist(COMMAND_READBIN);
		if (remotePath == null) {
			System.out.println("You must supply a -p <path> with readbin");
			System.exit(-1);
		}
		checkTablePresent(lvconn, TechSupportTableProvider.fsTableName);
		String ident=getIdent();
		
		System.out.println(String.format("Reading binary file: %s", remotePath));
		
		final QueryConfig qb = new QueryConfig();
		qb.setQueryType(LiveViewQueryType.SNAPSHOT);
		qb.setQueryString(String.format("select %s from %s where path=%s%s", COMMAND_READBIN, TechSupportTableProvider.fsTableName, remotePath, (Util.isEmpty(ident)) ? "" : ident));
		final SnapshotQueryListener iter = new SnapshotQueryListener();
		final Query q = lvconn.registerQuery(qb, iter);
		
		// readbin currently returns the entire binary content in one tuple
		if (!iter.hasNext()) {
			System.err.println(String.format("Problem binary file: %s", remotePath));
			return;
		}
		Tuple t=iter.next();
		
		String fileName= Paths.get(remotePath).getFileName().toString();
		Path localPath = Paths.get(distPath + "/" + fileName);
		OutputStream outStream=Files.newOutputStream(localPath, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
		ByteArrayView bav=t.getBlobBuffer(NAME_BLOB);
		outStream.write(bav.array(), bav.offset(), bav.length());
		outStream.close();
	}
	
	private static void doGetProfiles(LiveViewConnection lvconn) throws Exception {
		checkDist(COMMAND_GETPROFILES);
		checkTablePresent(lvconn, TechSupportTableProvider.tsTableName);
		String ident=getIdent();
		
		final QueryConfig qb = new QueryConfig();
		qb.setQueryType(LiveViewQueryType.SNAPSHOT);
		qb.setQueryString(String.format("select %s from %s%s", COMMAND_GETPROFILES, TechSupportTableProvider.tsTableName, (Util.isEmpty(ident)) ? "" : " where " + ident));
		final SnapshotQueryListener iter = new SnapshotQueryListener();
		try {
			final Query q = lvconn.registerQuery(qb, iter);
			System.out.println(String.format("Getting profiles from: %s", (proxyLvuri == null) ? lvconn.getConnectionURI() : proxyLvuri));
			
			OutputStream profileStream=null;
			String profileChildName=null;
			
			while (iter.hasNext()) {
				Tuple t=iter.next();
				
				if (!t.isNull(NAME_TEXT)) {
					String header=t.getString(NAME_TEXT);
					System.out.println(header);
					// "Profile: %s size: %s"
					profileChildName=header.substring(header.indexOf(":")+1, header.indexOf("size:")-1).trim();

					if (profileStream != null) {
						profileStream.close();
					}
					Path profilePath=Paths.get(distPath + "/" + profileChildName);
					profileStream=Files.newOutputStream(profilePath, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
					continue;
				}

				if (!t.isNull(NAME_BLOB)) {
					ByteArrayView bav=t.getBlobBuffer(NAME_BLOB);
					profileStream.write(bav.array(), bav.offset(), bav.length());
					continue;
				}
			}
			if (profileStream != null) {
				profileStream.close();
			}
			
		} catch (Exception e) {
			throw e;
		}
	}
	
	private static void doGetLogs(LiveViewConnection lvconn) throws Exception {
		checkTablePresent(lvconn, TechSupportTableProvider.tsTableName);
		String ident=getIdent();
		
		final QueryConfig qb = new QueryConfig();
		qb.setQueryType(LiveViewQueryType.SNAPSHOT);
		qb.setQueryString(String.format("select %s from %s%s", COMMAND_GETLOGS, TechSupportTableProvider.tsTableName, (Util.isEmpty(ident)) ? "" : " where " + ident));
		final SnapshotQueryListener iter = new SnapshotQueryListener();
		try {
			final Query q = lvconn.registerQuery(qb, iter);
			System.out.println(String.format("Getting logs from: %s", (Util.isEmpty(ident)) ? lvconn.getConnectionURI() : ident));
			while (iter.hasNext()) {
				Tuple t=iter.next();
				if (!t.isNull(NAME_TEXT)) {
					System.out.println(t.getString(NAME_TEXT));
				}
			}
		} catch (Exception e) {
			throw e;
		}
	}
	
	private static void doGetSystemTables(LiveViewConnection lvconn) throws LiveViewException {
		checkTablePresent(lvconn, TechSupportTableProvider.proxyTableName);
		
		systemTableSet=new HashSet<String>();
		systemTableSet.addAll(Arrays.asList(systemTableList.split(",")));
		
		for (String table : systemTableSet) {
			System.out.println(String.format("%s\n", table));
			dumpTable(table, lvconn);
			System.out.println("\n");
		}
	}
	
	private static void dumpTable(String table, LiveViewConnection lvconn)  {
		try {
			final QueryConfig qb = new QueryConfig();
			qb.setQueryType(LiveViewQueryType.SNAPSHOT);
			String ident=getIdent();

			if (Util.isEmpty(ident)) {
				qb.setQueryString(String.format("select * from %s where limit %s", table, queryLimit));
			} else {
				qb.setQueryString(String.format("select snapshot from %s where %s query=select * from %s where limit %s",
						TechSupportTableProvider.proxyTableName, ident, table, queryLimit));
			}
			
			final SnapshotQueryListener iter = new SnapshotQueryListener();
			final Query q = lvconn.registerQuery(qb, iter);
			
			while (iter.hasNext()) {
				Tuple t=iter.next();
				System.out.println(t.toString());
			}
		} catch (Exception e) {
			System.err.println(String.format("Problem querying table: %s -  %s", table, e.getMessage()));
		}
	}
	
	private static void doGetStackTrace(LiveViewConnection lvconn) throws Exception {
		checkTablePresent(lvconn, TechSupportTableProvider.tsTableName);
		String ident=getIdent();
		
		final QueryConfig qb = new QueryConfig();
		qb.setQueryType(LiveViewQueryType.SNAPSHOT);
		qb.setQueryString(String.format("select %s from %s%s", COMMAND_GETSTACKTRACE, TechSupportTableProvider.tsTableName, (Util.isEmpty(ident)) ? "" : " where " + ident));
		final SnapshotQueryListener iter = new SnapshotQueryListener();
		try {
			final Query q = lvconn.registerQuery(qb, iter);
			boolean firstLine=true;
			while (iter.hasNext()) {
				Tuple t=iter.next();
	
				if (distPath==null) {
					if (firstLine) {
						firstLine=false;
						System.out.println(String.format("Stack trace for %s", (proxyLvuri==null) ? lvconn.getConnectionURI() : proxyLvuri) + "\n");
					}
					System.out.println(t.getString(NAME_TEXT));
					continue;
				}
				if (bufferedWriter == null) {
					SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMddHHmmss");
					String outFile="StackTrace_" + sdf.format(new Date()) + ".log";
					Path localZipPath= Paths.get(distPath + "/" + outFile);
					bufferedWriter=Files.newBufferedWriter(localZipPath, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
					bufferedWriter.write(String.format("Stack trace for %s", (proxyLvuri==null) ? lvconn.getConnectionURI() : proxyLvuri) + "\n\n");
				}
				bufferedWriter.write(t.getString(NAME_TEXT) + "\n");
				continue;
			}
		} catch (Exception e) {
			throw e;
		}
	}
	
	private static void doFullGC(LiveViewConnection lvconn) throws Exception {
		checkTablePresent(lvconn, TechSupportTableProvider.tsTableName);
		String ident=getIdent();
		
		final QueryConfig qb = new QueryConfig();
		qb.setQueryType(LiveViewQueryType.SNAPSHOT);
		qb.setQueryString(String.format("select %s from TSTable%s", COMMAND_DOFULLGC, (Util.isEmpty(ident)) ? "" : "where " + ident));
		
		final SnapshotQueryListener iter = new SnapshotQueryListener();
		try {
			final Query q = lvconn.registerQuery(qb, iter);
			
			while (iter.hasNext()) {
				Tuple t=iter.next();
				if (!t.isNull(NAME_TEXT)) {
					String line=t.getString(NAME_TEXT);
					System.out.println(line);
					continue;
				}
			}
		} catch (Exception e) {
			throw e;
		}

	}
	
	private static void doGetHeapDump(LiveViewConnection lvconn) throws Exception {
		checkDist(COMMAND_GETHEAPDUMP);
		checkTablePresent(lvconn, TechSupportTableProvider.tsTableName);
		String ident=getIdent();
		
		final QueryConfig qb = new QueryConfig();
		qb.setQueryType(LiveViewQueryType.SNAPSHOT);

		qb.setQueryString(String.format("select %s from TSTable%s", COMMAND_GETHEAPDUMP, (Util.isEmpty(ident)) ? "" : "where " + ident));
		
		int chunks=0;
		final SnapshotQueryListener iter = new SnapshotQueryListener();
		try {
			final Query q = lvconn.registerQuery(qb, iter);
			String heapName="NameNotProvided.hprof";
			while (iter.hasNext()) {
				Tuple t=iter.next();
				
				if (!t.isNull(NAME_BLOB)) {
					if (outStream == null) {
						System.out.print("Copying ");
						Path localHeapPath= Paths.get(distPath + "/" + heapName);
						outStream=Files.newOutputStream(localHeapPath, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
					}
					ByteArrayView bav=t.getBlobBuffer(NAME_BLOB);
					outStream.write(bav.array(), bav.offset(), bav.length());
					chunks++;
					if ((chunks%100) == 0) {
						System.out.print(".");
					}
					continue;
				}
				
				if (!t.isNull(NAME_TEXT)) {
					String line=t.getString(NAME_TEXT);
					String startsPrefix="Doing heap dump:";
					if (line.startsWith(startsPrefix)) {
						String heapPath=line.substring(startsPrefix.length());
						heapPath = heapPath.trim();
						File heapFile = new File(heapPath);
						heapName=heapFile.getName();
					}
					System.out.println(line);
					continue;
				}
			}
		} catch (Exception e) {
			throw e;
		}
	}
	
	private static void doGetSnapShot(LiveViewConnection lvconn) throws Exception {
		checkDist(COMMAND_GETSNAPSHOT);
		checkTablePresent(lvconn, TechSupportTableProvider.tsTableName);
		String ident=getIdent();
		
		final QueryConfig qb = new QueryConfig();
		qb.setQueryType(LiveViewQueryType.SNAPSHOT);
		
		String zipName=null;
		qb.setQueryString(String.format("select %s from TSTable%s", COMMAND_GETSNAPSHOT, (Util.isEmpty(ident)) ? "" : " where " + ident));
		
		int chunks=0;
		final SnapshotQueryListener iter = new SnapshotQueryListener();
		try {
			final Query q = lvconn.registerQuery(qb, iter);
			while (iter.hasNext()) {
				Tuple t=iter.next();
				
				if (!t.isNull(NAME_BLOB)) {
					if (outStream == null) {
						System.out.print("Copying ");
						Path localZipPath= Paths.get(distPath + "/" + zipName);
						outStream=Files.newOutputStream(localZipPath, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
					}
					ByteArrayView bav=t.getBlobBuffer(NAME_BLOB);
					outStream.write(bav.array(), bav.offset(), bav.length());
					chunks++;
					if ((chunks%100) == 0) {
						System.out.print(".");
					}
					continue;
				}
				
				if (!t.isNull(NAME_TEXT)) {
					String line=t.getString(NAME_TEXT);
					String startsPrefix="Completed snapshot:";
					if (line.startsWith(startsPrefix)) {
						String snapPath=line.substring(startsPrefix.length(), line.indexOf(","));
						snapPath = snapPath.trim();
						File snapFile = new File(snapPath);
						zipName=snapFile.getName();
					}
					System.out.println(line);
					continue;
				}
			}
		} catch (Exception e) {
			throw e;
		}
	}

	private static String getIdent() {
		if ((proxyLvuri == null) && (serviceName == null)) {
			return "";
		} else {
			if (proxyLvuri != null) {
				return String.format("lvuri=%s", proxyLvuri); 
			} else {
				return String.format("service=%s", serviceName); 
			}
		}
	}
	
	/**
	 * getLVuriFromService - BUGBUG there's a copy of this method in the TSTable class too. It needs to go someplace everyone can get to
	 * @param lvconn - LV connection to the local system
	 * @param serviceName - the Service name, and optional ";username:password"
	 * @return a full LV URI
	 * @throws LiveViewException
	 */
	public static String getLVuriFromService(LiveViewConnection lvconn, String serviceName) throws LiveViewException {
		try {
			
			String creds=null;
			String noCreds;
			if (serviceName.indexOf(";") != -1) {
				creds=serviceName.substring(serviceName.indexOf(";")+1);
				noCreds=serviceName.substring(0, serviceName.indexOf(";"));
			} else {
				noCreds=serviceName;
			}

			System.out.println(String.format("base name: %s, creds: %s", noCreds, creds));
			
			final QueryConfig qc = new QueryConfig();
			qc.setQueryType(LiveViewQueryType.SNAPSHOT);
			qc.setQueryString(String.format("select Value from LVNodeInfo where Category=='Cluster' && Name=='Member' && Detail=='%s'", noCreds));
			
			final SnapshotQueryListener iter = new SnapshotQueryListener();
			lvconn.registerQuery(qc, iter);

			if (!iter.hasNext()) {
				throw LiveViewExceptionType.UNDERLYING_SB_EXCEPTION.error(String.format("Can not find %s in LVNodeInfo", noCreds)); 
			}
			
			Tuple t=iter.next();
			return String.format("lv://%s%s", (creds==null) ? "" : creds + "@", t.getString("Value").substring(5));
				
		} catch (Exception e) {
			throw LiveViewExceptionType.UNDERLYING_SB_EXCEPTION.error(String.format("failed to get LVNodeInfo table to lookup service: %s - %s", serviceName, e.getMessage())); 
		}
	}
}
