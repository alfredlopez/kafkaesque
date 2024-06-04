package shell.subscript

import com.asanasoft.common.init.impl.Environment

//package shell.script.sample;
import groovy.sql.Sql
import com.asanasoft.common.init.impl.DataSources
import io.vertx.core.json.JsonObject

/**
 * A simple Groovy example program that connects to a MySQL database,
 * execute a simple query, and display the results to the console.
 */
class Sample {

    public void createIndex(){
        Environment environment = Environment.getInstance();
        JsonObject dataSourcesDefEnv = new JsonObject();

        for (String key : environment.getResult().keySet()) {
            if (key.endsWith("_dataSourceName")) {
                String dataSourceName = environment.getString(key);
                JsonObject dataSourceJson = new JsonObject();
                dataSourceJson.put("jdbcUrl", environment.getString(dataSourceName + "_jdbcUrl"));
                dataSourceJson.put("username", environment.getString(dataSourceName + "_username"));
                dataSourceJson.put("password", environment.getString(dataSourceName + "_password"));
                dataSourceJson.put("jdbcDriver", environment.getString(dataSourceName + "_jdbcDriver"));

                dataSourcesDefEnv.put(dataSourceName, dataSourceJson);
            }
        }
       // println( " Database:" + dataSourcesDefEnv.encodePrettily());
        JsonObject azureDB = dataSourcesDefEnv.getValue("azure-sqldb");
        println "Azure DB JDBC URL " + azureDB.getValue("jdbcUrl");
        println "Azure DB JDBC URL " + azureDB.getValue("username");
        println "Azure DB JDBC URL " + azureDB.getValue("password");
        println "Azure DB JDBC URL " + azureDB.getValue("jdbcDriver");


       def sql = Sql.newInstance(azureDB.getValue("jdbcUrl").toString(),
                azureDB.getValue("username").toString(), azureDB.getValue("password").toString(),azureDB.getValue("jdbcDriver").toString())
        // execute a simple query
        //     println sql.execute("CREATE TABLE dbo.test_table (col1 int NOT NULL, col2 char(25), col3 decimal(10,2), col4 varchar(25), col5 datetime, PRIMARY KEY (col1), UNIQUE (col2))")
        sql.eachRow("select * from dbo.dropdown_values "){ row ->
            // print data returned by the query
            println(row);
        }
        // close the connection
        sql.close();
    }

    /*static void main(String[] args) {
        // connect to db
        DataSources dataSources = DataSources.getInstance();
        dataSources.getDataSourcesDef().each {datasource->
            println("Datasource Name: " + datasource);
        };
        def sql = Sql.newInstance("jdbc:sqlserver://azugessqlpreview.database.secure.windows.net:1433;database=gregor;Encrypt=false;loginTimeout=30;",
                "u08f2b510f43", "qfNgBO8flazWmFqysKwT07mv4VKyAbZefHmjiAN8EqDSghdIRBRRlw==", "com.microsoft.sqlserver.jdbc.SQLServerDriver")
        // execute a simple query
   //     println sql.execute("CREATE TABLE dbo.test_table (col1 int NOT NULL, col2 char(25), col3 decimal(10,2), col4 varchar(25), col5 datetime, PRIMARY KEY (col1), UNIQUE (col2))")
        sql.eachRow("select * from dbo.dropdown_values "){ row ->
            // print data returned by the query
            println(row);
        }
        // close the connection
        sql.close()
    }*/
}
