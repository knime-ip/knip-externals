/**
 * BuildFilesGenerator - generates maven poms and category.xml from an update-site.xml.
 *
 * @author Jonathan Hale (University of Konstanz)
 */

import groovy.xml.DOMBuilder
import groovy.xml.dom.DOMCategory
import groovy.util.logging.Slf4j

class Helper{
	static String resolveProperty(p_project, p_version, p_compatible){

		String version = p_version
		if(p_version.startsWith("\${")){
			// maven version we need to worry!
			version = p_project.properties.getProperty(p_version.substring(2, p_version.length()-1))
		}
		if(p_compatible)
			version = version.replaceAll("[^\\d.]", "")

		return version
	}
}

/**
 * Artifact
 * A maven artifact.
 */
class Artifact {
	String group, name, version
	Boolean attachSource

	Artifact(p_project, p_group, p_name, p_version) {
		this.group 	= p_group
		this.name 	= p_name
		this.version	= Helper.resolveProperty(p_project, p_version, false)
		this.attachSource = true
	}
	Artifact(project, group, name, version, attachSource) {
		this.group = group
		this.name = name
		this.version = Helper.resolveProperty(project, version, false)
		if(attachSource == "" || attachSource == "true") {
			this.attachSource = true
		} else if (attachSource == "false") {
			this.attachSource = false
		} else {
			throw new IllegalArgumentException("The property \"attachSource\" must be either 'false','true', or unset!")
		}
	}
}

/**
 * BundleRef
 * Barebones information about a bundle to reference it via its name, group and version.
 */
class BundleRef extends Artifact {
	Boolean isExternal
    String bundleId

	BundleRef(project, p_group, p_name, p_version, p_isExternal, p_bundleId) {
		super(project, p_group, p_name, p_version)
		this.isExternal = p_isExternal
		this.bundleId = p_bundleId
	}

	String getBundleName(){
		if(!isExternal){
			return name
		}
        if(bundleId!= ''){
			return bundleId
		}
		return group + "." + name
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
	Boolean attachSources = true

	Bundle(project, group, name, version) {
		this.group 	= group
		this.name 	= name
		this.version	= Helper.resolveProperty(project, version, true)

		/* default values */
		this.instructions 	= ""
		this.exports 		= "*"
		this.imports		= "" //TODO: ?

		this.artifacts = new ArrayList<Artifact>()
		this.dependencies = new ArrayList<BundleRef>()
	}

	void addArtifact(project, group, name, version, attachSource) {
		this.artifacts.add(new Artifact(project, group, name, version, attachSource))
	}

	void addArtifact(Artifact artifact) {
		this.artifacts.add(artifact)
	}

	void addDependency(p_project, p_group, p_name, p_version, p_isExternal, p_bundleId) {
		this.dependencies.add(new BundleRef(p_project, p_group, p_name, p_version, p_isExternal, p_bundleId))
	}

	void addDependency(BundleRef p_br) {
		this.dependencies.add(p_br)
	}

	void setInstructions(p_instructions) {
		if (p_instructions != null) {
			this.instructions = p_instructions
		}
	}

	void setImport(p_imports) {
		if (p_imports != null) {
			this.imports = p_imports
		}
	}

	void setExport(p_exports) {
		if (p_exports != null) {
			this.exports = p_exports
		}
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
	}
}

/**
 * Repository
 * Contains information about an additional repository.
 */
class Repository {
	String id, layout, url

	Repository(p_id, p_url) {
		this(p_id, "", p_url)
	}

	Repository(p_id, p_layout, p_url) {
		this.id = p_id
		this.url = p_url
		this.layout = p_layout
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
	}

	void addRepository(p_id, p_layout, p_url) {
		if (p_layout != null) {
			this.repositories.add(new Repository(p_id, p_layout, p_url))
		} else {
			this.repositories.add(new Repository(p_id, p_url))
		}
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
		this.site = null
	}

	void collectDataFromXML(project, String buildDir, String p_filename) throws IOException {
		def files = [p_filename] as Queue
		def path, reader
		def prefix = p_filename.substring(0, p_filename.lastIndexOf('/'))

		while ((path = files.poll()) != null) {
			try {
				reader = new FileReader(path)
			} catch(IOException e) {
				throw e
			}


			def mainTag = DOMBuilder.parse(reader).documentElement
			use (DOMCategory) {

			def bundleGroupTags = null
			if (mainTag.name() == "updatesite") {
				def splitName = mainTag.'@name'.split(':')
				this.site = new UpdateSite(splitName[0], splitName[1])

				if (mainTag.'repositories' != null) {
					mainTag.repositories[0].'repository'.each {
						repo -> this.site.addRepository(repo.'id'.text(), repo.'layout'.text(), repo.'url'.text())
					}
				}

				mainTag.'include'.each {
					filename -> files.offer(prefix + File.separator + filename.text())
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
								def bundle = new Bundle(project, bundlegroup, bundleTag.'@name', bundleTag.'@version')
								bundleTag.'artifacts'[0].'artifact'.each {
									artifact -> bundle.addArtifact(project, artifact.'group'.text(), artifact.'id'.text(), artifact.'version'.text(), artifact.'attachSource'.text())
								}

								if (bundleTag.'dependencies' != null) {
									if (bundleTag.'dependencies'.size() > 0) {
										bundleTag.'dependencies'[0].'bundleref'.each {
											bundleref ->
												def name = bundleref.'@name'.split(':')
												def isExternal = Boolean.parseBoolean(bundleref.'@isExternal')
												def bundleId = bundleref.'@bundleId'
												bundle.addDependency(project, name[0], name[1], bundleref.'@version', isExternal, bundleId)
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
 * Main function
 */
def main() {

	def buildStamp = new Date().format("yyyyMMddHHmm")

	log.info("--- Build Files Generator Version 1.0.0 ---")
	log.info("--- Build Timestamp is " + buildStamp +  " ---")
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

	DataCollector data = new DataCollector()
	data.collectDataFromXML(project, buildDir, inputFile)

	log.info("> Generating parent, bundlegroup and bundle poms...")

	/*
	 * Generate Bundle Poms
	 */
	String templateDir = properties['templateDir']
	if (templateDir == null) {
		log.warn("Property 'templateDir' undefined. Assuming buildDir.")
		templateDir = ""
	}

	String parentTemplateFilename = properties['parentTemplate']
	if (parentTemplateFilename == null) {
		fail("Property 'parentTemplate' undefined. Please define a .xml template file in <properties> tag.")
	}
	String bundleTemplateFilename = properties['bundleTemplate']
	if (bundleTemplateFilename == null) {
		fail("Property 'bundleTemplate' undefined. Please define a .xml template file in <properties> tag.")
	}
	String sourceBundleTemplateFilename = properties['sourceBundleTemplate']
	if (sourceBundleTemplateFilename == null) {
		fail("Property 'sourceBundleTemplate' undefined. Please define a .xml template file in <properties> tag.")
	}
	String bundleGroupTemplateFilename = properties['bundleGroupTemplate']
	if (bundleGroupTemplateFilename == null) {
		fail("Property 'bundleGroupTemplate' undefined. Please define a .xml template file in <properties> tag.")
	}

	Template bundleTemplate = new Template(templateDir + File.separator + bundleTemplateFilename)
	Template sourceBundleTemplate = new Template(templateDir + File.separator + sourceBundleTemplateFilename)
	Template bundleGroupTemplate = new Template(templateDir + File.separator + bundleGroupTemplateFilename)
	Template parentTemplate = new Template(templateDir + File.separator + parentTemplateFilename)
	try {
		String modules = ""
		String parentModules = "<modules>\n"
		String parentRepos = "<repositories>\n"

		// add repositories
		for(Repository repo : data.site.repositories) {
			parentRepos += ("\t\t<repository>\n\t\t\t<id>" + repo.id + "</id>\n"
				 + ((repo.layout.isEmpty()) ? ("") : ("\t\t\t<layout>" + repo.layout + "</layout>\n"))
				 + "\t\t\t<url>" + repo.url + "</url>\n\t\t</repository>\n")
		}

		// add bundle groups and generate bundlegroup poms
		for(BundleGroup group : data.site.bundleGroups) {
			Map group_map = ["BUNDLEGROUP_NAME":group.name]
			modules = "\t<modules>\n"
			parentModules += "\t\t<module>" + group.name + "</module>\n"

			// add bundles and generate bundle poms
			for (Bundle bundle : group.bundles) {
				modules += "\t\t<module>" + bundle.name + "</module>\n"

				String dependencies = "\t<dependencies>\n"
				String sourceDependencies = "\t<dependencies>\n"
				String requireBundles = ""

				for (Artifact artifact : bundle.artifacts) {
					dependencies += ("\t\t<dependency>\n"
						+ "\t\t\t<groupId>" + artifact.group + "</groupId>\n"
						+ "\t\t\t<artifactId>" + artifact.name + "</artifactId>\n"
						+ "\t\t\t<version>" + artifact.version + "</version>\n"
						+ "\t\t\t<exclusions>\n"
						+ "\t\t\t\t<exclusion>\n"
						+ "\t\t\t\t\t<groupId>log4j</groupId>\n"
						+ "\t\t\t\t\t<artifactId>log4j</artifactId>\n"
						+ "\t\t\t\t</exclusion>\n"
						+ "\t\t\t</exclusions>\n"
						+ "\t\t</dependency>\n")

					if(artifact.attachSource) {
						sourceDependencies += ("\n\t\t<dependency>\n"
								+ "\t\t\t<groupId>" + artifact.group + "</groupId>\n"
								+ "\t\t\t<artifactId>" + artifact.name + "</artifactId>\n"
								+ "\t\t\t<version>" + artifact.version + "</version>\n"
								+ "\t\t\t<classifier>sources</classifier>\n"
								+ "\t\t</dependency>\n")
					}
				}
				def first = true
				for (BundleRef bundleRef : bundle.dependencies) {
					dependencies += ("\n\t\t<dependency>\n"
						+ "\t\t\t<groupId>" + bundleRef.group + "</groupId>\n"
						+ "\t\t\t<artifactId>" + bundleRef.name + "</artifactId>\n"
						+ "\t\t\t<version>" + bundleRef.version + "</version>\n"
						+ "\t\t\t<type>bundle</type>\n\t\t\t<scope>provided</scope>\n"
						+ "\t\t</dependency>\n")
					if (first) {
						first = false
					} else {
						requireBundles += ",\n"
					}

					requireBundles += "\t\t" + bundleRef.getBundleName() + ";bundle-version=\"" + Helper.resolveProperty(project, bundleRef.version, true) + "\""
				}

				requireBundles = (requireBundles.isEmpty()) ? "" : "\t<Require-Bundle>\n" + requireBundles + "\n\t</Require-Bundle>\n"

				// write bundle pom
				bundleTemplate.writeFile(buildDir + File.separator + group.name + File.separator + bundle.name, "pom.xml", [
					"BUNDLE_GROUP":bundle.group.name,
					"BUNDLE_NAME":bundle.name,
					"BUNDLE_VERSION":Helper.resolveProperty(project, bundle.version, true) + "." + buildStamp,
					"BUNDLE_INSTRUCTIONS":"<instructions>\n" + requireBundles + bundle.instructions + "</instructions>",
					"BUNDLE_EXPORT":bundle.exports,
					"BUNDLE_IMPORT":bundle.imports,
					"BUNDLE_ARTIFACTS":dependencies + "\t</dependencies>\n"
				])

				if (sourceDependencies.equals("\t<dependencies>\n")){  // if all artifacts have no source attachment, we skip this bundle
					bundle.attachSources = false
					continue
				}
				// write source bundle pom
				sourceBundleTemplate.writeFile(buildDir + File.separator + group.name + File.separator + bundle.name + "-sources", "pom.xml", [
						"BUNDLE_GROUP":bundle.group.name,
						"BUNDLE_NAME":bundle.name,
						"BUNDLE_VERSION":Helper.resolveProperty(project, bundle.version, true) + "." + buildStamp,
						"BUNDLE_ARTIFACTS":sourceDependencies + "\t</dependencies>\n"
				])
				modules += "\t\t<module>" + bundle.name +"-sources" + "</module>\n"
			}

			group_map['MODULES'] = modules + "\t</modules>\n"

			bundleGroupTemplate.writeFile(buildDir + File.separator + group.name, "pom.xml", group_map)
		}
		parentTemplate.writeFile(buildDir, "pom.xml", ["MODULES":parentModules+"\t</modules>","REPOSITORIES":parentRepos+"\t</repositories>"])
	} catch(IOException e) {
		fail("Could not open template file '" + p_filename + "'." + e)
	}

	log.info("> Generating update site pom.xml...")
	String updateSiteTemplateFilename = properties['updateSiteTemplate']
	if (updateSiteTemplateFilename == null) {
		fail("Property 'updateSiteTemplate' undefined. Please define a .xml template file in <properties> tag.")
	}

	// Create updatesite pom
	Template updateSiteTemplate = new Template(templateDir + File.separator + updateSiteTemplateFilename)

	def dependencies = "\t<dependencies>\n"
	def bundles = ""
	data.site.bundleGroups.each {
		group -> group.bundles.each {
			bundle-> dependencies += ("\t\t<dependency> \n"
                    + "\t\t\t<groupId>" + data.site.group + "</groupId>\n"
                    + "\t\t\t<artifactId>" + bundle.name + "</artifactId>\n"
                    + "\t\t\t<version>" + bundle.version + "." + buildStamp +"</version>\n\t\t</dependency>\n"
				)

				if(bundle.attachSources){
				dependencies +=	("\t\t<dependency> \n"
                    + "\t\t\t<groupId>" + data.site.group + "</groupId>\n"
                    + "\t\t\t<artifactId>" + bundle.name + ".source" + "</artifactId>\n"
                    + "\t\t\t<version>" + bundle.version + "." + buildStamp +"</version>\n\t\t</dependency>\n")
					bundles += "\t<bundle id=\"" + bundle.name + ".source" + "\" version=\"0.0.0\" />\n"
				}

				bundles += "\t<bundle id=\"" + bundle.name + "\" version=\"0.0.0\" />\n"
		}
	}
	updateSiteTemplate.writeFile(buildDir + File.separator + "update-site", "pom.xml", ["GROUP":data.site.group,"NAME":data.site.name,"DEPENDENCIES":dependencies+"\t</dependencies>\n"])

	log.info("> Generating category.xml...")
	String categoryTemplateFilename = properties['categoryTemplate']
	if (categoryTemplateFilename == null) {
		fail("Property 'categoryTemplate' undefined. Please define a .xml template file in <properties> tag.")
	}

	Template categoryTemplate = new Template(templateDir + File.separator + categoryTemplateFilename)
	categoryTemplate.writeFile(buildDir + File.separator + "update-site", "category.xml", ["BUNDLES":bundles])

	log.info("> SUCCESS!")
}
main()

