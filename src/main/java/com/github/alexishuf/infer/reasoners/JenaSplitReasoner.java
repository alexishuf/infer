package com.github.alexishuf.infer.reasoners;

import com.google.common.base.Preconditions;
import org.apache.jena.ontology.OntModelSpec;
import org.apache.jena.rdf.model.*;
import org.apache.jena.reasoner.Reasoner;
import org.apache.jena.sparql.graph.GraphFactory;

import javax.annotation.Nonnull;
import java.lang.reflect.Modifier;
import java.util.*;

import static java.util.Arrays.stream;
import static org.apache.jena.rdf.model.ModelFactory.createModelForGraph;

@ReasonerName("jena")
public class JenaSplitReasoner implements SplitReasoner {
    private static Map<String, OntModelSpec> profileMap;

    private boolean echoEnabled;
    private String profile = "owl-fb";

    @Nonnull
    @Override
    public String getProfile() {
        return profile;
    }

    @Nonnull
    @Override
    public Set<String> getProfiles() {
        return profileMap.keySet();
    }

    @Override
    public void setProfile(String name) throws IllegalArgumentException {
        Preconditions.checkArgument(profileMap.containsKey(name));
        profile = name;
    }

    @Override
    public boolean isEchoEnabled() {
        return echoEnabled;
    }

    @Override
    public void setEchoEnabled(boolean enabled) {
        this.echoEnabled = enabled;
    }

    @Nonnull
    @Override
    public Model apply(@Nonnull Model main) {
        return ModelFactory.createInfModel(profileMap.get(profile).getReasoner(), main);
    }


    @Override
    public void apply(@Nonnull Model inBackground, @Nonnull Model inMain,
                      @Nonnull Model outBackground, @Nonnull Model outMain) {
        Reasoner r = profileMap.get(profile).getReasoner();
        r = r.bindSchema(inBackground.getGraph());
        Model bg = createModelForGraph(r.bind(GraphFactory.createDefaultGraph()));
        if (isEchoEnabled())
            filterStatements(outBackground, bg);
        else
            filterStatements(outBackground, bg, inBackground);
        outBackground.setNsPrefixes(inBackground);

        Model inf = createModelForGraph(r.bind(inMain.getGraph()));
        if (isEchoEnabled())
            filterStatements(outMain, inf, bg);
        else
            filterStatements(outMain, inf, bg, inMain);
        outMain.setNsPrefixes(inMain);
        outMain.withDefaultMappings(inBackground);
    }

    private void filterStatements(@Nonnull Model out, @Nonnull Model in,
                                  @Nonnull Model... blacklist) {
        for (StmtIterator it = in.listStatements(); it.hasNext(); ) {
            Statement s = it.next();
            if (Arrays.stream(blacklist).noneMatch(m -> m.contains(s))) out.add(s);
        }
    }

    static {
        profileMap = new LinkedHashMap<>();
        profileMap.put("owl-fb", OntModelSpec.OWL_MEM_RULE_INF);
        profileMap.put("trans", OntModelSpec.OWL_MEM_TRANS_INF);
        profileMap.put("rdfs", OntModelSpec.OWL_MEM_RDFS_INF);
        stream(OntModelSpec.class.getDeclaredFields())
                .filter(f -> (f.getModifiers() & Modifier.STATIC) > 0)
                .forEach(f -> {
                    try {
                        profileMap.put(f.getName(), (OntModelSpec) f.get(null));
                    } catch (IllegalAccessException e) {
                        throw new RuntimeException(e);
                    }
                });
        profileMap = Collections.unmodifiableMap(profileMap);
    }


}
