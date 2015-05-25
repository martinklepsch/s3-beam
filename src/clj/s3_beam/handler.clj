(ns s3-beam.handler
  (:require [clojure.data.json :as json]
            [ring.util.codec :refer [base64-encode]])
  (:import (javax.crypto Mac)
           (javax.crypto.spec SecretKeySpec)
           (java.text SimpleDateFormat)
           (java.util Date TimeZone)))

(defn now-plus [n]
  "Returns current time plus `n` minutes as string"
  (let [f (SimpleDateFormat. "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'")]
    (.setTimeZone f (TimeZone/getTimeZone "UTC" ))
    (.format f (Date. (+ (System/currentTimeMillis) (* n 60 1000))))))

(defn policy
  "Generate policy for upload of `key` with `mime-type` to be uploaded
  within optional `expiration-window` (defaults to 60)."
  ([bucket key mime-type]
     (policy bucket key mime-type 60))
  ([bucket key mime-type expiration-window]
     (ring.util.codec/base64-encode
      (.getBytes (json/write-str { "expiration" (now-plus expiration-window)
                                   "conditions" [{"bucket" bucket}
                                                 {"acl" "public-read"}
                                                 ["starts-with" "$Content-Type" mime-type]
                                                 ["starts-with" "$key" key]
                                                 {"success_action_status" "201"}]})
                 "UTF-8"))))

(defn hmac-sha1 [key string]
  "Returns signature of `string` with a given `key` using SHA-1 HMAC."
  (ring.util.codec/base64-encode
   (.doFinal (doto (javax.crypto.Mac/getInstance "HmacSHA1")
               (.init (javax.crypto.spec.SecretKeySpec. (.getBytes key) "HmacSHA1")))
             (.getBytes string "UTF-8"))))

(defn sign-upload
  "Takes the request's params {:file-name :mime-type} and
  the aws-config {:bucket, :aws-zone, :aws-access-key, :aws-secret-key}
  to produce a map with all the data needed to upload to s3.
  May take an extra opts map with:
   :key-fn - A fn (ifn?) that takes the request params {:file-name :mime-type}
             and returns the key for S3 (i.e. hash of the file-name, UUID, etc.).
             It should be side-effect free and return a string. 
             Defaults to the :file-name keyword."
  ([params aws-config] (sign-upload params aws-config {:key-fn :file-name}))
  ([{:keys [file-name mime-type] :as params}
    {:keys [bucket aws-zone aws-access-key aws-secret-key]}
    {:keys [key-fn]}]
   {:pre [(ifn? key-fn)]}
   (let [key (key-fn params)]
     (assert (string? key)
       (str "The given :key-fn returned " key " with type " (type key)
         " when given file-name: " file-name
         " and mime-type: " mime-type ". It should return a string."))
     (let [p (policy bucket key mime-type)]
       {:action (str "https://" bucket "." aws-zone ".amazonaws.com/")
        :key    key 
        :Content-Type mime-type
        :policy p
        :acl    "public-read"
        :success_action_status "201"
        :AWSAccessKeyId aws-access-key
        :signature (hmac-sha1 aws-secret-key p)}))))

(defn s3-sign [bucket aws-zone access-key secret-key]
  (fn [request]
    {:status 200
     :body   (pr-str (sign-upload (:params request) {:bucket bucket
                                                     :aws-zone aws-zone
                                                     :aws-access-key access-key
                                                     :aws-secret-key secret-key}))}))
