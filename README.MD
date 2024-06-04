# Kafkaesque Middleware

Prepared by: Alfred Lopez, Lead Engineer
November 14th, 2019

***[NOTE: This project is not entirely buildable or deployable. It is purposely missing key configuration/deployment files. This is for demonstration only, and to showcase my development. Though, with a little work, and insight from the documentation, it can be "made whole".]***

### Objective

This document proposes being the official source for the Kafkaesque middleware software, its uses, and its
technologies, as well as any future development. It does not go into every detail, but highlights the key areas of
operation. Everything else can be understood by reading the vertx documentation as well as the code.

## TECHNICAL ASPECTS

### A Little History

In the beginning, there was Jumbotron: an application now known as _,_ as of this writing, the **_Investment Data
Portal_** ( **IDP** ). Its purpose is to provide up to the minute information for Portfolio Managers, in a portal-like manner.
It allows Portfolio Analysts to dictate **what** data is displayed: in the form of _strategy scorecards,_ and _live reports_.
To accomplish this, IDP replicates data from disparate sources, processes its presentation format and displays the
information on a web browser. Though IDP's design allows for many sources, it currently pulls data from only one.
As data requirements became from complex, we needed a way to allow the business units to dictate **where** the
data comes from. IDP requires programmer intervention, but we needed a way to allow the business to self-serve.
Enter **_Kafkaesque_**.

**_Kafkaesque_** gets its name from **_Apache Kafka_**. **_Kafkaesque_** starts up an instance of **_Kafka Connect_** as well
as the dependent systems, **_Kafka_** and **_Zookeeper_**. Kafkaesque connects to data sources using Kafka
Connectors: both source and sink. Besides Kafka Connect, Kafkaesque integrate other network protocols, thanks
to its underlying technology, **_Vertx_** ( _[http://vertx.io](http://vertx.io)_ ).

Kafkaesque borrows many of the processes and infrastructure from IDP, several of which have been streamlined or
refactored due to afterthought. These will be covered in detail in the following sections. The first, and foremost,
thing to consider is understanding how a **_Vertx_** application works, and for that, I will refer you to the **_Vertx_**
documentation.

### The Startup Process

The first module that was refactored from IDP is the startup sequence^1. In IDP, the startup sequence relies heavily
on "custom code", i.e., every time we add another verticle, new code has to be added to manage it and its
dependencies. We decided to abstract the process by introducing the **_DefaultQuartzVerticle_** class. This class
inherits from **_io.vertx.core.AbstractVerticle_** and implements the **_Quartz's TriggerListener_** and **_JobListener_**
interfaces. As with IDP, Kafkaesque has a main verticle ( **_Application_** ) that is responsible for deploying all other
verticles. All the verticles extend **_DefaultQuartzVerticle. DefaultQuartzVerticle_** deploys its dependents first,
before starting itself up, thereby guaranteeing that dependent services are available. It does this in a recursive
fashion, by reading in configuration information in JSON form. A typical configuration looks like the following:

(^1) I suggest reading "Jumbotron Startup Sequence" document, first, to familiarize yourself before reading further into this section. This section
assume you're familiar with many terms.

In this case, the verticle _Scheduler_ will deploy and dependents (in this case, it has none), then run all the object in the _preInits_ list (also empty), run its startup sequence, then, lastly, run the _postInits_ list of objects. _dependents_ is a list of other verticles with their own dependents, preInits, and postInits. Verticles are deployed "depth first",
meaning, the deepest verticle in a branch is deployed first.

The rest of the configuration is as follows:

- **instanceClass** - the Java class to instantiate
- **instanceType** - the type of Verticle: _worker_ or _standard_ (see https://vertx.io/docs/vertx-core/java/
    #_verticles)
- **instantiate** - whether or not the verticle is instantiated ( _true_ or _false)_
- **_config_** - an JSON object containing all configuration parameters for the current verticle

Here's a typical` configuration as found in the file _verticles.json_ :

```
{
  "verticleName": "Application",
  "instanceClass": "com.asanasoft.common.Application",
  "instanceType": "standard",
  "config": {},
  "dependents": [
    {
      "verticleName": "KafkaConnectInstance",
      "instanceClass": "com.asanasoft.common.verticle.KafkaConnectInstance",
      "instanceType": "worker",
      "config": {
        "plugin.path": "/Work/Java/Kafkaesque/src/main/resources/kafka/plugins",
        "connectors.dir": "/Work/Java/Kafkaesque/src/main/resources/kafka/connectors/LOCAL",
        "key.converter": "org.apache.kafka.connect.json.JsonConverter",
        "value.converter": "org.apache.kafka.connect.json.JsonConverter",
        "key.converter.schemas.enable":"true",
        "value.converter.schemas.enable":"true",
        "bootstrap.servers": "127.0.0.1:9092",
        "offset.storage.file.filename":"data/kafka/connect/kcStorage",
        "rest.host.name": "127.0.0.1",
        "rest.port": "8083",
        "error.tolerance": "all",
        "errors.retry.timeout": "300000",
        "errors.retry.delay.max": "30000",
        "error.log.enable": "true",
        "errors.log.include.messages": "false",
        "errors.deadletterqueue.topic.name": "my-connector-errors"
      },
      "dependents": [
        {
          "verticleName": "KafkaInstance",
          "instanceClass": "com.asanasoft.common.verticle.KafkaInstance",
          "instanceType": "worker",
          "config": {
            "zookeeper.connect": "127.0.0.1:2181",
            "offsets.topic.replication.factor": "1",
            "zookeeper.session.timeout.ms": "30000",
            "message.max.bytes": "250000",
            "replica.fetch.max.bytes": "350000",
            "replica.fetch.response.max.bytes": "350000",
            "broker.id": "-1",
            "delete.topic.enable": "true",
            "log.dirs": "data/kafka/logs",
            "log.flush.interval.messages": "1000",
            "log.flush.interval.ms": "60000",
            "log.retention.hours": "24",
            "log.roll.hours": "1",
            "max.poll.interval.ms": "600000",
            "listeners": "PLAINTEXT://127.0.0.1:9092,SSL://:9091"
          },
          "dependents": [{
            "verticleName": "ZookeeperInstance",
            "instanceClass": "com.asanasoft.common.verticle.ZookeeperInstance",
            "instanceType": "worker",
            "dependents": [],
            "config": {
              "tickTime": "2000",
              "dataDir": "data/zookeeper",
              "clientPort": "2181",
              "maxClientCnxns": "0"
            }
          }]
        }
      ]
    },
    {
      "verticleName": "BitsyDBInstance",
      "instanceClass": "com.asanasoft.common.verticle.BitsyDBInstance",
      "instanceType": "worker",
      "config": {
        "backupStore": "DBStore",
        "backupStoreConfig": "backupStoreConfig.properties",
        "dbPath": "./data/bitsy/",
        "dbName": "kafkaesque",
        "GraphDBBackup": "./data/bitsy/"
      },
      "dependents": [
      ]
    },
    {
      "verticleName": "Scheduler",
      "instanceClass": "com.asanasoft.common.verticle.Scheduler",
      "instanceType": "worker",
      "preInits": [
        {
          "instanceClass": "com.asanasoft.common.init.impl.CodecInitializer",
          "config": {
          }
        }
      ],
      "config": {
      },
      "dependents": [
      ]
    },
    {
      "verticleName": "CacheInstance",
      "instanceClass": "com.asanasoft.common.verticle.CacheInstance",
      "instanceType": "worker",
      "config": {
        "defaultRegion": "kafkaesque",
        "cacheAddress": "KafkaCache"
      },
      "preInits": [
        {
          "instanceClass": "com.asanasoft.common.init.impl.DataSources",
          "config": {
            "context": "datasources"
          }
        },
        {
          "instanceClass": "com.asanasoft.common.init.impl.DataInitializer",
          "config": {
            "datasourceName": "p-mysql",
            "ddlFile": "loadDDL.sql"
          }
        }
      ],
      "dependents": [
      ]
    }
  ]
}
```

In detail, each verticle does the following:

_1._ Setup a _MessageConsumer_ using a UUID as the address. This consumer listens for successful deployment of
    each immediate dependent.
_2._ Deploys dependents in parallel. Vertx is asynchronous, so deploying dependents in a loop effectively deploys
    dependents in parallel.
_3._ Each immediate dependent signals their completion via the event bus using the parent's address.
_4._ Once all immediate dependents complete their successful deployments, the verticle instantiates all preInit
    objects calling their respective **_init()_** methods.
_5._ Then it calls its own **_start()_** method. This is where the verticle initializes itself.
_6._ After **_start()_** , the verticle then instantiates all postInit object calling their respective **_init()_** methods.
_7._ If no exceptions occurred during its deployment, it then signals its parent via the event bus using its parent's
    address.
_8._ Additionally, each verticle creates a _MessageConsumer_ using the object's name as the address. This address
will be used to communicate with the verticle, if the verticle provides a service to the rest of the application. An
example of this is the **_Scheduler_**.

**_Environment_**

**_Kafkaesque's_** entry point is the **_Application_** verticle. **_Vertx_** calls the **_start()_** method of a verticle when it deploys
it. **_Application's start()_** method runs the **_Environment_** initializer (extends **_AbstractInitializer_** ). **_Environment's_**
job is to cache all the properties files and provide a lookup service for the application. The main properties file is
**environment.properties**. This file tells **_Environment_** which properties files to process. You'll find more details in
the _IDP Startup Sequence_ document. It behooves you to look through the resource folder and study the properties
files.

### Key Components

Here are the key verticles and their functions:

**_Application_**

Besides being responsible for the startup sequence, the **_Application_** verticle initializes and starts up the web
server and the GraphQL sub-module.

**_BitsyDBInstance_**

This verticle starts up the Bitsy GraphDB and performs a restore of the database under the following conditions:

**_-_** the database exists on startup, but there's no data


**_-_** the database does not exist on startup
It also registers itself with the **_Scheduler_** to receive backup notifications. Backups restores are done using the
_StoreService_ (see the _Store Service_ documentation for details).

**_KafkaConnectInstance_**

This verticle instantiates an instance of the Kafka Connect module. It is dependent on the **_Kafka_** server which is
dependent on **_Zookeeper_**. This verticle deploys connector configuration (found in <root>/src/main/resources/
kafka/connectors folder) to the _Kafka Connect_ instance by first comparing property values to key entries using the
**_Environment_** object. If it finds a match, it replaces the value of the connector key with the value of the found key.
For example, a source connector can have the following values:

connection.url=dudedb_jdbcUrl
connection.user=dudedb_username
connection.password=dudedb_password

**_KafkaConnectInstance_** queries the **_Environment_** object for _dudedb_jdbcUrl_. If a value is returned by
**_Environment_** , then that value will be used for _connection.url_. This is done because the connector files are not
encrypted. Only normal properties files are. So sensitive information are placed in encrypted properties files which
are read in and decrypted by the **_Environment_** object.

**_Kafka Connect_** exposes an API to manage the connectors. Since **_Kafkaesque_** lives in PCF, there is only one
port open to it. Because of this, we have a **_KafkaConnectProxyHandler_** listening on the _/connectors_ endpoint,
which matches the endpoint exposed by **_Kafka Connect._** Please refer to the Apache Kafka documentation for
details on how to use this API. The API can be use from an application such as **_Postman_**. You will find a Postman
collection file that you can import into **_Postman_** in the <root>/src/main/resources/kafka/postman folder.

Currently, there are two source connectors and two corresponding sink connectors. Please see the **_Kafka
Connect_** and Confluents's **_JDBC Connector_** documentation for more information.

**_Scheduler_**

The **_Scheduler_** verticle starts up Quartz and accepts incoming registrations from objects in the application.
Objects that register with **_Scheduler_** will be notified when an event happens. It is the object's job to determine
whether or not that event pertains to them.


**_ShellInstance_**

The **_ShellInstance_** accepts code scripts in various JVM scripting languages and executes them. It registers itself
with the **_Scheduler_** to receive notice when it's time to scan the default **_StoreService_** object for new scripts to
execute.

**_CacheInstance_**

This verticle creates an instance of the Apache Java Caching System (JCS). JCS is strictly used for the GraphQL
service to cache requests and their results. It has an eviction policy of 120secs. This can be configured with the
default JCS configuration file. Please see the Apache JCS documentation for more details.


