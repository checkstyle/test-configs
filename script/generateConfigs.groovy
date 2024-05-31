def runJavaXmlParser(String xmlFilePath, String moduleName) {
    try {
        def command = "java -jar XMLParsing.jar ${xmlFilePath} ${moduleName}"
        def proc = command.execute()
        proc.in.eachLine { line -> println line }
        proc.err.eachLine { line -> System.err.println line }
        proc.waitFor()
    } catch (Exception e) {
        e.printStackTrace()
    }
}

def main() {
    def repoUrl = "https://github.com/checkstyle/checkstyle.git"
    def destinationDir = "checkstyle-repo"
    def xmlFilePath = "${destinationDir}/src/xdocs/checks/annotation/annotationlocation.xml"
    def moduleName = "AnnotationLocation"

    println "Cloning repository..."
    "git clone ${repoUrl} ${destinationDir}".execute().waitFor()

    println "Running Java XML Parser..."
    runJavaXmlParser(xmlFilePath, moduleName)

    println "Generated configuration for ${moduleName}"
}

main()

