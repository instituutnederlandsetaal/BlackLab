# Install BlackLab standalone on MacOS (by Dirk Roorda)

> This guide was written by Dirk Roorda for the CLARIAH project General Missives project. This is a slightly edited (top heading, other heading levels) archived version; the original can be found [here](https://github.com/CLARIAH/wp6-missieven/blob/master/blacklab/install.md). It is licensed under the MIT license.

## About

This document describes how to install a working Blacklab server and client
on a standalone machine running
macos.

## TOC

* Prerequisites
    * Requirements
    * Java
    * Tomcat
        * Run Tomcat
        * Manage TomCat
            * Set up managers
* Blacklab
    * File organization
        * Explanation
    * Server
        * Configure
        * Example corpus
        * Deploy within TomCat
        * Test the query tool
    * Client

## Prerequisites

### Requirements:

* macos 10.15.7 or higher (Catalina), earlier probably works as good
* Commandline tools installed (part of XCode)
  `xcode-select --install`
* [HomeBrew](https://brew.sh) (a macos package manager)


### JAVA

We use Homebrew to install a Java Development Kit (JDK), from an open source,
using a mechanism of HomeBrew that is optimized for large binaries.

```
brew update
brew tap homebrew/cask
brew cask install adoptopenjdk
java -version
```

Results in:

```
openjdk version "15" 2020-09-15
OpenJDK Runtime Environment AdoptOpenJDK (build 15+36)
OpenJDK 64-Bit Server VM AdoptOpenJDK (build 15+36, mixed mode, sharing)
```

### TOMCAT

We install both TomCat and its native library, in order to get access to SSL.

```
brew install tomcat
brew install tomcat-native
```

We get a notification:

```
In order for tomcat's APR lifecycle listener to find this library, you'll
need to add it to java.library.path. This can be done by adding this line
to $CATALINA_HOME/bin/setenv.sh

  CATALINA_OPTS="$CATALINA_OPTS -Djava.library.path=/usr/local/opt/tomcat-native/lib"

If $CATALINA_HOME/bin/setenv.sh doesn't exist, create it and make it executable.
```

**N.B** This `CATALINA` is a code name for TomCat and has nothing to do with the current
macos version 10.15, also named `Catalina`.

OK, it appears that

`CATALINA_HOME` is `/usr/local/Cellar/tomcat/9.0.40/libexec`
although there is no CATALINA_HOME visible to the shell.
You can either set such a variable and use it in the ocmmands below, or spell
the value out.

It seems that it is not needed to set this variable for TomCat to work.

Depending on whether you have chosen to set `CATALINA_HOME` in your shell
say either

```
vim $CATALINA_HOME/bin/setenv.sh
```
or

```
vim /usr/local/Cellar/tomcat/9.0.40/libexec/bin/setenv.sh
```

and add the line

```
CATALINA_OPTS="$CATALINA_OPTS -Djava.library.path=/usr/local/opt/tomcat-native/lib"
```

Then

either

```
chmod ugo+x $CATALINA_HOME/bin/setenv.sh
```

or

```
chmod ugo+x /usr/local/Cellar/tomcat/9.0.40/libexec/bin/setenv.sh
```

#### Run TOMCAT

There are several ways to run TomCat:

As a service that starts when the Mac starts:

```
brew services start tomcat
```

Manually

```
catalina run
```

and stop it by `Ctrl-C`.

As a background process:

```
catalina start
```

and stop it with

```
catalina stop
```

See also

```
catalina -h
```

#### Manage TOMCAT

In the browser, navigate to

http://localhost:8080

You should see a page that says that you have successfully installed TomCat.

##### Set up managers

See the users in

```
cd /usr/local/Cellar/tomcat/9.0.40/libexec 
vim conf/tomcat-users.xml
```

and add the lines

```
<role rolename="manager-gui"/>
<user username="dirk" password="dirk" roles="manager-gui"/> 
```

where you can replace `dirk` and `dirk` by whatever you like.

## Blacklab

To see what Blacklab is, see
[blacklab intro](https://blacklab.ivdnt.org/index.html).

A working blacklab installation consists of a server, a client, and corpus data.

### File organization

The following bit of file organization is not rigidly prescribed.
You can also choose another organization.
Whatever you do, it needs to be reflected in subsequent config files and shell commands.

This is an organization that I find convenient at this stage:

```
blacklab/
         data/
              incoming/
              indexes/
         program/
         installation/
```

I have put it all under `~/local` i.e. my home directory and then
a subdirectory `blacklab`.

#### Contents and downloads

The `data` directory will receive corpus data.

The `incoming` subdir receives downloaded data, the `indexes` subdir is the destination
of the blacklab indexer.

The `installation` directory receives the downloaded `blacklab-server-2.1.0` war file.

This file is attached to a release of the Blacklab repo.
The releases are listed
[here](https://github.com/instituutnederlandsetaal/BlackLab/releases/)
and we pick release 2.1.0.
You see a file
[blacklab-server-2.1.0.war](https://github.com/instituutnederlandsetaal/BlackLab/releases/download/v2.1.0/blacklab-server-2.1.0.war)
there, download it and place it in the `installation` directory.

We will unzip it in place, and copy its `WEB-INF/lib` directory to the `program` directory.

Over there, we move the `blacklab-2.1.0.jar` file one level up, so that it is directly beneath
the `program` dir.

When we `cd` to the program dir, we can easily run the java program in the blacklab jar file,
supported by the libraries in the jar files under the `lib` subdirectory.

We'll need the blacklab program soon: for indexing the first corpus.

We also need to download a front-end, a.k.a. client.
This is in the
[INL/corpus-frontend](https://github.com/instituutnederlandsetaal/corpus-frontend) repo.
Again, move to the
[releases](https://github.com/instituutnederlandsetaal/corpus-frontend/releases)
page and there you find release 2.1.0.
You see a file
[corpus-frontend-2.1.0.war](https://github.com/instituutnederlandsetaal/corpus-frontend/releases/download/v2.1.0/corpus-frontend-2.1.0.war)
there, download it and place it in the `installation` directory.

### Server
See
[blacklab-server overview](/server/)

#### Configure

Set an environment variable to point to the blacklab server config, do
this in your `.zshrc` file.

Note that `~` will not work properly, so spell out the complete path
from the root of your system to the directory where your blacklab config dir is:

```
BLACKLAB_CONFIG_DIR="/Users/dirk/local/blacklab"
export BLACKLAB_CONFIG_DIR
```

Then edit/create a server config file:

```
cd ~/local/blacklab
vim blacklab-server.yaml
```

and add the contents

```
---
configVersion: 2

# Where indexes can be found
# (list directories whose subdirectories are indexes, or directories containing a single index)
indexLocations:
- /Users/dirk/local/blacklab/data/indexes
```

**N.B.**
Note that in this config file you can not use the `~` abbreviation.

Deploying the blacklab war now leads to a friendly message from blacklab that there are no indexes.
So, before we deploy, we create the indexes for an example corpus and put it in place.

#### Example data

We download the
[Brown corpus](https://github.com/instituutnederlandsetaal/BlackLab/wiki/brownCorpus.lemmatized.xml.zip),
a single XML file of 66 MB when unzipped, to be put in `data/incoming`.

Run the blacklab index tool by running the blacklab jar:

```
cd ~/local/blacklab/program/
```

Then run the jar:

```
java -cp "blacklab-2.1.0.jar" nl.inl.blacklab.tools.IndexTool create ~/local/blacklab/data/indexes/brown ~/local/blacklab/data/incoming/brownCorpus.lemmatized.xml tei
```

#### Deploy within Tomcat

In the terminal, give the command

```
catalina start
```

Then, in the browser navigate (again) to

http://localhost:8080

Click the manage app and login with `dirk`, `dirk` (which is what have have put in the
TomCat config file for users, above).

In the list of applications, click the blacklab-server-2.1.0 entry.

You should see something like:

```xml
<blacklabResponse>
    <blacklabBuildTime>2020-06-22 16:01:41</blacklabBuildTime>
    <blacklabVersion>2.1.0</blacklabVersion>
    <indices>
        <index name="brown">
            <displayName>brown</displayName>
            <description/>
            <status>available</status>
            <documentFormat>tei</documentFormat>
            <timeModified>2020-11-24 11:47:37</timeModified>
            <tokenCount>1008320</tokenCount>
        </index>
    </indices>
    <user>
        <loggedIn>false</loggedIn>
        <canCreateIndex>false</canCreateIndex>
    </user>
</blacklabResponse>
```

which means that all is well and that the Brown corpus indexes have been found.

#### Test the query tool

Still in the `program` directory you can run the query tool:

```
java -cp blacklab-2.1.0.jar nl.inl.blacklab.tools.QueryTool ~/local/blacklab/data/indexes/brown
```

You get a prompt `CorpusQL> `.
Enter the query `"egg"` followed by a newline.

You get results.
Exit by giving the command `exit`.

```
CorpusQL> "egg"
   1. [0106]               is thick , much like an [egg] plant?s skin , so that poison
   2. [0115]                it with beaten yolk of [egg]
   3. [0144]        kitchen for coffee grounds and [egg] shells . All these materials and
   4. [0303]            On this , she builds an `` [egg] compartment ?? or `` egg cell ?? which
   5. [0303] builds an `` egg compartment ?? or `` [egg] cell ?? which is filled with
   6. [0303]             the beebread loaf and the [egg] compartment is closed . The queen
   7. [0303]                  retires to a life of [egg] laying . The first worker bees
   8. [0303]      in unobtrusively , to deposit an [egg] on a completed loaf of
   9. [0303]        before the bumblebees seal the [egg] compartment . The hosts never seem
  10. [0303]             which is provided with an [egg] plus a store of beebread
  11. [0332]              with the chicken and the [egg] . Which came first ? ? Is it
  12. [0400]    constructed a highboard around the [egg] case which he had placed
12 hits in 6 documents
105 ms elapsed
CorpusQL> exit
dirk:~/local/blacklab/program > 
```

### Client

The main front-end for Blacklab is in a separate
[GitHub repo](https://github.com/instituutnederlandsetaal/corpus-frontend/)
