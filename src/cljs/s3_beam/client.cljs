(ns s3-beam.client
  (:require-macros [cljs.core.async.macros :refer [go]])
  (:require [cljs.reader :as reader]
            [cljs.core.async :as async :refer [chan put! close! pipeline-async]]
            [goog.dom :as gdom]
            [goog.events :as events])
  (:import [goog Uri]
           [goog.net XhrIo EventType ErrorCode]
           [goog.events EventType]))

(defn file->map [f]
  {:name (.-name f)
   :type (.-type f)
   :size (.-size f)})

(defn signing-url [server-url fname fmime]
  {:pre [(string? server-url) (string? fname) (string? fmime)]}
  (.toString (doto (Uri. server-url)
               (.setParameterValue "file-name" fname)
               (.setParameterValue "mime-type" fmime))))

(defn sign-file
  "Takes a `server-url` and a fn `edn-ize` which is for interpreting the server response text into edn.
   Takes a `key-fn` function that takes one argument (the filename) and generates a object-key to
   use to create the file on S3.
   Also takes an input map with:
      :file                  - A `File` object
      :identifier (optional) - A variable used to uniquely identify this file upload.
                               This will be included in the response channel.
      :key (optional)        - The file-name parameter that is sent to the signing server. If a :key key
                               exists in the input-map it will be used instead of the key-fn as an object-key.
   Sends the request to the server-url to be signed."
  [server-url edn-ize key-fn headers-fn input-map-or-file ch]
  (let [xhr (XhrIo.)
        {:keys [file identifier] :as input-map}
        (if (map? input-map-or-file)
          input-map-or-file
          {:file input-map-or-file})
        fmap (file->map file)
        _ (assert (some? (:type fmap)) "File must have a file type provided, it cannot be nil.")
        fmap (cond
               (:key input-map) (assoc fmap :name (:key input-map))
               key-fn (update-in fmap [:name] key-fn)
               :else fmap)]
    (events/listen xhr goog.net.EventType.SUCCESS
                   (fn [_]
                     (put! ch {:f          file
                               :signature  (edn-ize (.getResponseText xhr))
                               :identifier identifier})
                     (close! ch)))
    (events/listen xhr goog.net.EventType.ERROR
                   (fn [_]
                     (let [error-code (.getLastErrorCode xhr)
                           error-message (.getDebugMessage goog.net.ErrorCode error-code)
                           http-error-code (.getStatus xhr)]
                       (put! ch {:identifier      identifier
                                 :error-code      error-code
                                 :error-message   (str "While trying to sign file: "
                                                       error-message)
                                 :http-error-code http-error-code})
                       (close! ch))))
    (. xhr send (signing-url server-url (:name fmap) (:type fmap)) "GET" nil (clj->js (if (some? headers-fn)
                                                                                        (headers-fn fmap)
                                                                                        {})))))

(defn formdata-from-map [m]
  (let [fd (new js/FormData)]
    (doseq [[k v] m]
      (.append fd (name k) v))
    fd))

(defn xml->map
  "Transforms a shallow XML document into a map."
  [xml]
  (let [kv (->> (array-seq (.. xml -firstChild -childNodes))
                (map (fn [n] [(.toLowerCase (.-nodeName n))
                              (gdom/getTextContent n)])))]
    (zipmap (map (comp keyword first) kv) (map second kv))))

(defn upload-file
  "Take the signature information `upload-info`, and a response channel `ch`,
  and does the S3 upload.
  Responds on the response channel with a map with:
    :file - the File that was uploaded
    :response - a map of S3 metadata: :key, :location, :bucket, :etag
    :identifier - an identifier pass-through value for caller to identify the file with
  If the upload-info has an :error-code key because the signing failed, skips the xhr/send of the file,
  and simply replies with the error messages and codes from the failed upload-info sign attempt."
  [upload-info ch]
  (if (contains? upload-info :error-code)
    (do
      (put! ch upload-info)
      (close! ch))
    (let [xhr (XhrIo.)
          identifier (:identifier upload-info)
          sig-fields [:key :Content-Type :success_action_status :policy :x-amz-credential :x-amz-signature :x-amz-algorithm :x-amz-date :acl]
          signature (select-keys (:signature upload-info) sig-fields)
          form-data (formdata-from-map signature)]
      (.append form-data "file" (:f upload-info)) ;need to add this last otherwise s3 throws an error
      (events/listen xhr goog.net.EventType.SUCCESS
                     (fn [_]
                       (put! ch {:file       (:f upload-info)
                                 :response   (when-let [response-xml (.getResponseXml xhr)]
                                               (xml->map response-xml))
                                 :xhr        xhr
                                 :identifier identifier})
                       (close! ch)))
      (events/listen xhr goog.net.EventType.ERROR
                     (fn [_]
                       (let [error-code (.getLastErrorCode xhr)
                             error-message (.getDebugMessage goog.net.ErrorCode error-code)
                             http-error-code (.getStatus xhr)]
                         (put! ch {:identifier      identifier
                                   :error-code      error-code
                                   :error-message   (str "While trying to upload file: "
                                                         error-message)
                                   :response        (when-let [response-xml (.getResponseXml xhr)]
                                                      (xml->map response-xml))
                                   :xhr             xhr
                                   :http-error-code http-error-code})
                         (close! ch))))
      (. xhr send (:action (:signature upload-info)) "POST" form-data))))

(defn s3-pipe
  "Takes a channel where completed uploads will be reported as a map and returns a channel where
  you can put File objects, or input maps that should get uploaded. (see `upload-file` and `sign-file`)
  May also take an options map with:
    :server-url      - the signing server url, defaults to \"/sign\"
    :response-parser - a function to process the signing response from the signing server into EDN
                       defaults to read-string.
    :key-fn          - a function used to generate the object key for the uploaded file on S3
                       defaults to nil, which means it will use the passed filename as the object key.
    :headers-fn      - a function used to create the headers for the GET request to the signing server."
  ([report-chan] (s3-pipe report-chan {}))
  ([report-chan opts]
   (let [opts (merge {:server-url "/sign" :response-parser #(reader/read-string %)}
                     opts)
         to-process (chan)
         signed (chan)]
     (pipeline-async 3
                     signed
                     (partial sign-file
                              (:server-url opts)
                              (:response-parser opts)
                              (:key-fn opts)
                              (:headers-fn opts))
                     to-process)
     (pipeline-async 3 report-chan upload-file signed)
     to-process)))
