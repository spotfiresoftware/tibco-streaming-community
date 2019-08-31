# Decision Table Schema

This sample demonstrates generating Java wrappers from a decision table 
model (schema file) and using the wrappers to create and populate a 
StreamBase decision table. For simplicity, the content written to the 
decision table is hard-coded.

The following error is initially present when this project is loaded into
StreamBase Studio:

Execution response-xjc of goal org.jvnet.jaxb2.maven2:maven-jaxb2-plugin:
0.13.1: generate failed: org.xml.sax.SAXNotSupportedException: 
FEATURE_SECURE_PROCESSING: Cannot set the feature to false when security 
manager is present. 

To eliminate this error and generate the Java wrappers, run the dt-schema 
launch configuration included with this project:

    Run > Run Configurations... > Maven Build > dt-schema
    
and then refresh the project:

    Project Explorer > right-click dt-schema project > Refresh

Once the wrappers are generated, run CreateDecisionTable.java, which uses
the wrappers to write a sample decision table, sample.sbdt. This decision
table can be opened in Studio's Decision Table editor and loaded into
the Decision Table operator.  

* [Decision Table Schema](src/site/markdown/index.md)