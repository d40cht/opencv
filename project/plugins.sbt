//https://raw.github.com/d40cht/sbt-cpp/master/releases/org/seacourt/build/sbt-cpp_2.9.2_0.12/0.0.7/ivy-0.0.7.xml

//resolvers +=  Resolver.url("Navetas Repo", url("http://github.lan.ise-oxford.com/Navetas/navetasivyrepo/raw/master/releases"))( Patterns("[organisation]/[module]_[scalaVersion]_[sbtVersion]/[revision]/[artifact]-[revision].[ext]?access_token=d876983389a69dafd17234f810daaacd215cfa4b") )

resolvers +=  Resolver.url("Sbt-cpp", url("https://raw.github.com/d40cht/sbt-cpp/master/releases"))( Patterns("[organisation]/[module]_[scalaVersion]_[sbtVersion]/[revision]/[artifact]-[revision].[ext]") )

addSbtPlugin("org.seacourt.build" %% "sbt-cpp" % "0.0.7")

