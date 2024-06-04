package shell.subscript

import com.lambdazen.bitsy.BitsyGraph
import com.asanasoft.common.service.graphdb.GraphDBService
import org.apache.tinkerpop.gremlin.process.traversal.dsl.graph.GraphTraversalSource
import org.apache.tinkerpop.gremlin.structure.Graph

import java.nio.file.Paths;

//import com.asanasoft.common.init.impl.Environment;

class GremlinAPI {
    def BitsyGraph bitsyGraph;
    def GraphDBService graphDatabaseService = null;
    static String greet(String name) {
      //  def temp = binding.getVariable("dbName");
        return name +  " Hello"
    }
    public GraphTraversalSource getBitsyDB(String path){
        bitsyGraph = new BitsyGraph(Paths.get(path))
        return bitsyGraph.traversal();
    }

    public void shutDownGraph(BitsyGraph g){
        g.shutdown();
    }

    public BitsyGraph getBitsyGraph() {
        return bitsyGraph
    }

    public void setBitsyGraph(BitsyGraph bitsyGraph) {
        this.bitsyGraph = bitsyGraph
    }
}

