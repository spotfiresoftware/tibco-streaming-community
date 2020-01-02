/*
* Copyright © 2019. TIBCO Software Inc.
* This file is subject to the license terms contained
* in the license file that is distributed with this file.
*/
package com.tibco.techsupport.tableprovider;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.shiro.SecurityUtils;
import org.apache.shiro.subject.Subject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.streambase.liveview.client.AbstractQueryListener;
import com.streambase.liveview.client.BeginDeleteEvent;
import com.streambase.liveview.client.EndDeleteEvent;
import com.streambase.liveview.client.LiveViewConnection;
import com.streambase.liveview.client.LiveViewConnectionFactory;
import com.streambase.liveview.client.LiveViewException;
import com.streambase.liveview.client.LiveViewExceptionType;
import com.streambase.liveview.client.LiveViewPermission;
import com.streambase.liveview.client.LiveViewQueryLanguage;
import com.streambase.liveview.client.LiveViewQueryType;
import com.streambase.liveview.client.LiveViewTableCapability;
import com.streambase.liveview.client.OrderDefinition;
import com.streambase.liveview.client.Query;
import com.streambase.liveview.client.QueryClosedEvent;
import com.streambase.liveview.client.QueryConfig;
import com.streambase.liveview.client.QueryListener;
import com.streambase.liveview.client.SnapshotQueryListener;
import com.streambase.liveview.client.Table.TableStatus;
import com.streambase.liveview.client.TupleRemovedEvent;
import com.streambase.liveview.client.TupleUpdatedEvent;
import com.streambase.liveview.client.internal.LiveViewConstants;
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
import com.streambase.sb.Schema;
import com.streambase.sb.Timestamp;
import com.streambase.sb.Tuple;
import com.streambase.sb.util.Msg;

/*
 * This is the ProxyQuery implementation. Through this table, you can send arbitrary queries to any node in the cluster.
 * The LVURL provided will need to have username/password if auth is enabled on the remote node.
 * The query syntax is very simple:
 * 
 * select [ snapshot | delete | continuous ] from ProxyQuery where [ lvuri=<remote URI> | service=<Full.Service.Name[;username:password]> ] query=<LiveQL string>
 * 	return is snapshot or snapshot_and_continuous where the schema is based on the supplied LiveQL projection
 *  The table schema is NOOP(string)
 * 
 * Note that there is a white list of tables that can be queried. The default value for this white list is the LiveView
 * system tables. However you can set liveview.tstableprovider.proxyquery.tablewhitelist to a comma separated list of
 * tables you wish to allow to be proxied. Clearing, or setting to an empty string, this system property will allow
 * all tables to be proxied.
 * 
 * For the delete query command, in addition to the proxy user having delete permission on the target table, the originating
 * user has to have delete table permission to the ProxyQuery table.  
 * 
 * The lvuri can point to any reachable LiveView anywhere. The service name can only be a node in the same cluster.
 * 
 * Examples:
 * select snapshot from ProxyQuery where lvuri=lv://server1.yourcompany.com:20580 query="select * from LVSessionQueries"
 * select snapshot from ProxyQuery where lvuri=lv://myName:myPassword@server2.yourcompany.com:10580 query="select count() as Totals, ICAO from PlanesCleaned group by ICAO"
 * 
 * select delete from ProxyQuery where lvuri=lv://server1.yourcompany.com:10580 query="select * from ItemsSales limit 100"
 * 
 * live select continuous from ProxyQuery where service=MyNode.MyCluster;MyUser:MyPassword query="select * from ItemsSales where category=='toy'"
 * 
 * See the parseQuery method for details 
 */
public class ProxyQueryTable implements Table  {

    private Logger logger = LoggerFactory.getLogger(ProxyQueryTable.class);
    private BlockingQueue<ProxyQueryOperation> queue=new  LinkedBlockingQueue<ProxyQueryOperation>();
    AtomicBoolean shouldRun=new AtomicBoolean(true);
    
    private String NAME_NOOP="NOOP";
    private Schema.Field FIELD_NOOP = new Schema.Field(NAME_NOOP, CompleteDataType.forString());
    private List<String> pKeyFieldArray = Arrays.asList(NAME_NOOP);
    
    private List<String> dataFieldArray = Arrays.asList(NAME_NOOP);
    private Schema tableSchema = new Schema(null, FIELD_NOOP);

    // Some members for holding information about the ProxyQueryTable object 
	private CatalogedTable catalogTable=null;
	private final TableProviderControl helper;
	private final String tableName;
	
	// used to lazily get the LVNodeInfo table if a service reference is made
	private LiveViewConnection localLVConn=null;

	// support a white list of tables
	private final String tableWhiteList = System.getProperty("liveview.tstableprovider.proxyquery.tablewhitelist");
	private final String defaultWhiteList="LiveViewStatistics,LVAlertRulesStatus,LVNodeInfo,LVSessionPublishers,LVSessionQueries,LVSessions,LVTables,LVTableColumns";
	private Set<String> tableWhiteSet=null;
	
	// map so we can close continuous queries when the originating client closes
	private Map<String, QueryHolder> queryMap = new HashMap<String, QueryHolder>();
	
	ProxyQueryThread proxyQueryThread=null;
	
    public ProxyQueryTable(TableProviderControl helper, String tableName) {
		this.helper=helper;
		this.tableName=tableName;
		
		// work is done in a separate thread 
		proxyQueryThread = new ProxyQueryThread(this);
		proxyQueryThread.start();
		
		catalogTable = new CatalogedTable(tableName);
		catalogTable.setStatus(TableStatus.ENABLED, "Ready to Query");
		catalogTable.setCapabilities(EnumSet.of(LiveViewTableCapability.SNAPSHOT, LiveViewTableCapability.CONTINUOUS));
		catalogTable.setQueryLanguages( EnumSet.of(LiveViewQueryLanguage.OTHER));
		catalogTable.setDescription("TableProvider that can execute queries on remote nodes. V1.0");
		catalogTable.setGroup("TechnicalSupport");
        catalogTable.setCreateTime(Timestamp.now());
        catalogTable.setSchema(tableSchema);
       
        catalogTable.setKeyFields(pKeyFieldArray);
        catalogTable.setRuntimeTable(this);
        
        helper.insert(catalogTable);
        
        if (tableWhiteList == null || !tableWhiteList.isEmpty()) {
        	tableWhiteSet=new HashSet<String>();
        }
        if (tableWhiteSet != null) {
        	if (tableWhiteList == null) {
        		tableWhiteSet.addAll(Arrays.asList(defaultWhiteList.split(",")));
        	} else {
        		tableWhiteSet.addAll(Arrays.asList(tableWhiteList.split(",")));
        	}
        }
    }
    
	@Override
	public void addListener(QueryEventListener listener, LiveViewQueryType arg1, QueryModel queryModel) throws LiveViewException {
		// Just add a new query to a work list
		logger.info(String.format("Adding query: %s : %s ID: %s", queryModel.getProjection(), queryModel.getPredicate(), listener.getId()));
		queue.add(new ProxyQueryOperation(listener, queryModel));
	}

	@Override
	public TablePublisher createPublisher(String arg0) throws LiveViewException {
		throw LiveViewExceptionType.PUBLISHING_NOT_SUPPORTED.error();
	}

	@Override
	public void removeListener(QueryEventListener listener) throws LiveViewException {
		logger.info(String.format("Removing query ID: %s", listener.getId()));
		
		QueryHolder qh=queryMap.remove(listener.getId());
		// only need to do anything with continuous queries.
		if (qh != null) {
			qh.query.close();
			qh.lvconn.close();
		}
	}

	public Schema getSchema() {
		return tableSchema;
	}
	
	public String getTableName() {
		return tableName;
	}
	
	public void shutdown() {
		shouldRun.set(false);

		// close all the currently running queries
		for (QueryHolder qh : queryMap.values()) {
			if (qh != null) {
				qh.query.close();
				qh.lvconn.close();
			}
		}
	}
	
	/*
	 * Simple helper class to hold a query in the work list
	 */
	private class ProxyQueryOperation {
		final QueryEventListener listener;
		final QueryModel queryModel;
		
		public ProxyQueryOperation(QueryEventListener listener, QueryModel queryModel) {
			this.listener=listener;
			this.queryModel=queryModel;
		}
	}
	
	// helper class to hold stuff so we can close queries on the proxied server when the client closes
	private class QueryHolder {
		final Query query;
		final LiveViewConnection lvconn;
		
		QueryHolder(Query query, LiveViewConnection lvconn) {
			this.query=query;
			this.lvconn=lvconn;
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
		Pattern queryOK = Pattern.compile("select +(\\w+) +from +(\\w+) +where +(.*)");
		Matcher matcher=queryOK.matcher(lowerQuery);
		if (!matcher.matches()) {
			throw LiveViewExceptionType.INVALID_REQUEST.error(String.format("Failed basic ProxyQueryTable parse: %s", queryString));
		}
		
		String resolvedLVURI;
		queryOK = Pattern.compile("select +(\\w+) +from +(\\w+) +where +lvuri=(\\S+) +query=(.*)");
		matcher=queryOK.matcher(lowerQuery);
		if (!matcher.matches()) {
			queryOK = Pattern.compile("select +(\\w+) +from +(\\w+) +where +service=(\\S+) +query=(.*)");
			matcher=queryOK.matcher(lowerQuery);
			if (!matcher.matches()) {
				throw LiveViewExceptionType.INVALID_REQUEST.error(String.format("ProxyQueryTable could not find lvuri or service: %s", queryString));
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
		
		if (!matcher.matches()) {
			throw LiveViewExceptionType.INVALID_REQUEST.error(String.format("Failed basic ProxyQueryTable parse: %s", queryString));
		}
	
		if (matcher.group(1).equals("delete")) {
			Subject subject = SecurityUtils.getSubject();
	        String permission= String.format("%s:%s:%s", LiveViewPermission.Type.TABLE, LiveViewPermission.Action.DELETE, tableName );
			boolean isPermitted = !subject.isAuthenticated() || subject.isPermitted(permission);
			if (!isPermitted) {
				throw LiveViewExceptionType.UNAUTHORIZED.error("You are not permitted to do a proxy delete");
			}
		}
		
		// Need the case of the URI (passwords) and LiveQL string
		String lvuri=resolvedLVURI;
		String liveQL=queryString.substring(matcher.start(4), matcher.end(4));
		
		QueryConfig queryConfig;
		Query query;
		LiveViewConnection lvconn=null;
		try {
			String parserURI=lvuri;
			if (!parserURI.contains(LiveViewConstants.CLIENT_INFO)) {
				parserURI=String.format("%s?%s=%s", parserURI, LiveViewConstants.CLIENT_INFO, "ProxyQueryParser");
			}
			lvconn=LiveViewConnectionFactory.getConnection(parserURI);
			QueryConfig qc= new QueryConfig();
			qc.setQueryString(liveQL);
			query=lvconn.describeQuery(qc);
			queryConfig=query.getConfig();
			
			if (tableWhiteSet != null) {
				String t=queryConfig.getTable();
				if (!tableWhiteSet.contains(t)) {
					throw LiveViewExceptionType.UNAUTHORIZED.error(String.format("The table %s is not on the ProxyQuery whitelist", t));
				}
			}

			logger.debug(String.format("Fields: %s", query.getFields()));			
		} catch (LiveViewException e) {
			throw e;
		} catch (Exception e) {
			throw LiveViewExceptionType.INVALID_REQUEST.error(e.getMessage());
		} finally  {
			if (lvconn != null) {
				lvconn.close();
			}
		}
			
		List<String> queryKeyList = new ArrayList<String>();
		queryKeyList.add(LiveViewConstants.CQS_INTERNAL_ID);
		String command=matcher.group(1);
		
		QueryModel queryModel = new QueryModel() {
			
			String projection=command;
			String predicate=lvuri + " " + liveQL;
			Schema projectionSchema=new Schema (null, query.getFields());
			
			private Schema.Field FIELD_CQS_INTERNAL_ID = new Schema.Field(LiveViewConstants.CQS_INTERNAL_ID, CompleteDataType.forLong());
		    private Schema keySchema = new Schema(null, FIELD_CQS_INTERNAL_ID);

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
				return projectionSchema;
			}
			
			@Override
			public LiveViewQueryType getQueryType() {
				return queryConfig.getQueryType();
			}
			
			@Override
			public LiveViewQueryLanguage getQueryLanguage() {
				return LiveViewQueryLanguage.LIVEVIEW;
			}
			
			@Override
			public String getProjection() {		
				// this is actually the command to execute. 
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
				return queryKeyList;
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
				return projectionSchema;
			}
			
			@Override
			public List<String> getDataFields() {
				List<Schema.Field> fields=query.getFields();
				List<String> ret = new ArrayList<String>();
				for (Schema.Field f : fields) {
					ret.add(f.getName());
				}
				return ret;
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
	 * The ProxyQueryThread does all the work.
	 */
	private class ProxyQueryThread extends Thread {
		
		private final ProxyQueryTable proxyQueryTable;
		
		public ProxyQueryThread(ProxyQueryTable ts) {
			this.proxyQueryTable=ts;
		}
		
		public void run() {	
			// Now we are ready to start processing queries.
			while (shouldRun.get()) {
				ProxyQueryOperation proxyOp=null;
				try {
					proxyOp=null;
					proxyOp=queue.poll(500, TimeUnit.MILLISECONDS);
					
					if (proxyOp !=null) {
						proxyOp(proxyOp.listener, proxyOp.queryModel);
					}
				} catch (LiveViewException le) {
					if (proxyOp != null) {
						new QueryExceptionEvent(this, le).dispatch(proxyOp.listener);
					}
				} catch (Exception e) {
					logger.warn(Msg.format("Unexpected exception: {0}", e.getMessage()));
				}
			}
		}
		
		/*
		 * This is the method that figures out what to do.
		 */
		private void proxyOp(QueryEventListener listener, QueryModel queryModel) throws LiveViewException {
	
			String verb=queryModel.getProjection();
			String pred=queryModel.getPredicate();
			String lvuri=pred.substring(0, pred.indexOf(" "));
			
			LiveViewConnection lvconn=null;
			if (!lvuri.contains(LiveViewConstants.CLIENT_INFO)) {
				lvuri=String.format("%s?%s=%s", lvuri, LiveViewConstants.CLIENT_INFO, "ProxyQueryExecution");
			}
			try {
				lvconn=LiveViewConnectionFactory.getConnection(lvuri);
			} catch (Exception e) {
				throw LiveViewExceptionType.UNDERLYING_SB_EXCEPTION.error(String.format("Problem connecting to: %s - exception: %s", lvuri, e.getMessage()));
			}
			
			switch (verb.toLowerCase()) {
			
			case "snapshot": {
				doSnapshot(listener, queryModel, lvconn);
				break;
			}
			
			case "delete": {
				doDelete(listener, queryModel, lvconn);
				break;
			}
			
			case "continuous": {
				doContinuous(listener, queryModel, lvconn);
				break;
			}
				
			default:
				throw LiveViewExceptionType.UNDERLYING_SB_EXCEPTION.error(String.format("Unsupported verb: %s", verb));
			
			}
		}
		
		private void doContinuous(QueryEventListener listener, QueryModel queryModel, LiveViewConnection lvconn) {
			
			String pred=queryModel.getPredicate();
			String lvuri=pred.substring(0, pred.indexOf(" "));
			String liveQL=pred.substring(pred.indexOf(" ")+1, pred.length());

			Query query=null;
			QueryConfig qc=null;
			try {
					
				qc = new QueryConfig();
				qc.setQueryType(LiveViewQueryType.SNAPSHOT_AND_CONTINUOUS);
				qc.setQueryString(liveQL);
				logger.debug(String.format("Registering Proxy Continuous ID: %s: %s to: %s", listener.getId(), qc.getQuery(), lvuri));
			
				ProxyContinuousListener proxyListener=new ProxyContinuousListener(listener, queryModel);
				query = lvconn.registerQuery(qc, proxyListener);
				
				// Put this query in the map to be closed later.
				queryMap.put(listener.getId(), new QueryHolder(query, lvconn));
				
            } catch (Exception e) {
            	logger.info(String.format("Proxy Continuous exception: %s to: %s exception: %s", qc.getQuery(), lvuri, e.getMessage()));
				if (lvconn != null) {
					lvconn.close();
				}
				throw e;
			}
		}
		
		private void doDelete (QueryEventListener listener, QueryModel queryModel, LiveViewConnection lvconn) {
			String pred=queryModel.getPredicate();
			String lvuri=pred.substring(0, pred.indexOf(" "));
			String liveQL=pred.substring(pred.indexOf(" ")+1, pred.length());

			Query query=null;
			try {
					
				final QueryConfig qc = new QueryConfig();
				qc.setQueryType(LiveViewQueryType.DELETE);
				qc.setQueryString(liveQL);
				logger.info(String.format("Doing proxy Delete: %s to: %s", qc.getQuery(), lvuri));
						
				final MyDeleteListner myDel = new MyDeleteListner();
				query = lvconn.registerQuery(qc, myDel);

				// using "snapshot" type from client for delete. Maybe we should use Delete instead?
				new BeginSnapshotEvent(this).dispatch(listener);
				
				Exception deleteException=null;
	            try {
					myDel.waitForBegin();
		            logger.info(String.format("Begin Delete: %s to: %s", qc.getQuery(), lvuri));
		            myDel.waitForEnd();
		            logger.info(String.format("End Delete: %s to: %s", qc.getQuery(), lvuri));
		            deleteException=myDel.e;
	            } catch (Exception e) {
	            	deleteException=e;
				}
	            
	            if (deleteException != null) {
	            	logger.info(String.format("Proxy Delete exception: %s to: %s e:%s", qc.getQuery(), lvuri, deleteException.getMessage()));
	            	if (deleteException instanceof LiveViewException) {
	            		new QueryExceptionEvent(this, (LiveViewException)deleteException).dispatch(listener);
	            	} else {
	            		new QueryExceptionEvent(this,LiveViewExceptionType.UNDERLYING_SB_EXCEPTION.error(deleteException)).dispatch(listener);
	            	}
	            }
	            
	        } finally {
	            if (query != null) {
//	            	new com.streambase.liveview.server.event.query.EndDeleteEvent(this).dispatch(listener);
	            	new EndSnapshotEvent(this).dispatch(listener);
	            	query.close();
	            }
				if (lvconn != null) {
					lvconn.close();
				}
	        }	
		}
		
		
		private void doSnapshot(QueryEventListener listener, QueryModel queryModel, LiveViewConnection lvconn) {			
			String pred=queryModel.getPredicate();
			String lvuri=pred.substring(0, pred.indexOf(" "));
			String liveQL=pred.substring(pred.indexOf(" ")+1, pred.length());
						
			Query query=null;
			try {
					
				final QueryConfig qc = new QueryConfig();
				qc.setQueryType(LiveViewQueryType.SNAPSHOT);
				qc.setQueryString(liveQL);
				qc.setIncludeInternal(true);
				logger.info(String.format("Doing proxy Snapshot: %s to: %s", qc.getQuery(), lvuri));
				
				final SnapshotQueryListener iter = new SnapshotQueryListener();
				query = lvconn.registerQuery(qc, iter);
				new BeginSnapshotEvent(this).dispatch(listener);
				
				while (iter.hasNext()) {
					Tuple t=iter.next();
					new TupleAddedEvent(this, t.getLong(LiveViewConstants.CQS_INTERNAL_ID), t, queryModel).dispatch(listener);
				}
				
			} catch (LiveViewException e) {
				new QueryExceptionEvent(this, e).dispatch(listener);
			} catch (Exception e) {
				LiveViewException wrappedE = LiveViewExceptionType.UNDERLYING_SB_EXCEPTION.error(e);
				new QueryExceptionEvent(this, wrappedE).dispatch(listener);
			} finally {
				if (query!=null) {
					query.close();
					new EndSnapshotEvent(this).dispatch(listener);
				}
				if (lvconn != null) {
					lvconn.close();
				}
			}
		}
		
		private class MyDeleteListner extends AbstractQueryListener {
		    
		    private CountDownLatch begin=new CountDownLatch(1);
		    private CountDownLatch end=new CountDownLatch(1);
		    private Exception e=null;
		    
		    public void deleteBegin(final BeginDeleteEvent event) {
		        begin.countDown();
		    }
		    
		    public void deleteEnd(final EndDeleteEvent event) {
		        end.countDown();
		    }
		    
		    public void exceptionRaised(com.streambase.liveview.client.QueryExceptionEvent event) {
		        e=event.getException();
		        end.countDown();
		        begin.countDown();
		    }

		    public void queryClosed(final QueryClosedEvent event) {
		        end.countDown();
		        begin.countDown();
		    }
		    
		    public void waitForBegin() throws Exception {
		        begin.await();
		        if (e != null) {
		            throw e;
		        }
		        return;
		    }
		    
		    public void waitForEnd() throws Exception {
	            end.await();
	            if (e != null) {
	                throw e;
	            }
	            return;
		    }
		}
	
		private class ProxyContinuousListener implements QueryListener {

			private Schema querySchema=null;
			private QueryEventListener listener;
			private QueryModel queryModel;
			
			ProxyContinuousListener(QueryEventListener listener, QueryModel queryModel) {
				this.listener=listener;
				this.queryModel=queryModel;
			}
			
			@Override
			public void deleteBegin(BeginDeleteEvent arg0) {
				// TODO Auto-generated method stub
			}

			@Override
			public void deleteEnd(EndDeleteEvent arg0) {
				// TODO Auto-generated method stub
			}

			@Override
			public void exceptionRaised(com.streambase.liveview.client.QueryExceptionEvent arg0) {
				logger.info(String.format("Got exceptionRaised for ID: %s exception: %s", listener.getId(), arg0.getException().getMessage()));
				new QueryExceptionEvent(this, arg0.getException()).dispatch(listener);
			}

			@Override
			public void queryClosed(QueryClosedEvent arg0) {
				logger.debug(String.format("Got queryClose for ID: %s", listener.getId()));
				
				QueryHolder qh=queryMap.remove(listener.getId());
				// only need to do anything with continuous queries.
				if (qh != null) {
					qh.query.close();
					qh.lvconn.close();
				}
			}

			@Override
			public void snapshotBegin(com.streambase.liveview.client.BeginSnapshotEvent arg0) {
				List<Schema.Field> fields=arg0.getFields();
				querySchema = new Schema(null, fields);
				logger.debug(String.format("Got BEGIN SNAP for ID: %s", listener.getId()));
				
				new BeginSnapshotEvent(this).dispatch(listener);
			}

			@Override
			public void snapshotEnd(com.streambase.liveview.client.EndSnapshotEvent arg0) {
				logger.debug(String.format("Got END SNAP for ID: %s", listener.getId()));
				new EndSnapshotEvent(this).dispatch(listener);
			}

			@Override
			public void tupleAdded(com.streambase.liveview.client.TupleAddedEvent arg0) {
				logger.debug(String.format("ID: %s Add: %s", listener.getId(), arg0.getTuple().toString()));
				new TupleAddedEvent(this, arg0.getKey(), arg0.getTuple(), queryModel).dispatch(listener);
			}

			@Override
			public void tupleRemoved(TupleRemovedEvent arg0) {
				logger.debug(String.format("ID: %s Add: %s", listener.getId(), arg0.getTuple()));
				new com.streambase.liveview.server.event.tuple.TupleRemovedEvent(this, arg0.getKey(), arg0.getTuple(), queryModel).dispatch(listener);
			}

			@Override
			public void tupleUpdated(TupleUpdatedEvent arg0) {
				logger.debug(String.format("ID: %s Add: %s", listener.getId(), arg0.getTuple()));
				List<Schema.Field> changedFields= arg0.getChangedFields();
				List<Integer> changedIndices = new ArrayList<Integer>();
				for (Schema.Field f : changedFields) {
					changedIndices.add(f.getIndex());
				}
				new com.streambase.liveview.server.event.tuple.TupleUpdatedEvent(this, arg0.getKey(), arg0.getTuple(), changedIndices, queryModel).dispatch(listener);
			}
		}
	}
}