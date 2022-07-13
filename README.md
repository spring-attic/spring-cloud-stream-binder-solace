# spring-cloud-stream-binder-solace is no longer actively maintained by VMware, Inc.


Spring Cloud Stream JMS Binder – Solace Support
-----------------------------------------------

This module provides provisioning functionality as required by [spring-cloud-stream-binder-jms](../../../).

### How it works

Solace allows creating the SCS model by [subscribing queues to topics](http://dev.solacesystems.com/get-started/java-tutorials/topic-queue-mapping_java/).
This capability is enabled by the proprietary Java API.

### Compiling the module

To be able to compile the module Solace Java and JMS API jars need to be 
available. Maven is configured to look for them in the lib folder. 

The required sol-jms, sol-common and sol-jcsmp JARs are bundled together in the 
download available at http://dev.solacesystems.com/downloads/download_jms-api/

The current version was built against solace API v7.1.2

### Running tests

In order to run tests a Solace instance is required. There are virtual images available for download 
as well as images for various cloud providers at http://dev.solacesystems.com/downloads/.

We found the VirtualBox image fairly easy to set up. We have provided `provision_solace.sh` to configure and boot
this image; simply provide the path to the downloaded `.ova` file then update `src/main/test/resources/application.yml`
with the IP address the script outputs.
