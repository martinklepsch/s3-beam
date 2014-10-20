# s3-beam

`s3-beam` is a Clojure/Clojurescript library designed to help you upload files
from the browser to S3 (CORS upload).

```clj
[org.martinklepsch/s3-beam "0.1.0"]
```

## Usage

To upload files directly to S3 you need to send special request
parameters that are based on your AWS credentials, the file name, mime
type, date etc.
Since we **don't want to store our credentials in the client** these
parameters need to be generated on the server side.
For this reason this library consists of two parts:

1. A pluggable route that will send back the required parameters for a
   given file-name & mime-type
2. A client-side core.async pipeline setup that will retrieve the
   special parameters for a given File object, upload it to S3 and
   report back to you

### 1. Enable CORS on your S3 bucket

Please follow Amazon's [official documentation](http://docs.aws.amazon.com/AmazonS3/latest/dev/cors.html).

### 2. Plug-in the route to sign uploads

```clj
(ns your.server
  (:require [s3-beam.handler :as s3b]
            [compojure.core :refer [GET defroutes]]
            [compojure.route :refer [resources]]))

(def bucket "your-bucket")
(def access-key "your-aws-access-key")
(def secret-key "your-aws-secret-key")

(defroutes routes
  (resources "/")
  (GET "/sign" {params :params} (s3b/s3-sign bucket access-key secret-key)))
```

**NOTE: for now the only supported route to sign uploads is `/sign`. In a future
release this will be customizable.**

### 3. Integrate the upload pipeline into your frontend

In your frontend code you can now use `s3-beam.client/s3-pipe`. `s3-pipe`'s argument is a channel
where completed uploads will be reported. The function returns a channel where you can put File objects
that should get uploaded.

An example using it within an Om component:

```clj
(defcomponent upload-form [app-state owner]
  (init-state [_]
    (let [uploaded (chan 20)]
      {:dropped-queue (chan 20)
       :upload-queue (s3/s3-pipe uploaded)
       :uploaded uploaded
       :uploads []}))
  (did-mount [_]
    (listen-file-drop js/document (om/get-state owner :dropped-queue))
    (go (while true
          (let [{:keys [dropped-queue upload-queue uploaded uploads]} (om/get-state owner)]
            (let [[v ch] (alts! [dropped-queue uploaded])]
              (cond
               (= ch dropped-queue) (put! upload-queue v)
               (= ch uploaded) (om/set-state! owner :uploads (conj uploads v))))))))
  (render-state [this state]
    ; ....
    )
```

## Contributing

Pull requests and issues are welcome. There are a few things I'd like to improve:

* **Testing:** currently there are no tests
* **Custom route:** allow use of other routes than `/sign`
* **Error handling:** what happens when the request fails?
* **Upload progress:** XhrIo supports `PROGRESS` events and especially
  for larger uploads it'd be nice to have them


## License

Copyright © 2014 Martin Klepsch

Distributed under the Eclipse Public License either version 1.0 or (at
your option) any later version.
