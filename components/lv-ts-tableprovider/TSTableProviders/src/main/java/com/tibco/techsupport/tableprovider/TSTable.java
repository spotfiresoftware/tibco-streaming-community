/*
* Copyright © 2019. Cloud Software Group, Inc.
* This file is subject to the license terms contained
* in the license file that is distributed with this file.
*/
package com.tibco.techsupport.tableprovider;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.InputStream;
import java.lang.management.ManagementFactory;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.management.MBeanServer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.streambase.liveview.client.LiveViewConnection;
import com.streambase.liveview.client.LiveViewConnectionFactory;
import com.streambase.liveview.client.LiveViewException;
import com.streambase.liveview.client.LiveViewExceptionType;
import com.streambase.liveview.client.LiveViewQueryLanguage;
import com.streambase.liveview.client.LiveViewQueryType;
import com.streambase.liveview.client.LiveViewTableCapability;
import com.streambase.liveview.client.OrderDefinition;
import com.streambase.liveview.client.Query;
import com.streambase.liveview.client.QueryConfig;
import com.streambase.liveview.client.SnapshotQueryListener;
import com.streambase.liveview.client.Table.TableStatus;
import com.streambase.liveview.server.event.query.BeginSnapshotEvent;
import com.streambase.liveview.server.event.query.EndSnapshotEvent;
import com.streambase.liveview.server.event.query.QueryExceptionEvent;
import com.streambase.liveview.server.event.query.listener.QueryEventListener;
import com.streambase.liveview.server.event.tuple.TupleAddedEvent;
import com.streambase.liveview.server.query.QueryModel;
import com.streambase.liveview.server.table.CatalogedTable;
import com.streambase.liveview.server.table.Table;
import com.streambase.liveview.server.table.plugin.TableProviderControl;
import com.streambase.liveview.server.table.publisher.TablePublisher;
import com.streambase.liveview.server.util.FunctionalCopier;
import com.streambase.sb.ByteArrayView;
import com.streambase.sb.CompleteDataType;
import com.streambase.sb.NullValueException;
import com.streambase.sb.Schema;
import com.streambase.sb.Timestamp;
import com.streambase.sb.Tuple;
import com.streambase.sb.TupleException;
import com.streambase.sb.util.GetStackTrace;
import com.streambase.sb.util.Msg;
import com.streambase.sb.util.ProcessReader;
import com.streambase.sb.util.Util;
import com.streambase.sb.util.Version;
import com.sun.management.HotSpotDiagnosticMXBean;

/*
 * This is the TSTable implementation. It is intended to expose TS insight into a server in constrained manner
 * 
 * SELECT [ getsnapshot | getstacktrace | getlogs | getprofiles | getversion | getheapdump | dofullgc ] from TSTable [ where lvuri= <RemotelvURI> | service=<My.Service.cluster[;username:password]> ]
 * 	return is snapshot only with schema: index(long), Text(String), BlobValue(blob)
 * 
 * The lvuri can point to any reachable LiveView anywhere. The service name can only be a node in the same cluster.
 *
 * See the parseQuery method for details 
 */
public class TSTable implements Table  {

    private Logger logger = LoggerFactory.getLogger(TSTable.class);
    private BlockingQueue<TSTableOperation> queue=new  LinkedBlockingQueue<TSTableOperation>();
    AtomicBoolean shouldRun=new AtomicBoolean(true);
    
    public final static String COMMAND_GETSTACKTRACE="getstacktrace";
    public final static String COMMAND_GETHEAPDUMP="getheapdump";
    public final static String COMMAND_DOFULLGC="dofullgc";
    public final static String COMMAND_GETLOGS="getlogs"; 
    public final static String COMMAND_GETSNAPSHOT="getsnapshot";
    public final static String COMMAND_GETPROFILES="getprofiles";
    public final static String COMMAND_GETVERSION="getversion"; 

    private String NAME_INDEX="Index";
    private String NAME_TEXT="Text";
    private String NAME_BLOB="BlobValue";
    private Schema.Field FIELD_TEXT = new Schema.Field(NAME_TEXT, CompleteDataType.forString());
    private List<String> pKeyFieldArray = Arrays.asList(NAME_INDEX);
    
	private Schema.Field FIELD_BLOB = new Schema.Field(NAME_BLOB, CompleteDataType.forBlob());
	private Schema.Field FIELD_INDEX = new Schema.Field(NAME_INDEX, CompleteDataType.forLong());
    private List<String> dataFieldArray = Arrays.asList(NAME_INDEX, NAME_TEXT, NAME_BLOB);
    private Schema tableSchema = new Schema(null, FIELD_INDEX, FIELD_TEXT, FIELD_BLOB);
    private Schema keySchema = new Schema(null, FIELD_INDEX);
    
    // Some members for holding information about the TSTable object 
	private CatalogedTable catalogTable=null;
	private final TableProviderControl helper;
	private final String tableName;
	
	// used to lazily get the LVNodeInfo table if a service reference is made
	private LiveViewConnection localLVConn=null;
	
	// installPath and product home of this node
	private final String installPath=Util.getSystemProperty("com.tibco.ep.dtm.nodeInstallDirectory");
	private final String productHome=Util.getSystemProperty("com.tibco.ep.dtm.product.home");
	private final String liveviewProjectOut=Util.getSystemProperty("liveview.project.out", installPath);
	
	private final int processWaitTimeMS=Util.getIntSystemProperty("liveview.tstableprovider.process.wait.ms", 600000);
	private final int bufferSize=Util.getIntSystemProperty("liveview.tstableprovider.buffersize", 64*1024);
	
	TSTableThread tsTableThread=null;
	
    public TSTable(TableProviderControl helper, String tableName) {
		this.helper=helper;
		this.tableName=tableName;
		
		// work is done in a separate thread 
		tsTableThread = new TSTableThread(this);
		tsTableThread.start();
		
		catalogTable = new CatalogedTable(tableName);
		catalogTable.setStatus(TableStatus.ENABLED, "Ready to Execute");
		catalogTable.setCapabilities(EnumSet.of(LiveViewTableCapability.SNAPSHOT));
		catalogTable.setQueryLanguages( EnumSet.of(LiveViewQueryLanguage.OTHER));
		catalogTable.setDescription("TableProvider that provides TS visiablity to a node.  V1.0");
		catalogTable.setGroup("TechnicalSupport");
        catalogTable.setCreateTime(Timestamp.now());
        catalogTable.setSchema(tableSchema);
       
        catalogTable.setKeyFields(pKeyFieldArray);
        catalogTable.setRuntimeTable(this);
        
        helper.insert(catalogTable);
     }
    
	@Override
	public void addListener(QueryEventListener listener, LiveViewQueryType arg1, QueryModel queryModel) throws LiveViewException {
		// Just add a new query to a work list
		logger.debug(String.format("Adding query: %s : %s", queryModel.getProjection(), queryModel.getPredicate()));
		queue.add(new TSTableOperation(listener, queryModel));
	}

	@Override
	public TablePublisher createPublisher(String arg0) throws LiveViewException {
		throw LiveViewExceptionType.PUBLISHING_NOT_SUPPORTED.error();
	}

	@Override
	public void removeListener(QueryEventListener arg0) throws LiveViewException {
		// Since this table provider only does snapshots, we don't have anything to clean up here.
	}

	public Schema getSchema() {
		return tableSchema;
	}
	
	public String getTableName() {
		return tableName;
	}
	
	public void shutdown() {
		shouldRun.set(false);
	}
	
	/*
	 * Simple helper class to hold a query in the work list
	 */
	private class TSTableOperation {
		
		public final QueryEventListener listener;
		public final QueryModel queryModel;
		
		public TSTableOperation(QueryEventListener listener, QueryModel queryModel) {
			this.listener=listener;
			this.queryModel=queryModel;
		}
	}
	
	/*
	 * The parseQuery method basically defines the query language for the TableProvider 
	 */
	@Override
	public QueryModel parseQuery(CatalogedTable catalogedTable, String queryString, LiveViewQueryType type, boolean includeInternal, String additionalPredicate) throws LiveViewException {
	
		logger.debug("parseQuery: " + queryString);
		
		if ((queryString==null) || queryString.isEmpty()) {
			throw LiveViewExceptionType.INVALID_REQUEST.error("No Query string found");
		}
		
		String lowerQuery = queryString.toLowerCase();
		Pattern queryOK = Pattern.compile("select +(\\w+) +from +(\\w+)(.*)");
		Matcher theMatcher=queryOK.matcher(lowerQuery);
		
		if (!theMatcher.matches()) {
			throw LiveViewExceptionType.INVALID_REQUEST.error(String.format("Failed basic TSTable parse: %s", lowerQuery));
		}
	
		// Now see if this command should be proxied
		Pattern proxyPattern = Pattern.compile("select +(\\w+) +from +(\\w+) +where +lvuri=(.*)");
		Matcher proxied=proxyPattern.matcher(lowerQuery);

		String lvuri=null;
		if (proxied.matches()) {
			// Setup for lv uri proxy
			lvuri=proxied.group(3);
			lvuri=lvuri.trim();
			int uriEnd = lvuri.indexOf(" ");
			if (uriEnd != -1) {
				lvuri=lvuri.substring(0, uriEnd);
			}
			int beginURI=queryString.length() - ((uriEnd == -1) ? lvuri.length() : (lvuri.length()-uriEnd));
			int originalEnd=(uriEnd == -1) ? queryString.length() : (queryString.length()-uriEnd);
			// get back the original URI because there may be cased passwords
			lvuri="lvuri=" + queryString.substring(beginURI, originalEnd);
			logger.debug(String.format("lvURI proxing to: %s", lvuri));
		} else {
			// See if service name is given.
			proxyPattern = Pattern.compile("select +(\\w+) +from +(\\w+) +where +service=(.*)");
			proxied=proxyPattern.matcher(lowerQuery);
			if (proxied.matches()) {
				// get the URI from the service name
				if (localLVConn == null) {
					localLVConn = LiveViewConnectionFactory.getConnection(TableUtils.getEncapsulatingURI());
				}
				lvuri=TableUtils.getLVuriFromService(localLVConn, queryString.substring(proxied.start(3), proxied.end(3)));
				logger.debug(String.format("service %s proxing to: %s", queryString.substring(proxied.start(3), proxied.end(3)), lvuri));
			}
		}
		
		String thePrediate=lvuri;
		Matcher matcher=theMatcher;
		
		QueryModel queryModel = new QueryModel() {
			
			String projection=matcher.group(1);
			String table=matcher.group(2);
			// Need the case of the predicate
			String predicate=thePrediate;
			
			@Override
			public void validate(CatalogedTable catalogedTable, boolean includeInternalFields) throws LiveViewException {
				// TODO Auto-generated method stub
			}
			
			@Override
			public void setTupleCopier(Schema schema, Schema schema2, FunctionalCopier tupleCopier) {
				// TODO Auto-generated method stub
			}
			
			@Override
			public boolean isPivotQuery() {
				// TODO Auto-generated method stub
				return false;
			}
			
			@Override
			public boolean isCalcColumnQuery() {
				// TODO Auto-generated method stub
				return false;
			}
			
			@Override
			public boolean isAggQuery() {
				// TODO Auto-generated method stub
				return false;
			}
			
			@Override
			public boolean hasTimeWindowedPredicate() {
				// TODO Auto-generated method stub
				return false;
			}
			
			@Override
			public boolean hasTemporalPredicate() {
				// TODO Auto-generated method stub
				return false;
			}
			
			@Override
			public boolean hasPredicate() {
				// TODO Auto-generated method stub
				return false;
			}
			
			@Override
			public boolean hasPivot() {
				// TODO Auto-generated method stub
				return false;
			}
			
			@Override
			public boolean hasOrderBy() {
				// TODO Auto-generated method stub
				return false;
			}
			
			@Override
			public boolean hasLimit() {
				// TODO Auto-generated method stub
				return false;
			}
			
			@Override
			public boolean hasHaving() {
				// TODO Auto-generated method stub
				return false;
			}
			
			@Override
			public boolean hasGroupBy() {
				// TODO Auto-generated method stub
				return false;
			}
			
			@Override
			public String getWindowStartExpr() throws UnsupportedOperationException {
				// TODO Auto-generated method stub
				return null;
			}
			
			@Override
			public String getWindowEndExpr() throws UnsupportedOperationException {
				// TODO Auto-generated method stub
				return null;
			}
			
			@Override
			public FunctionalCopier getTupleCopier(Schema schema, Schema schema2) {
				// TODO Auto-generated method stub
				return null;
			}
			
			@Override
			public String getTimestampField() throws UnsupportedOperationException {
				// TODO Auto-generated method stub
				return null;
			}
			
			@Override
			public long getTemporalPredicateComponent() throws UnsupportedOperationException {
				// TODO Auto-generated method stub
				return 0;
			}
			
			@Override
			public String getTable() {
				return tableName;
			}
			
			@Override
			public Schema getSchema() {
				return tableSchema;
			}
			
			@Override
			public LiveViewQueryType getQueryType() {
				return LiveViewQueryType.SNAPSHOT;
			}
			
			@Override
			public LiveViewQueryLanguage getQueryLanguage() {
				return LiveViewQueryLanguage.OTHER;
			}
			
			@Override
			public String getProjection() {
				return projection;
			}
			
			@Override
			public String getPredicate() {
				return predicate;
			}
			
			@Override
			public String getPivotValues() {
				// TODO Auto-generated method stub
				return null;
			}
			
			@Override
			public String getPivotField() {
				// TODO Auto-generated method stub
				return null;
			}
			
			@Override
			public String getPivotAggExpression() {
				// TODO Auto-generated method stub
				return null;
			}
			
			@Override
			public OrderDefinition getOrderDefinition() throws UnsupportedOperationException {
				// TODO Auto-generated method stub
				return null;
			}
			
			@Override
			public int getLimit() throws UnsupportedOperationException {
				// TODO Auto-generated method stub
				return 0;
			}
			
			@Override
			public Schema getKeySchema() {
				return keySchema;
			}
			
			@Override
			public List<String> getKeyFields() {
				return pKeyFieldArray;
			}
			
			@Override
			public String getHaving() {
				// TODO Auto-generated method stub
				return null;
			}
			
			@Override
			public List<String> getGroupBy() throws UnsupportedOperationException {
				// TODO Auto-generated method stub
				return null;
			}
			
			@Override
			public Schema getFullSchema() {
				return tableSchema;
			}
			
			@Override
			public List<String> getDataFields() {
				return dataFieldArray;
			}
			
			@Override
			public CatalogedTable getCatalogedTable() {
				return catalogedTable;
			}

			@Override
			public String getDisplayablePredicate() {
				return predicate;
			}
		};
					
		return queryModel;
	}
	
	/*
	 * The TSTableThread does all the work.
	 */
	private class TSTableThread extends Thread {
		
		private final TSTable tsTable;
		
		public TSTableThread(TSTable ts) {
			this.tsTable=ts;
		}
		
		public void run() {	
			// Now we are ready to start processing queries.
			while (shouldRun.get()) {
				TSTableOperation tsOp=null;
				try {
					tsOp=null;
					tsOp=queue.poll(500, TimeUnit.MILLISECONDS);
					
					if (tsOp !=null) {
						TSClientWorkerThread tswt = new TSClientWorkerThread(tsOp.listener, tsOp.queryModel);
						tswt.setDaemon(true);
						tswt.start();
					}
				} catch (Exception e) {
					logger.warn(Msg.format("Unexpected exception: {0}", e.getMessage()));
				} 
			}
		}
		
		/*
		 * The TSClientWorkerThread thread is where the ts table command is run.
		 */
		private class TSClientWorkerThread extends Thread {
			
			final QueryEventListener listener;
			final QueryModel queryModel;
			
			public TSClientWorkerThread (QueryEventListener listener, QueryModel queryModel) {
				this.listener=listener;
				this.queryModel=queryModel;
			}
			
			public void run() {	
				List<String> argsList = new ArrayList<String>();
				long idKey=1;
				
				try {
					new BeginSnapshotEvent(this).dispatch(listener);
					doTSOp(listener, queryModel);
					new EndSnapshotEvent(this).dispatch(listener);
					
				} catch (LiveViewException e) {
					new QueryExceptionEvent(this, e).dispatch(listener);
					return;
				} catch (Exception e) {
					new QueryExceptionEvent(this, LiveViewExceptionType.UNDERLYING_SB_EXCEPTION.error(e)).dispatch(listener);
					return;
				}
			}
		}
		
		/*
		 * This is the method that figures out what to do.
		 *  
		 */
		private void doTSOp(QueryEventListener listener, QueryModel queryModel) throws LiveViewException {
	
			String verb=queryModel.getProjection();
			String pred=queryModel.getPredicate();
			long idKey=1;	
			
			if (pred != null) {
				doProxy(listener, queryModel);
				return;
			}
			logger.info(String.format("Doing TSTable command: %s", verb.toLowerCase()));

			switch (verb.toLowerCase()) {
			
			case COMMAND_GETSNAPSHOT: {
				
				String epadminPath=productHome + "/distrib/tibco/bin/epadmin";
				if (System.getProperty("os.name").startsWith("Windows")) {
					epadminPath=epadminPath+".exe";
				}
				
				String[] args = {epadminPath, "create", "snapshot", "--installpath=" + installPath } ;
				File file = new File(epadminPath);
				if (!file.isFile()) {
					throw LiveViewExceptionType.UNDERLYING_SB_EXCEPTION.error(String.format("Could not find epadmin: %s", epadminPath));
				}
				File dirPath = new File(installPath);
				if (!dirPath.isDirectory()) {
					throw LiveViewExceptionType.UNDERLYING_SB_EXCEPTION.error(String.format("Could not find installPath: %s", installPath));
				}
				
				try {
					sendTuple(idKey++, String.format("Doing snapshot for: %s", installPath), null, listener, queryModel);
					
					ProcessBuilder pb = new ProcessBuilder(args);
					ProcessReader pr = new ProcessReader(pb.start());
					pr.waitFor(processWaitTimeMS);
					
					if (pr.exitValue()!=0) {
						String errMsg=String.format("Failed to get snapshot: %s", installPath);
						logger.warn(errMsg);
						sendTuple(idKey++, errMsg, null, listener, queryModel);
						throw LiveViewExceptionType.UNDERLYING_SB_EXCEPTION.error(errMsg);
					}
					
					String pathToZip=null;
					
					String[] stdout= pr.getAndRemoveStdOutLines();
					if ((stdout.length!=0) && !stdout[0].isEmpty()) {
						String prefix="Created snapshot archive";
						int pathoffset=stdout[0].indexOf(prefix);
						if (pathoffset != -1) {
							pathToZip = stdout[0].substring(pathoffset+prefix.length());
							pathToZip=pathToZip.trim();
						}
					} else {
						throw LiveViewExceptionType.UNDERLYING_SB_EXCEPTION.error(String.format("Failed to grok snapshot location: %s", installPath));
					}
					
					File zipFile=new File(pathToZip);
					long zipLength=zipFile.length();
					sendTuple(idKey++, String.format("Completed snapshot: %s, size: %s", pathToZip, zipLength), null, listener, queryModel);
					
					InputStream is=Files.newInputStream(Paths.get(pathToZip), StandardOpenOption.READ);
					
					byte[] buffer=new byte[bufferSize];
					int bufNum=0;
					while (true) {
						int readSize=is.read(buffer);
						if (readSize == -1) {
							break;
						}
						ByteArrayView bav= ByteArrayView.makeView(buffer, 0, readSize);
						sendTuple(idKey++, Long.toString(bufNum++), bav, listener, queryModel);
					}

					sendTuple(idKey++, "Copy completed", null, listener, queryModel);
					
				} catch (Exception e) {
					throw LiveViewExceptionType.UNDERLYING_SB_EXCEPTION.error(e);
				}
				break;
			}
			
			case COMMAND_GETPROFILES: {
				File profilePath = new File(liveviewProjectOut + "/lv-profile");
				if (!profilePath.isDirectory()) {
					throw LiveViewExceptionType.UNDERLYING_SB_EXCEPTION.error(String.format("Could not find profile path: %s", profilePath));
				}
				
				File[] filesList;
				try {
					filesList = profilePath.listFiles();
					if ((filesList ==null) || (filesList.length==0)) {
						sendTuple(idKey++, String.format("No profiles found at: %s", profilePath.getAbsolutePath()), null, listener, queryModel);
					}				
				} catch (Exception e) {
					throw LiveViewExceptionType.UNDERLYING_SB_EXCEPTION.error(String.format("Problems getting profile directory: %s", e.getMessage()));
				}

				for (File profile : filesList) {
					logger.info(String.format("DEBUG profile %s", profile.getAbsolutePath()));
					try {
						long profileLength=profile.length();
						sendTuple(idKey++, String.format("Profile: %s size: %s", profile.getName(), profileLength), null, listener, queryModel);
						
						InputStream is=Files.newInputStream(Paths.get(profile.getAbsolutePath()), StandardOpenOption.READ);
						
						byte[] buffer=new byte[bufferSize];
						while (true) {
							int readSize=is.read(buffer);
							if (readSize == -1) {
								break;
							}
							ByteArrayView bav= ByteArrayView.makeView(buffer, 0, readSize);
							sendTuple(idKey++, null, bav, listener, queryModel);
						}
					} catch (Exception e) {
						logger.info(String.format("Error getting profile: %s exception: %s", profile, e.getMessage()));
					}
				}

				break;
			}
			
			case COMMAND_GETSTACKTRACE : {
				try {
					List<String> stack=GetStackTrace.get();
					for (String l : stack) {
						sendTuple(idKey++, l, null, listener, queryModel);
					}
					
				} catch (Exception e) {
					throw LiveViewExceptionType.UNDERLYING_SB_EXCEPTION.error(e);
				}
	
				break;
			}
			
			case COMMAND_GETHEAPDUMP : {
				String fileName = String.format("%s/TSTableHeapDump-%s-%s.hprof", liveviewProjectOut, Util.getHostName(), System.currentTimeMillis());
				try {
					sendTuple(idKey++, String.format("Doing heap dump: %s", fileName), null, listener, queryModel);
					// Perform heap dump
					final String HOTSPOT_BEAN_NAME = "com.sun.management:type=HotSpotDiagnostic";
					MBeanServer server = ManagementFactory.getPlatformMBeanServer();
			        HotSpotDiagnosticMXBean bean =  ManagementFactory.newPlatformMXBeanProxy(
	                        server,
	                        HOTSPOT_BEAN_NAME,
	                        HotSpotDiagnosticMXBean.class);
			        bean.dumpHeap(fileName, true);
					// read heap dump back
					File dumpFile=new File(fileName);
					long dumpLength=dumpFile.length();
					sendTuple(idKey++, String.format("Completed heap dump, size: %s", dumpLength), null, listener, queryModel);
					
					InputStream is=Files.newInputStream(Paths.get(fileName), StandardOpenOption.READ);
					
					byte[] buffer=new byte[bufferSize];
					int bufNum=0;
					while (true) {
						int readSize=is.read(buffer);
						if (readSize == -1) {
							break;
						}
						ByteArrayView bav= ByteArrayView.makeView(buffer, 0, readSize);
						sendTuple(idKey++, Long.toString(bufNum++), bav, listener, queryModel);
					}

					sendTuple(idKey++, "Copy completed", null, listener, queryModel);
					// heap dumps can be very large. dont acrete them.
					dumpFile.delete();
					
				} catch (Exception e) {
					throw LiveViewExceptionType.UNDERLYING_SB_EXCEPTION.error(e);
				}

				break;
			}
			
			case COMMAND_DOFULLGC : {
				try {
					sendTuple(idKey++, "Doing full GC...", null, listener, queryModel);
					System.gc();
					sendTuple(idKey++, "GC complete", null, listener, queryModel);
				} catch (Exception e) {
					throw LiveViewExceptionType.UNDERLYING_SB_EXCEPTION.error(e);
				}
				
				break;
			}
			
			case COMMAND_GETVERSION: {
				try {
					sendTuple(idKey++, Version.VERSION_STRING, null, listener, queryModel);
				} catch (Exception e) {
					throw LiveViewExceptionType.UNDERLYING_SB_EXCEPTION.error(e);
				}
				break;
			}
			
			case COMMAND_GETLOGS : {
				try {
					
					File logPath = new File(installPath + "/logs");
					if (!logPath.isDirectory()) {
						throw LiveViewExceptionType.UNDERLYING_SB_EXCEPTION.error(String.format("Could not find the server logs: %s", logPath));
					}
					
					File[] filesList = logPath.listFiles();
					
					for (File file : filesList) {
						if (!file.getName().endsWith(".log")) {
							continue;
						}
						
						sendTuple(idKey++, String.format("Log: %s", file.getName()), null, listener, queryModel);
						
						try (BufferedReader br = new BufferedReader(new FileReader(file))) {
						    String line;
						    while ((line = br.readLine()) != null) {
						    	sendTuple(idKey++, line, null, listener, queryModel);
						    }
						} catch (Exception e) {
							throw LiveViewExceptionType.UNDERLYING_SB_EXCEPTION.error(e);
						}
					}
					
				} catch (Exception e) {
					throw LiveViewExceptionType.UNDERLYING_SB_EXCEPTION.error(e);
				}
	
				break;
			}
			
			default:
				throw LiveViewExceptionType.UNDERLYING_SB_EXCEPTION.error(String.format("Unsupported verb: %s", verb));
			
			}
		}
		private void sendTuple(Long index, String text, ByteArrayView bav, QueryEventListener listener, QueryModel queryModel) throws NullValueException, TupleException {
	    	Tuple tuple = tableSchema.createTuple();
	    	tuple.setLong(NAME_INDEX, index);
	    	tuple.setString(NAME_TEXT, text);
	    	if (bav != null) {
	    		tuple.setBlobBuffer(NAME_BLOB, bav);
	    	}
	    	new TupleAddedEvent(this, index, tuple, queryModel).dispatch(listener);
		}
		
		/*
		 * This method will proxy TSTable request to whatever LVURI the user specifies.
		 */
		private void doProxy(QueryEventListener listener, QueryModel queryModel) throws LiveViewException {
			
			String pred=queryModel.getPredicate();
			String lvuri=pred.substring(pred.indexOf("=")+1);
			
			Query query=null;
			LiveViewConnection lvconn=null;
			try {
				lvconn=LiveViewConnectionFactory.getConnection(lvuri);
				
				final QueryConfig qb = new QueryConfig();
				qb.setQueryType(LiveViewQueryType.SNAPSHOT);
				qb.setQueryString(String.format("select %s from TSTable", queryModel.getProjection()));
				logger.info(String.format("Doing proxy TSTable call: %s to: %s", qb.getQuery(), lvuri));
				
				final SnapshotQueryListener iter = new SnapshotQueryListener();
				query = lvconn.registerQuery(qb, iter);
	
				while (iter.hasNext()) {
					Tuple t=iter.next();
					sendTuple(t.getLong(NAME_INDEX),
							t.isNull(NAME_TEXT) ? null : t.getString(NAME_TEXT),
							t.isNull(NAME_BLOB) ? null : t.getBlobBuffer(NAME_BLOB),
							listener, queryModel);
				}
			} catch (LiveViewException e) {
				throw e;
			} catch (Exception e) {
				throw LiveViewExceptionType.UNDERLYING_SB_EXCEPTION.error(e);
			} finally {
				if (query!=null) {
					query.close();
				}
				if (lvconn != null) {
					lvconn.close();
				}
			}
		}
	}	
}