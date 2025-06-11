import{_ as a,c as i,o as n,al as e}from"./chunks/framework.B3j7FRen.js";const d=JSON.parse('{"title":"Configuration","description":"","frontmatter":{},"headers":[],"relativePath":"server/configuration.md","filePath":"server/050_configuration.md","lastUpdated":1749114403000}'),l={name:"server/configuration.md"};function t(p,s,h,k,r,o){return n(),i("div",null,s[0]||(s[0]=[e(`<h1 id="configuration" tabindex="-1">Configuration <a class="header-anchor" href="#configuration" aria-label="Permalink to &quot;Configuration&quot;">​</a></h1> <p>BlackLab and BlackLab Server settings can be configured in configuration files.</p> <h2 id="yaml-vs-json" tabindex="-1">YAML vs. JSON <a class="header-anchor" href="#yaml-vs-json" aria-label="Permalink to &quot;YAML vs. JSON&quot;">​</a></h2> <p>These files can be in YAML or JSON format. On this page, we will use the YAML format (as it allows comments and is arguably more readable), but it the two can be easily converted back and forth (for example <a href="https://www.json2yaml.com/" target="_blank" rel="noreferrer">here</a>). Just be sure to use the <code>.json</code> extension for JSON and <code>.yaml</code> or <code>.yml</code> for YAML.</p> <h2 id="blacklab-vs-blacklab-server-config" tabindex="-1">BlackLab vs. BlackLab Server config <a class="header-anchor" href="#blacklab-vs-blacklab-server-config" aria-label="Permalink to &quot;BlackLab vs. BlackLab Server config&quot;">​</a></h2> <p>The configuration files are called <code>blacklab-server.yaml</code> and <code>blacklab.yaml</code>. You most likely only need <code>blacklab-server.yaml</code>, which can contain everything <code>blacklab.yaml</code> can and more.</p> <p>To be precise: BlackLab programs such as QueryTool, IndexTool and BlackLab Server always look for <code>blacklab.yaml</code>. BlackLab Server also looks for <code>blacklab-server.yaml</code>. So if you&#39;re just running BlackLab Server, you probably only need a <code>blacklab-server.yaml</code> file, but if you also want to configure some detail about how IndexTool and QueryTool (or other BlackLab-based applications) run, it can be useful to have a <code>blacklab.yaml</code> file too.</p> <h2 id="config-file-locations" tabindex="-1">Config file locations <a class="header-anchor" href="#config-file-locations" aria-label="Permalink to &quot;Config file locations&quot;">​</a></h2> <p>Where should this file (or files) be located? BlackLab looks for them in the following places:</p> <ul><li>the directory specified in <code>$BLACKLAB_CONFIG_DIR</code>, if this environment variable was defined</li> <li><code>$HOME/.blacklab</code></li> <li><code>/etc/blacklab</code></li> <li>the Java classpath</li></ul> <p>In addition, BlackLab Server will also look for <code>blacklab-server.yaml</code> in the directory where the .war file is located, e.g. <code>/usr/share/tomcat/webapps</code>.</p> <h2 id="sections" tabindex="-1">Sections <a class="header-anchor" href="#sections" aria-label="Permalink to &quot;Sections&quot;">​</a></h2> <p>Here we take a look at individual sections of the configuration file. This is a work in progress; see the <a href="#complete-config-file">complete config file</a> for a full example.</p> <h3 id="search-settings" tabindex="-1">Search settings <a class="header-anchor" href="#search-settings" aria-label="Permalink to &quot;Search settings&quot;">​</a></h3> <p>This controls how BlackLab searches your corpora.</p> <div class="language-yaml vp-adaptive-theme"><button title="Copy Code" class="copy"></button><span class="lang">yaml</span><pre class="shiki shiki-themes github-light github-dark vp-code" tabindex="0"><code><span class="line"><span style="--shiki-light:#6A737D;--shiki-dark:#6A737D;"># Defaults for searching</span></span>
<span class="line"><span style="--shiki-light:#6A737D;--shiki-dark:#6A737D;"># NOTE: these are BlackLab defaults, not the defaults for the BlackLab Server parameters;</span></span>
<span class="line"><span style="--shiki-light:#6A737D;--shiki-dark:#6A737D;"># see the parameters section for those.</span></span>
<span class="line"><span style="--shiki-light:#22863A;--shiki-dark:#85E89D;">search</span><span style="--shiki-light:#24292E;--shiki-dark:#E1E4E8;">:</span></span>
<span class="line"></span>
<span class="line"><span style="--shiki-light:#6A737D;--shiki-dark:#6A737D;">    # Collator to use for sorting, grouping, etc.</span></span>
<span class="line"><span style="--shiki-light:#22863A;--shiki-dark:#85E89D;">    collator</span><span style="--shiki-light:#24292E;--shiki-dark:#E1E4E8;">:</span></span>
<span class="line"><span style="--shiki-light:#22863A;--shiki-dark:#85E89D;">        language</span><span style="--shiki-light:#24292E;--shiki-dark:#E1E4E8;">: </span><span style="--shiki-light:#032F62;--shiki-dark:#9ECBFF;">nl</span><span style="--shiki-light:#6A737D;--shiki-dark:#6A737D;">   # required</span></span>
<span class="line"><span style="--shiki-light:#22863A;--shiki-dark:#85E89D;">        country</span><span style="--shiki-light:#24292E;--shiki-dark:#E1E4E8;">: </span><span style="--shiki-light:#032F62;--shiki-dark:#9ECBFF;">NL</span><span style="--shiki-light:#6A737D;--shiki-dark:#6A737D;">    # optional</span></span>
<span class="line"><span style="--shiki-light:#6A737D;--shiki-dark:#6A737D;">        #variant: x     # optional</span></span>
<span class="line"></span>
<span class="line"><span style="--shiki-light:#6A737D;--shiki-dark:#6A737D;">    # Default number of words around hit.</span></span>
<span class="line"><span style="--shiki-light:#22863A;--shiki-dark:#85E89D;">    contextSize</span><span style="--shiki-light:#24292E;--shiki-dark:#E1E4E8;">: </span><span style="--shiki-light:#005CC5;--shiki-dark:#79B8FF;">5</span></span>
<span class="line"></span>
<span class="line"><span style="--shiki-light:#6A737D;--shiki-dark:#6A737D;">    # The default maximum number of hits to retrieve (and use for sorting, grouping, etc.).</span></span>
<span class="line"><span style="--shiki-light:#6A737D;--shiki-dark:#6A737D;">    # -1 means no limit, but be careful, this may stress your server.</span></span>
<span class="line"><span style="--shiki-light:#22863A;--shiki-dark:#85E89D;">    maxHitsToRetrieve</span><span style="--shiki-light:#24292E;--shiki-dark:#E1E4E8;">: </span><span style="--shiki-light:#005CC5;--shiki-dark:#79B8FF;">1000000</span></span>
<span class="line"><span style="--shiki-light:#24292E;--shiki-dark:#E1E4E8;">    </span></span>
<span class="line"><span style="--shiki-light:#6A737D;--shiki-dark:#6A737D;">    # The default maximum number of hits to count.</span></span>
<span class="line"><span style="--shiki-light:#6A737D;--shiki-dark:#6A737D;">    # -1 means no limit, but be careful, this may stress your server.</span></span>
<span class="line"><span style="--shiki-light:#22863A;--shiki-dark:#85E89D;">    maxHitsToCount</span><span style="--shiki-light:#24292E;--shiki-dark:#E1E4E8;">: </span><span style="--shiki-light:#005CC5;--shiki-dark:#79B8FF;">-1</span></span></code></pre></div><h3 id="authentication-and-authorization" tabindex="-1">Authentication and authorization <a class="header-anchor" href="#authentication-and-authorization" aria-label="Permalink to &quot;Authentication and authorization&quot;">​</a></h3> <p>You can configure an authentication system in BlackLab. This does not take care of log in, sign up, etc., but only instructs BlackLab how to find the currently logged-in user (if there is one). This is useful if you want users to be able to create private corpora.</p> <p>To read the user id from a request header, attribute or parameter:</p> <div class="language-yaml vp-adaptive-theme"><button title="Copy Code" class="copy"></button><span class="lang">yaml</span><pre class="shiki shiki-themes github-light github-dark vp-code" tabindex="0"><code><span class="line"><span style="--shiki-light:#22863A;--shiki-dark:#85E89D;">authentication</span><span style="--shiki-light:#24292E;--shiki-dark:#E1E4E8;">:</span></span>
<span class="line"><span style="--shiki-light:#22863A;--shiki-dark:#85E89D;">    system</span><span style="--shiki-light:#24292E;--shiki-dark:#E1E4E8;">:</span></span>
<span class="line"><span style="--shiki-light:#22863A;--shiki-dark:#85E89D;">        class</span><span style="--shiki-light:#24292E;--shiki-dark:#E1E4E8;">: </span><span style="--shiki-light:#032F62;--shiki-dark:#9ECBFF;">AuthRequestValue</span></span>
<span class="line"><span style="--shiki-light:#6A737D;--shiki-dark:#6A737D;">        # attribute|header|parameter</span></span>
<span class="line"><span style="--shiki-light:#22863A;--shiki-dark:#85E89D;">        type</span><span style="--shiki-light:#24292E;--shiki-dark:#E1E4E8;">: </span><span style="--shiki-light:#032F62;--shiki-dark:#9ECBFF;">attribute</span></span>
<span class="line"><span style="--shiki-light:#6A737D;--shiki-dark:#6A737D;">        # name of the attribute, header or parameter that contains the user id</span></span>
<span class="line"><span style="--shiki-light:#22863A;--shiki-dark:#85E89D;">        name</span><span style="--shiki-light:#24292E;--shiki-dark:#E1E4E8;">: </span><span style="--shiki-light:#032F62;--shiki-dark:#9ECBFF;">userId</span></span></code></pre></div><p>To use HTTP Basic Authentication (if enabled in <code>web.xml</code>, which it is not by default):</p> <div class="language-yaml vp-adaptive-theme"><button title="Copy Code" class="copy"></button><span class="lang">yaml</span><pre class="shiki shiki-themes github-light github-dark vp-code" tabindex="0"><code><span class="line"><span style="--shiki-light:#22863A;--shiki-dark:#85E89D;">authentication</span><span style="--shiki-light:#24292E;--shiki-dark:#E1E4E8;">:</span></span>
<span class="line"><span style="--shiki-light:#22863A;--shiki-dark:#85E89D;">    system</span><span style="--shiki-light:#24292E;--shiki-dark:#E1E4E8;">:</span></span>
<span class="line"><span style="--shiki-light:#22863A;--shiki-dark:#85E89D;">        class</span><span style="--shiki-light:#24292E;--shiki-dark:#E1E4E8;">: </span><span style="--shiki-light:#032F62;--shiki-dark:#9ECBFF;">AuthHttpBasic</span></span></code></pre></div><p>For testing, <code>AuthDebugFixed</code> can be useful:</p> <div class="language-yaml vp-adaptive-theme"><button title="Copy Code" class="copy"></button><span class="lang">yaml</span><pre class="shiki shiki-themes github-light github-dark vp-code" tabindex="0"><code><span class="line"><span style="--shiki-light:#22863A;--shiki-dark:#85E89D;">authentication</span><span style="--shiki-light:#24292E;--shiki-dark:#E1E4E8;">:</span></span>
<span class="line"><span style="--shiki-light:#22863A;--shiki-dark:#85E89D;">    system</span><span style="--shiki-light:#24292E;--shiki-dark:#E1E4E8;">:</span></span>
<span class="line"><span style="--shiki-light:#22863A;--shiki-dark:#85E89D;">        class</span><span style="--shiki-light:#24292E;--shiki-dark:#E1E4E8;">: </span><span style="--shiki-light:#032F62;--shiki-dark:#9ECBFF;">AuthDebugFixed</span></span>
<span class="line"><span style="--shiki-light:#22863A;--shiki-dark:#85E89D;">        userId</span><span style="--shiki-light:#24292E;--shiki-dark:#E1E4E8;">: </span><span style="--shiki-light:#032F62;--shiki-dark:#9ECBFF;">me@example.com</span></span></code></pre></div><p>With this, BlackLab will simply assume the specified user is always logged in. This is obviously not safe to use in production.</p> <h2 id="minimal-config-file" tabindex="-1">Minimal config file <a class="header-anchor" href="#minimal-config-file" aria-label="Permalink to &quot;Minimal config file&quot;">​</a></h2> <p>Here&#39;s a minimal configuration file for BlackLab Server. Name it <code>blacklab-server.yaml</code> and place it in the same directory as the <code>blacklab-server.war</code> file or in <code>/etc/blacklab</code> (or configure a different location as just described).</p> <div class="language-yaml vp-adaptive-theme"><button title="Copy Code" class="copy"></button><span class="lang">yaml</span><pre class="shiki shiki-themes github-light github-dark vp-code" tabindex="0"><code><span class="line"><span style="--shiki-light:#6F42C1;--shiki-dark:#B392F0;">---</span></span>
<span class="line"><span style="--shiki-light:#22863A;--shiki-dark:#85E89D;">configVersion</span><span style="--shiki-light:#24292E;--shiki-dark:#E1E4E8;">: </span><span style="--shiki-light:#005CC5;--shiki-dark:#79B8FF;">2</span></span>
<span class="line"></span>
<span class="line"><span style="--shiki-light:#6A737D;--shiki-dark:#6A737D;"># Where corpora (indexes) can be found</span></span>
<span class="line"><span style="--shiki-light:#6A737D;--shiki-dark:#6A737D;"># (list directories whose subdirectories are cocpora, or directories containing a single corpus)</span></span>
<span class="line"><span style="--shiki-light:#22863A;--shiki-dark:#85E89D;">indexLocations</span><span style="--shiki-light:#24292E;--shiki-dark:#E1E4E8;">:</span></span>
<span class="line"><span style="--shiki-light:#24292E;--shiki-dark:#E1E4E8;">- </span><span style="--shiki-light:#032F62;--shiki-dark:#9ECBFF;">/data/index</span></span></code></pre></div><p>This simply tells BlackLab Server where to find its indexes.</p> <p>A minimal example of <code>blacklab.yaml</code> would be no file at all, as no setting in <code>blacklab.yaml</code> is required for IndexTool or QueryTool to run.</p> <h2 id="complete-config-file" tabindex="-1">Complete config file <a class="header-anchor" href="#complete-config-file" aria-label="Permalink to &quot;Complete config file&quot;">​</a></h2> <p>Below is a fully populated version of <code>blacklab-server.yaml</code>.</p> <div class="tip custom-block"><p class="custom-block-title">TIP</p> <p>You don&#39;t need all these sections! Just use the ones you want to specifically influence, and leave the rest out. See the minimal config file above to get started.</p></div> <div class="tip custom-block"><p class="custom-block-title">TIP</p> <p>You can also use a file called <code>blacklab.yaml</code> if you want to configure details about running <code>IndexTool</code> and <code>QueryTool</code> as well. It can only contain the <code>log</code>, <code>search</code>, <code>indexing</code> and <code>plugin</code> sections (located at the end of this example config). This file may be useful if you want to increase the number of metadata values stored in the index metadata file, for example. If you&#39;re not sure, you probably don&#39;t need this.</p></div> <div class="language-yaml vp-adaptive-theme"><button title="Copy Code" class="copy"></button><span class="lang">yaml</span><pre class="shiki shiki-themes github-light github-dark vp-code" tabindex="0"><code><span class="line"><span style="--shiki-light:#6F42C1;--shiki-dark:#B392F0;">---</span></span>
<span class="line"><span style="--shiki-light:#6A737D;--shiki-dark:#6A737D;"># BlackLab Server config file</span></span>
<span class="line"><span style="--shiki-light:#6A737D;--shiki-dark:#6A737D;"># ===============================================================</span></span>
<span class="line"></span>
<span class="line"><span style="--shiki-light:#6A737D;--shiki-dark:#6A737D;"># This indicates we&#39;re using the new index format.</span></span>
<span class="line"><span style="--shiki-light:#22863A;--shiki-dark:#85E89D;">configVersion</span><span style="--shiki-light:#24292E;--shiki-dark:#E1E4E8;">: </span><span style="--shiki-light:#005CC5;--shiki-dark:#79B8FF;">2</span></span>
<span class="line"></span>
<span class="line"><span style="--shiki-light:#6A737D;--shiki-dark:#6A737D;"># Where indexes can be found</span></span>
<span class="line"><span style="--shiki-light:#6A737D;--shiki-dark:#6A737D;"># (list directories whose subdirectories are indexes, or directories containing a single index)</span></span>
<span class="line"><span style="--shiki-light:#22863A;--shiki-dark:#85E89D;">indexLocations</span><span style="--shiki-light:#24292E;--shiki-dark:#E1E4E8;">:</span></span>
<span class="line"><span style="--shiki-light:#24292E;--shiki-dark:#E1E4E8;">- </span><span style="--shiki-light:#032F62;--shiki-dark:#9ECBFF;">/data/index</span></span>
<span class="line"></span>
<span class="line"><span style="--shiki-light:#6A737D;--shiki-dark:#6A737D;"># Directory containing each users&#39; private indexes</span></span>
<span class="line"><span style="--shiki-light:#6A737D;--shiki-dark:#6A737D;"># (only works if you&#39;ve configured an authentication system, see below)</span></span>
<span class="line"><span style="--shiki-light:#22863A;--shiki-dark:#85E89D;">userIndexes</span><span style="--shiki-light:#24292E;--shiki-dark:#E1E4E8;">: </span><span style="--shiki-light:#032F62;--shiki-dark:#9ECBFF;">/data/user-index</span></span>
<span class="line"></span>
<span class="line"><span style="--shiki-light:#6A737D;--shiki-dark:#6A737D;"># Settings related to BlackLab Server&#39;s protocol, i.e. requests and responses</span></span>
<span class="line"><span style="--shiki-light:#22863A;--shiki-dark:#85E89D;">protocol</span><span style="--shiki-light:#24292E;--shiki-dark:#E1E4E8;">:</span></span>
<span class="line"></span>
<span class="line"><span style="--shiki-light:#6A737D;--shiki-dark:#6A737D;">    # If true, omits empty annotation values from XML results.</span></span>
<span class="line"><span style="--shiki-light:#22863A;--shiki-dark:#85E89D;">    omitEmptyProperties</span><span style="--shiki-light:#24292E;--shiki-dark:#E1E4E8;">: </span><span style="--shiki-light:#005CC5;--shiki-dark:#79B8FF;">false</span></span>
<span class="line"><span style="--shiki-light:#24292E;--shiki-dark:#E1E4E8;">    </span></span>
<span class="line"><span style="--shiki-light:#6A737D;--shiki-dark:#6A737D;">    # Default response type (XML or JSON; default XML)</span></span>
<span class="line"><span style="--shiki-light:#22863A;--shiki-dark:#85E89D;">    defaultOutputType</span><span style="--shiki-light:#24292E;--shiki-dark:#E1E4E8;">: </span><span style="--shiki-light:#032F62;--shiki-dark:#9ECBFF;">XML</span></span>
<span class="line"><span style="--shiki-light:#24292E;--shiki-dark:#E1E4E8;">    </span></span>
<span class="line"><span style="--shiki-light:#6A737D;--shiki-dark:#6A737D;">    # Value for the Access-Control-Allow-Origin HTTP header (default: *)</span></span>
<span class="line"><span style="--shiki-light:#22863A;--shiki-dark:#85E89D;">    accessControlAllowOrigin</span><span style="--shiki-light:#24292E;--shiki-dark:#E1E4E8;">: </span><span style="--shiki-light:#032F62;--shiki-dark:#9ECBFF;">&quot;*&quot;</span></span>
<span class="line"><span style="--shiki-light:#24292E;--shiki-dark:#E1E4E8;">    </span></span>
<span class="line"></span>
<span class="line"></span>
<span class="line"><span style="--shiki-light:#6A737D;--shiki-dark:#6A737D;"># Defaults and maximum values for parameters</span></span>
<span class="line"><span style="--shiki-light:#6A737D;--shiki-dark:#6A737D;"># (some values will affect server load)</span></span>
<span class="line"><span style="--shiki-light:#22863A;--shiki-dark:#85E89D;">parameters</span><span style="--shiki-light:#24292E;--shiki-dark:#E1E4E8;">:</span></span>
<span class="line"></span>
<span class="line"><span style="--shiki-light:#6A737D;--shiki-dark:#6A737D;">    # Are searches case/accent-sensitive or -insensitive by default?</span></span>
<span class="line"><span style="--shiki-light:#22863A;--shiki-dark:#85E89D;">    defaultSearchSensitivity</span><span style="--shiki-light:#24292E;--shiki-dark:#E1E4E8;">: </span><span style="--shiki-light:#032F62;--shiki-dark:#9ECBFF;">insensitive</span></span>
<span class="line"></span>
<span class="line"><span style="--shiki-light:#6A737D;--shiki-dark:#6A737D;">    # The maximum number of hits to process (return as results, and </span></span>
<span class="line"><span style="--shiki-light:#6A737D;--shiki-dark:#6A737D;">    # use for sorting, grouping, etc.). -1 means no limit.</span></span>
<span class="line"><span style="--shiki-light:#6A737D;--shiki-dark:#6A737D;">    # (&quot;maxretrieve&quot; parameter)</span></span>
<span class="line"><span style="--shiki-light:#6A737D;--shiki-dark:#6A737D;">    # (higher values will put more stress on the server)</span></span>
<span class="line"><span style="--shiki-light:#22863A;--shiki-dark:#85E89D;">    processHits</span><span style="--shiki-light:#24292E;--shiki-dark:#E1E4E8;">:</span></span>
<span class="line"><span style="--shiki-light:#22863A;--shiki-dark:#85E89D;">        default</span><span style="--shiki-light:#24292E;--shiki-dark:#E1E4E8;">: </span><span style="--shiki-light:#005CC5;--shiki-dark:#79B8FF;">1000000</span></span>
<span class="line"><span style="--shiki-light:#22863A;--shiki-dark:#85E89D;">        max</span><span style="--shiki-light:#24292E;--shiki-dark:#E1E4E8;">: </span><span style="--shiki-light:#005CC5;--shiki-dark:#79B8FF;">2000000</span></span>
<span class="line"></span>
<span class="line"><span style="--shiki-light:#6A737D;--shiki-dark:#6A737D;">    # The maximum number of hits to count. -1 means no limit.</span></span>
<span class="line"><span style="--shiki-light:#6A737D;--shiki-dark:#6A737D;">    # (&quot;maxcount&quot; parameter)</span></span>
<span class="line"><span style="--shiki-light:#6A737D;--shiki-dark:#6A737D;">    # (higher values will put more stress on the server)</span></span>
<span class="line"><span style="--shiki-light:#22863A;--shiki-dark:#85E89D;">    countHits</span><span style="--shiki-light:#24292E;--shiki-dark:#E1E4E8;">:</span></span>
<span class="line"><span style="--shiki-light:#22863A;--shiki-dark:#85E89D;">        default</span><span style="--shiki-light:#24292E;--shiki-dark:#E1E4E8;">: </span><span style="--shiki-light:#005CC5;--shiki-dark:#79B8FF;">-1</span></span>
<span class="line"><span style="--shiki-light:#22863A;--shiki-dark:#85E89D;">        max</span><span style="--shiki-light:#24292E;--shiki-dark:#E1E4E8;">: </span><span style="--shiki-light:#005CC5;--shiki-dark:#79B8FF;">10000000</span></span>
<span class="line"></span>
<span class="line"><span style="--shiki-light:#6A737D;--shiki-dark:#6A737D;">    # Number of results per page (&quot;number&quot; parameter). -1 means no limit.</span></span>
<span class="line"><span style="--shiki-light:#6A737D;--shiki-dark:#6A737D;">    # (a very high max value might lead to performance problems)</span></span>
<span class="line"><span style="--shiki-light:#22863A;--shiki-dark:#85E89D;">    pageSize</span><span style="--shiki-light:#24292E;--shiki-dark:#E1E4E8;">:</span></span>
<span class="line"><span style="--shiki-light:#22863A;--shiki-dark:#85E89D;">        default</span><span style="--shiki-light:#24292E;--shiki-dark:#E1E4E8;">: </span><span style="--shiki-light:#005CC5;--shiki-dark:#79B8FF;">50</span></span>
<span class="line"><span style="--shiki-light:#22863A;--shiki-dark:#85E89D;">        max</span><span style="--shiki-light:#24292E;--shiki-dark:#E1E4E8;">: </span><span style="--shiki-light:#005CC5;--shiki-dark:#79B8FF;">3000</span></span>
<span class="line"></span>
<span class="line"><span style="--shiki-light:#6A737D;--shiki-dark:#6A737D;">    # Context around match (&quot;context&quot; parameter)</span></span>
<span class="line"><span style="--shiki-light:#6A737D;--shiki-dark:#6A737D;">    # (higher values might cause copyright issues and may stress the server)</span></span>
<span class="line"><span style="--shiki-light:#6A737D;--shiki-dark:#6A737D;">    # Set to 0 to omit the left and right context altogether.</span></span>
<span class="line"><span style="--shiki-light:#22863A;--shiki-dark:#85E89D;">    contextSize</span><span style="--shiki-light:#24292E;--shiki-dark:#E1E4E8;">:</span></span>
<span class="line"><span style="--shiki-light:#22863A;--shiki-dark:#85E89D;">        default</span><span style="--shiki-light:#24292E;--shiki-dark:#E1E4E8;">: </span><span style="--shiki-light:#005CC5;--shiki-dark:#79B8FF;">5</span></span>
<span class="line"><span style="--shiki-light:#22863A;--shiki-dark:#85E89D;">        max</span><span style="--shiki-light:#24292E;--shiki-dark:#E1E4E8;">: </span><span style="--shiki-light:#005CC5;--shiki-dark:#79B8FF;">20</span></span>
<span class="line"></span>
<span class="line"><span style="--shiki-light:#6A737D;--shiki-dark:#6A737D;">    #  Default pattern language to use.</span></span>
<span class="line"><span style="--shiki-light:#6A737D;--shiki-dark:#6A737D;">    #  The pattlang URL parameter override this value.</span></span>
<span class="line"><span style="--shiki-light:#22863A;--shiki-dark:#85E89D;">    patternLanguage</span><span style="--shiki-light:#24292E;--shiki-dark:#E1E4E8;">: </span><span style="--shiki-light:#032F62;--shiki-dark:#9ECBFF;">bcql</span></span>
<span class="line"></span>
<span class="line"><span style="--shiki-light:#6A737D;--shiki-dark:#6A737D;">    #  Default filter language to use.</span></span>
<span class="line"><span style="--shiki-light:#6A737D;--shiki-dark:#6A737D;">    #  The filterlang URL parameter override this value.</span></span>
<span class="line"><span style="--shiki-light:#22863A;--shiki-dark:#85E89D;">    filterLanguage</span><span style="--shiki-light:#24292E;--shiki-dark:#E1E4E8;">: </span><span style="--shiki-light:#032F62;--shiki-dark:#9ECBFF;">luceneql</span></span>
<span class="line"></span>
<span class="line"><span style="--shiki-light:#6A737D;--shiki-dark:#6A737D;">    # By default, should we include the grouped hits in</span></span>
<span class="line"><span style="--shiki-light:#6A737D;--shiki-dark:#6A737D;">    # grouped responses? If false, just include group </span></span>
<span class="line"><span style="--shiki-light:#6A737D;--shiki-dark:#6A737D;">    # identity and size. Defaults to false. Can be overridden </span></span>
<span class="line"><span style="--shiki-light:#6A737D;--shiki-dark:#6A737D;">    # using the &quot;includegroupcontents&quot; URL parameter.</span></span>
<span class="line"><span style="--shiki-light:#22863A;--shiki-dark:#85E89D;">    writeHitsAndDocsInGroupedHits</span><span style="--shiki-light:#24292E;--shiki-dark:#E1E4E8;">: </span><span style="--shiki-light:#005CC5;--shiki-dark:#79B8FF;">false</span></span>
<span class="line"></span>
<span class="line"><span style="--shiki-light:#6A737D;--shiki-dark:#6A737D;">    # If we&#39;re capturing part of our matches, should</span></span>
<span class="line"><span style="--shiki-light:#6A737D;--shiki-dark:#6A737D;">    # we include empty captures? This can happen when the</span></span>
<span class="line"><span style="--shiki-light:#6A737D;--shiki-dark:#6A737D;">    # clause to capture is optional, e.g. A:[]?</span></span>
<span class="line"><span style="--shiki-light:#6A737D;--shiki-dark:#6A737D;">    # Defaults to false. Can be overridden using the </span></span>
<span class="line"><span style="--shiki-light:#6A737D;--shiki-dark:#6A737D;">    # &quot;omitemptycaptures&quot; URL parameter.</span></span>
<span class="line"><span style="--shiki-light:#22863A;--shiki-dark:#85E89D;">    omitEmptyCaptures</span><span style="--shiki-light:#24292E;--shiki-dark:#E1E4E8;">: </span><span style="--shiki-light:#005CC5;--shiki-dark:#79B8FF;">false</span></span>
<span class="line"></span>
<span class="line"><span style="--shiki-light:#6A737D;--shiki-dark:#6A737D;">    # What REST API version to attempt compatibility with.</span></span>
<span class="line"><span style="--shiki-light:#6A737D;--shiki-dark:#6A737D;">    # Valid values are currently: 3.0, 4.0, current, experimental.</span></span>
<span class="line"><span style="--shiki-light:#6A737D;--shiki-dark:#6A737D;">    # Defaults to current.</span></span>
<span class="line"><span style="--shiki-light:#22863A;--shiki-dark:#85E89D;">    api</span><span style="--shiki-light:#24292E;--shiki-dark:#E1E4E8;">: </span><span style="--shiki-light:#032F62;--shiki-dark:#9ECBFF;">current</span></span>
<span class="line"></span>
<span class="line"></span>
<span class="line"></span>
<span class="line"><span style="--shiki-light:#6A737D;--shiki-dark:#6A737D;">#  Settings for job caching.</span></span>
<span class="line"><span style="--shiki-light:#22863A;--shiki-dark:#85E89D;">cache</span><span style="--shiki-light:#24292E;--shiki-dark:#E1E4E8;">:</span></span>
<span class="line"></span>
<span class="line"><span style="--shiki-light:#6A737D;--shiki-dark:#6A737D;">    # How much free memory the cache should shoot for (in megabytes) while cleaning up.</span></span>
<span class="line"><span style="--shiki-light:#6A737D;--shiki-dark:#6A737D;">    # Because we don&#39;t have direct control over the garbage collector, we can&#39;t reliably clean up until</span></span>
<span class="line"><span style="--shiki-light:#6A737D;--shiki-dark:#6A737D;">    # this exact number is available. Instead we just get rid of a few cached tasks whenever a</span></span>
<span class="line"><span style="--shiki-light:#6A737D;--shiki-dark:#6A737D;">    # new task is added and we&#39;re under this target number.</span></span>
<span class="line"><span style="--shiki-light:#22863A;--shiki-dark:#85E89D;">    targetFreeMemMegs</span><span style="--shiki-light:#24292E;--shiki-dark:#E1E4E8;">: </span><span style="--shiki-light:#005CC5;--shiki-dark:#79B8FF;">100</span></span>
<span class="line"></span>
<span class="line"><span style="--shiki-light:#6A737D;--shiki-dark:#6A737D;">    # The minimum amount of free memory required to start a new search task. If this memory is not available,</span></span>
<span class="line"><span style="--shiki-light:#6A737D;--shiki-dark:#6A737D;">    # your search will be queued.</span></span>
<span class="line"><span style="--shiki-light:#22863A;--shiki-dark:#85E89D;">    minFreeMemForSearchMegs</span><span style="--shiki-light:#24292E;--shiki-dark:#E1E4E8;">: </span><span style="--shiki-light:#005CC5;--shiki-dark:#79B8FF;">50</span></span>
<span class="line"><span style="--shiki-light:#24292E;--shiki-dark:#E1E4E8;">    </span></span>
<span class="line"><span style="--shiki-light:#6A737D;--shiki-dark:#6A737D;">    # Maximum number of searches that may be queued. If you try to add another search, this will return an error.</span></span>
<span class="line"><span style="--shiki-light:#6A737D;--shiki-dark:#6A737D;">    # Queued searches don&#39;t take up memory, but it&#39;s no use building up a huge queue that will take a very long time</span></span>
<span class="line"><span style="--shiki-light:#6A737D;--shiki-dark:#6A737D;">    # to get through. Better to ask users to return when server load is lower.</span></span>
<span class="line"><span style="--shiki-light:#22863A;--shiki-dark:#85E89D;">    maxQueuedSearches</span><span style="--shiki-light:#24292E;--shiki-dark:#E1E4E8;">: </span><span style="--shiki-light:#032F62;--shiki-dark:#9ECBFF;">20,</span></span>
<span class="line"></span>
<span class="line"><span style="--shiki-light:#6A737D;--shiki-dark:#6A737D;">    # How long after it was last accessed will a completed search task be removed from </span></span>
<span class="line"><span style="--shiki-light:#6A737D;--shiki-dark:#6A737D;">    # the cache? (in seconds)</span></span>
<span class="line"><span style="--shiki-light:#6A737D;--shiki-dark:#6A737D;">    # (don&#39;t set this too low; instead, set targetFreeMemMegs, the target amount of free memory)</span></span>
<span class="line"><span style="--shiki-light:#6A737D;--shiki-dark:#6A737D;">    # If you want to disable the cache altogether, set this to 0.</span></span>
<span class="line"><span style="--shiki-light:#22863A;--shiki-dark:#85E89D;">    maxJobAgeSec</span><span style="--shiki-light:#24292E;--shiki-dark:#E1E4E8;">: </span><span style="--shiki-light:#005CC5;--shiki-dark:#79B8FF;">3600</span></span>
<span class="line"></span>
<span class="line"><span style="--shiki-light:#6A737D;--shiki-dark:#6A737D;">    # If a search is aborted, how long should we keep the search in the cache to prevent</span></span>
<span class="line"><span style="--shiki-light:#6A737D;--shiki-dark:#6A737D;">    # the client from resubmitting right away? A sort of &quot;deny list&quot; if you will.</span></span>
<span class="line"><span style="--shiki-light:#22863A;--shiki-dark:#85E89D;">    denyAbortedSearchSec</span><span style="--shiki-light:#24292E;--shiki-dark:#E1E4E8;">: </span><span style="--shiki-light:#005CC5;--shiki-dark:#79B8FF;">600</span></span>
<span class="line"></span>
<span class="line"><span style="--shiki-light:#6A737D;--shiki-dark:#6A737D;">    # After how much time should a running search be aborted?</span></span>
<span class="line"><span style="--shiki-light:#6A737D;--shiki-dark:#6A737D;">    # (larger values put stress on the server, but allow complicated searches to complete)</span></span>
<span class="line"><span style="--shiki-light:#22863A;--shiki-dark:#85E89D;">    maxSearchTimeSec</span><span style="--shiki-light:#24292E;--shiki-dark:#E1E4E8;">: </span><span style="--shiki-light:#005CC5;--shiki-dark:#79B8FF;">300</span></span>
<span class="line"></span>
<span class="line"><span style="--shiki-light:#6A737D;--shiki-dark:#6A737D;">    # How long the client may keep results we give them in their local (browser) cache.</span></span>
<span class="line"><span style="--shiki-light:#6A737D;--shiki-dark:#6A737D;">    # This is used to write HTTP cache headers. Low values mean clients might re-request</span></span>
<span class="line"><span style="--shiki-light:#6A737D;--shiki-dark:#6A737D;">    # the same information, making clients less responsive and consuming more network resources.</span></span>
<span class="line"><span style="--shiki-light:#6A737D;--shiki-dark:#6A737D;">    # Higher values make clients more responsive but could cause problems if the data (or worse,</span></span>
<span class="line"><span style="--shiki-light:#6A737D;--shiki-dark:#6A737D;">    # the protocol) changes after an update. A value of an hour or so seems reasonable.</span></span>
<span class="line"><span style="--shiki-light:#22863A;--shiki-dark:#85E89D;">    clientCacheTimeSec</span><span style="--shiki-light:#24292E;--shiki-dark:#E1E4E8;">: </span><span style="--shiki-light:#005CC5;--shiki-dark:#79B8FF;">3600</span></span>
<span class="line"></span>
<span class="line"><span style="--shiki-light:#6A737D;--shiki-dark:#6A737D;">    # Maximum number of cache entries to keep.</span></span>
<span class="line"><span style="--shiki-light:#6A737D;--shiki-dark:#6A737D;">    # Please note that memory use per cache entry may vary wildly,</span></span>
<span class="line"><span style="--shiki-light:#6A737D;--shiki-dark:#6A737D;">    # so you may prefer to use targetFreeMemMegs to set a &quot;free memory goal&quot;</span></span>
<span class="line"><span style="--shiki-light:#6A737D;--shiki-dark:#6A737D;">    # and/or maxJobAgeSec to set a maximum age for cache entries.</span></span>
<span class="line"><span style="--shiki-light:#22863A;--shiki-dark:#85E89D;">    maxNumberOfJobs</span><span style="--shiki-light:#24292E;--shiki-dark:#E1E4E8;">: </span><span style="--shiki-light:#005CC5;--shiki-dark:#79B8FF;">100</span></span>
<span class="line"><span style="--shiki-light:#24292E;--shiki-dark:#E1E4E8;">    </span></span>
<span class="line"><span style="--shiki-light:#6A737D;--shiki-dark:#6A737D;">    # The cache implementation to use.</span></span>
<span class="line"><span style="--shiki-light:#6A737D;--shiki-dark:#6A737D;">    # (FQDN or class name (in package nl.inl.blacklab.server.search) of</span></span>
<span class="line"><span style="--shiki-light:#6A737D;--shiki-dark:#6A737D;">    # SearchCache subclass to instantiate)</span></span>
<span class="line"><span style="--shiki-light:#6A737D;--shiki-dark:#6A737D;">    # The default is BlsCache. An alternative is ResultsCache, which is more</span></span>
<span class="line"><span style="--shiki-light:#6A737D;--shiki-dark:#6A737D;">    # efficient if you have a large number of small, short-lived indexes.</span></span>
<span class="line"><span style="--shiki-light:#22863A;--shiki-dark:#85E89D;">    implementation</span><span style="--shiki-light:#24292E;--shiki-dark:#E1E4E8;">: </span><span style="--shiki-light:#032F62;--shiki-dark:#9ECBFF;">BlsCache</span></span>
<span class="line"></span>
<span class="line"></span>
<span class="line"></span>
<span class="line"><span style="--shiki-light:#6A737D;--shiki-dark:#6A737D;"># Settings related to tuning server load and client responsiveness</span></span>
<span class="line"><span style="--shiki-light:#22863A;--shiki-dark:#85E89D;">performance</span><span style="--shiki-light:#24292E;--shiki-dark:#E1E4E8;">:</span></span>
<span class="line"></span>
<span class="line"><span style="--shiki-light:#6A737D;--shiki-dark:#6A737D;">    # How many search tasks should be able to run simultaneously</span></span>
<span class="line"><span style="--shiki-light:#6A737D;--shiki-dark:#6A737D;">    # (set this to take advantage of the cores/threads available to the machine;</span></span>
<span class="line"><span style="--shiki-light:#6A737D;--shiki-dark:#6A737D;">    # probably don&#39;t set it any larger, as this won&#39;t help and might hurt)</span></span>
<span class="line"><span style="--shiki-light:#6A737D;--shiki-dark:#6A737D;">    # Note that this is a rough guideline, not an absolute maximum number of threads</span></span>
<span class="line"><span style="--shiki-light:#6A737D;--shiki-dark:#6A737D;">    # that will ever be running. New searches are only started (unqueued) if there&#39;s </span></span>
<span class="line"><span style="--shiki-light:#6A737D;--shiki-dark:#6A737D;">    # fewer than this number of threads running.</span></span>
<span class="line"><span style="--shiki-light:#6A737D;--shiki-dark:#6A737D;">    # (-1 to autodetect)</span></span>
<span class="line"><span style="--shiki-light:#22863A;--shiki-dark:#85E89D;">    maxConcurrentSearches</span><span style="--shiki-light:#24292E;--shiki-dark:#E1E4E8;">: </span><span style="--shiki-light:#005CC5;--shiki-dark:#79B8FF;">6</span></span>
<span class="line"></span>
<span class="line"><span style="--shiki-light:#6A737D;--shiki-dark:#6A737D;">    # How many threads may a single search task use at most?</span></span>
<span class="line"><span style="--shiki-light:#6A737D;--shiki-dark:#6A737D;">    # (lower values will allow more simultaneous searches to run;</span></span>
<span class="line"><span style="--shiki-light:#6A737D;--shiki-dark:#6A737D;">    # higher values improve search performance, but will crowd out other searches.</span></span>
<span class="line"><span style="--shiki-light:#6A737D;--shiki-dark:#6A737D;">    # e.g. if you set this to the same number as maxConcurrentSearches, a single </span></span>
<span class="line"><span style="--shiki-light:#6A737D;--shiki-dark:#6A737D;">    # search may queue all other searches until it&#39;s done)</span></span>
<span class="line"><span style="--shiki-light:#22863A;--shiki-dark:#85E89D;">    maxThreadsPerSearch</span><span style="--shiki-light:#24292E;--shiki-dark:#E1E4E8;">: </span><span style="--shiki-light:#005CC5;--shiki-dark:#79B8FF;">3</span></span>
<span class="line"></span>
<span class="line"><span style="--shiki-light:#6A737D;--shiki-dark:#6A737D;">    # Abhort a count if the client hasn&#39;t asked about it for 30s</span></span>
<span class="line"><span style="--shiki-light:#6A737D;--shiki-dark:#6A737D;">    # (lower values are easier on the server, but might abort a count too soon)</span></span>
<span class="line"><span style="--shiki-light:#22863A;--shiki-dark:#85E89D;">    abandonedCountAbortTimeSec</span><span style="--shiki-light:#24292E;--shiki-dark:#E1E4E8;">: </span><span style="--shiki-light:#005CC5;--shiki-dark:#79B8FF;">30</span></span>
<span class="line"></span>
<span class="line"></span>
<span class="line"><span style="--shiki-light:#6A737D;--shiki-dark:#6A737D;"># Settings for diagnosing problems</span></span>
<span class="line"><span style="--shiki-light:#22863A;--shiki-dark:#85E89D;">debug</span><span style="--shiki-light:#24292E;--shiki-dark:#E1E4E8;">:</span></span>
<span class="line"><span style="--shiki-light:#6A737D;--shiki-dark:#6A737D;">    #  A list of IPs that will run in debug mode.</span></span>
<span class="line"><span style="--shiki-light:#6A737D;--shiki-dark:#6A737D;">    #  In debug mode, ...</span></span>
<span class="line"><span style="--shiki-light:#6A737D;--shiki-dark:#6A737D;">    #  - the /cache-info resource show the contents of the job cache</span></span>
<span class="line"><span style="--shiki-light:#6A737D;--shiki-dark:#6A737D;">    #    (other debug information resources may be added in the future)</span></span>
<span class="line"><span style="--shiki-light:#6A737D;--shiki-dark:#6A737D;">    #  - output is prettyprinted by default (can be overriden with the prettyprint</span></span>
<span class="line"><span style="--shiki-light:#6A737D;--shiki-dark:#6A737D;">    #    GET parameter)</span></span>
<span class="line"><span style="--shiki-light:#22863A;--shiki-dark:#85E89D;">    addresses</span><span style="--shiki-light:#24292E;--shiki-dark:#E1E4E8;">:</span></span>
<span class="line"><span style="--shiki-light:#24292E;--shiki-dark:#E1E4E8;">    - </span><span style="--shiki-light:#005CC5;--shiki-dark:#79B8FF;">127.0.0.1</span><span style="--shiki-light:#6A737D;--shiki-dark:#6A737D;">       #  IPv4 localhost</span></span>
<span class="line"><span style="--shiki-light:#24292E;--shiki-dark:#E1E4E8;">    - </span><span style="--shiki-light:#032F62;--shiki-dark:#9ECBFF;">0:0:0:0:0:0:0:1</span><span style="--shiki-light:#6A737D;--shiki-dark:#6A737D;"> #  IPv6 localhost</span></span>
<span class="line"></span>
<span class="line"></span>
<span class="line"><span style="--shiki-light:#6A737D;--shiki-dark:#6A737D;"># How to determine current user</span></span>
<span class="line"><span style="--shiki-light:#6A737D;--shiki-dark:#6A737D;"># (you only need this if you want per-user private indices or authorization)</span></span>
<span class="line"><span style="--shiki-light:#22863A;--shiki-dark:#85E89D;">authentication</span><span style="--shiki-light:#24292E;--shiki-dark:#E1E4E8;">:</span></span>
<span class="line"><span style="--shiki-light:#22863A;--shiki-dark:#85E89D;">    system</span><span style="--shiki-light:#24292E;--shiki-dark:#E1E4E8;">:</span></span>
<span class="line"><span style="--shiki-light:#22863A;--shiki-dark:#85E89D;">        class</span><span style="--shiki-light:#24292E;--shiki-dark:#E1E4E8;">: </span><span style="--shiki-light:#032F62;--shiki-dark:#9ECBFF;">AuthDebugFixed</span></span>
<span class="line"><span style="--shiki-light:#22863A;--shiki-dark:#85E89D;">        userId</span><span style="--shiki-light:#24292E;--shiki-dark:#E1E4E8;">: </span><span style="--shiki-light:#032F62;--shiki-dark:#9ECBFF;">jan.niestadt@ivdnt.org</span></span>
<span class="line"></span>
<span class="line"><span style="--shiki-light:#6A737D;--shiki-dark:#6A737D;">    #  Clients from these IPs may choose their own user id and send it along in a GET parameter userid.</span></span>
<span class="line"><span style="--shiki-light:#6A737D;--shiki-dark:#6A737D;">    #  This setting exists for web applications that contact the webservice (partly) through the</span></span>
<span class="line"><span style="--shiki-light:#6A737D;--shiki-dark:#6A737D;">    #  server component. They would get the same session id for each user, making them likely </span></span>
<span class="line"><span style="--shiki-light:#6A737D;--shiki-dark:#6A737D;">    #  to hit the maxRunningJobsPerUser setting. Instead, they should assign session IDs for each of</span></span>
<span class="line"><span style="--shiki-light:#6A737D;--shiki-dark:#6A737D;">    #  their clients and send them along with any request to the webservice.</span></span>
<span class="line"><span style="--shiki-light:#22863A;--shiki-dark:#85E89D;">    overrideUserIdIps</span><span style="--shiki-light:#24292E;--shiki-dark:#E1E4E8;">:</span></span>
<span class="line"><span style="--shiki-light:#24292E;--shiki-dark:#E1E4E8;">    - </span><span style="--shiki-light:#005CC5;--shiki-dark:#79B8FF;">127.0.0.1</span><span style="--shiki-light:#6A737D;--shiki-dark:#6A737D;">       #  IPv4 localhost</span></span>
<span class="line"><span style="--shiki-light:#24292E;--shiki-dark:#E1E4E8;">    - </span><span style="--shiki-light:#032F62;--shiki-dark:#9ECBFF;">0:0:0:0:0:0:0:1</span><span style="--shiki-light:#6A737D;--shiki-dark:#6A737D;"> #  IPv6 localhost</span></span>
<span class="line"><span style="--shiki-light:#24292E;--shiki-dark:#E1E4E8;">    </span></span>
<span class="line"><span style="--shiki-light:#6A737D;--shiki-dark:#6A737D;">    # This is an insecure way of authenticating to BlackLab Server by sending</span></span>
<span class="line"><span style="--shiki-light:#6A737D;--shiki-dark:#6A737D;">    # two HTTP headers. It is only intended for testing purposes.</span></span>
<span class="line"><span style="--shiki-light:#6A737D;--shiki-dark:#6A737D;">    # </span></span>
<span class="line"><span style="--shiki-light:#6A737D;--shiki-dark:#6A737D;">    # Choose a &#39;secret&#39; password here. Then send your requests to BlackLab Server </span></span>
<span class="line"><span style="--shiki-light:#6A737D;--shiki-dark:#6A737D;">    # with the extra HTTP headers X-BlackLabAccessToken (the &#39;secret&#39; password) and</span></span>
<span class="line"><span style="--shiki-light:#6A737D;--shiki-dark:#6A737D;">    # X-BlackLabUserId (the user you wish to authenticate as).</span></span>
<span class="line"><span style="--shiki-light:#6A737D;--shiki-dark:#6A737D;">    # </span></span>
<span class="line"><span style="--shiki-light:#6A737D;--shiki-dark:#6A737D;">    # Needless to say this method is insecure because it allows full access to</span></span>
<span class="line"><span style="--shiki-light:#6A737D;--shiki-dark:#6A737D;">    # all users&#39; corpora, and the access token could potentially leak to an</span></span>
<span class="line"><span style="--shiki-light:#6A737D;--shiki-dark:#6A737D;">    # attacker.</span></span>
<span class="line"><span style="--shiki-light:#6A737D;--shiki-dark:#6A737D;">    #</span></span>
<span class="line"><span style="--shiki-light:#6A737D;--shiki-dark:#6A737D;">    # DO NOT USE EXCEPT FOR TESTING</span></span>
<span class="line"><span style="--shiki-light:#6A737D;--shiki-dark:#6A737D;">    #debugHttpHeaderAuthToken: secret</span></span>
<span class="line"></span>
<span class="line"></span>
<span class="line"><span style="--shiki-light:#6A737D;--shiki-dark:#6A737D;"># ---------------------------------------------------------------------------</span></span>
<span class="line"><span style="--shiki-light:#6A737D;--shiki-dark:#6A737D;"># What follows are general BlackLab settings that can apply to different </span></span>
<span class="line"><span style="--shiki-light:#6A737D;--shiki-dark:#6A737D;"># BlackLab applications, not just to BlackLab Server.</span></span>
<span class="line"><span style="--shiki-light:#6A737D;--shiki-dark:#6A737D;"># (These can go in a separate file named blacklab.yaml, which is read by all</span></span>
<span class="line"><span style="--shiki-light:#6A737D;--shiki-dark:#6A737D;"># BlackLab applications. Make sure to include configVersion as well)</span></span>
<span class="line"><span style="--shiki-light:#6A737D;--shiki-dark:#6A737D;"># (you generally don&#39;t need to change these if you&#39;re running BlackLab Server,</span></span>
<span class="line"><span style="--shiki-light:#6A737D;--shiki-dark:#6A737D;"># unless you&#39;re using some of the advanced features such as indexing/plugins,</span></span>
<span class="line"><span style="--shiki-light:#6A737D;--shiki-dark:#6A737D;"># or you&#39;re trying to diagnose problems)</span></span>
<span class="line"></span>
<span class="line"></span>
<span class="line"><span style="--shiki-light:#6A737D;--shiki-dark:#6A737D;"># Settings related to logging</span></span>
<span class="line"><span style="--shiki-light:#22863A;--shiki-dark:#85E89D;">log</span><span style="--shiki-light:#24292E;--shiki-dark:#E1E4E8;">:</span></span>
<span class="line"></span>
<span class="line"><span style="--shiki-light:#6A737D;--shiki-dark:#6A737D;">    # What subjects to log messages for</span></span>
<span class="line"><span style="--shiki-light:#22863A;--shiki-dark:#85E89D;">    trace</span><span style="--shiki-light:#24292E;--shiki-dark:#E1E4E8;">:</span></span>
<span class="line"><span style="--shiki-light:#6A737D;--shiki-dark:#6A737D;">        # BL trace settings</span></span>
<span class="line"><span style="--shiki-light:#22863A;--shiki-dark:#85E89D;">        indexOpening</span><span style="--shiki-light:#24292E;--shiki-dark:#E1E4E8;">: </span><span style="--shiki-light:#005CC5;--shiki-dark:#79B8FF;">false</span></span>
<span class="line"><span style="--shiki-light:#22863A;--shiki-dark:#85E89D;">        optimization</span><span style="--shiki-light:#24292E;--shiki-dark:#E1E4E8;">: </span><span style="--shiki-light:#005CC5;--shiki-dark:#79B8FF;">true</span></span>
<span class="line"><span style="--shiki-light:#22863A;--shiki-dark:#85E89D;">        queryExecution</span><span style="--shiki-light:#24292E;--shiki-dark:#E1E4E8;">: </span><span style="--shiki-light:#005CC5;--shiki-dark:#79B8FF;">true</span></span>
<span class="line"></span>
<span class="line"><span style="--shiki-light:#6A737D;--shiki-dark:#6A737D;">        # BLS trace settings</span></span>
<span class="line"><span style="--shiki-light:#22863A;--shiki-dark:#85E89D;">        cache</span><span style="--shiki-light:#24292E;--shiki-dark:#E1E4E8;">: </span><span style="--shiki-light:#005CC5;--shiki-dark:#79B8FF;">true</span></span>
<span class="line"></span>
<span class="line"></span>
<span class="line"><span style="--shiki-light:#6A737D;--shiki-dark:#6A737D;"># Defaults for searching</span></span>
<span class="line"><span style="--shiki-light:#6A737D;--shiki-dark:#6A737D;"># NOTE: these are BlackLab defaults, not the defaults for the BlackLab Server parameters;</span></span>
<span class="line"><span style="--shiki-light:#6A737D;--shiki-dark:#6A737D;"># see the parameters section for those.</span></span>
<span class="line"><span style="--shiki-light:#22863A;--shiki-dark:#85E89D;">search</span><span style="--shiki-light:#24292E;--shiki-dark:#E1E4E8;">:</span></span>
<span class="line"></span>
<span class="line"><span style="--shiki-light:#6A737D;--shiki-dark:#6A737D;">    # Collator to use for sorting, grouping, etc.</span></span>
<span class="line"><span style="--shiki-light:#22863A;--shiki-dark:#85E89D;">    collator</span><span style="--shiki-light:#24292E;--shiki-dark:#E1E4E8;">:</span></span>
<span class="line"><span style="--shiki-light:#22863A;--shiki-dark:#85E89D;">        language</span><span style="--shiki-light:#24292E;--shiki-dark:#E1E4E8;">: </span><span style="--shiki-light:#032F62;--shiki-dark:#9ECBFF;">nl</span><span style="--shiki-light:#6A737D;--shiki-dark:#6A737D;">   # required</span></span>
<span class="line"><span style="--shiki-light:#22863A;--shiki-dark:#85E89D;">        country</span><span style="--shiki-light:#24292E;--shiki-dark:#E1E4E8;">: </span><span style="--shiki-light:#032F62;--shiki-dark:#9ECBFF;">NL</span><span style="--shiki-light:#6A737D;--shiki-dark:#6A737D;">    # optional</span></span>
<span class="line"><span style="--shiki-light:#6A737D;--shiki-dark:#6A737D;">        #variant: x     # optional</span></span>
<span class="line"></span>
<span class="line"><span style="--shiki-light:#6A737D;--shiki-dark:#6A737D;">    # Default number of words around hit.</span></span>
<span class="line"><span style="--shiki-light:#22863A;--shiki-dark:#85E89D;">    contextSize</span><span style="--shiki-light:#24292E;--shiki-dark:#E1E4E8;">: </span><span style="--shiki-light:#005CC5;--shiki-dark:#79B8FF;">5</span></span>
<span class="line"></span>
<span class="line"><span style="--shiki-light:#6A737D;--shiki-dark:#6A737D;">    # The default maximum number of hits to retrieve (and use for sorting, grouping, etc.).</span></span>
<span class="line"><span style="--shiki-light:#6A737D;--shiki-dark:#6A737D;">    # -1 means no limit, but be careful, this may stress your server.</span></span>
<span class="line"><span style="--shiki-light:#22863A;--shiki-dark:#85E89D;">    maxHitsToRetrieve</span><span style="--shiki-light:#24292E;--shiki-dark:#E1E4E8;">: </span><span style="--shiki-light:#005CC5;--shiki-dark:#79B8FF;">1000000</span></span>
<span class="line"><span style="--shiki-light:#24292E;--shiki-dark:#E1E4E8;">    </span></span>
<span class="line"><span style="--shiki-light:#6A737D;--shiki-dark:#6A737D;">    # The default maximum number of hits to count.</span></span>
<span class="line"><span style="--shiki-light:#6A737D;--shiki-dark:#6A737D;">    # -1 means no limit, but be careful, this may stress your server.</span></span>
<span class="line"><span style="--shiki-light:#22863A;--shiki-dark:#85E89D;">    maxHitsToCount</span><span style="--shiki-light:#24292E;--shiki-dark:#E1E4E8;">: </span><span style="--shiki-light:#005CC5;--shiki-dark:#79B8FF;">-1</span></span>
<span class="line"></span>
<span class="line"><span style="--shiki-light:#6A737D;--shiki-dark:#6A737D;">    # How eagerly to apply &quot;forward index matching&quot; to certain queries</span></span>
<span class="line"><span style="--shiki-light:#6A737D;--shiki-dark:#6A737D;">    # [advanced technical setting; don&#39;t worry about this unless you want to experiment]</span></span>
<span class="line"><span style="--shiki-light:#6A737D;--shiki-dark:#6A737D;">    # [if you want to disable forward index matching, which may be beneficial</span></span>
<span class="line"><span style="--shiki-light:#6A737D;--shiki-dark:#6A737D;">    #  if you indexes are small and your query volume is high, set this to 0]</span></span>
<span class="line"><span style="--shiki-light:#22863A;--shiki-dark:#85E89D;">    fiMatchFactor</span><span style="--shiki-light:#24292E;--shiki-dark:#E1E4E8;">: </span><span style="--shiki-light:#005CC5;--shiki-dark:#79B8FF;">900</span></span>
<span class="line"><span style="--shiki-light:#24292E;--shiki-dark:#E1E4E8;">    </span></span>
<span class="line"><span style="--shiki-light:#6A737D;--shiki-dark:#6A737D;">    # Enable result sets larger than 2^31?</span></span>
<span class="line"><span style="--shiki-light:#6A737D;--shiki-dark:#6A737D;">    # If you don&#39;t need this, you can disable it for slightly better performance.</span></span>
<span class="line"><span style="--shiki-light:#6A737D;--shiki-dark:#6A737D;">    # (defaults to true)</span></span>
<span class="line"><span style="--shiki-light:#22863A;--shiki-dark:#85E89D;">    enableHugeResultSets</span><span style="--shiki-light:#24292E;--shiki-dark:#E1E4E8;">: </span><span style="--shiki-light:#005CC5;--shiki-dark:#79B8FF;">true</span></span>
<span class="line"></span>
<span class="line"></span>
<span class="line"><span style="--shiki-light:#6A737D;--shiki-dark:#6A737D;"># Options for indexing operations, if enabled</span></span>
<span class="line"><span style="--shiki-light:#6A737D;--shiki-dark:#6A737D;"># (right now, in BLS, they&#39;re only enabled for logged-in users in</span></span>
<span class="line"><span style="--shiki-light:#6A737D;--shiki-dark:#6A737D;">#  their own private area)</span></span>
<span class="line"><span style="--shiki-light:#22863A;--shiki-dark:#85E89D;">indexing</span><span style="--shiki-light:#24292E;--shiki-dark:#E1E4E8;">:</span></span>
<span class="line"></span>
<span class="line"><span style="--shiki-light:#6A737D;--shiki-dark:#6A737D;">    # (By default, http downloads of e.g. metadata are not allowed)</span></span>
<span class="line"><span style="--shiki-light:#22863A;--shiki-dark:#85E89D;">    downloadAllowed</span><span style="--shiki-light:#24292E;--shiki-dark:#E1E4E8;">: </span><span style="--shiki-light:#005CC5;--shiki-dark:#79B8FF;">false</span></span>
<span class="line"></span>
<span class="line"><span style="--shiki-light:#6A737D;--shiki-dark:#6A737D;">    # Where to store cached files</span></span>
<span class="line"><span style="--shiki-light:#22863A;--shiki-dark:#85E89D;">    downloadCacheDir</span><span style="--shiki-light:#24292E;--shiki-dark:#E1E4E8;">: </span><span style="--shiki-light:#032F62;--shiki-dark:#9ECBFF;">/tmp/bls-download-cache</span></span>
<span class="line"></span>
<span class="line"><span style="--shiki-light:#6A737D;--shiki-dark:#6A737D;">    # Max. size of entire cache in MB</span></span>
<span class="line"><span style="--shiki-light:#22863A;--shiki-dark:#85E89D;">    downloadCacheSizeMegs</span><span style="--shiki-light:#24292E;--shiki-dark:#E1E4E8;">: </span><span style="--shiki-light:#005CC5;--shiki-dark:#79B8FF;">100</span></span>
<span class="line"></span>
<span class="line"><span style="--shiki-light:#6A737D;--shiki-dark:#6A737D;">    # Max. size of single download in MB</span></span>
<span class="line"><span style="--shiki-light:#22863A;--shiki-dark:#85E89D;">    downloadCacheMaxFileSizeMegs</span><span style="--shiki-light:#24292E;--shiki-dark:#E1E4E8;">: </span><span style="--shiki-light:#005CC5;--shiki-dark:#79B8FF;">1</span></span>
<span class="line"></span>
<span class="line"><span style="--shiki-light:#6A737D;--shiki-dark:#6A737D;">    # Max. number of zip files to keep opened</span></span>
<span class="line"><span style="--shiki-light:#22863A;--shiki-dark:#85E89D;">    zipFilesMaxOpen</span><span style="--shiki-light:#24292E;--shiki-dark:#E1E4E8;">: </span><span style="--shiki-light:#005CC5;--shiki-dark:#79B8FF;">10</span></span>
<span class="line"><span style="--shiki-light:#24292E;--shiki-dark:#E1E4E8;">    </span></span>
<span class="line"><span style="--shiki-light:#6A737D;--shiki-dark:#6A737D;">    # Number of threads to use for indexing operations</span></span>
<span class="line"><span style="--shiki-light:#6A737D;--shiki-dark:#6A737D;">    # (more threads is faster, but uses more memory)</span></span>
<span class="line"><span style="--shiki-light:#22863A;--shiki-dark:#85E89D;">    numberOfThreads</span><span style="--shiki-light:#24292E;--shiki-dark:#E1E4E8;">: </span><span style="--shiki-light:#005CC5;--shiki-dark:#79B8FF;">2</span></span>
<span class="line"><span style="--shiki-light:#24292E;--shiki-dark:#E1E4E8;">    </span></span>
<span class="line"><span style="--shiki-light:#6A737D;--shiki-dark:#6A737D;">    # Max. number of values to store per metadata field</span></span>
<span class="line"><span style="--shiki-light:#22863A;--shiki-dark:#85E89D;">    maxMetadataValuesToStore</span><span style="--shiki-light:#24292E;--shiki-dark:#E1E4E8;">: </span><span style="--shiki-light:#005CC5;--shiki-dark:#79B8FF;">100</span></span>
<span class="line"><span style="--shiki-light:#24292E;--shiki-dark:#E1E4E8;">    </span></span>
<span class="line"><span style="--shiki-light:#6A737D;--shiki-dark:#6A737D;">    # Max. number of indices per user</span></span>
<span class="line"><span style="--shiki-light:#6A737D;--shiki-dark:#6A737D;">    # (only relevant if you&#39;ve configured private indices and authorization)</span></span>
<span class="line"><span style="--shiki-light:#22863A;--shiki-dark:#85E89D;">    maxNumberOfIndicesPerUser</span><span style="--shiki-light:#24292E;--shiki-dark:#E1E4E8;">: </span><span style="--shiki-light:#005CC5;--shiki-dark:#79B8FF;">10</span></span>
<span class="line"></span>
<span class="line"><span style="--shiki-light:#6A737D;--shiki-dark:#6A737D;">    # Should inline tags and relations be indexed case- and accent-sensitive?</span></span>
<span class="line"><span style="--shiki-light:#6A737D;--shiki-dark:#6A737D;">    # This used to be the default, but we&#39;ve switched over to case-insensitive</span></span>
<span class="line"><span style="--shiki-light:#6A737D;--shiki-dark:#6A737D;">    # indexing by default.</span></span>
<span class="line"><span style="--shiki-light:#22863A;--shiki-dark:#85E89D;">    relationsSensitive</span><span style="--shiki-light:#24292E;--shiki-dark:#E1E4E8;">: </span><span style="--shiki-light:#005CC5;--shiki-dark:#79B8FF;">false</span></span>
<span class="line"></span>
<span class="line"></span>
<span class="line"><span style="--shiki-light:#6A737D;--shiki-dark:#6A737D;"># Plugin options. Plugins allow you to automatically convert files (e.g. .html, .docx) or </span></span>
<span class="line"><span style="--shiki-light:#6A737D;--shiki-dark:#6A737D;"># apply linguistic tagging before indexing via BLS (experimental functionality).</span></span>
<span class="line"><span style="--shiki-light:#22863A;--shiki-dark:#85E89D;">plugins</span><span style="--shiki-light:#24292E;--shiki-dark:#E1E4E8;">:</span></span>
<span class="line"></span>
<span class="line"><span style="--shiki-light:#6A737D;--shiki-dark:#6A737D;">    # Should we initialize plugins when they are first used?</span></span>
<span class="line"><span style="--shiki-light:#6A737D;--shiki-dark:#6A737D;">    # (plugin initialization can take a while; during development, delayed initialization is</span></span>
<span class="line"><span style="--shiki-light:#6A737D;--shiki-dark:#6A737D;">    # often convenient, but during production, you usually want to initialize right away)</span></span>
<span class="line"><span style="--shiki-light:#22863A;--shiki-dark:#85E89D;">    delayInitialization</span><span style="--shiki-light:#24292E;--shiki-dark:#E1E4E8;">: </span><span style="--shiki-light:#005CC5;--shiki-dark:#79B8FF;">false</span></span>
<span class="line"></span>
<span class="line"><span style="--shiki-light:#6A737D;--shiki-dark:#6A737D;">    # # Individual plugin configurations</span></span>
<span class="line"><span style="--shiki-light:#22863A;--shiki-dark:#85E89D;">    plugins</span><span style="--shiki-light:#24292E;--shiki-dark:#E1E4E8;">:</span></span>
<span class="line"></span>
<span class="line"><span style="--shiki-light:#6A737D;--shiki-dark:#6A737D;">        # Conversion plugin</span></span>
<span class="line"><span style="--shiki-light:#22863A;--shiki-dark:#85E89D;">        OpenConvert</span><span style="--shiki-light:#24292E;--shiki-dark:#E1E4E8;">:</span></span>
<span class="line"><span style="--shiki-light:#22863A;--shiki-dark:#85E89D;">            jarPath</span><span style="--shiki-light:#24292E;--shiki-dark:#E1E4E8;">: </span><span style="--shiki-light:#032F62;--shiki-dark:#9ECBFF;">&quot;/home/jan/projects/openconvert_en_tagger/OpenConvertMaven/target/OpenConvert-0.2.0.jar&quot;</span></span>
<span class="line"></span>
<span class="line"><span style="--shiki-light:#6A737D;--shiki-dark:#6A737D;">        # Tagging plugin</span></span>
<span class="line"><span style="--shiki-light:#22863A;--shiki-dark:#85E89D;">        DutchTagger</span><span style="--shiki-light:#24292E;--shiki-dark:#E1E4E8;">:</span></span>
<span class="line"><span style="--shiki-light:#22863A;--shiki-dark:#85E89D;">            jarPath</span><span style="--shiki-light:#24292E;--shiki-dark:#E1E4E8;">: </span><span style="--shiki-light:#032F62;--shiki-dark:#9ECBFF;">&quot;/home/jan/projects/openconvert_en_tagger/DutchTagger/target/DutchTagger-0.2.0.jar&quot;</span></span>
<span class="line"><span style="--shiki-light:#22863A;--shiki-dark:#85E89D;">            vectorFile</span><span style="--shiki-light:#24292E;--shiki-dark:#E1E4E8;">:  </span><span style="--shiki-light:#032F62;--shiki-dark:#9ECBFF;">&quot;/home/jan/projects/openconvert_en_tagger/tagger-data/sonar.vectors.bin&quot;</span></span>
<span class="line"><span style="--shiki-light:#22863A;--shiki-dark:#85E89D;">            modelFile</span><span style="--shiki-light:#24292E;--shiki-dark:#E1E4E8;">:   </span><span style="--shiki-light:#032F62;--shiki-dark:#9ECBFF;">&quot;/home/jan/projects/openconvert_en_tagger/tagger-data/withMoreVectorrs&quot;</span></span>
<span class="line"><span style="--shiki-light:#22863A;--shiki-dark:#85E89D;">            lexiconFile</span><span style="--shiki-light:#24292E;--shiki-dark:#E1E4E8;">: </span><span style="--shiki-light:#032F62;--shiki-dark:#9ECBFF;">&quot;/home/jan/projects/openconvert_en_tagger/tagger-data/spelling.tab&quot;</span></span></code></pre></div>`,64)]))}const g=a(l,[["render",t]]);export{d as __pageData,g as default};
