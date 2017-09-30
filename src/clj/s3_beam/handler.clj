(ns s3-beam.handler
  (:require [clojure.data.json :as json]
            [ring.util.codec :refer [base64-encode]]
            [clojure.set :as set])
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
   within `expiration-window`, and `acl`."
  [bucket metadata expiration-window acl]
  (let [conditions      (->> (set/rename-keys metadata {:file-name "key"
                                                        :mime-type "Content-Type"})
                             (map (fn [[k v]] ["eq" (str "$" (name k)) (name v)])))]
    (-> {"expiration" (now-plus expiration-window)
         "conditions" (concat [{"bucket" bucket}
                               {"acl" acl}
                               {"success_action_status" "201"}]
                              conditions)}
        (json/write-str)
        (.getBytes "UTF-8")
        (ring.util.codec/base64-encode))))

(defn hmac-sha1 [key string]
  "Returns signature of `string` with a given `key` using SHA-1 HMAC."
  (ring.util.codec/base64-encode
   (.doFinal (doto (javax.crypto.Mac/getInstance "HmacSHA1")
               (.init (javax.crypto.spec.SecretKeySpec. (.getBytes key) "HmacSHA1")))
             (.getBytes string "UTF-8"))))

(def zone->endpoint
  "Mapping of AWS zones to S3 endpoints as documented here:
   http://docs.aws.amazon.com/general/latest/gr/rande.html#s3_region"
  {"us-east-1"      "s3"
   "us-west-1"      "s3-us-west-1"
   "us-west-2"      "s3-us-west-2"
   "eu-west-1"      "s3-eu-west-1"
   "eu-central-1"   "s3-eu-central-1"
   "ap-southeast-1" "s3-ap-southeast-1"
   "ap-southeast-2" "s3-ap-southeast-2"
   "ap-northeast-1" "s3-ap-northeast-1"
   "sa-east-1"      "s3-sa-east-1"})

(defn sign-upload [{:keys [file-name mime-type] :as metadata}
                   {:keys [bucket aws-zone aws-access-key aws-secret-key acl upload-url] :or {acl "public-read"}}]
  (if-not upload-url
    (assert (zone->endpoint aws-zone) "No endpoint found for given AWS Zone"))
  (assert aws-access-key "AWS Access Key cannot be nil")
  (assert aws-secret-key "AWS Secret Key cannot be nil")
  (assert acl "ACL cannot be nil")
  (assert mime-type "Mime-type cannot be nil.")
  (let [p (policy bucket metadata 60 acl)
        action (or upload-url
                   (str "https://" (zone->endpoint aws-zone)
                        ".amazonaws.com/" bucket "/"))
        response {:action action
                  :upload-url action
                  :key file-name
                  :Content-Type mime-type
                  :policy p
                  :acl acl
                  :success_action_status "201"
                  :AWSAccessKeyId aws-access-key
                  :signature (hmac-sha1 aws-secret-key p)}]
    (assoc response
      :form-data (merge (dissoc metadata :file-name :mime-type)
                        (dissoc response :action :upload-url)))))

(defn s3-sign
  ([bucket aws-zone access-key secret-key]
   (s3-sign bucket aws-zone access-key secret-key nil))
  ([bucket aws-zone access-key secret-key upload-url]
   (fn [request]
     {:status  200
      :body    (pr-str (sign-upload (:params request) {:bucket         bucket
                                                       :aws-zone       aws-zone
                                                       :aws-access-key access-key
                                                       :aws-secret-key secret-key
                                                       :upload-url     upload-url}))
      :headers {"Content-Type" "application/edn"}})))
