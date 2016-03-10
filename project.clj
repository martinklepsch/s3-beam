(defproject org.martinklepsch/s3-beam "0.5.2-SNAPSHOT"
  :author "Martin Klepsch <http://www.martinklepsch.org>"
  :description "CORS Upload to S3 via Clojure(script)"
  :url "http://github.com/martinklepsch/s3-beam"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}

  :source-paths ["src/clj" "src/cljs"]

  :dependencies [[org.clojure/clojure "1.6.0"]
                 [org.clojure/clojurescript "0.0-2371" :scope "provided"]
                 ;[com.google.javascript/closure-compiler "v20140814"]
                 [org.clojure/data.json "0.2.5"]
                 [org.clojure/core.async "0.1.346.0-17112a-alpha"]
                 [ring/ring-codec "1.0.0" :scope "provided"]]

  :scm {:name "git"
         :url "https://github.com/martinklepsch/s3-beam"})
