#!/bin/sh
mvn clean package || exit 1
cat > infer <<EOF
#!/bin/sh

exec java -jar "\$0" "\$@"

EOF
cat target/infer-*.jar >> infer || exit 1
chmod +x infer
./infer --help &> /dev/null \
    || (echo "Failed to produce a runnable file!"; exit 1)