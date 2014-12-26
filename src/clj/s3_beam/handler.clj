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

(defn sign-upload [{:keys [file-name mime-type]}
                   {:keys [bucket aws-access-key aws-secret-key aws-zone]}]
  (let [p (policy bucket file-name mime-type)]
    {:action (str "https://" bucket "." aws-zone ".amazonaws.com/")
     :key    file-name
     :Content-Type mime-type
     :policy p
     :acl    "public-read"
     :success_action_status "201"
     :AWSAccessKeyId aws-access-key
     :signature (hmac-sha1 aws-secret-key p)}))

(defn s3-sign [bucket access-key secret-key & {:keys [aws-zone] :or {aws-zone "s3-eu-west-1"}}]
  (fn [request]
    {:status 200
     :body   (pr-str (sign-upload (:params request) {:bucket bucket
                                                     :aws-access-key access-key
                                                     :aws-secret-key secret-key
                                                     :aws-zone aws-zone}))}))
