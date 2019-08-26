(set-env!
  :resource-paths #{"src/clj" "src/cljs"}
  :dependencies  '[[adzerk/bootlaces   "0.1.13" :scope "test"]
                   [org.clojure/clojure "1.10.1" :scope "provided"]
                   [org.clojure/clojurescript "1.10.520" :scope "provided"]
                   ;; [com.google.javascript/closure-compiler "v20140814"]
                   [org.clojure/data.json "0.2.6"]
                   [org.clojure/core.async "0.4.500"]
                   [ring/ring-codec "1.1.2" :scope "provided"]])

(require '[adzerk.bootlaces :refer :all])

(def +version+ "0.6.0-alpha4")
(bootlaces! +version+ :dont-modify-paths? true)

(task-options!
 pom  {:project     'org.martinklepsch/s3-beam
       :version     +version+
       :description "CORS Upload to S3 via Clojure(script)"
       :url         "http://github.com/martinklepsch/s3-beam"
       :scm         {:url "https://github.com/martinklepsch/s3-beam"}
       :license     {"EPL" "http://www.eclipse.org/legal/epl-v10.html"}})
