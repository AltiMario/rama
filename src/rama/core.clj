(ns rama.core
  (:require [aleph.http :as http]
            [clojure.java.io :as io]
            [clojure.edn :as edn]
            [clojure.tools.logging :as log]
            [safely.core :refer [safely]]
            [compojure.api.sweet :refer :all]
            [schema.core :as s]
            ))



(defn default-wrappers [config handler]
  (let [{:keys [name description path]} (:api config)]
    (api
     ;;
     ;; API docs
     ;;
     {:swagger
      {:ui (or path "/api-docs")
       :spec "/swagger.json"
       :data {:info {:title (or (str name " API") "API")
                     :description (or description "Just another API")}
              :tags [{:name "api", :description (or description "Just another API")}]}}}
     ;;
     ;; Health check
     ;;
     (GET "/healthcheck" []
          :summary "Returns 200 ok if the service is running"
          :return {:message String}
          {:satus 200 :body {:message "OK"}})

     ;;
     ;; Application handlers
     ;;
     handler

     ;;
     ;; everything else is NOT FOUND
     ;;
     (undocumented
      (fn [_]
        {:status 404 :body {:message "Not found"}})))))


(defn config
  ([]
   (->> ["./config/config.edn"
         (and (System/getenv "HOME") (str (System/getenv "HOME") "/config/config.edn"))
         (io/resource "config.edn")]
        (filter identity)
        (some config)))
  ([file]
   (when file
     (let [config (safely (-> file slurp edn/read-string)
                          :on-error :log-level :trace :default nil)]
       (log/debug "Attempting to read configuration from:"
                  file (if config "OK!" "FAIL!"))
       config))))


(defn start-server [{:keys [server] :as config} handler]
  (log/info "Starting server on:" server)
  (http/start-server (default-wrappers config handler) server))
