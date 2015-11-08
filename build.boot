(set-env!
  :source-paths   #{"src/clj" "src/cljs"}
  :dependencies  '[[adzerk/bootlaces   "0.1.11" :scope "test"]
                   [org.clojure/clojure "1.6.0" :scope "provided"]
                   [org.clojure/clojurescript "0.0-2371" :scope "provided"]
                   [org.clojure/data.json "0.2.5"]
                   [org.clojure/core.async "0.1.346.0-17112a-alpha"]])

(require '[adzerk.bootlaces :refer :all])

(def +version+ "0.4.0")
(bootlaces! +version+)
(set-env! :resource-paths #{"src/clj" "src/cljs"})

(task-options!
 pom  {:project     'org.martinklepsch/s3-beam
       :version     +version+
       :description "CORS Upload to S3 via Clojure(script)"
       :url         "http://github.com/martinklepsch/s3-beam"
       :scm         {:url "https://github.com/martinklepsch/s3-beam"}
       :license     {"EPL" "http://www.eclipse.org/legal/epl-v10.html"}})
