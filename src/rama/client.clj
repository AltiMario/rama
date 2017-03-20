(ns rama.client
  "
  This namespace contains HTTP client functions which simplify communications
  with other services. Here some key features:

       - connection pooling
       - transit over json encoding
       - json support


  usage:

      ;; fetch transit encoded content from remote service
      ;; by using get, post, put, and delete
      (get \"http://localhost:8080/healthcheck\")
      ;; optionally encode a body request
      (get \"http://localhost:8080/healthcheck\" {:foo \"bar\"})
      (post \"http://localhost:8080/api/service1\" {:x 23 :y 10})

      ;; alternatively use JSON encoding for request/response
      ;; by using jget, jpost, jput, and jdelete
      (jget \"http://localhost:8080/healthcheck\" {:foo \"bar\"})
      (jpost \"http://localhost:8080/api/service1\" {:x 23 :y 10})


  "

  (:require [aleph.http :as http]
            [manifold.deferred :as d]
            [byte-streams :as bs]
            [cognitect.transit :as transit]
            [cheshire.core :as json]
            [clojure.java.io :as io])
  (:import [java.io ByteArrayInputStream ByteArrayOutputStream StringBufferInputStream]))


(defn to-transit [data]
  (when data
    (let [out    (ByteArrayOutputStream. 1024)] ;; initial size, extended automatically
      (transit/write (transit/writer out :json) data)
      (String. (.toByteArray out) "UTF-8"))))


(defn from-transit [^String input]
  (when input
    (transit/read
     (transit/reader (StringBufferInputStream. input)  :json))))


(defn to-json
  "Convert a Clojure data structure into it's json pretty print equivalent
   or compact version.
   usage:
   (to-json {:a \"value\" :b 123} :pretty true)
   ;=> {
   ;=>   \"a\" : \"value\",
   ;=>   \"b\" : 123
   ;=> }
   (to-json {:a \"value\" :b 123})
   ;=> {\"a\":\"value\",\"b\":123}
   "
  [data & {:keys [pretty] :or {pretty false}}]
  (when
    (-> data
        (json/generate-string {:pretty pretty :date-format "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'"})
        ((fn [s] (if pretty (str s \newline) s))))))



(defn from-json
  "Convert a json string into a Clojure data structure
   with keyword as keys"
  [data]
  (when data
    (-> data
        (json/parse-string true))))


(defn- requests* [req]
  ({:get    http/get
    :post   http/post
    :put    http/put
    :delete http/delete}
   req (fn [& args]
         (throw
          (IllegalArgumentException.
           (str "Invalid or unsupported operation HTTP operation:" req))))))


(defn- http-request
  ([op url body]
   (http-request op url body nil))
  ([op url body {:keys [connection-timeout request-timeout pool-timeout
                        marshall un-marshall] :as opts}]
   @(d/chain
     ((requests* op)
      url
      (merge
       {:connection-timeout (or connection-timeout 5000)
        :request-timeout (or request-timeout 5000)
        :pool-timeout (or pool-timeout 5000)
        :body (marshall body)}
       opts))
     ;;(fn [r] (clojure.pprint/pprint r) r)
     :body
     bs/to-string
     un-marshall)))


(def ^:private transit-opts
  {:content-type :transit+json :accept :transit+json
   :marshall to-transit
   :un-marshall from-transit})


(def ^:private json-opts
  {:content-type :json :accept :json
   :marshall to-json
   :un-marshall from-json})



;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;                                                                            ;;
;;          ---==| T R A N S I T   H T T P   R E Q U E S T S |==----          ;;
;;                                                                            ;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;


(defn get
  "HTTP GET method with body encoding in transit+json"
  ([url]
   (get url nil nil))
  ([url body]
   (get url body nil))
  ([url body {:keys [connection-timeout request-timeout pool-timeout] :as opts}]
   (http-request :get url body (merge transit-opts opts))))


(defn post
  "HTTP POST method with body encoding in transit+json"
  ([url body]
   (post url body nil))
  ([url body {:keys [connection-timeout request-timeout pool-timeout] :as opts}]
   (http-request :post url body (merge transit-opts opts))))


(defn put
  "HTTP PUT method with body encoding in transit+json"
  ([url body]
   (put url body nil))
  ([url body {:keys [connection-timeout request-timeout pool-timeout] :as opts}]
   (http-request :put url body (merge transit-opts opts))))


(defn delete
  "HTTP DELETE method with body encoding in transit+json"
  ([url]
   (delete url nil nil))
  ([url body]
   (delete url body nil))
  ([url body {:keys [connection-timeout request-timeout pool-timeout] :as opts}]
   (http-request :delete url body (merge transit-opts opts))))



;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;                                                                            ;;
;;              ---==| J S O N   H T T P   R E Q U E S T |==----              ;;
;;                                                                            ;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;


(defn jget
  "HTTP GET method with body encoding in json"
  ([url]
   (jget url nil nil))
  ([url body]
   (jget url body nil))
  ([url body {:keys [connection-timeout request-timeout pool-timeout] :as opts}]
   (http-request :get url body (merge json-opts opts))))


(defn jpost
  "HTTP POST method with body encoding in json"
  ([url body]
   (jpost url body nil))
  ([url body {:keys [connection-timeout request-timeout pool-timeout] :as opts}]
   (http-request :post url body (merge json-opts opts))))


(defn jput
  "HTTP PUT method with body encoding in json"
  ([url body]
   (jput url body nil))
  ([url body {:keys [connection-timeout request-timeout pool-timeout] :as opts}]
   (http-request :put url body (merge json-opts opts))))


(defn jdelete
  "HTTP DELETE method with body encoding in json"
  ([url]
   (jdelete url nil nil))
  ([url body]
   (jdelete url body nil))
  ([url body {:keys [connection-timeout request-timeout pool-timeout] :as opts}]
   (http-request :delete url body (merge json-opts opts))))
