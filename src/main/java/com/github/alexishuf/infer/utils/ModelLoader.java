package com.github.alexishuf.infer.utils;

import com.google.common.base.Stopwatch;
import com.google.common.collect.BiMap;
import com.google.common.collect.HashBiMap;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.rdf.model.RDFNode;
import org.apache.jena.riot.*;
import org.apache.jena.riot.system.StreamRDFLib;
import org.apache.jena.riot.system.StreamRDFWrapper;
import org.apache.jena.vocabulary.OWL;
import org.apache.jena.vocabulary.OWL2;
import org.apache.jena.vocabulary.RDF;
import org.apache.jena.vocabulary.RDFS;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class ModelLoader {
    private static Logger logger = LoggerFactory.getLogger(ModelLoader.class);
    private static Set<String> neverFetch = Stream.of(OWL2.NS, RDFS.getURI(), RDF.getURI())
            .map(s -> s.replaceAll("^(.*)#$", "$1")).collect(Collectors.toSet());
    private static Set<Pattern> neverFetchRx = new HashSet<>();

    private Set<String> fetched = new HashSet<>();
    private Model main = ModelFactory.createDefaultModel();
    private Model bg = ModelFactory.createDefaultModel();
    private Model model = main;
    private Lang firstLang = null;
    private Lang hintLang = null;
    private boolean autoImport = false;

    public @Nonnull Model getMain() {
        return main;
    }
    public @Nonnull Model getBackground() {
        return bg;
    }

    public @Nonnull ModelLoader withHintLang(Lang lang) {
        Model m;
        this.hintLang = lang;
        return this;
    }

    public @Nonnull ModelLoader withAutoImport(boolean enable) {
        autoImport = enable;
        return this;
    }

    public ModelLoader withBlacklist(String... excludesURIs) {
        neverFetch.addAll(Arrays.asList(excludesURIs));
        return this;
    }

    public ModelLoader withBlacklistRegExp(String... regExps) {
        Arrays.stream(regExps).map(Pattern::compile).forEach(neverFetchRx::add);
        return this;
    }

    public @Nonnull ModelLoader toMain() {
        model = main;
        return this;
    }

    public @Nonnull ModelLoader toBackground() {
        model = bg;
        return this;
    }

    public @Nonnull ModelLoader files(File... files) {
        for (File file : files) {
            Stopwatch sw = Stopwatch.createStarted();
            try (FileInputStream is = new FileInputStream(file)) {
                Lang lang = RDFLanguages.filenameToLang(file.getName());
                if (lang != null) firstLang = lang;
                else lang = hintLang;
                Model tmp = ModelFactory.createDefaultModel();
                RDFParser.create().source(is).lang(lang).context(null)
                        .parse(new PrefixCatcher(tmp));
                addModel(model, tmp, sw, null);
            } catch (IOException e) {
                logger.error("Error reading file {}. Halt.", file, e);
            }
        }
        return this;
    }

    private  @Nonnull ModelLoader loadUri(@Nonnull Model dest, String uri) {
        try {
            Stopwatch sw = Stopwatch.createStarted();
            Model tmp = ModelFactory.createDefaultModel();
            RDFParser.create().source(uri).lang(hintLang).context(null)
                    .parse(new PrefixCatcher(tmp));
            RDFDataMgr.read(tmp, uri, hintLang);
            addModel(dest, tmp, sw, uri);
        } catch (RiotNotFoundException e) {
            logger.error("URI {} not found, will ignore and continue.", uri, e);
        }
        return this;
    }

    public @Nonnull ModelLoader uris(String... uris) {
        for (String uri : uris) loadUri(model, uri);
        return this;
    }

    private class PrefixCatcher extends StreamRDFWrapper {
        private Model m;

        public PrefixCatcher(Model model) {
            super(StreamRDFLib.graph(model.getGraph()));
            this.m = model;
        }

        @Override
        public void prefix(String prefix, String iri) {
            if (m.getNsURIPrefix(iri) != null) return;
            while (m.getNsPrefixURI(prefix) != null) {
                prefix += "x";
            }
            m.setNsPrefix(prefix, iri);
        }
    }

    private void addModel(@Nonnull Model dest, @Nonnull Model tmp, @Nonnull Stopwatch sw,
                          @Nullable String uri) {
        Set<String> set = new HashSet<>();
        if (uri != null) {
            set.add(uri);
            fetched.add(uri);
        }
        tmp.listSubjectsWithProperty(RDF.type, OWL.Ontology).toSet().stream()
                .filter(n -> !n.isAnon()).forEach(n -> set.add(n.getURI()));
        logger.info("Loaded {} triples into {} in {}. Ontology URI: {}", tmp.size(),
                dest == main ? "main" : (dest == bg ? "bg" : "other"), sw,
                set.stream().reduce((a, b) -> a  + ", " + b).orElse(""));
        dest.add(tmp);

        if (!autoImport)
            return;
        tmp.listObjectsOfProperty(OWL.imports).toSet().stream().filter(RDFNode::isURIResource)
                .map(r -> r.asResource().getURI().replaceAll("^(.*)#$", "$1"))
                .filter(u -> !fetched.contains(u) && !neverFetch.contains(u))
                .filter(u -> neverFetchRx.stream().noneMatch(p -> p.matcher(u).matches()))
                .distinct()
                .forEach(u -> loadUri(bg, u));
    }

    public @Nonnull ModelLoader guess(String... filesOrUris) {
        for (String fileOrUri : filesOrUris) {
            if (fileOrUri.matches("(?:https?|file|ftp):/?/"))
                uris(fileOrUri);
            else
                files(new File(fileOrUri));
        }
        return this;
    }

    public Lang getFirstLang() {
        return firstLang;
    }
}
