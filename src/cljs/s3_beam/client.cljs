(ns s3-beam.client
  (:require-macros [cljs.core.async.macros :refer [go]])
  (:require [cljs.reader :as reader]
            [cljs.core.async :as async :refer [chan put! close! pipeline-async]]
            [goog.dom :as gdom]
            [goog.net.XhrIo :as xhr]))

(defn file->map [f]
  {:name (.-name f)
   :type (.-type f)
   :size (.-size f)})

(defn signing-url [fname fmime]
 (str "/sign?file-name=" fname "&mime-type=" fmime))

(defn sign-file [file ch]
  (let [fmap    (file->map file)
        edn-ize #(reader/read-string (.getResponseText (.-target %)))]
    (xhr/send (signing-url (:name fmap) (:type fmap))
           (fn [res]
             (put! ch {:f file :signature (edn-ize res)})
             (close! ch)))))

(defn formdata-from-map [m]
  (let [fd (new js/FormData)]
    (doseq [[k v] m]
      (.append fd (name k) v))
    fd))

(defn upload-file [upload-info ch]
  (let [sig-fields [:key :Content-Type :success_action_status :policy :AWSAccessKeyId :signature :acl]
        signature  (select-keys (:signature upload-info) sig-fields)
        form-data  (formdata-from-map (merge signature {:file (:f upload-info)}))]
    (xhr/send
     (:action (:signature upload-info))
     (fn [res]
       (let [loc (first (.getElementsByTagName (.getResponseXml (.-target res)) "Location"))]
         (put! ch (gdom/getTextContent loc))
         (close! ch)))
     "POST"
     form-data)))

(defn s3-pipe [report-chan]
  (let [to-process (chan)
        signed     (chan)]
    (pipeline-async 3 signed sign-file to-process)
    (pipeline-async 3 report-chan upload-file signed)
    to-process))
