# AMS Client Operator

## Introduction

The AMS client operator demonstrates the use of the TIBCO Artifact Management Server's REST API. The operator can be used for 
enumerating the AMS projects and their artifacts, and for fetching, adding, updating, and deleting artifacts. More importantly,
the operator's source code can be used as the starting point for building your own Java AMS client application.

The .zip file includes two example StreamBase 7.7 applications: one that provides low-level access to AMS's individual REST end points, 
and a second that demonstrates publishing model files to AMS. 

## Details

The operator provides a single control input port and a single results output port. The input port's command field conveys
to the operator which of several commands to execute. Command-specific information, such as the AMS project name and artifact
path, is provided to the operator through a handful of other input port fields. On receipt of an input tuple, the operator validates
the command, reads the additional command-specific information from the input tuple, sends one or more REST requests to the 
AMS server, and emits a tuple on its output port with the results. Output tuples include a copy of the input tuple, a status
tuple to convey the success or failure of the command, and several command-specific fields that hold, for example, the
set of available AMS projects and the artifacts within a project.

The operator contains properties to configure the AMS server connection, including its host name and listening port number,
whether a secure connection is used, and the user name and password of an active AMS account. By default, the operator 
automatically commits all artifact adds, updates, and deletes. Auto-commit behavior can be disabled though a checkbox on 
the operator's configuration page.
