package com.github.alexishuf.infer.utils;

import org.apache.jena.graph.Graph;
import org.apache.jena.graph.compose.MultiUnion;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;


import static java.util.Arrays.stream;

public class Utils {
    public static Model union(Model... models) {
        return ModelFactory.createModelForGraph(new MultiUnion(stream(models)
                .map(m -> (Graph)new CloseShieldGraph(m.getGraph())).iterator()));
    }
}
