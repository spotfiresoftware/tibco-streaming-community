/*
* Copyright © 2019. Cloud Software Group, Inc.
* This file is subject to the license terms contained
* in the license file that is distributed with this file.
*/
package com.tibco.techsupport.tableprovider;

import java.io.File;
import java.io.FileWriter;
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
import com.streambase.sb.CompleteDataType;
import com.streambase.sb.NullValueException;
import com.streambase.sb.Schema;
import com.streambase.sb.Timestamp;
import com.streambase.sb.Tuple;
import com.streambase.sb.TupleException;
import com.streambase.sb.util.Msg;
import com.streambase.sb.util.ProcessReader;
import com.streambase.sb.util.Util;

/*
 * This is the ProxyLVClient implementation. It executes the provided lv-client command in a process spawned
 * from the context of the process that the LiveView server is running as. This table only makes sense when
 * used to proxy lv-client commands to LV servers that can't be directly connected to.
 * 
 * select <ignored> from LVClient where [ lvuri= <RemotelvURI> | service=<My.Service.cluster[;username:password] ] lvcmd=<lv-client command line to execute>
 * return is snapshot only with schema OutputFromCommand(string)
 * 
 * The lvuri can point to any reachable LiveView anywhere. The service name can only be a node in the same cluster.
 *
 * While no validation is done on the lv-client command, this table provider is really intended for use with the
 * administrative commands. Things such as killsession, sessioncontrol, etc. It is not intended to be used for
 * querying (use the ProxyQueryTable for that) - or publishing  (use the ProxyPublishTable for that). 
 * 
 * See the parseQuery method for details 
 */
public class LVClientTable implements Table  {

    private Logger logger = LoggerFactory.getLogger(LVClientTable.class);
    private BlockingQueue<LVTableOperation> queue=new  LinkedBlockingQueue<LVTableOperation>();
    AtomicBoolean shouldRun=new AtomicBoolean(true);
    
    // This is the maximum time the lv-client command will be allowed to run
    private final int processWaitTimeMS=Util.getIntSystemProperty("liveview.tstableprovider.process.wait.ms", 30000);
    
    private String NAME_VALUE="OutputFromCommand";
    Schema.Field FIELD_VALUE = new Schema.Field(NAME_VALUE, CompleteDataType.forString());
    List<String> pKeyFieldArray = Arrays.asList(NAME_VALUE);
	private Schema tableSchema=null;
	private Schema keySchema=null;

    // Some members for holding information about the TSTable object 
	private CatalogedTable catalogTable=null;
	private final TableProviderControl helper;
	private final String tableName;
	
	// used to lazily get the LVNodeInfo table if a service reference is made
	private LiveViewConnection localLVConn=null;
		
	LVClientTableThread lvTableThread=null;
	
	String SLF4J_CONFIG = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\r\n" + 
			"<configuration>\r\n" + 
			" \r\n" + 
			" <!-- Default logging config -->\r\n" + 
			" \r\n" + 
			"  <appender name=\"RootConsoleAppender\" \r\n" + 
			"            class=\"ch.qos.logback.core.ConsoleAppender\">\r\n" + 
			"	<encoder>\r\n" + 
			"      <pattern>%msg%n</pattern>\r\n" + 
			"    </encoder>\r\n" + 
			"  </appender>\r\n" + 
			"      \r\n" + 
			"  <root>\r\n" + 
			"    <level value=\"info\"/>\r\n" + 
			"    <appender-ref ref=\"RootConsoleAppender\"/>         \r\n" + 
			"  </root>\r\n" + 
			"\r\n" + 
			"</configuration>";
	
    public LVClientTable(TableProviderControl helper, String tableName) {
		this.helper=helper;
		this.tableName=tableName;
		
		// work is done in a separate thread 
		lvTableThread = new LVClientTableThread(this);
		lvTableThread.start();
		
		catalogTable = new CatalogedTable(tableName);
		catalogTable.setStatus(TableStatus.ENABLED, "Ready to Execute");
		catalogTable.setCapabilities(EnumSet.of(LiveViewTableCapability.SNAPSHOT));
		catalogTable.setQueryLanguages( EnumSet.of(LiveViewQueryLanguage.OTHER));
		catalogTable.setDescription("TableProvider that proxies lv-client commands. V1.0");
		catalogTable.setGroup("TechnicalSupport");
        catalogTable.setCreateTime(Timestamp.now());
        tableSchema = new Schema(null, FIELD_VALUE);
        keySchema = new Schema(null, FIELD_VALUE);
        catalogTable.setKeyFields(pKeyFieldArray);
        catalogTable.setSchema(tableSchema);
        
        catalogTable.setRuntimeTable(this);
        
        helper.insert(catalogTable);
     }
    
	@Override
	public void addListener(QueryEventListener listener, LiveViewQueryType arg1, QueryModel queryModel) throws LiveViewException {
		// Just add a new query to a work list
		logger.debug(String.format("Adding query: %s : %s", queryModel.getProjection(), queryModel.getPredicate()));
		queue.add(new LVTableOperation(listener, queryModel));
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
	private class LVTableOperation {
		
		public final QueryEventListener listener;
		public final QueryModel queryModel;
		
		public LVTableOperation(QueryEventListener listener, QueryModel queryModel) {
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
		Pattern queryOK = Pattern.compile("select +([\\w\\*]+) +from +(\\w+) +where +(.*)");
		Matcher matcher=queryOK.matcher(lowerQuery);
		if (!matcher.matches()) {
			throw LiveViewExceptionType.INVALID_REQUEST.error(String.format("Failed basic ProxyLVClient parse: %s", queryString));
		}
		
		boolean doProxy=false;
		String resolvedLVURI=null;
		queryOK = Pattern.compile("select +([\\w\\*]+) +from +(\\w+) +where +lvcmd=(.*)");
		matcher=queryOK.matcher(lowerQuery);
		if (!matcher.matches()) {
			doProxy=true;
			
			queryOK = Pattern.compile("select +([\\w\\*]+) +from +(\\w+) +where +lvuri=(\\S+) +lvcmd=(.*)");
			matcher=queryOK.matcher(lowerQuery);
			if (!matcher.matches()) {
				queryOK = Pattern.compile("select +([\\w\\*]+) +from +(\\w+) +where +service=(\\S+) +lvcmd=(.*)");
				matcher=queryOK.matcher(lowerQuery);
				
				if (!matcher.matches()) {
					throw LiveViewExceptionType.INVALID_REQUEST.error(String.format("ProxyLVClient could not find lvuri or service: %s", queryString));
				}
				// find the lvuri from service name in the LVNodeInfo table
				if (localLVConn ==null) {
					localLVConn = LiveViewConnectionFactory.getConnection(TableUtils.getEncapsulatingURI());
				}
				String service=queryString.substring(matcher.start(3), matcher.end(3));
				resolvedLVURI=TableUtils.getLVuriFromService(localLVConn, service);
			} else {
				resolvedLVURI=queryString.substring(matcher.start(3), matcher.end(3));
			}
		}
		String cmd;
		String predicate;
		if (doProxy) {
			cmd=queryString.substring(matcher.start(4));
			predicate=resolvedLVURI + " " + cmd;
		} else {
			cmd=queryString.substring(matcher.start(3));
			predicate=cmd;
		}
		
		String thePrediate=predicate;
		boolean IsProxy=doProxy;
		
		QueryModel queryModel = new QueryModel() {
			
			String projection=(IsProxy) ? "doProxy" : "noProxy";
			String table=tableName;
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
				return true;
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
				return pKeyFieldArray;
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
	private class LVClientTableThread extends Thread {
		
		private final LVClientTable lvTable;
		
		public LVClientTableThread(LVClientTable ts) {
			this.lvTable=ts;
		}
		
		public void run() {	
			// Now we are ready to start processing queries.
			while (shouldRun.get()) {
				LVTableOperation tsOp=null;
				try {
					tsOp=null;
					tsOp=queue.poll(500, TimeUnit.MILLISECONDS);
					
					if (tsOp !=null) {
						LVClientWorkerThread lvct = new LVClientWorkerThread(tsOp.listener, tsOp.queryModel);
						lvct.setDaemon(true);
						lvct.start();
					}
				} catch (Exception e) {
					logger.warn(Msg.format("Unexpected exception: {0}", e.getMessage()));
				} 
			}
		}
	}
	
	/*
	 * The LVClientWorkerThread thread is where the lv-client command is actually run.
	 */
	private class LVClientWorkerThread extends Thread {
		
		final QueryEventListener listener;
		final QueryModel queryModel;
		
		public LVClientWorkerThread (QueryEventListener listener, QueryModel queryModel) {
			this.listener=listener;
			this.queryModel=queryModel;
		}
		
		public void run() {	
			List<String> argsList = new ArrayList<String>();
			long idKey=1;
			
			try {
				new BeginSnapshotEvent(this).dispatch(listener);
				
				String verb=queryModel.getProjection();
				if (verb.equals("doProxy")) {
					doProxy(listener, queryModel);
					return;
				}
				
				String cmd=queryModel.getPredicate();
				logger.info(String.format("Doing lv-client command: %s", cmd));
	
				String classPath = Util.getSystemProperty("java.class.path");
				String javaHome= Util.getSystemProperty("java.home");

				// agh, quiet down slf4j
				String tmpDir=System.getProperty("java.io.tmpdir");
				String infoSLF4J=String.format("%s/%s", tmpDir, "ProxyLVClientTable-slf4j-info.xml");
				File slf4jFile = new File(infoSLF4J);
				if (!slf4jFile.exists()) {
					FileWriter slf4jWriter = new FileWriter(slf4jFile.getAbsolutePath());
					slf4jWriter.write(SLF4J_CONFIG);
					slf4jWriter.close();
				}
				
				argsList.add(String.format("%s/bin/java", javaHome));
				argsList.add("-Xmx512m");
				argsList.add(String.format("-Dlogback.configurationFile=%s", slf4jFile.getAbsolutePath()));
				argsList.add("-cp");
				argsList.add(classPath);
				argsList.add("com.streambase.liveview.client.cmdline.LVCommandShell");
				
				// splitting like this is very weak.
				String[] args=cmd.split(" ");
				argsList.addAll(Arrays.asList(args));

				logger.debug(String.format("Built command: %s", argsList.toString()));
				String [] ars=new String[argsList.size()];
				argsList.toArray(ars);
				ProcessBuilder pb = new ProcessBuilder(ars);
				
				ProcessReader pr = new ProcessReader(pb.start());
				pr.waitFor(processWaitTimeMS);
				
				if (pr.exitValue()!=0) {
					logger.warn("Error getting snapshot");
				}
				
				String[] stdout= pr.getStdOutLines();
				if ((stdout.length > 1) || !stdout[0].isEmpty()) {
					for (String l : stdout) {
				    	Tuple tuple = tableSchema.createTuple();
				    	tuple.setString(NAME_VALUE, "stdout: " + l);
				    	new TupleAddedEvent(this, idKey++, tuple, queryModel).dispatch(listener);
						if (queryModel.hasLimit()) {
							if (queryModel.getLimit() < idKey) {
								throw LiveViewExceptionType.SNAPSHOT_OVER_LIMIT.error();
							}
						}
					}
				}
				String[] errout= pr.getStdErrLines();
				if ((errout.length > 1) || !errout[0].isEmpty()) {
					for (String l : errout) {
				    	Tuple tuple = tableSchema.createTuple();
				    	tuple.setString(NAME_VALUE, "stderr: " + l);
				    	new TupleAddedEvent(this, idKey++, tuple, queryModel).dispatch(listener);
						if (queryModel.hasLimit()) {
							if (queryModel.getLimit() < idKey) {
								throw LiveViewExceptionType.SNAPSHOT_OVER_LIMIT.error();
							}
						}
					}
				}
				new EndSnapshotEvent(this).dispatch(listener);
				
			} catch (LiveViewException e) {
				new QueryExceptionEvent(this, e).dispatch(listener);
				return;
			} catch (Exception e) {
				new QueryExceptionEvent(this, LiveViewExceptionType.UNDERLYING_SB_EXCEPTION.error(e)).dispatch(listener);
				return;
			}
		
		}
		
		private void sendTuple(long index, String text, QueryEventListener listener, QueryModel queryModel) throws NullValueException, TupleException {
	    	Tuple tuple = tableSchema.createTuple();
	    	tuple.setString(NAME_VALUE, text);
	    	new TupleAddedEvent(this, index, tuple, queryModel).dispatch(listener);
		}
		
		/*
		 * This method will proxy LVClient request to whatever LVURI the user specifies.
		 */
		private void doProxy(QueryEventListener listener, QueryModel queryModel) throws LiveViewException {
			
			String pred=queryModel.getPredicate();
			String lvuri=pred.substring(0, pred.indexOf(" "));
			String lvCmd=pred.substring(pred.indexOf(" ")+1);
			
			long idKey=1;
			
			Query query=null;
			LiveViewConnection lvconn=null;
			try {
				lvconn=LiveViewConnectionFactory.getConnection(lvuri);
				
				final QueryConfig qb = new QueryConfig();
				qb.setQueryType(LiveViewQueryType.SNAPSHOT);
				qb.setTable("ProxyLVClient");
				qb.setQueryString(String.format("select noop from ProxyLVClient where lvcmd=%s", lvCmd));
				logger.info(String.format("Doing proxy LVClient : %s to: %s", lvCmd, lvuri));

				final SnapshotQueryListener iter = new SnapshotQueryListener();
				query = lvconn.registerQuery(qb, iter);
			
				while (iter.hasNext()) {
					Tuple t=iter.next();
					sendTuple(idKey++, t.getString(NAME_VALUE), listener, queryModel);
				}
				new EndSnapshotEvent(this).dispatch(listener);
				
			} catch (LiveViewException e) {
				new QueryExceptionEvent(this, e).dispatch(listener);
				return;
			} catch (Exception e) {
				new QueryExceptionEvent(this, LiveViewExceptionType.UNDERLYING_SB_EXCEPTION.error(e)).dispatch(listener);
				return;
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