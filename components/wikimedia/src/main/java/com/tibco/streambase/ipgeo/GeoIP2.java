package com.tibco.streambase.ipgeo;


import java.io.IOException;
import java.io.InputStream;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.List;

import com.maxmind.geoip2.DatabaseReader;
import com.maxmind.geoip2.exception.GeoIp2Exception;
import com.maxmind.geoip2.model.CityResponse;
import com.maxmind.geoip2.model.CountryResponse;
import com.maxmind.geoip2.record.Location;
import com.streambase.sb.DataType;
import com.streambase.sb.Schema;
import com.streambase.sb.StreamBaseException;
import com.streambase.sb.Tuple;
import com.streambase.sb.operator.Operator;
import com.streambase.sb.operator.Parameterizable;
import com.streambase.sb.operator.ResourceNotFoundException;
import com.streambase.sb.operator.TypecheckException;

public class GeoIP2 extends Operator implements Parameterizable {

	public static final long serialVersionUID = 1407939125056L;
	// Properties
	private String countryDBFile, cityDBFile;
	private String IPInputFieldName;
	private String continentOutputFieldName;
	private String countryCodeOutputFieldName;
	private String stateCodeOutputFieldName;
	private String cityOutputFieldName;
	private String latOutputFieldName, lonOutputFieldName;
	private String displayName = "IP to Geo information";
	private DatabaseReader countryDB;
	private DatabaseReader cityDB;

	public GeoIP2() {
		super();
		setPortHints(1, 1);
		setDisplayName(displayName);
		setShortDisplayName(this.getClass().getSimpleName());
		setCountryDBFile("");
		setCityDBFile("");
		
		setIPInputFieldName("IP");
		setContinentOutputFieldName("Continent");
		setCountryCodeOutputFieldName("CountryCode");
		setStateCodeOutputFieldName("StateCode");
		setCityOutputFieldName("City");
		setLatOutputFieldName("lat");
		setLonOutputFieldName("lon");
	}

	public void typecheck() throws TypecheckException {
		try {
			getResourceContents(getCountryDBFile()).close();
			getResourceContents(getCityDBFile()).close();
		} catch (ResourceNotFoundException e) {
			throw new TypecheckException(e);
		} catch (IOException e) {
			throw new TypecheckException(e);
		} catch (StreamBaseException e) {
			throw new TypecheckException(e);
		}
		List<Schema.Field> outputFields = new ArrayList<Schema.Field>();
		outputFields.addAll(getInputSchema(0).fields());
		
		outputFields.add(Schema.createField(DataType.STRING, getContinentOutputFieldName()));
		outputFields.add(Schema.createField(DataType.STRING, getCountryCodeOutputFieldName()));
		outputFields.add(Schema.createField(DataType.STRING, getStateCodeOutputFieldName()));
		outputFields.add(Schema.createField(DataType.STRING, getCityOutputFieldName()));
		outputFields.add(Schema.createField(DataType.DOUBLE, getLatOutputFieldName()));
		outputFields.add(Schema.createField(DataType.DOUBLE, getLonOutputFieldName()));
		
		Schema outputSchema = new Schema(null, outputFields);
		setOutputSchema(0, outputSchema);
	}

	public void processTuple(int inputPort, Tuple tuple)
			throws StreamBaseException {

		Tuple output = getOutputSchema(0).createTuple();
		//copy input
		for (Schema.Field f : tuple.getSchema()) {
			output.setField(f.getName(), tuple.getField(f));
		}
		
		
		String ip = tuple.getString(getIPInputFieldName());
		try {
			// set country code field
			CountryResponse country = countryDB.country(InetAddress.getByName(ip));
			output.setField(getContinentOutputFieldName(),  country.getContinent().getName());
			output.setField(getCountryCodeOutputFieldName(), country.getCountry().getIsoCode());
			
			CityResponse city = cityDB.city(InetAddress.getByName(ip));
			output.setField(getCityOutputFieldName(), city.getCity().getName());
			output.setField(getStateCodeOutputFieldName(), city.getMostSpecificSubdivision().getIsoCode());
			Location cityLoc = city.getLocation();
			if (cityLoc != null) {
				output.setField(getLatOutputFieldName(), cityLoc.getLatitude());
				output.setField(getLonOutputFieldName(), cityLoc.getLongitude());
			}
			
			sendOutput(0, output);
		} catch (UnknownHostException e) {
			// not an ip address; we'll let the output field stay null
			sendOutput(0, output);
		} catch (IOException e) {
			sendErrorOutput(e);
		} catch (GeoIp2Exception e) {
			sendErrorOutput(e);
		}
	}

	public void init() throws StreamBaseException {
		super.init();
		InputStream countryDBStream = getResourceContents(getCountryDBFile());
		InputStream cityDBStream = getResourceContents(getCityDBFile());
		try {
			countryDB = new DatabaseReader.Builder(countryDBStream).build();
			cityDB = new DatabaseReader.Builder(cityDBStream).build();
		} catch (IOException e) {
			throw new StreamBaseException(e);
		}
	}
	
	@Override
	public void shutdown() {
		try {
			countryDB.close();
			cityDB.close();
		} catch (IOException e) { /** ignore */ }
		super.shutdown();
	}


	public void setCountryDBFile(String dbfile) {
		this.countryDBFile = dbfile;
	}

	public String getCountryDBFile() {
		return this.countryDBFile;
	}

	public void setIPInputFieldName(String IPFieldName) {
		this.IPInputFieldName = IPFieldName;
	}

	public String getIPInputFieldName() {
		return this.IPInputFieldName;
	}

	public String getCountryCodeOutputFieldName() {
		return countryCodeOutputFieldName;
	}

	public void setCountryCodeOutputFieldName(String countryCodeOutputFieldName) {
		this.countryCodeOutputFieldName = countryCodeOutputFieldName;
	}

	public String getStateCodeOutputFieldName() {
		return stateCodeOutputFieldName;
	}

	public void setStateCodeOutputFieldName(String stateCodeOutputFieldName) {
		this.stateCodeOutputFieldName = stateCodeOutputFieldName;
	}

	public String getLatOutputFieldName() {
		return latOutputFieldName;
	}
	
	public void setLatOutputFieldName(String latOutputFieldName) {
		this.latOutputFieldName = latOutputFieldName;
	}
	
	public String getLonOutputFieldName() {
		return lonOutputFieldName;
	}
	
	public void setLonOutputFieldName(String lonOutputFieldName) {
		this.lonOutputFieldName = lonOutputFieldName;
	}
	
	public String getCityDBFile() {
		return cityDBFile;
	}

	public void setCityDBFile(String cityDBFile) {
		this.cityDBFile = cityDBFile;
	}
	
	public String getContinentOutputFieldName() {
		return continentOutputFieldName;
	}
	
	public void setContinentOutputFieldName(String continentOutputFieldName) {
		this.continentOutputFieldName = continentOutputFieldName;
	}
	
	public String getCityOutputFieldName() {
		return cityOutputFieldName;
	}
	
	public void setCityOutputFieldName(String cityOutputFieldName) {
		this.cityOutputFieldName = cityOutputFieldName;
	}

}
