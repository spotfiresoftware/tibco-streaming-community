# Contributing

As a public repository, we encourage and welcome contributions.  

## General

We follow the GitHub flow process, including :

* Repository is public – i.e. read-only to everyone.
* Collaborators are users with direct push access to the repository
    * Are from TIBCO Streaming engineering team
    * Are part of github team @streambasesamples
* Any user can submit a pull request
    * The request should mention @streambasesamples
    * One or more collaborators will engage with the aim of accepting the pull request
    * Pull requests can contain changes to existing samples or new samples
* All issues are tracked by github issues
    * See https://github.com/TIBCOSoftware/tibco-streaming-community/issues
    * Any user can create issues
    * Collaborators are expected to progress issues

## Sample requirements

* Samples may consist of one of:
  * A single fragment
  * A [maven aggregator](http://maven.apache.org/pom.html#Aggregation) + more than one fragment
  * A [maven aggregator](http://maven.apache.org/pom.html#Aggregation) + at least one application archive + at least one fragment
* New samples must be placed under the "components" directory and should be referenced by the aggregator pom in that directory
* Samples contain documentation in [markdown](https://guides.github.com/features/mastering-markdown/) format conforming to [maven site documentation rules](https://maven.apache.org/guides/mini/guide-site.html) (plus a README.md at the root of any new sample – even if the sample contains the same documentation somewhere else), containing:
    * Introduction
    * Code description or design notes, including studio screen shots if useful
    * For application archives, deployment configuration and instructions
    * Test case description and expected results
    * Links to main files (such as configurations and pom.xml)
* Fragments must include junit test case(s)
* Application archives must include integration tests that at least start the application up
* Samples must import into studio with no errors or warnings (after following any manual instructions)
* Ideally samples shouldn't reference 3rd party dependencies not available publicly or part of the StreamBase release. This allows samples to work without additional steps for both customers and automated builds.  However, when this isn't possible, the following applies: 
    * If the dependency is available on a vendor maintained maven repository, instructions are provided in that sample to use that repository.
    * If the dependency is only available with a manual download, instructions are provided in the sample to manually download the dependency, install into 
    the local maven repository and (optionally) deploy to a shared repository.
    * Internally, we do the same but deploy (or mirror) the dependency to a shared 3rd party repository. This repository is included in the sample builds.
    * Care must be taken to keep the metadata intact ( for example maintain copyright and license information ) so that the maven site info reports are correct.
* Commit messages should be clear and concise, describing the change well and referencing any GitHub issues if applicable. See https://chris.beams.io/posts/git-commit/ for a description of good commit messages.
* In order to support Windows, samples should limit file lengths as much as possible:
  * Keep artifactId's short
  * In node deployment files, specify short engine name

## License

All contributions to this repository, must include a licences element with a reference to the [BSD 3-Clause License](https://raw.githubusercontent.com/TIBCOSoftware/tibco-streaming-community/master/docs/LICENSE) included in this repository.

```
<licenses>
    <license>
        <name>BSD 3-Clause License</name>
        <url>https://raw.githubusercontent.com/TIBCOSoftware/tibco-streaming-community/master/docs/LICENSE</url>
        <distribution>repo</distribution>
    </license>
</licenses>
```

## Making the sample visible in Studio

In order to make a sample visible from Studio, the property `com.tibco.ep.sb.studio.sample` must be included and set to `true` in the root pom.xml of the sample. Name and description elements must also be included and should be kept as short as possible. Take note that these will be displayed in a Studio dialog that lists available content from this repository.

```
<name>The Name of the Sample</name>
<description>A brief description of the sample</description>

<properties>
    <com.tibco.ep.sb.studio.sample>true</com.tibco.ep.sb.studio.sample>
</properties>
```

## Empty directories

Git doesn't store empty directories, so such directories that need to be part of the sample should include an empty .gitignore file.

## Images

In order to support viewing documents in github, studio and maven generated site documentation, images need to be placed in several different locations. Recommendations include:

* Markdown files are stored in src/site/markdown
* Images are stored in src/site/markdown/images
* A soft link is added from src/site/resources/images to src/site/markdown/images
* Links to images in markdown files are of the format **\!\[Alt Text\]\(images/MyImage.png\)**

## Index

Jenkins will re-generate `README.md` files based on the pom.xml metadata in the maven aggregators. Therefore the `README.md` files that exist outside of individual components should not be updated manually.