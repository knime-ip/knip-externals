/**
 * BuildFilesGenerator - generates maven poms and category.xml from an udaptesite xml.
 *
 * @author Jonathan Hale (University of Konstanz)
 */

import groovy.xml.DOMBuilder
import groovy.xml.dom.DOMCategory
import groovy.util.logging.Slf4j

/**
 * Artifact
 * A maven artifact.
 */
class Artifact {
	String group, name, version
	
	Artifact(p_group, p_name, p_version) {
		this.group 	= p_group
		this.name 	= p_name
		this.version= p_version
	}
}

/**
 * BundleRef
 * Barebones inforamtion about a bundle to reference it via its name, group and version.
 */
class BundleRef extends Artifact {	
	BundleRef(p_group, p_name, p_version) {
		super(p_group, p_name, p_version)
	}
}
/**
 * Bundle
 * Contains all information about a bundle.
 */
class Bundle {
	List<Artifact> artifacts
	List<BundleRef> dependencies
	
	BundleGroup group
	String name, version, instructions
	String exports, imports
			
	Bundle(p_group, p_name, p_version) {
		this.group 	= p_group
		this.name 	= p_name
		this.version= p_version
		
		/* default values */
		this.instructions 	= ""
		this.exports 		= "*"
		this.imports		= "*" //TODO: ?
		
		
		this.artifacts = new ArrayList<Artifact>()
		this.dependencies = new ArrayList<BundleRef>()
	}
	
	void addArtifact(p_group, p_name, p_version) {
		this.artifacts.add(new Artifact(p_group, p_name, p_version))
	}
	
	void addArtifact(Artifact p_a) {
		this.artifacts.add(p_a)
	}
	
	void addDependency(p_group, p_name, p_version) {
		this.dependencies.add(new BundleRef(p_group, p_name, p_version))
	}
	
	void addDependency(BundleRef p_br) {
		this.dependencies.add(p_br)
	}
	
	void setInstructions(p_instructions) {
		if (p_instructions != null) {
			this.instructions = p_instructions
		}
		return
	}
	
	void setImport(p_imports) {
		if (p_imports != null) {
			this.imports = p_imports
		}
		return
	}
	
	void setExport(p_exports) {
		if (p_exports != null) {
			this.exports = p_exports
		}
		return
	}
}

/**
 * BundleGroup
 * Contains a list of bundles associated with a name.
 */
class BundleGroup {
	String name
	List<Bundle> bundles
	
	BundleGroup(p_name) {
		this.name = p_name
		this.bundles = new ArrayList<Bundle>()
	}
	
	void addBundle(Bundle p_bundle) {
		if (p_bundle != null) {
			this.bundles.add(p_bundle)
		}
		return
	}
}

/**
 * Repository
 * Contains information about an additional repository.
 */
class Repository {
	String id, url
	
	Repository(p_id,p_url) {
		this.id = p_id
		this.url = p_url
	}
}

/**
 * UpdateSite
 * Contains all information needed to generate update site build files.
 */
class UpdateSite {
	List<BundleGroup> bundleGroups
	List<Repository> repositories
	
	String group, name
	
	UpdateSite(p_group, p_name) {
		this.group = p_group
		this.name  = p_name
		this.bundleGroups = new ArrayList<BundleGroup>()
		this.repositories = new ArrayList<Repository>()
	}
	
	void addRepository(Repository p_repo) {
		if (p_repo == null) {
			return
		}
		this.repositories.add(p_repo)
		return
	}
	
	void addRepository(p_id, p_url) {
		this.repositories.add(new Repository(p_id, p_url))
	}
	
	void addBundleGroup(BundleGroup p_bg) {
		if (p_bg == null) {
			return
		}
		this.bundleGroups.add(p_bg)
	}
}

/**
 * DataCollector:
 * - Parses xml
 * - Parses values from xml
 * - Stores everything in an UpdateSite
 */
class DataCollector {
	UpdateSite site
	
	DataCollector() {
		this.site = null;
	}
	
	void collectDataFromXML(String buildDir, String p_filename) throws IOException {
		def files = [p_filename] as Queue
		def path, reader
		
		while ((path = files.poll()) != null) {
			try {
				reader = new FileReader(path)
			} catch(IOException e) {
				throw e
			}
			
			
			def mainTag = groovy.xml.DOMBuilder.parse(reader).documentElement
			use (DOMCategory) {
				
			def bundleGroupTags = null
			if (mainTag.name() == "updatesite") {
				def splitName = mainTag.'@name'.split(':')
				this.site = new UpdateSite(splitName[0], splitName[1]);
				
				if (mainTag.'repositories' != null) {
					mainTag.repositories[0].'repository'.each {
						repo -> this.site.addRepository(repo.'id'.text(), repo.'url'.text())
					}
				}
				
				mainTag.'include'.each {
					filename -> files.offer(filename.text())
				}
				bundleGroupTags = mainTag.'bundlegroup'
				
			} else if (mainTag.name() == "bundlegroup") {
				bundleGroupTags = [mainTag]
			} else {
				fail "Invalid document element in \"" + buildDir + File.separator + path + "\""
			}
			
			bundleGroupTags.each {
					bundleGroupTag ->
						def bundlegroup = new BundleGroup(bundleGroupTag.'@name')
						
						bundleGroupTag.'bundle'.each {
							bundleTag ->
								def bundle = new Bundle(bundlegroup, bundleTag.'@name', bundleTag.'@version')
								bundleTag.'artifacts'[0].'artifact'.each {
									artifact -> bundle.addArtifact(artifact.'group'.text(), artifact.'id'.text(), artifact.'version'.text())
								}
								
								if (bundleTag.'dependencies' != null) {
									if (bundleTag.'dependencies'.size() > 0) {
										bundleTag.'dependencies'[0].'bundleref'.each {
											bundleref -> 
											def name = bundleref.'@name'.split(':')
											bundle.addDependency(name[0], name[1], bundleref.'@version')
										}
									}
								}
								
								bundle.setInstructions(bundleTag.'instructions'.text())
								bundle.setImport(bundleTag.'import'.text())
								bundle.setExport(bundleTag.'export'.text())
								
								bundlegroup.addBundle(bundle)
						}
						site.addBundleGroup(bundlegroup)
			}
			}
		}
	}
}

/**
 * Template
 * Read a file and then write a file with a list of keywords to replace.
 */
class Template {
	String templateString
	
	Template(p_filename) throws IOException {
		templateString = ""
		new File(p_filename).eachLine {
			line -> templateString += line + "\n"
		}
	}
	
	void writeFile(p_path, p_name, Map<String, String> keywords) throws IOException {
		def outFile = new File(p_path, p_name)
		outFile.getParentFile().mkdirs()
		outFile.createNewFile()
		
		// prepare the String with replaced keywords
		String result = templateString
		for (Map.Entry entry : keywords.entrySet()) {
			result = result.replaceAll('%\\(' + entry.getKey() + '\\)', {entry.getValue()})
		}
		
		outFile.withWriter { w ->
				w << result
		}
	}
}

/**
 * Globals
 */
def String VERSION = "0.0.1"

/**
 * Main function
 */
def main() {
	
	log.info("--- Build Files Generator Version 1.0.0 ---")
	log.info("> Initializing...")
	
	def buildDir = properties['buildDir']
	if (buildDir == null) {
		log.warn("Property 'buildDir' undefined. Assuming working directory.")
		buildDir = "."
	}
	
	log.info("> Collecting Information...")
	
	def inputFile = properties['input']
	if (inputFile == null) {
		fail("Property 'input' undefined. Please define a .xml input file in <properties> tag.")
	}
	
	def DataCollector data = new DataCollector()
	data.collectDataFromXML(buildDir, inputFile)
	
	log.info("> Generating parent, bundlegroup and bundle poms...")
	
	/*
	 * Generate Bundle Poms
	 */
	def String templateDir = properties['templateDir']
	if (templateDir == null) {
		log.warn("Property 'templateDir' undefined. Assuming buildDir.")
		templateDir = ""
	}
	
	def String parentTemplateFilename = properties['parentTemplate']
	if (parentTemplateFilename == null) {
		fail("Property 'parentTemplate' undefined. Please define a .xml template file in <properties> tag.")
	}
	def String bundleTemplateFilename = properties['bundleTemplate']
	if (bundleTemplateFilename == null) {
		fail("Property 'bundleTemplate' undefined. Please define a .xml template file in <properties> tag.")
	}
	def String bundleGroupTemplateFilename = properties['bundleGroupTemplate']
	if (bundleGroupTemplateFilename == null) {
		fail("Property 'bundleGroupTemplate' undefined. Please define a .xml template file in <properties> tag.")
	}
	
	def Template bundleTemplate = new Template(templateDir + File.separator + bundleTemplateFilename)
	def Template bundleGroupTemplate = new Template(templateDir + File.separator + bundleGroupTemplateFilename)
	def Template parentTemplate = new Template(templateDir + File.separator + parentTemplateFilename)
	try {
		def String modules = ""
		def String parentModules = "<modules>\n"
		def String parentRepos = "<repositories>\n"
		
		// add repositories
		for(Repository repo : data.site.repositories) {
			parentRepos += "\t<repository><id>" + repo.id + "</id><url>" + repo.url + "</url></repository>\n"
		}
		
		// add bundle groups and generate bundlegroup poms
		for(BundleGroup group : data.site.bundleGroups) {
			def Map group_map = ["BUNDLEGROUP_NAME":group.name]
			modules = "<modules>\n"
			parentModules += "\t<module>" + group.name + "</module>\n"
			
			// add bundles and generate bundle poms
			for (Bundle bundle : group.bundles) {
				modules += "\t<module>" + bundle.name + "</module>\n"
				
				String dependencies = "<dependencies>\n"
				
				for (Artifact a : bundle.artifacts) {
					dependencies += ("\n<dependency> \n"
						+ "\t\t<groupId>" + a.group + "</groupId>\n"
						+ "\t\t<artifactId>" + a.name + "</artifactId>\n"
						+ "\t\t<version>" + a.version + "</version>\n"
						+ "\t</dependency>\n")
				}
				for (BundleRef d : bundle.dependencies) {
					dependencies += ("\n<dependency> \n"
						+ "\t\t<groupId>" + d.group + "</groupId>\n"
						+ "\t\t<artifactId>" + d.name + "</artifactId>\n"
						+ "\t\t<version>" + d.version + "</version>\n"
						+ "\t\t<type>bundle</type>\n\t\t<scope>provided</scope>"
						+ "\t</dependency>\n")
				}
				
				bundleTemplate.writeFile(buildDir + File.separator + group.name + File.separator + bundle.name, "pom.xml", [
					"BUNDLE_GROUP":bundle.group.name,
					"BUNDLE_NAME":bundle.name,
					"BUNDLE_VERSION":bundle.version,
					"BUNDLE_INSTRUCTIONS":bundle.instructions,
					"BUNDLE_EXPORT":bundle.exports,
					"BUNDLE_IMPORT":bundle.imports,
					"BUNDLE_ARTIFACTS":dependencies + "</dependencies>\n"
				])
			}
			group_map['MODULES'] = modules + "</modules>\n"
			
			bundleGroupTemplate.writeFile(buildDir + File.separator + group.name, "pom.xml", group_map)
		}
		parentTemplate.writeFile(buildDir, "pom.xml", ["MODULES":parentModules+"</modules>","REPOSITORIES":parentRepos+"</repositories>"])
	} catch(IOException e) {
		fail("Could not open template file '" + p_filename + "'.")
	}
	
	log.info("> Generating update site pom.xml...")
	def String updateSiteTemplateFilename = properties['updateSiteTemplate']
	if (updateSiteTemplateFilename == null) {
		fail("Property 'updateSiteTemplate' undefined. Please define a .xml template file in <properties> tag.")
	}
	
	def Template updateSiteTemplate = new Template(templateDir + File.separator + updateSiteTemplateFilename)
	
	def dependencies = "<dependencies>\n"
	def bundles = ""
	data.site.bundleGroups.each {
		group -> group.bundles.each {
			bundle-> dependencies += ("\t<dependency> \n"
				+ "\t\t<groupId>" + data.site.group + "</groupId>\n"
				+ "\t\t<artifactId>" + bundle.name + "</artifactId>\n"
				+ "\t\t<version>" + bundle.version + "</version>\n\t</dependency>"
				)
				bundles += "\t<bundle id=\"" + bundle.name + "\" version=\"0.0.0\" />\n"
		}
	}
	updateSiteTemplate.writeFile(buildDir + File.separator + "update-site", "pom.xml", ["GROUP":data.site.group,"NAME":data.site.name,"DEPENDENCIES":dependencies+"\t</dependencies>\n"])
	
	log.info("> Generating category.xml...")
	def String categoryTemplateFilename = properties['categoryTemplate']
	if (categoryTemplateFilename == null) {
		fail("Property 'categoryTemplate' undefined. Please define a .xml template file in <properties> tag.")
	}
	
	def Template categoryTemplate = new Template(templateDir + File.separator + categoryTemplateFilename)
	categoryTemplate.writeFile(buildDir + File.separator + "update-site", "category.xml", ["BUNDLES":bundles])
	
	log.info("> SUCCESS!")
}
main()

