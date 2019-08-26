(defproject org.martinklepsch/s3-beam "0.6.0-alpha5"
  :author "Martin Klepsch <http://www.martinklepsch.org>"
  :description "CORS Upload to S3 via Clojure(script)"
  :url "http://github.com/martinklepsch/s3-beam"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}

  :source-paths ["src/clj" "src/cljs"]

  :dependencies [[org.clojure/clojure "1.10.1" :scope "provided"]
                 [org.clojure/clojurescript "1.10.520" :scope "provided"]
                 [org.clojure/data.json "0.2.6"]
                 [org.clojure/core.async "0.4.500"]
                 [ring/ring-codec "1.1.2" :scope "provided"]]



  :scm {:name "git"
         :url "https://github.com/martinklepsch/s3-beam"})
