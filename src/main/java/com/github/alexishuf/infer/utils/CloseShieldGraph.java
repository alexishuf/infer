package com.github.alexishuf.infer.utils;

import org.apache.jena.graph.Graph;
import org.apache.jena.sparql.graph.GraphWrapper;

public class CloseShieldGraph extends GraphWrapper {
    public CloseShieldGraph(Graph graph) {
        super(graph);
    }

    @Override
    public void close() { /* pass */ }
}
