# knip-scijava-bundles

Wraps artifacts distributed via maven into OSGi Bundles for use with KNIME Image
Processing.

## How to build

### Requirements:

To be able to build this project, the following projects must be installed via
maven beforehand:

- [knip-imglib2-ops](https://github.com/knime-ip/knip-imglib2-ops) 
- [knip-trackmate-fork](https://github.com/dietzc/TrackMate)

Checkout these projects and run `mvn clean install` in each of their root directories.

### Building

Build the update site by running the ``rebuild.sh`` script.

## How to install into your eclipse

After successfully building, the update site is available in two forms:

- As archive: `target/update-site/target/knip-osgi-update-site-1.0.0.zip`
- As folder:  `target/update-site/target/repository`


## How to add a new bundles

1. Create or locate the appropriate bundle group file in the ``auogen/bundlegroups``
   folder. There is one bundlegroup per maven group. The example artifact
   ``com.examplegroup.exampleartifact`` thus would be placed into the
   bundle group ``com.examplegroup.xml``. A bundlegroup file has the following structure:

```xml
  <bundlegroup name="org.examplegroup">
      <!-- bundles -->
  </bundlegroup>
```

Don't forget to add new bundlegroups to the `updatesite.xml` file in the `autogen` folder.
```xml
  <include>bundlegroups/com.examplegroup.xml</include>
```


2. Add a bundle to a bundlegroup following this template:
```xml
   <bundle name="example-bundle-name" version="${example-bundle-name.version}">
       <artifacts>
       <!-- List all artifacts that belong to this bundle. -->
           <artifact>
               <group>com.examplegroup</group>
               <id>exampleartifact</id>
               <version>${example-bundle-name.version}</version>
           </artifact>
       </artifacts>
       <dependencies>
           <!-- You can define dependencies to other bundles created by this project -->
           <bundleref name="org.examplegroup:exampledependency" version="${exampledependency.version}" />
           <!-- You can also define a depency as external if it is sattisfied  by a KNIME update site  -->
           <bundleref name="com.examplegroup:externaldependency" version="${externaldependency.version}" isExternal="true" />
       </dependencies>
       <!-- List all the packages you need to export: you can use the wildcard: "*" to export a packaga and all subpackages, different roots are seperated by ","  -->
       <export>com.examplegroup.exampleartifact.*, com.examplegroup.otherartifact.*</export> 
   </bundle>
```

### Optional parameters for bundles
- __Don't attach a source bundle:__
    In some cases you might not want to attach a source bundle to an artifact, e.g. if the
    creation takes a very long time, or if the source bundle causes problems due to a layout that
    is incompatible with OSGi (e.g. classes in the default package). To skip
    the creation of a source bundle, set the ``attachSource`` property to
    `false` by adding the following code to an artifact:
```xml
    <artifact>
        ...
        <attachSource>false</attachSource>
    </artifact>
```
 __External dependencies with a custom name:__ Sometimes you need to depend on osgi bundles that are not shipped with this update site, but provided otherwise, e.g. by the KNIME target platform, where they might have a different bundle id from the one inferred by this tool. You can specify such a custom bundleId as follows:
```xml
<dependencies>
    ...
    <bundleref name="com.examplegroup:externaldependency" version="${externaldependency.version}" isExternal="true" bundleId="com.externaldependency:customname" />
</dependencies>
``` 

- __Additional instructions:__ You can pass additional arguments for a specific
  bundle to the [Apache felix bundle plugin](http://felix.apache.org/documentation/subprojects/apache-felix-maven-bundle-plugin-bnd.html).
  The xml code must be escaped ([syntax](http://stackoverflow.com/questions/1091945/what-characters-do-i-need-to-escape-in-xml-documents)).
  Example 
    ```xml
	<bundle name="example-bundle" version="${example-bundle.version}">
		<artifacts>
            <!-- ... -->
		</artifacts>
        <export>   
            <!-- ... -->
        </export>
        <instructions>
            <!-- instructions must escape xml: <exampleInstruction>exampleValue</exampleInstruction> becomes:-->
          &lt;exampleInstruction&gt;exampleValue&lt;/exampleInstruction&gt;
        </instructions>
	</bundle>
    ```
    - __Accept OSGi errors:__ One such instruction is `_failok` which will allow
      the bundle to be built even if errors occur. This is needed if you want to
      bundle code that does not follow to OSGi standards, e.g. has classes in the
      default package. The code snippet for `_failok`:
        
        ```xml
          <instructions>
            &lt;_failok&gt;true&lt;/_failok&gt; 
          </instructions>
          ```
     Note the xml escaping:  `&lt;` for `<` and `&gt;` for `>`.
