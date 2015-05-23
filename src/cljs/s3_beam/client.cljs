(ns s3-beam.client
  (:import (goog Uri))
  (:require-macros [cljs.core.async.macros :refer [go]])
  (:require [cljs.reader :as reader]
            [cljs.core.async :as async :refer [chan put! close! pipeline-async]]
            [goog.dom :as gdom]
            [goog.net.XhrIo :as xhr]))

(defn file->map [f]
  {:name (.-name f)
   :type (.-type f)
   :size (.-size f)})

(defn signing-url [server-url fname fmime]
  {:pre [(string? server-url) (string? fname) (string? fmime)]}
  (.toString (doto (Uri. server-url)
               (.setParameterValue "file-name" fname)
               (.setParameterValue "mime-type" fmime))))

(defn sign-file [server-url file ch]
  (let [fmap    (file->map file)
        edn-ize #(reader/read-string (.getResponseText (.-target %)))]
    (xhr/send (signing-url server-url (:name fmap) (:type fmap))
           (fn [res]
             (put! ch {:f file :signature (edn-ize res)})
             (close! ch)))))

(defn formdata-from-map [m]
  (let [fd (new js/FormData)]
    (doseq [[k v] m]
      (.append fd (name k) v))
    fd))

(defn xml->clj
  "Transforms a shallow XML document into an idiomatic clj map"
  [xml]
  (let [kv (->> (array-seq (.. xml -firstChild -childNodes))
                (map (fn [n] [(.toLowerCase (.-nodeName n))
                             (gdom/getTextContent n)])))]
    (zipmap (map (comp keyword first) kv) (map second kv))))

(defn upload-file
  "Uploads the file given by upload-info and puts the file and
  upload response in the ch. Contains the following keys:
    :file - the File Object that was uploaded
    :response - S3 file data
      :key
      :location
      :bucket
      :etag"
  [upload-info ch]
  (let [sig-fields [:key :Content-Type :success_action_status :policy :AWSAccessKeyId :signature :acl]
        signature  (select-keys (:signature upload-info) sig-fields)
        form-data  (formdata-from-map (merge signature {:file (:f upload-info)}))]
    (xhr/send
     (:action (:signature upload-info))
     (fn [res]
       (put! ch {:file (:f upload-info)
                 :response (xml->clj (.. res -target getResponseXml))})
       (close! ch))
     "POST"
     form-data)))

(defn s3-pipe
  "Takes a channel where completed uploads will be reported and 
  returns a channel where you can put File objects that should get uploaded.
  May also take an optiosn map with:
    :server-url - the sign server url, defaults to \"/sign\""
  ([report-chan] (s3-pipe report-chan {:server-url "/sign"}))
  ([report-chan opts]
   (let [to-process (chan)
         signed     (chan)]
     (pipeline-async 3 signed (partial sign-file (:server-url opts)) to-process)
     (pipeline-async 3 report-chan upload-file signed)
     to-process)))
