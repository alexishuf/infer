## infer
This is a simple command-line wrapper around Jena reasoners (and others in a not so near future).

Typicall use is to compute entailment of a file. owl:imports objects are dereferenced and 
included in the inference. Output is sent to stdout and uses the same RDF syntax as the input:
```bash
./infer file.ttl
```

You may want to split *background* and *main* triples. Triples from `file.ttl` are in *main*, 
triples from objects of owl:imports are in *background*. You can explicitly state if a file 
(or URI) should be loaded in *main* or *background* using the appropiate option flag (-B loads a 
URI into *background*). `--split` separates the output of entailments.    
```bash
./infer -B http://www.w3.org/2006/time -o main.ttl -O bg.ttl file.ttl 
```

Using the `--no-echo` option, only new triples are output. In combination with `--split`, 
triples inferred by *background* are not considered new triples in *main*.

### Build (& install)

Build as any mvn project. `mvn clean package` should give you a fat jat in `target/`. For more 
convenience, you can build a really executable file (under any system with bash) and place it 
somewhere on your `$PATH`:
```bash
git clone https://github.com/alexishuf/infer.git
cd infer
./create_executable.sh && mv infer ~/bin/
cd ~
infer file.ttl
```

### Options
Here goes the output of `./infer --help` to save you the effort of building.
```text
 [INPUT ...] [--exclude-uri (-x) URI] [--exclude-uri-rx (-X) RX] [--help (-h)] [--input (-i) FILE] [--input-bg (-b) FILE] [--input-bg-uri (-B) URI] [--input-language (-l) LANG] [--input-uri (-I) URI] [--list-input-languages (-L)] [--list-output-formats (-F)] [--list-profiles (-P)] [--list-reasoners (-R)] [--no-dereference-imports (-D)] [--no-echo (-E)] [--output (-o) FILE] [--output-bg (-O) FILE] [--output-format (-f) FMT] [--profile (-p) VAL] [--reasoner (-r) VAL] [--split (-s)]

 INPUT                         : One or more input files or URIs that are
                                 considered under the main inputs (-i, -I).
 --exclude-uri (-x) URI        : Do not dereference these URIs
 --exclude-uri-rx (-X) RX      : Same as --exclude-uri, but uses a regular
                                 expression (Java syntax)
 --help (-h)                   : Show usage (default: true)
 --input (-i) FILE             : Use the given file as background triples. Has
                                 effect on split reasoning.
 --input-bg (-b) FILE          : Use the given file as background triples. Has
                                 effect on split reasoning.
 --input-bg-uri (-B) URI       : Same as --input-bg, but takes a URI
 --input-language (-l) LANG    : Input RDF language can be media type, file
                                 extension (without '.') or a field name in
                                 org.apache.jena.riotRDFLanguages. Default is
                                 to guess from input.
 --input-uri (-I) URI          : Same as --input-bg, but takes a URI
 --list-input-languages (-L)   : List all supported input languages (default:
                                 false)
 --list-output-formats (-F)    : List all supported output formats (default:
                                 false)
 --list-profiles (-P)          : List all profiles of selected reasoner
                                 (default: false)
 --list-reasoners (-R)         : List all available reasoners and exit
                                 (default: false)
 --no-dereference-imports (-D) : Disable downloading of owl:imports documents
                                 (default: false)
 --no-echo (-E)                : Disables echo, outputting only inferred 
                                 triples (default: false)
 --output (-o) FILE            : Output file for the main triples or all
                                 triples if --split is not givne
 --output-bg (-O) FILE         : Output file for the background triples
 --output-format (-f) FMT      : Similar to --input-language, but also
                                 understands field names from org.apache.jena.ri
                                 ot.RDFFormat. Default is to use the default
                                 RDFFormat of the input language
 --profile (-p) VAL            : Set reasoner profile (default: owl-fb)
 --reasoner (-r) VAL           : Use the specified reasoner (default: jena)
 --split (-s)                  : Splits inferences from background inputs and
                                 inferences from the union of main and
                                 background files. The latter does not include
                                 triples in the former. (default: false)
```