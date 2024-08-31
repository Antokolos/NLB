mvn generate-sources package
rm -r dist
mkdir dist
cp NLBB/script/* dist/
mkdir dist/lib
cp NLBB/target/NLBB*.jar dist/lib
cp NLBB/target/lib/* dist/lib
cp NLBB/target/modules/* dist/lib
cp -r NLBW/fonts dist/fonts
cp -r NLBW/xsl dist/xsl
cp -r NLBW/cfg dist/cfg
cp -r NLBW/res dist/res
cp -r NLBW/template dist/template
mvn clean
