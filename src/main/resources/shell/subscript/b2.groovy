import io.vertx.core.json.JsonObject;
import org.apache.tinkerpop.gremlin.groovy.jsr223.GremlinGroovyScriptEngine;
import org.apache.tinkerpop.gremlin.process.traversal.dsl.graph.GraphTraversal;
import org.apache.tinkerpop.gremlin.structure.Vertex;
import shell.subscript.GremlinAPI;
import com.asanasoft.common.service.graphdb.DBConstants;
import groovy.json.*;
import shell.subscript.GremlinAPI;
import com.lambdazen.bitsy.*
import io.vertx.core.json.JsonArray
import io.vertx.core.json.JsonObject
import org.slf4j.Logger
import org.slf4j.LoggerFactory

import javax.script.Bindings
import java.nio.file.Paths
import java.io.IOException;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.core.JsonGenerationException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.asanasoft.common.service.graphdb.impl.*;
import org.apache.tinkerpop.gremlin.groovy.jsr223.GremlinGroovyScriptEngine;
import org.apache.tinkerpop.gremlin.process.traversal.dsl.graph.GraphTraversal;
import org.apache.tinkerpop.gremlin.process.traversal.dsl.graph.GraphTraversalSource;
import org.apache.tinkerpop.gremlin.structure.Graph;
import org.apache.tinkerpop.gremlin.structure.Vertex;

import javax.script.Bindings

protected JsonObject getJsonFrom(Vertex vertex) {
    JsonObject result = new JsonObject();

    for (String key : vertex.keys()) {
        result.put(key, vertex.property(key).value());
    }

    return result;
}

protected JsonObject buildShallowCopyOfVertex(Vertex v) {
    JsonObject obj = getJsonFrom(v);
    JsonObject shallowObj = new JsonObject();
    shallowObj.put(DBConstants.INTERNAL_CLASS, obj.getString(DBConstants.INTERNAL_CLASS));
    String keyFieldName = obj.getString(DBConstants.USE_KEY);
    shallowObj.put(DBConstants.USE_KEY, keyFieldName);
    shallowObj.put(keyFieldName, obj.getValue(keyFieldName));

    return shallowObj;
}

protected JsonObject retrieveVertexGraph(Vertex startVertex, boolean getChildren, int recursionLevel, Logger logger) {
    if (recursionLevel == 0) {
        logger.debug("In retrieveVertexGraph, with getChildren = " + getChildren + ", level=" + recursionLevel + "...");
    }
    JsonObject result = getJsonFrom(startVertex);
    print("startVertex = \n" + result.encodePrettily());

    Object value = null;

    for (String key : result.getMap().keySet()) {
        value = result.getValue(key);

        print("value = " + value);

        if (value instanceof String && ((String) value).startsWith("object:")) {
            Iterator<Vertex> vertices = g.V(startVertex.id()).outE((String) value).inV();
            if (getChildren) {
                result.put(key, retrieveVertexGraph(vertices.next(), true, recursionLevel + 1));
            } else {
                JsonObject shallowObj = buildShallowCopyOfVertex(vertices.next());
                result.put(key, shallowObj);
            }
        } else if (value instanceof String && ((String) value).startsWith("array:")) {
            JsonArray jsonArray = new JsonArray();
            result.put(key, jsonArray);

            Iterator<Vertex> vertices = g.V(startVertex.id()).outE((String) value).order().by("seq").inV();

            while (vertices.hasNext()) {
                if (getChildren) {
                    jsonArray.add(retrieveVertexGraph(vertices.next(), true, recursionLevel + 1));
                } else {
                    JsonObject shallowObj = buildShallowCopyOfVertex(vertices.next());
                    jsonArray.add(shallowObj);
                }
            }
        }
    }
    print(result.encodePrettily());
    return result;
}


protected static void evaluate(String query, final List<String> arguments) {
    final GremlinGroovyScriptEngine engine = new GremlinGroovyScriptEngine();

    final Bindings bindings = engine.createBindings();
    bindings.put("args", arguments.toArray());

    try {
        engine.eval(query, bindings);
    } catch (Exception e) {
        System.err.println(e.getMessage());
    }
}

def dbName = binding.getVariable("dbName");
def dbPath = binding.getVariable("dbPath");
//print(dbName + "  DB values passed via Java");
def GremlinAPI gremlinAPI = new GremlinAPI();

def GraphTraversalSource g = gremlinAPI.getBitsyDB(dbPath + dbName);

def final GremlinGroovyScriptEngine scriptEngine = new GremlinGroovyScriptEngine();
List<JsonObject> result = new ArrayList<JsonObject>();
Bindings bindings = scriptEngine.createBindings();
bindings.put("g", g);
bindings.put("result", result);

def String script = "g.V().hasLabel('JTChart').where(inE().count().is(gt(1)))";

try {
    Object scriptObject = scriptEngine.eval(script, bindings);
    GraphTraversal scriptResult = (GraphTraversal) scriptObject;
    Vertex vertex;
    Object graphObject;

    print("hasNext() = " + scriptResult.hasNext());

    while (scriptResult.hasNext()) {
        graphObject = scriptResult.next();

        print("graphObjet = " + graphObject.getClass().getName());

        //Only aggregate Vertices
        if (graphObject instanceof Vertex) {

            print("Retrieving Vertex Graph...");
            vertex = (Vertex) graphObject;
            retrieveVertexGraph(vertex, false, 0, logger)

            result.add(vertex);
        }
    }
} catch (Exception e) {
    e.printStackTrace();
}

gremlinAPI.getBitsyGraph().shutdown(); [];

return result;
