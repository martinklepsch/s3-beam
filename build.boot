(set-env!
  :resource-paths #{"src/clj" "src/cljs"}
  :dependencies  '[[adzerk/bootlaces   "0.1.13" :scope "test"]
                   [org.clojure/clojure "1.6.0" :scope "provided"]
                   [org.clojure/clojurescript "1.7.170" :scope "provided"]
                   ;; [com.google.javascript/closure-compiler "v20140814"]
                   [org.clojure/data.json "0.2.5"]
                   [org.clojure/core.async "0.1.346.0-17112a-alpha"]
                   [ring/ring-codec "1.0.0" :scope "provided"]])

(require '[adzerk.bootlaces :refer :all])

(def +version+ "0.6.0-alpha3")
(bootlaces! +version+ :dont-modify-paths? true)

(task-options!
 pom  {:project     'org.martinklepsch/s3-beam
       :version     +version+
       :description "CORS Upload to S3 via Clojure(script)"
       :url         "http://github.com/martinklepsch/s3-beam"
       :scm         {:url "https://github.com/martinklepsch/s3-beam"}
       :license     {"EPL" "http://www.eclipse.org/legal/epl-v10.html"}})
