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

(defn signing-url [server-url params]
  {:pre [(string? server-url) (map? params)]}
  (let [uri (Uri. server-url)]
    (doseq [[k v] params]
      (.setParameterValue uri (name k) v))
    (.toString uri)))

(defn sign-file [opts file ch]
  (let [server-url (:server-url opts)
        fmap    (file->map file)
        params (merge fmap ((:params opts) file))
        edn-ize #(reader/read-string (.getResponseText (.-target %)))]
    (xhr/send (signing-url server-url params)
           (fn [res]
             (put! ch {:f file :signature (edn-ize res)})
             (close! ch))
              "GET"
              nil
              (clj->js ((:headers opts) file)))))

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
       (let [loc (aget (.getElementsByTagName (.getResponseXml (.-target res)) "Location") 0)]
         (put! ch (gdom/getTextContent loc))
         (close! ch)))
     "POST"
     form-data)))

(defn s3-pipe
  "Takes a channel where completed uploads will be reported and 
  returns a channel where you can put File objects that should get uploaded.
  May also take an options map with:
    :server-url - the sign server url, defaults to \"/sign\"
    :headers - A function that returns a map with headers to send in the GET request
    :params - A function that returns a map with parameters to attach to the signing URL."
  ([report-chan] (s3-pipe report-chan {:server-url "/sign"}))
  ([report-chan opts]
   (let [to-process (chan)
         signed     (chan)]
     (pipeline-async 3 signed (partial sign-file opts) to-process)
     (pipeline-async 3 report-chan upload-file signed)
     to-process)))
