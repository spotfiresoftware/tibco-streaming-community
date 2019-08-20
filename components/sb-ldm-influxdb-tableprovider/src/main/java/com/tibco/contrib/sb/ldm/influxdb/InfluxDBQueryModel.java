package com.tibco.contrib.sb.ldm.influxdb;
import java.util.List;

import com.streambase.liveview.client.LiveViewException;
import com.streambase.liveview.client.LiveViewExceptionType;
import com.streambase.liveview.client.LiveViewQueryLanguage;
import com.streambase.liveview.client.LiveViewQueryType;
import com.streambase.liveview.client.OrderDefinition;
import com.streambase.liveview.server.query.QueryModelBase;
import com.streambase.liveview.server.table.CatalogedTable;
import com.streambase.sb.expr.SBExpr;
import com.streambase.sb.util.Util;

/**
 * Implementation Notes:
 * <p>
 * <ul>
 * <li>TIBCO LiveView Desktop uses a standard "WHERE TRUE" clause, that InfluxDB does not accept,
 *       and thus this is replaced with a different condition that always holds true</li>
 *       
 * <li>influxdb recommends double-quoting all identifiers (in part, they accept spaces, start with numbers, etc.),
 *       and single-quote string values to match; the user is responsible for this</li>
 * </ul>
 */
public class InfluxDBQueryModel extends QueryModelBase {

	private final InfluxDBTable influxDBTable;
	private final String queryString;
	
	public InfluxDBQueryModel(CatalogedTable catalogedTable, InfluxDBTable influxDBTable, String queryString, LiveViewQueryType type, String additionalPredicate) {
		super(catalogedTable, queryString, type, additionalPredicate);
		this.influxDBTable = influxDBTable;
		this.queryString = cleanupQueryString(queryString);
		resultSchema = influxDBTable.getSchema();
	}

	private String cleanupQueryString(String input) {
		String result = input;
		// make case insensitive
		if (input.matches("(?i).*WHERE\\s+TRUE\\s+.*")) { //$NON-NLS-1$
			result = input.replaceFirst("(?)WHERE\\s+TRUE", "WHERE time > 0"); //$NON-NLS-1$ //$NON-NLS-2$
		}
		return result;
	}

	@Override
	public void validate(CatalogedTable catalogedTable, boolean includeInternalFields) throws LiveViewException {
		// a real example would validate the syntax here as much as able to
		if (Util.isEmpty(queryString)) {
			throw LiveViewExceptionType.INVALID_QUERY.error();
		}
	}

	@Override
	public String getTable() {
		return influxDBTable.getTableName();
	}

	public String getQueryString() {
		return queryString;
	}
	
	
	//
	// These are not implemented by this table provider, and are needed only in advanced situations
	// 
	
	@Override
	public String getProjection() {
		return null;
	}

	@Override
	public String getPredicate() {
		return null;
	}

	@Override
	public boolean hasPredicate() {
		return false;
	}

	@Override
	public boolean hasTemporalPredicate() {
		return false;
	}

	@Override
	public long getTemporalPredicateComponent() throws UnsupportedOperationException {
		return NO_TIME_PREDICATE;
	}

	@Override
	public boolean hasGroupBy() {
		return false;
	}

	@Override
	public List<String> getGroupBy() throws UnsupportedOperationException {
		return null;
	}

	@Override
	public boolean hasOrderBy() {
		return false;
	}

	@Override
	public boolean hasLimit() {
		return false;
	}

	@Override
	public int getLimit() throws UnsupportedOperationException {
		return NO_LIMIT;
	}

	@Override
	public boolean hasTimeWindowedPredicate() {
		return false;
	}

	@Override
	public String getTimestampField() throws UnsupportedOperationException {
		return null;
	}

	@Override
	public String getWindowStartExpr() throws UnsupportedOperationException {
		return null;
	}

	@Override
	public String getWindowEndExpr() throws UnsupportedOperationException {
		return null;
	}

	@Override
	public OrderDefinition getOrderDefinition() throws UnsupportedOperationException {
		return OrderDefinition.NONE;
	}

	@Override
	public String getPivotAggExpression() {
		return null;
	}

	@Override
	public String getPivotField() {
		return null;
	}

	@Override
	public String getPivotValues() {
		return null;
	}

	@Override
	public LiveViewQueryLanguage getQueryLanguage() {
		return LiveViewQueryLanguage.OTHER; 
	}

	@Override
	public boolean hasPivot() {
		return false;
	}

	@Override
	public String getOriginalQuery() {
		return queryString;
	}

	@Override
	public String getDisplayablePredicate() {
		return "";
	}

	@Override
	public boolean hasHaving() {
		return false;
	}
	@Override
	public String getHaving() {
		return null;
	}
	@Override
	public SBExpr getHavingExpr() {
		return null;
	}


	@Override
	public String getEnhancedToString() {
		return toString();
	}

}
