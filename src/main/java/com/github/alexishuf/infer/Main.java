package com.github.alexishuf.infer;

import com.github.alexishuf.infer.reasoners.SplitReasoner;
import com.github.alexishuf.infer.reasoners.ReasonerRegistry;
import com.github.alexishuf.infer.utils.ModelLoader;
import com.google.common.base.Stopwatch;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.riot.*;
import org.kohsuke.args4j.Argument;
import org.kohsuke.args4j.CmdLineParser;
import org.kohsuke.args4j.Option;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.lang.reflect.Modifier;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static com.github.alexishuf.infer.utils.Utils.union;
import static org.apache.jena.riot.RDFWriterRegistry.defaultSerialization;

public class Main {
    private static Logger logger = LoggerFactory.getLogger(Main.class);

    @Option(name = "--reasoner", aliases = {"-r"}, usage = "Use the specified reasoner")
    private  String reasoner = "jena";
    @Option(name = "--profile", aliases = {"-p"}, usage = "Set reasoner profile")
    private String profile = "owl-fb";
    @Option(name = "--split", aliases = {"-s"}, usage = "Splits inferences from background inputs " +
            "and inferences from the union of main and background files. The latter does not " +
            "include triples in the former.")
    private boolean split = false;
    @Option(name = "--no-echo", aliases = {"-E"},
            usage = "Disables echo, outputting only inferred  triples")
    private boolean noEcho = false;
    @Option(name = "--no-dereference-imports", aliases = {"-D"},
            usage = "Disable downloading of owl:imports documents")
    private boolean noFollowImports = false;
    @Option(name = "--exclude-uri", aliases = {"-x"}, metaVar = "URI",
            usage = "Do not dereference these URIs")
    private String[] excludesURIs = {};
    @Option(name = "--exclude-uri-rx", aliases = {"-X"}, metaVar = "RX",
            usage = "Same as --exclude-uri, but uses a regular expression (Java syntax)")
    private String[] excludesURIRegexps = {};


    @Option(name = "--input-language", aliases = {"-l"}, metaVar = "LANG",
            usage = "Input RDF language can be media type, file extension (without '.') or a " +
                    "field name in org.apache.jena.riotRDFLanguages. Default is to guess " +
                    "from input.")
    private String inputLanguage = null;
    @Option(name = "--output-format", aliases = {"-f"}, metaVar = "FMT",
            usage = "Similar to --input-language, but also understands field names from " +
                    "org.apache.jena.riot.RDFFormat. Default is to use the default RDFFormat " +
                    "of the input language")
    private String outputFormat = null;


    @Option(name = "--input-bg", aliases = {"-b"}, metaVar = "FILE",
            usage = "Use the given file as background triples. Has effect on split reasoning.")
    private File[] inputBgFiles = {};
    @Option(name = "--input-bg-uri", aliases = {"-B"}, metaVar = "URI",
            usage = "Same as --input-bg, but takes a URI")
    private String[] inputBgURIs = {};
    @Option(name = "--input", aliases = {"-i"}, metaVar = "FILE",
            usage = "Use the given file as background triples. Has effect on split reasoning.")
    private File[] inputMainFiles = {};
    @Option(name = "--input-uri", aliases = {"-I"}, metaVar = "URI",
            usage = "Same as --input-bg, but takes a URI")
    private String[] inputMainURIs = {};


    @Option(name = "--output", aliases = {"-o"},  metaVar = "FILE", usage = "Output file for the " +
            "main triples or all triples if --split is not givne")
    private File output;
    @Option(name = "--output-bg", aliases = {"-O"}, depends = {"--split"}, metaVar = "FILE",
            usage = "Output file for the background triples")
    private File outputBg;


    @Option(name = "--help", aliases = {"-h"}, help = true,
            usage = "Show usage")
    private boolean help = false;
    @Option(name = "--list-reasoners", aliases = {"-R"}, help = true,
            usage = "List all available reasoners and exit")
    private boolean listReasoners = false;
    @Option(name = "--list-profiles", aliases = {"-P"}, help = true,
            usage = "List all profiles of selected reasoner")
    private boolean listProfiles = false;
    @Option(name = "--list-input-languages", aliases = {"-L"}, help = true,
            usage = "List all supported input languages")
    private boolean listInputLanguages = false;
    @Option(name = "--list-output-formats", aliases = {"-F"}, help = true,
            usage = "List all supported output formats")
    private boolean listOutputFormats = false;

    @Argument(multiValued = true, metaVar = "INPUT", usage = "One or more input files or URIs " +
            "that are considered under the main inputs (-i, -I).")
    private String[] inputs = {};

    private static Map<String, RDFFormat> formatMap;
    private static List<String> formatList;

    public static void main( String[] args ) throws Exception {
        Main main = new Main();
        CmdLineParser parser = new CmdLineParser(main);
        parser.parseArgument(args);
        if (main.help) {
            parser.printSingleLineUsage(System.out);
            System.out.print("\n\n");
            parser.printUsage(System.out);
        }
        else {
            main.run();
        }
    }

    public void run() throws IOException {
        if (listReasoners) doListReasoners();
        if (listInputLanguages) doListInputLanguages();
        if (listOutputFormats) doListOutputFormats();

        SplitReasoner reasoner = this.reasoner != null ? setupReasoner() : null;
        if (reasoner != null && listProfiles) doListProfiles(reasoner);
        if (listReasoners || listInputLanguages || listOutputFormats || listProfiles) return;
        assert reasoner != null;

        reasoner.setProfile(profile);
        ModelLoader ldr = new ModelLoader().withHintLang(asLang(inputLanguage))
                .withAutoImport(!noFollowImports)
                .withBlacklist(excludesURIs)
                .withBlacklistRegExp(excludesURIRegexps);
        ldr.toMain().files(inputMainFiles).uris(inputMainURIs).guess(inputs)
                .toBackground().files(inputBgFiles).uris(inputBgURIs);
        reason(reasoner, ldr);
    }

    private void reason(SplitReasoner reasoner, ModelLoader ldr) throws IOException {
        Model main, bg = ModelFactory.createDefaultModel();;
        Stopwatch sw;
        if (split) {
            sw = Stopwatch.createStarted();
            main = reasoner.apply(ldr.getBackground(), ldr.getMain(), bg);
            long mainTriples = noEcho ? main.size() : main.size() - ldr.getMain().size(),
                   bgTriples = noEcho ? bg.size()   : bg.size()   - ldr.getBackground().size();
            logger.info("Inferred {} triples for main and {} for background in {}",
                    mainTriples, bgTriples, sw);
            if (outputBg != null)
                write(bg, outputBg, ldr.getFirstLang());
        } else {
            Model union = union(ldr.getBackground(), ldr.getMain());
            sw = Stopwatch.createStarted();
            main = reasoner.apply(union);
            long triples = noEcho ? main.size() : main.size() - union.size();
            logger.info("Inferred {} triples in {}", triples, sw);
        }

        write(main, output, ldr.getFirstLang());
    }

    private void doListProfiles(SplitReasoner reasoner) {
        list(String.format("Profiles for \"%s\" reasoner", this.reasoner),
                new ArrayList<>(reasoner.getProfiles()));
    }

    private void doListInputLanguages() {
        list("Input Languages", RDFLanguages.getRegisteredLanguages().stream()
                .map(l -> l.getName().replaceAll("\\W", ""))
                .map(String::toUpperCase)
                .distinct()
                .collect(Collectors.toList()));
    }

    private void doListOutputFormats() {
        list("Output Formats", formatList);
    }

    private void doListReasoners() {
        list("Reasoners", new ArrayList<>(ReasonerRegistry.getNames()));
    }

    private void list(String type, List<String> things) {
        System.out.printf("List of %s:\n", type);
        for (String thing : things) System.out.printf("\t- %s\n", thing);
        System.out.println();
    }


    private void write(@Nonnull Model model, @Nullable File file,
                       @Nullable Lang mainLanguage) throws IOException {
        RDFFormat fmt;
        if (outputFormat != null) {
            fmt = asRDFFormat(outputFormat);
            if (fmt == null)
                fmt = defaultSerialization(asLang(outputFormat));
        } else {
            fmt = defaultSerialization(inputLanguage != null ? asLang(inputLanguage)
                    : (mainLanguage != null ? mainLanguage : RDFLanguages.TURTLE));
            Lang lang = file == null ? fmt.getLang()
                    : RDFLanguages.filenameToLang(file.getName(), fmt.getLang());
            fmt = fmt.getLang() == lang ? fmt : defaultSerialization(lang);
        }
        if (file != null) {
            try (FileOutputStream os = new FileOutputStream(file)) {
                RDFDataMgr.write(os, model, fmt);
            }
        } else {
            RDFDataMgr.write(System.out, model, fmt);
        }
    }

    private @Nullable RDFFormat asRDFFormat(@Nonnull String string) {
        RDFFormat fmt = formatMap.getOrDefault(string.toLowerCase(), null);
        if (fmt == null) {
            Lang lang = asLang(string);
            if (lang != null) new RDFFormat(lang);
        }
        return fmt;
    }

    private @Nullable Lang asLang(@Nullable String string) {
        if (string == null) return null;
        Lang lang = RDFLanguages.nameToLang(string);
        if (lang == null) { //try filename/extension
            Matcher matcher = Pattern.compile("^.*([^.]+)$").matcher(string);
            if (matcher.matches()) lang = RDFLanguages.fileExtToLang(matcher.group(1));
        }
        return lang;
    }

    private @Nonnull
    SplitReasoner setupReasoner() {
        SplitReasoner reasoner = ReasonerRegistry.getReasoner(this.reasoner);
        reasoner.setEchoEnabled(!noEcho);
        return reasoner;
    }

    static {
        formatMap = new HashMap<>();
        formatList = new ArrayList<>();
        Arrays.stream(RDFFormat.class.getFields())
                .filter(f -> (f.getModifiers() & (Modifier.PUBLIC | Modifier.STATIC)) > 0
                        && RDFFormat.class.isAssignableFrom(f.getType()))
                .forEach(f -> {
                    try {
                        RDFFormat format = (RDFFormat) f.get(null);
                        formatMap.put(f.getName().toLowerCase(), format);
                        formatMap.put(format.toString().toLowerCase(), format);
                        formatList.add(f.getName());
                    } catch (IllegalAccessException e) {
                        throw new RuntimeException(e);
                    }
                });
        formatList = formatList.stream().map(String::toUpperCase).sorted().distinct()
                .collect(Collectors.toList());
    }
}
