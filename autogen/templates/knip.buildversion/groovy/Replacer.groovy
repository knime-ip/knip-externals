/**
 * BuildFilesGenerator - generates maven poms and category.xml from an update-site.xml.
 *
 * @author Jonathan Hale (University of Konstanz)
 */

import groovy.xml.DOMBuilder
import groovy.xml.dom.DOMCategory
import groovy.util.logging.Slf4j

/**
 * Globals
 */
def String VERSION = "0.0.1"


/**
 * Main function
 */
def main() {
	
	log.info("--- Version Injector Version " + VERSION + "---")

	def targetFile = properties['targetFile']
	if (targetFile == null) {
		fail("Property 'targetFile' undefined.")
	}

	def timeStamp = properties['buildStamp']
	if (timeStamp == null) {
		fail("Property 'buildStamp' undefined.")
	}

	ant.replace(file: targetFile, token: "%(BUILDSTAMP)", value: "<ktimestamp>" + timeStamp +"</ktimestamp>")
}
main()

