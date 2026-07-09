# Writing a Groovy BlackLab plugin with type checking

Groovy plugin do not need to be compiled like Java plugins; the `.groovy` script can be placed directly in BlackLab's `plugins` directory.

However, if you want to use type checking in your Groovy plugin, you can use this Maven project as a template. Copy this directory and import the project in your IDE, e.g. IntelliJ. Make sure the `blacklab` dependency in the `pom.xml` file points to the correct version of BlackLab you are using (for e.g. `5.0.0-SNAPSHOT`, you may have to clone blacklab and build it locally with `mvn install` for it to be available).

You should now be able to write your Groovy plugin with type checking and autocompletion. When you're happy with it, don't run the Maven build, just copy the `.groovy` file to BlackLab's `plugins` directory to test it. BlackLab will compile it at runtime.

See https://blacklab.ivdnt.org/development/customization/ for more information.
