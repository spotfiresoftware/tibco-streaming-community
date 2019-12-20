/*
* Copyright © 2019. TIBCO Software Inc.
* This file is subject to the license terms contained
* in the license file that is distributed with this file.
*/
package com.tibco.techsupport.tableprovider;

import java.util.Collection;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.streambase.liveview.client.LiveViewConnection;
import com.streambase.liveview.client.LiveViewConnectionFactory;
import com.streambase.liveview.client.LiveViewException;
import com.streambase.liveview.client.LiveViewExceptionType;
import com.streambase.liveview.client.Table;
import com.streambase.liveview.server.table.publisher.TablePublisher;
import com.streambase.sb.Schema;
import com.streambase.sb.Tuple;
import com.streambase.sb.TupleJSONUtil;
import com.streambase.sb.util.Util;

/*
 * ProxyPublisher creates publishers to the supplied LVURI/Table pair. It maintains a map of publishers so
 * they are reused for every publish to the same LVURI/Table.
 * 
 * When a publish occurs, an idle check interval is evaluated. If the interval has expired, 
 * all current publishers are reviewed and if it's been longer than an idle limit time, that publisher
 * is closed and removed.
 */	
public class ProxyPublisher  implements TablePublisher  {

	// map of all publishers
	private Map<String, PublisherHolder> publisherMap = new ConcurrentHashMap<String, PublisherHolder>();

	private long nextIdleCheck=0;
	// used to lazily get the LVNodeInfo table if a service reference is made
	private LiveViewConnection localLVConn=null;

	private Logger logger = LoggerFactory.getLogger(ProxyPublisher.class);
	
    // after idleLimit with no activity, a publish connection is closed and all maps cleared
	private final int idleLimit=Util.getIntSystemProperty("liveview.proxypublish.idlelimit.ms", 120000);
	// check for idle publishers every idleCheckInterval, but for now only looked at when someone publishes
	private final int idleCheckInterval=Util.getIntSystemProperty("liveview.proxypublish.idlecheck.interval.ms", 60000);
  
	public ProxyPublisher() {
	}
	
	@Override
	public void close() throws LiveViewException {
		// nothing we can do here
	}

	@Override
	public Long getLastPublishedId() throws LiveViewException {
		return null;
	}

	@Override
	public String getName() {
		return "ProxyPublisher";
	}

	@Override
	public void upsert(Long id, String CQSDataUpdatePredicate, Boolean CQSDelete, Tuple tuple) throws LiveViewException {

		String lvURI=null;
		PublisherHolder pubHolder=null;
		boolean exception=false;
		String lookupKey=null;
		String tableName=null;
		try {
			if (tuple.isNull(ProxyPublishTable.NAME_LVURI)) {
				if (tuple.isNull(ProxyPublishTable.NAME_SERVICENAME)) {
					throw LiveViewExceptionType.UNDERLYING_SB_EXCEPTION.error("Both LVURI and ServiceName can not be null");
				}
				lookupKey=tuple.getString(ProxyPublishTable.NAME_SERVICENAME);
			} else {
				lookupKey=tuple.getString(ProxyPublishTable.NAME_LVURI);
				lvURI=lookupKey;
			}
			if (tuple.isNull(ProxyPublishTable.NAME_TABLE)) {
				throw LiveViewExceptionType.UNDERLYING_SB_EXCEPTION.error(String.format("Table name can not be null: %s", lookupKey));
			}
			
			tableName=tuple.getString(ProxyPublishTable.NAME_TABLE);
			Boolean delete = (tuple.isNull(ProxyPublishTable.NAME_DELETE)) ? null : tuple.getBoolean(ProxyPublishTable.NAME_DELETE);
			String json=tuple.getString(ProxyPublishTable.NAME_JSONTUPLE);
	
			// first sync on the map, so we don't end up with making PublisherHolder more than once.
			// Which actually, wouldn't be a big deal. But this lock is very cheap.
			synchronized (publisherMap) {
				pubHolder=publisherMap.get(getKey(lookupKey, tableName));
				
				if (pubHolder == null) {
					// Need to make a new publisher.
					pubHolder = new PublisherHolder(lookupKey, tableName);
					publisherMap.put(getKey(lookupKey, tableName), pubHolder);
				}
			}
			
			// Now sync on pubHolder - this lock will be long when it needs to make a connection
			// But notably we aren't blocking any other tables/servers.
			synchronized (pubHolder) {
			
				if (pubHolder.lvconn == null) {
					
					if (lvURI==null) {
						// Need to get the LVURI
						// find the lvuri from service name in the LVNodeInfo table
						if ((localLVConn == null) || !localLVConn.isConnected()) { 
							localLVConn = LiveViewConnectionFactory.getConnection(TableUtils.getEncapsulatingURI());
						}
						lvURI=TableUtils.getLVuriFromService(localLVConn, lookupKey);
					}
					
					// need to get a connection
					pubHolder.lvconn= LiveViewConnectionFactory.getConnection(lvURI);
					
					// Need to fill in the tables schema
					Table table=pubHolder.lvconn.getTable(tableName);
					pubHolder.tableSchema=new Schema(null, table.getFields());
					pubHolder.setTablePublisher(table.getTablePublisher("ProxyPublish")); 
				}
				
				Tuple t=pubHolder.tableSchema.createTuple();
				TupleJSONUtil.setTupleFromJSON(t, json);				
		
				pubHolder.getTablePublisher().publish(delete, t);
			}
		} catch (LiveViewException e) {
			exception=true;
			throw e;
		}  catch (Exception e) {
			exception=true;
			throw LiveViewExceptionType.UNDERLYING_SB_EXCEPTION.error(String.format("Problem upserting to: %s - exception: %s", lvURI, e.getMessage()));
		} finally {
			if (exception) {
				if ((pubHolder != null) && (pubHolder.lvconn != null)) {
					pubHolder.lvconn.close();
					pubHolder.lvconn=null;
					publisherMap.put(getKey(lookupKey, tableName), pubHolder);
				}
			}
			
			if (nextIdleCheck < System.currentTimeMillis()) {
				long now=System.currentTimeMillis();
				nextIdleCheck=now+idleCheckInterval;
				long oldLimit=now-idleLimit;
				
				Collection<PublisherHolder> pubValues= publisherMap.values();
				Iterator<PublisherHolder> it= pubValues.iterator();
				while (it.hasNext()) {
					PublisherHolder p=it.next();
					if (p.lastPublishTime < oldLimit) {
						// this publisher has idled out
						logger.info(String.format("Removing idle publisher: %s", p.lvURI));
						if (p.lvconn != null) {
							p.lvconn.close();
							it.remove();
						}
					}
				}
			}
		}
	}
	
	private String getKey(String lvURI, String tableName) {
		return String.format("%s , %s", lvURI, tableName);
	}

	protected void shtudown() {
		for (PublisherHolder ph : publisherMap.values()) {
			if (ph.getTablePublisher() != null) {
				ph.getTablePublisher().close();
			}
			if (ph.lvconn != null) {
				ph.lvconn.close();
			}
		}
	}
}
