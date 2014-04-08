mvn package
rm -r dist
mkdir dist
cp NLBB/script/* dist/
mkdir dist/lib
cp NLBB/target/NLBB-2.0-SNAPSHOT.jar dist/lib
cp NLBB/target/dependency/* dist/lib
cp -r NLBW/fonts dist/fonts
cp -r NLBW/xsl dist/xsl
mvn clean