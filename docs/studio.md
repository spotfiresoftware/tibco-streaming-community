# Using in Spotfire StreamBase Studio&trade;

These samples are kept on-line in github - this allows browsing of the samples on-line,
support of contributions outside of Spotfire engineering and updates outside of the main
product release lifecycle.

The samples can also be imported into Spotfire StreamBase Studio&trade; :

## Import using Spotfire StreamBase Studio&trade;

* Use **File -> Import Samples and Community Content...** menu option.
* Agree to Spotfire&reg; Streaming Community terms and conditions.
* Choose what sample(s) to import:

![smartimport](loadsamples.gif)

This will create a copy the projects to the workspace and any changes to the imported projects will not modify the git checkout. To work with the cloned repository directly, follow the steps to import a project with the Maven importer.

## Using the Maven Importer

* Copy the path of the git checkout. You can find its location from **File -> Import Samples and Community Content...** menu option, and clicking on the **Open repository clones in system explorer** link. The location can also be opened from the StreamBase Studio preferences page.
* Use **Import -> Maven -> Existing Maven Projects** menu option.
* Paste the path to the cloned repositories.
* Choose what sample(s) to import:

![mavenimport](mavenimport.gif)

## Working with multiple projects

By default, projects are organized in Spotfire StreamBase Studio&trade; in a flat structure :

![flat](flat.png)

With multiple projects ( such as importing all the samples ) this can become confusing.  Use
**Projects Presentation -> Hierarchical** for a more structured organization :

![hierarchical](hierarchical.gif)

## Other useful eclipse plugins

* [Markdown Text Editor](https://marketplace.eclipse.org/content/markdown-text-editor)
* [GitHub Flavored Markdown viewer plugin](https://marketplace.eclipse.org/content/github-flavored-markdown-viewer-plugin)
* [Mylyn WikiText](https://marketplace.eclipse.org/content/mylyn-wikitext)
* [Docker tooling](https://marketplace.eclipse.org/content/eclipse-docker-tooling) ( requires [TM Terminal](https://marketplace.eclipse.org/content/tm-terminal) )
* [Eclipse Copyright Generator](https://jmini.github.io/Eclipse-Copyright-Generator/)