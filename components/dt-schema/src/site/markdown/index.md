# StreamBase Decision Table Schema

## Introduction

This sample demonstrates generating Java wrappers from a decision table model (schema file) and
using the wrappers to create and populate a StreamBase decision table. For simplicity, the actual 
content written to the decision table is hard-coded.

## Details

The sample as packaged as an Eclipse Maven project that contains a resources folder with the decision 
table model schema file, DecisionTable.xsd. The JAXB2 Maven Plugin compiles the schema into a set of
Java wrappers. The CreateDecisionTable Java class that ships with this sample uses the Java wrappers 
to create, populate, and serialize a StreamBase decision table to sample.sbdt. 

Instructions for use:

1. Import the sample .zip file into Eclipse (version 4.6.0 was used in developing this sample)

2. Build the sample by creating and running a Maven Build run configuration with a goal of package

3. Run the CreateDecisionTable class, which displays a message similar to the followingin the Console view:

`1489 bytes written to /.../dt-schema/sample.sbdt`
