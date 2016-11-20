# s3-beam

`s3-beam` is a Clojure/Clojurescript library designed to help you upload files
from the browser to S3 (CORS upload).

[](dependency)
```clojure
[org.martinklepsch/s3-beam "0.5.2"] ;; latest release
```
[](/dependency)

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
(def aws-zone "eu-west-1")
(def access-key "your-aws-access-key")
(def secret-key "your-aws-secret-key")

(defroutes routes
  (resources "/")
  (GET "/sign" {params :params} (s3b/s3-sign bucket aws-zone access-key secret-key)))
```

If you want to use a route different than `/sign`, define it in the
handler, `(GET "/my-cool-route" ...)`, and then pass it in the options
map to `s3-pipe` in the frontend.

If you are serving your S3 bucket with CloudFront, or another CDN/proxy, you can pass
`upload-url` as a fifth parameter to `s3-sign`, so that the ClojureScript client is directed
to upload through this bucket. You still need to pass the bucket name, as the policy that is
created and signed is based on the bucket name.

### 3. Integrate the upload pipeline into your frontend

In your frontend code you can now use `s3-beam.client/s3-pipe`.
`s3-pipe`'s argument is a channel where completed uploads will be
reported. The function returns a channel where you can put File
objects of a file map that should get uploaded. It can also take an
extra options map with the previously mentioned `:server-url` like so:

    (s3/s3-pipe uploaded {:server-url "/my-cool-route"}) ; assuming s3-beam.client is NS aliased as s3

The full options map spec is:

- `:server-url` the signing server url, defaults to "/sign"
- `:response-parser` a function to process the signing response from the signing server into EDN
                     defaults to read-string.
- `:key-fn` a function used to generate the object key for the uploaded file on S3
                   defaults to nil, which means it will use the passed filename as the object key.
- `:headers-fn` a function used to create the headers for the GET request to the signing server.
                   The returned headers should be a Clojure map of header name Strings to corresponding
                   header value Strings.

If you choose to place a file map instead of a `File` object, you file map should follow:

- `:file`                  A `File` object
- `:identifier` (optional) A variable used to uniquely identify this file upload.
                           This will be included in the response channel.
- `:key` (optional)        The file-name parameter that is sent to the signing server. If a `:key` key
                           exists in the input-map it will be used instead of the key-fn as an object-key.
- `:metadata` (optional)   Metadata for the object. See [Amazon's API docs](http://docs.aws.amazon.com/AmazonS3/latest/API/RESTObjectPOST.html)
                           for full details on which keys are supported. Keys and values can be strings or keywords.
                           **N.B.** Keys not on that list will not be accepted. If you want to set arbitrary metadata,
                           it needs to be prefixed with `x-amz-meta-*`.

An example using it within an Om component:

```clj
(ns your.client
  (:require [s3-beam.client :as s3]
  ...))

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

#### Return values

The spec for the returned map (in the example above the returned map is `v`):

- `:file` The `File` object from the uploaded file
- `:response` The upload response from S3 as a map with:
 - `:location` The S3 URL of the uploaded file
 - `:bucket` The S3 bucket where the file is located
 - `:key` The S3 key for the file
 - `:etag` The etag for the file
- `:xhr` The `XhrIo` object used to POST to S3
- `:identifier` A value used to uniquely identify the uploaded file

Or, if an error occurs during upload processing, an error-map will be placed on the response channel:

- `:identifier` A variable used to uniquely identify this file upload. This will be included in the response channel.
- `:error-code` The error code from the XHR
- `:error-message` The debug message from the error code
- `:http-error-code` The HTTP error code

## Changes

#### Unreleased 0.?.?

- Add support for assigning metadata to files when uploading them. See the file-map spec above for more details.
- Tweak keys and parameters for communication between the client and server parts of the library. This is backwards and
  forwards compatible between clients and servers running 0.5.2 and 0.?.?.

#### 0.5.2

- Allow the user to upload to S3 through a custom URL as an extra parameter to `sign-upload`
- Support bucket names with a '.' in them
- Add asserts that arguments are provided

#### 0.5.1

- Allow the upload-queue to be passed an input-map instead of a file. This
  input-map follows the spec:

    - `:file`                  A `File` object
    - `:identifier` (optional) A variable used to uniquely identify this file upload.
                               This will be included in the response channel.
    - `:key` (optional)        The file-name parameter that is sent to the signing server. If a `:key` key
                               exists in the input-map it will be used instead of the key-fn as an object-key.
- Introduce error handling. When an error has been thrown while uploading a file to S3
  an error-map will be put onto the channel. The error-map follows the spec:

    - `:identifier`      A variable used to uniquely identify this file upload. This will be
                         included in the response channel.
    - `:error-code`      The error code from the XHR
    - `:error-message`   The debug message from the error code
    - `:http-error-code` The HTTP error code
- New options are available in the options map:

    - `:response-parser` a function to process the signing response from the signing server into EDN
                         defaults to read-string.
    - `:key-fn`          a function used to generate the object key for the uploaded file on S3
                         defaults to nil, which means it will use the passed filename as the object key.
    - `:headers-fn`      a function used to create the headers for the GET request to the signing server.
- Places a map into the upload-channel with:
    - `:file`       The `File` object from the uploaded file
    - `:response`   The upload response from S3 as a map with:
     - `:location` The S3 URL of the uploaded file
     - `:bucket`   The S3 bucket where the file is located
     - `:key`      The S3 key for the file
     - `:etag`     The etag for the file
    - `:xhr`        The `XhrIo` object used to POST to S3
    - `:identifier` A value used to uniquely identify the uploaded file

#### 0.4.0

- Support custom ACLs. The `sign-upload` function that can be used to
  implement custom signing routes now supports an additional `:acl` key
  to upload assets with a different ACL than `public-read`.

        (sign-upload {:file-name "xyz.html" :mime-type "text/html"}
                     {:bucket bucket
                      :aws-zone aws-zone
                      :aws-access-key access-key
                      :aws-secret-key secret-key
                      :acl "authenticated-read"})
- Changes the arity of `s3-beam.handler/policy` function.

#### 0.3.1

- Correctly look up endpoints given a zone parameter ([#10](https://github.com/martinklepsch/s3-beam/pull/10/files))

#### 0.3.0

-  Allow customization of server-side endpoint ([1cb9b27](https://github.com/martinklepsch/s3-beam/commit/1cb9b2703691e172e275a95490b3fc8209dfa409))

        (s3/s3-pipe uploaded {:server-url "/my-cool-route"})

#### 0.2.0

- Allow passing of `aws-zone` parameter to `s3-sign` handler function ([b880736](https://github.com/martinklepsch/s3-beam/commit/b88073646b7c92b5493a168ce25d27feaa130c9e))

## Contributing

Pull requests and issues are welcome. There are a few things I'd like to improve:

* **Testing:** currently there are no tests
* **Error handling:** what happens when the request fails?
* **Upload progress:** XhrIo supports `PROGRESS` events and especially
  for larger uploads it'd be nice to have them

## Maintainers

Martin Klepsch
Daniel Compton

## License

Copyright © 2014 Martin Klepsch

Distributed under the Eclipse Public License either version 1.0 or (at
your option) any later version.
