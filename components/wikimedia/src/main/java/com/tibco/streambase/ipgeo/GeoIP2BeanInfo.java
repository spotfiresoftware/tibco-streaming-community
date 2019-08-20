package com.tibco.streambase.ipgeo;

import java.beans.*;

import com.streambase.sb.operator.parameter.*;

/**
 * A BeanInfo class controls what properties are exposed, add 
 * metadata about properties (such as which properties are optional), and access 
 * special types of properties that can't be automatically derived via reflection. 
 * If a BeanInfo class is present, only the properties explicitly declared in
 * this class will be exposed by StreamBase.
 */
public class GeoIP2BeanInfo extends SBSimpleBeanInfo {

	/*
	 * The order of properties below determines the order they are displayed within
	 * the StreamBase Studio property view. 
	 */
	public SBPropertyDescriptor[] getPropertyDescriptorsChecked()
			throws IntrospectionException {
		SBPropertyDescriptor[] p = {
				new ResourceFilePropertyDescriptor("countryDBFile", GeoIP2.class)
						.displayName("GeoLite2 Country DataBase File").description(""),
				new ResourceFilePropertyDescriptor("cityDBFile", GeoIP2.class)
						.displayName("GeoLite2 City DataBase File").description(""),
				new SBPropertyDescriptor("IPInputFieldName", GeoIP2.class)
						.displayName("IP Address Input Field Name").description(""),
				new SBPropertyDescriptor("ContinentOutputFieldName", GeoIP2.class)
						.displayName("Continent Output Field Name").description(""),
				new SBPropertyDescriptor("CountryCodeOutputFieldName", GeoIP2.class)
						.displayName("Country Code Output Field Name").description(""),
				new SBPropertyDescriptor("StateCodeOutputFieldName", GeoIP2.class)
						.displayName("State Code Output Field Name").description(""),
				new SBPropertyDescriptor("CityOutputFieldName", GeoIP2.class)
				.displayName("City Output Field Name").description(""),
				new SBPropertyDescriptor("LatOutputFieldName", GeoIP2.class)
				.displayName("Latitude Output Field Name").description(""),
				new SBPropertyDescriptor("LonOutputFieldName", GeoIP2.class)
				.displayName("Longitude Output Field Name").description(""),
						};
		return p;
	}

}
