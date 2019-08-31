package com.tibco.ep.community.dtschema;


import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.io.StringWriter;
import java.io.Writer;
import java.util.Calendar;
import java.util.List;

import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBElement;
import javax.xml.bind.Marshaller;
import javax.xml.namespace.QName;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

import org.w3c.dom.Document;

import com.sun.xml.bind.marshaller.NamespacePrefixMapper;
import com.tibco.ep.ams.model.response.Table;
import com.tibco.ep.ams.model.response.Table.DecisionTable;
import com.tibco.ep.ams.model.response.Table.DecisionTable.Columns;
import com.tibco.ep.ams.model.response.Table.DecisionTable.Columns.Column;
import com.tibco.ep.ams.model.response.Table.DecisionTable.Rule;
import com.tibco.ep.ams.model.response.Table.DecisionTable.Rule.Act;
import com.tibco.ep.ams.model.response.Table.DecisionTable.Rule.Cond;
import com.tibco.ep.ams.model.response.Table.Md;
import com.tibco.ep.ams.model.response.Table.Md.Prop;

/**
 * This sample demonstrates generating Java wrappers from a decision table model (schema file) and
 * using the wrappers to create and populate a StreamBase decision table. For simplicity, the actual
 * content written to the decision table is hard-coded.
 *
 * A StreamBase decision table is serialized to XML as in this example
 *
 * <pre>
 * {@code
 * <?xml version="1.0" encoding="UTF-8"?>
 * <Table:Table xmlns:Table="http:///com/tibco/cep/decision/table/model/DecisionTable.ecore" xmlns:xmi="http://www.omg.org/XMI" version="2.0">
 *     <md>
 *         <prop name="Priority" value="5"/>
 *         <prop name="EffectiveDate" type="String" value="2018-03-22 15:06:39"/>
 *         <prop name="ExpiryDate" type="String" value="2018-03-23 15:06:41"/>
 *         <prop name="SingleRowExecution" type="Boolean" value="true"/>
 *     </md>
 *     <decisionTable>
 *         <rule id="1">
 *             <cond colId="0" expr="Alice" id="1_0"/>
 *             <cond colId="1" expr="34" id="1_1"/>
 *             <act colId="2" expr="true" id="1_2"/>
 *             <act colId="3" expr="2500" id="1_3"/>
 *             <act colId="4" expr="Visa Granted" id="1_4"/>
 *         </rule>
 *         <rule id="2">
 *             <cond colId="0" expr="Bob" id="2_0"/>
 *             <cond colId="1" expr="61" id="2_1"/>
 *             <act colId="2" expr="false" id="2_2"/>
 *             <act colId="3" expr="5000" id="2_3"/>
 *             <act colId="4" expr="N/A" id="2_4"/>
 *         </rule>
 *         <columns>
 *             <column columnType="CONDITION" id="0" name="Name" propertyPath="Name"/>
 *             <column columnType="CONDITION" id="1" name="Age" propertyPath="Age" propertyType="1"/>
 *             <column columnType="ACTION" id="2" name="Eligible" propertyPath="Eligible" propertyType="4"/>
 *             <column columnType="ACTION" id="3" name="CreditLimit" propertyPath="CreditLimit" propertyType="1"/>
 *             <column columnType="ACTION" id="4" name="Status" propertyPath="Status"/>
 *         </columns>
 *     </decisionTable>
 * </Table:Table>
 * }
 * </pre>
 *
 * <p>
 * The IDs of the rules, columns, conditions, actions, are related and are assigned as follows:
 *
 *  <ul>
 *  <li>Each rule is assigned a one-based, monotonically increasing ID.
 *  <li>Each column is assigned a zero-based, monotonically increasing ID.
 *  <li>Each condition and action includes a colId indicating its association with a particular table column.
 *  <li>Each condition and action also includes an id, which is formatted as "ruleID_columnID".
 *  </ul>
 *
 *  <p>
 *  Note that although the various IDs are hardcoded for simplicity in the example below, they would be expected
 *  to assigned programmatically using incrementing counters in a production application.
 *
 * @author jklumpp
 */
public class CreateDecisionTable {

    /**
     * Demonstrates using Java wrappers generated from a decision table model schema (xsd)
     * to create a StreamBase decision table
     *
     * @param args ignored
     */
    public static void main(String[] args) {

        try {

            // Create a decision table
            Table table = new Table();
            table.setVersion("2.0");

            // Create metadata for the decision table
            Md metadata = new Md();

            // Add properties to the metadata
            List<Prop> properties = metadata.getProp();
            properties.add(new Prop("Priority", null, "5"));
            Calendar now = Calendar.getInstance();
            int year = now.get(Calendar.YEAR);
            int month = now.get(Calendar.MONTH)+1;
            int day = now.get(Calendar.DATE);
            properties.add(new Prop("EffectiveDate", "String", String.format("%d-%02d-%02d", year, month, day)));
            properties.add(new Prop("ExpiryDate", "String", String.format("%d-%02d-%02d", year+1, month, day)));
            properties.add(new Prop("SingleRowExecution", "Boolean", "true"));

            // Add the metadata to the decision table
            table.setMd(metadata);

            // Create the body of the decision table
            DecisionTable decisionTable = new DecisionTable();

            // Create the decision table's columns
            decisionTable.setColumns(new Columns());
            List<Column> columns = decisionTable.getColumns().getColumn();
            columns.add(new Column("0", "Name", "Name", null, "CONDITION"));
            columns.add(new Column("1", "Age", "Age", "1", "CONDITION"));
            columns.add(new Column("2", "Eligible", "Eligible", "4", "ACTION"));
            columns.add(new Column("3", "CreditLimit", "CreditLimit", "1", "ACTION"));
            columns.add(new Column("4", "Status", "Status", null, "ACTION"));

            // Create rules (rows) for the decision table
            List<Rule> rules = decisionTable.getRule();

            // First rule
            Rule rule = new Rule();
            rule.setId("1");

            // First rule's conditions
            List<Cond> conditions = rule.getCond();
            conditions.add(new Cond("1_0", "0", "Alice"));
            conditions.add(new Cond("1_1", "1", "34"));

            // First rule's actions
            List<Act> actions = rule.getAct();
            actions.add(new Act("1_2", "2", "true"));
            actions.add(new Act("1_3", "3", "2500"));
            actions.add(new Act("1_4", "4", "Visa Granted"));

            // Add the first rule to the decision table rules
            rules.add(rule);

            // Second rule
            rule = new Rule();
            rule.setId("2");

            // Second rule's conditions
            conditions = rule.getCond();
            conditions.add(new Cond("2_0", "0", "Bob"));
            conditions.add(new Cond("2_1", "1", "61"));

            // Second rule's actions
            actions = rule.getAct();
            actions.add(new Act("2_2", "2", "false"));
            actions.add(new Act("2_3", "3", "5000"));
            actions.add(new Act("2_4", "4", "N/A"));

            // Add the second rule to the decision table rules
            rules.add(rule);

            // Add the body to the decision table
            table.setDecisionTable(decisionTable);

            // Write the serialized decision table to a file
            serialize(table, new File("sample.sbdt"));
        }
        catch (Exception e) {
            // Minimal error handling
            e.printStackTrace();
        }
    }

    public static class MyNamespaceMapper extends NamespacePrefixMapper {

        private static final String XMI_PREFIX = "xmi";
        private static final String XMI_URI = "http://www.omg.org/XMI";

        private static final String TABLE_PREFIX = "Table";
        private static final String TABLE_URI = "http:///com/tibco/cep/decision/table/model/DecisionTable.ecore";

        @Override
        public String getPreferredPrefix(String namespaceUri, String suggestion, boolean requirePrefix) {
            if (TABLE_URI.equals(namespaceUri)) {
                return TABLE_PREFIX;
            }
            if (XMI_URI.equals(namespaceUri)) {
                return XMI_PREFIX;
            }
            return suggestion;
        }

        @Override
        public String[] getPreDeclaredNamespaceUris() {
            return new String[] {XMI_URI, TABLE_URI};
        }
    }

    /**
     * Serializes a decision table and writes it to a file
     *
     * @param table the decision table
     * @param file the file to write the serialized decision table to
     * @throws Exception if any errors occur
     */
    private static void serialize(Table table, File file) throws Exception {
        JAXBContext context = JAXBContext.newInstance(table.getClass());
        Marshaller m = context.createMarshaller();
        m.setProperty("com.sun.xml.bind.namespacePrefixMapper", new MyNamespaceMapper());
        StringWriter writer = new StringWriter();
        m.marshal(new JAXBElement<Table>(new QName("Table:Table"), Table.class, table), writer);
        DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
        dbf.setValidating(false);
        DocumentBuilder db = dbf.newDocumentBuilder();
        Document doc = db.parse(new ByteArrayInputStream(writer.toString().getBytes()));
        int len = prettyPrint(doc, new FileOutputStream(file));
        System.out.println(String.format("%d bytes written to %s", len, file.getCanonicalPath()));
    }

    /**
     * Formats the serialized decision table, including adding newlines and indentation and writes it to an output stream.
     *
     * @param xml an XML document
     * @param os an output stream
     * @return the number of bytes written to the output stream
     * @throws Exception if any errors occur
     */
    public static final int prettyPrint(Document xml, OutputStream os) throws Exception {
        TransformerFactory tf = TransformerFactory.newInstance();
        tf.setAttribute("indent-number", "4");
        Transformer transformer = tf.newTransformer();
        transformer.setOutputProperty(OutputKeys.ENCODING, "UTF-8");
        transformer.setOutputProperty(OutputKeys.INDENT, "yes");
        Writer out = new StringWriter();
        transformer.transform(new DOMSource(xml), new StreamResult(out));
        byte[] bytes = out.toString().getBytes();
        os.write(bytes);
        return bytes.length;
    }
}
